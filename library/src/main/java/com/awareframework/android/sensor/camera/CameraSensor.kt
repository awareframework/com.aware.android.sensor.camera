package com.awareframework.android.sensor.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.db.Engine
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Class decription
 *
 * @author  sercant
 * @date 21/04/2018
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

        internal var instance: CameraSensor? = null
    }

    /**
     * A reference to the opened [android.hardware.camera2.CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * The [android.util.Size] of video recording.
     */
    private lateinit var videoSize: Size

    /**
     * Whether the app is recording video now
     */
    private var isRecordingVideo = false

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraSensor.cameraDevice = cameraDevice
            if (isWaitingForCamera) {
                startRecordingVideo(CONFIG.videoLengthInMillis())
            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraSensor.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraSensor.cameraDevice = null
            stopSelf()
        }

    }

    /**
     * Output file for video
     */
    private var nextVideoAbsolutePath: String? = null

    private var mediaRecorder: MediaRecorder? = null

    private var isWaitingForCamera: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
//        if (textureView.isAvailable) {
//            openCamera(textureView.width, textureView.height)
//        } else {
//            textureView.surfaceTextureListener = surfaceTextureListener
//        }

        dbEngine = Engine.Builder(applicationContext)
                .setEncryptionKey(CONFIG.dbEncryptionKey)
                .setHost(CONFIG.dbHost)
                .setPath(CONFIG.dbPath)
                .setType(CONFIG.dbType)
                .build()

        if (cameraDevice == null) {
            isWaitingForCamera = true
            openCamera()
        } else {
            // TODO (sercant): if there is delay add here
            startRecordingVideo(CONFIG.videoLengthInMillis())
        }

        instance = this

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        closeCamera()
        stopBackgroundThread()

        instance = null
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun hasPermissionsGranted(permissions: Array<String>) =
            permissions.none {
                applicationContext.checkCallingOrSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

    /**
     * Tries to open a [CameraDevice]. The result is listened by [stateCallback].
     *
     * Lint suppression - permission is checked in [hasPermissionsGranted]
     */
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            Log.w(TAG, "Missing permissions! Aborting.")
            stopSelf()
            return
        }

        val manager = applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            val cameraId = chooseCameraId(CONFIG.facing, manager)

            // Choose the sizes for camera preview and video recording
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get available preview/video sizes")

            saveCameraCharacteristics(characteristics)

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))

            mediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Cannot access the camera.")
            stopSelf()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2API is used but not supported on the device")
            stopSelf()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    private fun chooseCameraId(face: Camera.CameraFace, cameraManager: CameraManager): String {
        val lensCharacteristics = when (face) {
            Camera.CameraFace.FRONT -> CameraCharacteristics.LENS_FACING_FRONT.toString()
            Camera.CameraFace.BACK -> CameraCharacteristics.LENS_FACING_BACK.toString()
        }

        return cameraManager.cameraIdList.find {
            it == lensCharacteristics
        } ?: cameraManager.cameraIdList[0]
    }

    /**
     * Close the [CameraDevice].
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            closeCaptureSession()

            mediaRecorder?.release()
            mediaRecorder = null

            cameraDevice?.close()
            cameraDevice = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        if (nextVideoAbsolutePath.isNullOrEmpty()) {
            nextVideoAbsolutePath = getVideoFilePath(CONFIG.contentPath)
        }

        val rotation = applicationContext.resources.configuration.orientation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                mediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }

        mediaRecorder?.apply {
            //            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(nextVideoAbsolutePath)
            setVideoEncodingBitRate(CONFIG.bitrate)
            setVideoFrameRate(CONFIG.frameRate)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    private fun saveCameraCharacteristics(characteristics: CameraCharacteristics) {
        // TODO (sercant): save camera characteristics
//        dbEngine?.save(characteristics.get(CameraCharacteristics.))
    }

    private fun getVideoFilePath(path: String): String {
        return if (path.isEmpty()) applicationContext.getExternalFilesDir(null).absolutePath + "/${System.currentTimeMillis()}.mp4"
        else "$path/${System.currentTimeMillis()}.mp4"
    }

    private fun startRecordingVideo(length: Long) {
        if (cameraDevice == null) {
            isWaitingForCamera = true
            openCamera()
        }

        if (isRecordingVideo) {
            Log.w(TAG, "Video recording session is already in progress.")
        }

        try {
            closeCaptureSession()
            setUpMediaRecorder()

            // Set up Surface for MediaRecorder
            val recorderSurface = mediaRecorder!!.surface
            val surfaces = ArrayList<Surface>().apply {
                add(recorderSurface)
            }
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(recorderSurface)
            }

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice?.createCaptureSession(surfaces,
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            captureSession = cameraCaptureSession
                            updateCapture()
//                            applicationContext.runOnUiThread {
                            isRecordingVideo = true
                            mediaRecorder?.start()

                            Handler().postDelayed({
                                stopRecordingVideo()
                            }, length)
//                            }
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            Log.e(TAG, "Failed.")
                            this@CameraSensor.stopSelf()
                        }
                    }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Update the camera preview. [startPreview] needs to be called in advance.
     */
    private fun updateCapture() {
        if (cameraDevice == null) return

        try {
            setUpCaptureRequestBuilder(captureRequestBuilder)
            HandlerThread("CameraPreview").start()
            captureSession?.setRepeatingRequest(captureRequestBuilder.build(),
                    null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun closeCaptureSession() {
        captureSession?.close()
        captureSession = null
    }

    private fun stopRecordingVideo() {
        mediaRecorder?.apply {
            stop()
            reset()
        }

        if (nextVideoAbsolutePath != null) {
            Log.e(TAG, "Video saved: $nextVideoAbsolutePath")
        }

        nextVideoAbsolutePath = null
        closeCamera()
        isRecordingVideo = false
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
        it.width == it.height * 4 / 3 && it.width <= 1080
    } ?: choices[choices.size - 1]

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onSync(intent: Intent?) {
        // TODO (sercant): start sync of camera characteristics
//        dbEngine?.startSync()
    }

}