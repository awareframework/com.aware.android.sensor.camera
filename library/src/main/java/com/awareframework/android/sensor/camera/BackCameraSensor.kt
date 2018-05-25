package com.awareframework.android.sensor.camera

import android.content.Intent

/**
 * Back Camera service
 *
 * @author  sercant
 * @date 25/05/2018
 */
class BackCameraSensor : CameraSensor() {

    companion object {
//        const val TAG = "BackCameraSensor"
        internal var instance: BackCameraSensor? = null

        internal val CONFIG: Camera.CameraConfig = Camera.CameraConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runningConfig = CONFIG
        instance = this

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}