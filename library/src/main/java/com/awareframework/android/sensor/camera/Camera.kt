package com.awareframework.android.sensor.camera

import android.content.Context
import com.awareframework.android.core.model.ISensorController
import com.awareframework.android.core.model.SensorConfig

/**
 * Class decription
 *
 * @author  sercant
 * @date 20/04/2018
 */
class Camera private constructor(val context: Context): ISensorController {

    companion object {
        const val FACING_FRONT: Int = 0
        const val FACING_BACK: Int = 1

        var config: CameraConfig = CameraConfig()
    }

    override fun disable() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun enable() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stop() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sync(force: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    data class CameraConfig(
        var bitrate: Int = 5000,
        var facing: Int = FACING_FRONT,
        var contentPath: String = "",
        var videoLength: Float = 10f // 10 seconds
    ) : SensorConfig(dbPath = "aware_camera") {
        fun videoLengthInMillis() = videoLength.toLong() * 1000
    }

    class Builder(val context: Context) {
        val config: CameraConfig = CameraConfig()

        fun build(): Camera = Camera(context)
    }

}