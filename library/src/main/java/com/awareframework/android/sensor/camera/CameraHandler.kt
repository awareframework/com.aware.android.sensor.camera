package com.awareframework.android.sensor.camera

import android.util.Size

/**
 * Camera interaction handler
 *
 * @author  sercant
 * @date 29/05/2018
 */
interface CameraHandler {
    val facing: CameraFace

    fun onCameraSessionEnd()
    fun onCameraConfigureFailure()
    fun onCameraConfigured()
    fun onCameraOpened()
    fun onCameraClosed()
    fun onCameraDisconnected()
    fun onCameraError(error: Int)
    fun onCaptureSizeSelection(sizes: Array<Size>) : Size
}