package com.awareframework.android.sensor.camera

import android.content.Context
import android.content.Intent
import com.awareframework.android.core.model.ISensorController
import com.awareframework.android.core.model.SensorConfig

/**
 * Class decription
 *
 * @author  sercant
 * @date 20/04/2018
 */
class Camera private constructor(private val context: Context) : ISensorController {

    enum class CameraFace {
        FRONT, BACK
    }

    companion object {
        private var config: CameraConfig = CameraSensor.CONFIG
    }

    override fun disable() {
        config.enabled = false
    }

    override fun enable() {
        config.enabled = true
    }

    override fun isEnabled(): Boolean = config.enabled

    override fun start() {
        context.startService(Intent(context, CameraSensor::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, CameraSensor::class.java))
    }

    override fun sync(force: Boolean) {
        // TODO (sercant): fix here
        CameraSensor.instance?.onSync(null)
    }

    data class CameraConfig(
            var bitrate: Int = 10000000,
            var frameRate: Int = 30,
            var facing: CameraFace = CameraFace.BACK,
            var contentPath: String = "",
            var retryCount: Int = 3,
            var retryDelay: Float = 1f, // in seconds
            var videoLength: Float = 10f, // 10 seconds
            var preferredWidth: Int = 1920,
            var preferredHeight: Int = 1080
    ) : SensorConfig(dbPath = "aware_camera") {
        fun videoLengthInMillis() = videoLength.toLong() * 1000
    }

    class Builder(private val context: Context) {

        fun setBitrate(rate: Int) = apply {
            config.bitrate = rate
        }

        fun setFrameRate(rate: Int) = apply {
            config.frameRate = rate
        }

        fun setFacing(facing: CameraFace) = apply {
            config.facing = facing
        }

        fun setContentPath(path: String) = apply {
            config.contentPath = path
        }

        fun setRetryCount(count: Int) = apply {
            config.retryCount = count
        }

        fun setRetryDelay(delay: Float) = apply {
            config.retryDelay = delay
        }

        fun setVideoLength(length: Float) = apply {
            config.videoLength = length
        }

        fun setPreferredWidth(width: Int) = apply {
            config.preferredWidth = width
        }

        fun setPreferredHeight(height: Int) = apply {
            config.preferredHeight = height
        }

        fun build(): Camera = Camera(context)
    }

}