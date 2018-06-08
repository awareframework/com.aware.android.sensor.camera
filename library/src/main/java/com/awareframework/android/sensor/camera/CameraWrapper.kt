package com.awareframework.android.sensor.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * A wrapper for camera2 API.
 *
 * @author  sercant
 * @date 28/05/2018
 */
class CameraWrapper(private val context: Context,
                    val handler: CameraHandler) {

    enum class State {
        BUSY,
        OPEN,
        WAITING_FOR_CAMERA,
        IDLE
    }

    var state: State = State.IDLE

    /**
     * A reference to the opened [android.hardware.camera2.CameraDevice].
     */
    var cameraDevice: CameraDevice? = null

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    var captureSession: CameraCaptureSession? = null

    /**
     * The [android.util.Size] of video recording.
     */
    lateinit var captureSize: Size

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    var backgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    val cameraOpenCloseLock = Semaphore(1)

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    lateinit var captureRequestBuilder: CaptureRequest.Builder

    /**
     * Orientation of the camera sensor
     */
    var sensorOrientation = 0

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraWrapper.cameraDevice = cameraDevice
            state = State.OPEN

            handler.onCameraOpened()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraWrapper.cameraDevice = null
            state = State.IDLE

            handler.onCameraDisconnected()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraWrapper.cameraDevice = null
            state = State.IDLE

            handler.onCameraError(error)
            stopRecordingVideo()
        }

    }

    /**
     * Starts a background thread and its [Handler].
     */
    fun startBackgroundThread() {
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
            e.printStackTrace()
        }
    }

    private fun closeCaptureSession() {
        captureSession?.close()
        captureSession = null
    }

    private fun chooseCameraId(face: CameraFace, cameraManager: CameraManager): String {
        val lensCharacteristics = when (face) {
            CameraFace.FRONT -> CameraCharacteristics.LENS_FACING_FRONT.toString()
            CameraFace.BACK -> CameraCharacteristics.LENS_FACING_BACK.toString()
            else -> CameraCharacteristics.LENS_FACING_BACK.toString()
        }

        return cameraManager.cameraIdList.find {
            it == lensCharacteristics
        } ?: cameraManager.cameraIdList[0]
    }

    /**
     * Tries to open a [CameraDevice]. The result is listened by [stateCallback].
     *
     */
    @SuppressLint("MissingPermission")
    fun open() {
        if (state != State.IDLE)
            return
        state = State.WAITING_FOR_CAMERA

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            val cameraId = chooseCameraId(handler.facing, manager)

            // Choose the sizes for camera preview and video recording
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get available preview/video sizes")

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            captureSize = handler.onCaptureSizeSelection(map.getOutputSizes(MediaRecorder::class.java))

            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            handler.onCameraError(e.reason)
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            handler.onCameraError(e.hashCode())
        } catch (e: InterruptedException) {
            handler.onCameraError(e.hashCode())
        }
    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    /**
     * Update the camera preview. [startPreview] needs to be called in advance.
     */
    fun updateCapture() {
        if (cameraDevice == null) return

        try {
            setUpCaptureRequestBuilder(captureRequestBuilder)
            HandlerThread("CameraRecording").start()
            captureSession?.setRepeatingRequest(captureRequestBuilder.build(),
                    null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Close the [CameraDevice].
     */
    fun close() {
        try {
            cameraOpenCloseLock.acquire()
            closeCaptureSession()

            cameraDevice?.close()
            cameraDevice = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
            state = State.IDLE
            handler.onCameraClosed()
        }
    }

    fun startRecordingVideo(surfaces: List<Surface>) {
        if (state == State.IDLE) {
            throw Exception("Camera needs to be opened first.")
        }

        if (state == State.BUSY) {
            throw Exception("Camera is busy.")
        }

        try {
            // Set up Surface for MediaRecorder
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                for (surface in surfaces)
                    addTarget(surface)
            }

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice?.createCaptureSession(surfaces,
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            captureSession = cameraCaptureSession
                            updateCapture()
                            state = State.BUSY

                            handler.onCameraConfigured()
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            close()
                            handler.onCameraConfigureFailure()
                        }
                    }, backgroundHandler)
        } catch (e: CameraAccessException) {
            throw e
        } catch (e: IOException) {
            throw e
        }
    }

    fun stopRecordingVideo() {
        try {
            captureSession?.let {
                it.stopRepeating()
                it.abortCaptures()
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } finally {
            captureSession = null
        }

        stopBackgroundThread()
        close()

        handler.onCameraSessionEnd()
    }
}
