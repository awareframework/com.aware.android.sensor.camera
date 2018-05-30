package com.awareframework.android.sensor.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
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
            private val onSessionComplete: (String) -> Unit) : CameraHandler {

        override val facing: CameraFace
            get() = config.facing

        /**
         * Output file for video
         */
        private var nextVideoAbsolutePath: String? = null

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
        }

        override fun onCameraConfigureFailure() {
            loge("Camera configuration failed.")
            onCameraClosed()
        }

        override fun onCameraConfigured() {
            logd("Camera configuration successful.")
            mediaRecorder?.start()

            Handler().postDelayed({
                stopRecording()
            }, (config.videoLength * 1000).toLong())
        }

        override fun onCameraOpened() {
            startRecording()
        }

        override fun onCameraClosed() {
            mediaRecorder?.release()
            mediaRecorder = null
        }

        override fun onCameraDisconnected() {
            loge("Camera disconnected.")
            onCameraClosed()
        }

        override fun onCameraError(error: Int) {
            loge("Camera error occured. Error code: $error")
            nextVideoAbsolutePath = null // so that we don't save the file.
        }

        override fun onCaptureSizeSelection(sizes: Array<Size>): Size {
            videoSize = chooseVideoSize(sizes, config.preferredWidth, config.preferredHeight)
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

                onSessionComplete(it)
            }

            nextVideoAbsolutePath = null
        }

        private fun setUpMediaRecorder() {
            if (nextVideoAbsolutePath.isNullOrEmpty()) {
                nextVideoAbsolutePath = getVideoFilePath(config.contentPath)
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            when (camera.sensorOrientation) {
                SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                    mediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
                SENSOR_ORIENTATION_INVERSE_DEGREES ->
                    mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
            }

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


    override fun onCreate() {
        super.onCreate()

        dbEngine = Engine.Builder(applicationContext)
                .setEncryptionKey(CONFIG.dbEncryptionKey)
                .setHost(CONFIG.dbHost)
                .setPath(CONFIG.dbPath)
                .setType(CONFIG.dbType)
                .build()

        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            loge("Camera permission is not granted. Gracefully stopping.")
            stopSelf()
        } else {
            if (primaryCameraRecordSession == null) {
                primaryCameraRecordSession = object : CameraRecordSession(applicationContext,
                        CONFIG,
                        saveVideoRecord) {

                    // Attempting to initialize the secondary camera since some devices doesn't support
                    // having two cameras open at the same time.
                    override fun onCameraOpened() {
                        super.onCameraOpened()

                        if (CONFIG.secondaryFacing != CameraFace.NONE) {
                            secondaryCameraRecordSession = CameraRecordSession(
                                    applicationContext,
                                    CONFIG.copy().apply { facing = CONFIG.secondaryFacing },
                                    saveVideoRecord)

                            secondaryCameraRecordSession?.record()
                        }
                    }
                }
            }

            primaryCameraRecordSession?.record()
        }

        return START_STICKY
    }

    private val saveVideoRecord: (filePath: String) -> Unit = {
        dbEngine?.save(VideoData(
                filePath = it,
                length = CONFIG.videoLength
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
    }

    private fun hasPermissionsGranted(permissions: Array<String>) =
            permissions.none {
                applicationContext.checkCallingOrSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onSync(intent: Intent?) {
        // TODO: sync
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