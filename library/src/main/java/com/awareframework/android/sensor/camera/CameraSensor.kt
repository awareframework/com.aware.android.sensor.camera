package com.awareframework.android.sensor.camera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.PRIORITY_MIN
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.camera.CameraWrapper.State.*
import com.awareframework.android.sensor.camera.model.VideoData

/**
 * Camera service
 *
 * @author  sercant
 * @date 29/05/2018
 */
class CameraSensor : AwareSensor() {

    companion object {
        const val TAG = "AwareCamera"
        val VIDEO_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        internal val CONFIG: Camera.CameraConfig = Camera.CameraConfig()

        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
        private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }

        var instance: CameraSensor? = null
    }

    private var primaryCameraRecordSession: CameraRecordSession? = null
    private var secondaryCameraRecordSession: CameraRecordSession? = null

    open class CameraRecordSession(
            private val context: Context,
            private val config: Camera.CameraConfig,
            private val onSessionComplete: (String, CameraRecordSession) -> Unit,
            val parent: CameraRecordSession? = null) : CameraHandler {

        override val facing: CameraFace
            get() = config.facing

        /**
         * Output file for video
         */
        var nextVideoAbsolutePath: String? = null

        private var mediaRecorder: MediaRecorder? = null

        /**
         * The [android.util.Size] of video recording.
         */
        private lateinit var videoSize: Size

        @Suppress("LeakingThis")
        val camera: CameraWrapper = CameraWrapper(context, this)

        fun record() {
            when (camera.state) {
                OPEN -> {
                    logd("Starting recording")
                    startRecording()
                }

                IDLE -> {
                    logd("Opening camera.")
                    camera.open()
                }

                BUSY, WAITING_FOR_CAMERA -> {
                    logd("Camera already in use.")
                }
            }
        }

        fun destroy() {
            when (camera.state) {
                OPEN -> {
                    camera.close()
                    mediaRecorder = null
                }
                BUSY, WAITING_FOR_CAMERA -> {
                    stopRecording()
                }
                IDLE -> mediaRecorder = null
            }
        }

        override fun onCameraSessionEnd() {
            camera.close()

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_SESSION_END))
        }

        override fun onCameraConfigureFailure() {
            loge("Camera configuration failed.")
            onCameraClosed()

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_CONFIGURE_FAILED))
        }

        override fun onCameraConfigured() {
            logd("Camera configuration successful.")
            mediaRecorder?.start()

            Handler().postDelayed({
                stopRecording()
            }, (config.videoLength * 1000).toLong())

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_CONFIGURED))
        }

        override fun onCameraOpened() {
            startRecording()

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_OPENED))
        }

        override fun onCameraClosed() {
            mediaRecorder?.release()
            mediaRecorder = null

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_CLOSED))
        }

        override fun onCameraDisconnected() {
            loge("Camera disconnected.")
            onCameraClosed()

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_DISCONNECTED))
        }

        override fun onCameraError(error: Int) {
            loge("Camera error occured. Error code: $error")
            nextVideoAbsolutePath = null // so that we don't save the file.

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_ERROR).apply {
                putExtra(Camera.EXTRA_CAMERA_ERROR, error)
            })
        }

        override fun onCaptureSizeSelection(sizes: Array<Size>): Size {
            videoSize = chooseVideoSize(sizes, config.preferredWidth, config.preferredHeight)

            context.sendBroadcast(Intent(Camera.ACTION_CAMERA_CAPTURE_SIZE_SELECTION).apply {
                putExtra(Camera.EXTRA_CAMERA_CAPTURE_SIZE_SELECTION_WIDTH, videoSize.width)
                putExtra(Camera.EXTRA_CAMERA_CAPTURE_SIZE_SELECTION_HEIGHT, videoSize.height)
            })

            return videoSize
        }

        private fun startRecording() {
            if (mediaRecorder == null) {
                mediaRecorder = MediaRecorder()
                setUpMediaRecorder()
            }

            mediaRecorder?.let {
                camera.startRecordingVideo(listOf(it.surface))
            }
        }

        private fun stopRecording() {
            camera.stopRecordingVideo()
            camera.close()

            nextVideoAbsolutePath?.let {
                logd("Video saved: $it")

                onSessionComplete(it, this)
            }

            // nextVideoAbsolutePath = null
        }

        private fun setUpMediaRecorder() {
            nextVideoAbsolutePath = getVideoFilePath(config.contentPath)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            val hint = when (camera.sensorOrientation) {
                SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                    DEFAULT_ORIENTATIONS.get(rotation)
                SENSOR_ORIENTATION_INVERSE_DEGREES ->
                    INVERSE_ORIENTATIONS.get(rotation)
                else ->
                    DEFAULT_ORIENTATIONS.get(rotation)
            }
            mediaRecorder?.setOrientationHint(hint)

            // logd("rotation: $rotation\tsensorOrientation: ${camera.sensorOrientation}\thint: $hint")


            mediaRecorder?.apply {
                // setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(nextVideoAbsolutePath)
                setVideoEncodingBitRate(config.bitrate)
                setVideoFrameRate(config.frameRate)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                // setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
        }

        /**
         * Iterate over supported camera video sizes to see which one best fits the
         * dimensions of the given view while maintaining the aspect ratio. If none can,
         * be lenient with the aspect ratio.
         *
         * @param choices Supported camera video sizes.
         * @param w     The width of the view.
         * @param h     The height of the view.
         * @return Best match camera video size to fit in the view.
         */
        private fun chooseVideoSize(choices: Array<Size>, w: Int, h: Int): Size {
            // Use a very small tolerance because we want an exact match.
            val aspectRatioTolerance = 0.1
            val targetRatio = w.toDouble() / h

            // Start with max value and refine as we iterate over available video sizes. This is the
            // minimum difference between view and camera height.
            var minDiff = Int.MAX_VALUE

            var optimalSize: Size? = null

            // Try to find a video size that matches aspect ratio and the target view size.
            // Iterate over all available sizes and pick the largest size that can fit in the view and
            // still maintain the aspect ratio.
            for (size in choices) {
                val ratio = size.width.toDouble() / size.height
                if (Math.abs(ratio - targetRatio) > aspectRatioTolerance)
                    continue
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size
                    minDiff = Math.abs(size.height - h)
                }
            }

            // Cannot find video size that matches the aspect ratio, ignore the requirement
            if (optimalSize == null) {
                minDiff = Int.MAX_VALUE
                for (size in choices) {
                    if (Math.abs(size.height - h) < minDiff) {
                        optimalSize = size
                        minDiff = Math.abs(size.height - h)
                    }
                }
            }

            return optimalSize ?: choices[0]
        }

        private fun getVideoFilePath(path: String): String {
            return if (path.isEmpty()) context.filesDir.absolutePath + "/${System.currentTimeMillis()}.mp4"
            else "$path/${System.currentTimeMillis()}.mp4"
        }

    }

    val cameraBroadcastReceiver = object : AwareSensor.SensorBroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                Camera.ACTION_START_RECORDING -> startRecordingVideo()
                Camera.ACTION_STOP_RECORDING -> {
                    primaryCameraRecordSession?.destroy()
                    secondaryCameraRecordSession?.destroy()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        dbEngine = Engine.Builder(applicationContext)
                .setEncryptionKey(CONFIG.dbEncryptionKey)
                .setHost(CONFIG.dbHost)
                .setPath(CONFIG.dbPath)
                .setType(CONFIG.dbType)
                .build()

        registerReceiver(cameraBroadcastReceiver, IntentFilter().apply {
            addAction(Camera.ACTION_START_RECORDING)
            addAction(Camera.ACTION_STOP_RECORDING)
        })

        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superReturn = super.onStartCommand(intent, flags, startId)

        // startForeground(0, null)

        if (!CONFIG.enabled) {
            stopSelf()
            return superReturn
        }

        startForeground()

        logd("Camera sensor started.")
//        else {
//            startRecordingVideo()
//        }

        return START_STICKY
    }

    fun startRecordingVideo() {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            loge("Camera permission is not granted. Gracefully stopping.")
            stopSelf()
        } else {
            if (primaryCameraRecordSession == null) {
                primaryCameraRecordSession = object : CameraRecordSession(applicationContext,
                        CONFIG,
                        onSaveVideoRecord) {

                    // Attempting to initialize the secondary camera since some devices doesn't support
                    // having two cameras open at the same time.
                    override fun onCameraOpened() {
                        super.onCameraOpened()

                        if (CONFIG.secondaryFacing != CameraFace.NONE && CONFIG.facing != CONFIG.secondaryFacing) {
                            secondaryCameraRecordSession = CameraRecordSession(
                                    applicationContext,
                                    CONFIG.copy().apply { facing = CONFIG.secondaryFacing },
                                    onSaveVideoRecord,
                                    parent = primaryCameraRecordSession)

                            secondaryCameraRecordSession?.record()
                        }
                    }
                }
            }

            primaryCameraRecordSession?.record()
        }
    }

    private val onSaveVideoRecord: (filePath: String, session: CameraRecordSession) -> Unit = { fp, session ->
        val parentFilePath = session.parent?.nextVideoAbsolutePath

        dbEngine?.save(VideoData(
                filePath = fp,
                length = CONFIG.videoLength,
                parentFilePath = parentFilePath
        ).apply {
            label = CONFIG.label
            timestamp = System.currentTimeMillis()
            deviceId = CONFIG.deviceId
        }, VideoData.TABLE_NAME)

        sendBroadcast(Intent(Camera.ACTION_VIDEO_RECORDED))
    }

    override fun onDestroy() {
        super.onDestroy()

        primaryCameraRecordSession?.destroy()
        secondaryCameraRecordSession?.destroy()

        dbEngine?.close()

        instance = null

        unregisterReceiver(cameraBroadcastReceiver)
    }

    private fun hasPermissionsGranted(permissions: Array<String>) =
            permissions.none {
                applicationContext.checkCallingOrSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

//    override fun onBind(p0: Intent?): IBinder? {
//        return null
//    }

    override fun onSync(intent: Intent?) {
        // TODO: sync
    }

    override fun onBind(intent: Intent?): IBinder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun startForeground() {
        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_fiber_smart_record_black_24dp)
                .setPriority(PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText("Aware camera service is running.")
                .build()
        startForeground(101, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "aware_camera_service"
        val channelName = "Aware Camera Service"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }
}

private fun logd(text: String) {
    Log.d(CameraSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(CameraSensor.TAG, text)
}

private fun loge(text: String) {
    Log.e(CameraSensor.TAG, text)
}