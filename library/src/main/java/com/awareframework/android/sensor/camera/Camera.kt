package com.awareframework.android.sensor.camera

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.awareframework.android.core.db.Engine
import com.awareframework.android.core.model.ISensorController
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.camera.model.VideoData
import com.google.gson.Gson

/**
 * Camera controller class
 *
 * @author  sercant
 * @date 20/04/2018
 */
class Camera private constructor(private val context: Context) : ISensorController {

    companion object {
        const val ACTION_VIDEO_RECORDED = "com.awareframework.android.sensor.camera.video_recorded"
        val config: CameraConfig = CameraSensor.CONFIG
    }

    override fun disable() {
        config.enabled = false
    }

    override fun enable() {
        config.enabled = true
    }

    override fun isEnabled(): Boolean = config.enabled

    override fun start() {
        if (config.enabled) {
            val builder = JobInfo.Builder(0, ComponentName(context, CameraSensor::class.java))
            builder.setMinimumLatency(1)
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            // Finish configuring the builder
            builder.run {
                setRequiresDeviceIdle(false)
                setRequiresCharging(false)
            }
            (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).schedule(builder.build())

//            CameraSensor.instance?.startRecordingVideo() ?:
//                context.startService(Intent(context, CameraSensor::class.java))
        }
    }

    override fun stop() {
        context.stopService(Intent(context, CameraSensor::class.java))
    }

    override fun sync(force: Boolean) {
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
            var preferredHeight: Int = 1080,
            var secondaryFacing: CameraFace = CameraFace.NONE
    ) : SensorConfig(dbPath = "aware_camera") {
        fun videoLengthInMillis() = videoLength.toLong() * 1000

        companion object {
            fun fromJson(json: String): CameraConfig = Gson().fromJson(json, CameraConfig::class.java)
        }

        fun toJson(): String = Gson().toJson(this)

        fun replaceWith(other: CameraConfig) {
            bitrate = other.bitrate
            frameRate = other.frameRate
            facing = other.facing
            contentPath = other.contentPath
            retryCount = other.retryCount
            retryDelay = other.retryDelay
            videoLength = other.videoLength
            preferredWidth = other.preferredWidth
            preferredHeight = other.preferredHeight
            enabled = other.enabled
            debug = other.debug
            label = other.label
            deviceId = other.deviceId
            dbEncryptionKey = other.dbEncryptionKey
            dbType = other.dbType
            dbPath = other.dbPath
            dbHost = other.dbHost
            secondaryFacing = other.secondaryFacing
        }
    }

    class Builder(private val context: Context) {
        private var config: CameraConfig = CameraConfig()

        /**
         * @param label collected data will be labeled accordingly. (default = "")
         */
        fun setLabel(label: String) = apply { config.label = label }

        /**
         * @param debug enable/disable logging to Logcat. (default = false)
         */
        fun setDebug(debug: Boolean) = apply { config.debug = debug }

        /**
         * @param key encryption key for the database. (default = no encryption)
         */
        fun setDatabaseEncryptionKey(key: String) = apply { config.dbEncryptionKey = key }

        /**
         * @param host host for syncing the database. (default = null)
         */
        fun setDatabaseHost(host: String) = apply { config.dbHost = host }

        /**
         * @param type which db engine to use for saving data. (default = NONE)
         */
        fun setDatabaseType(type: Engine.DatabaseType) = apply { config.dbType = type }

        /**
         * @param path path of the database.
         */
        fun setDatabasePath(path: String) = apply { config.dbPath = path }


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

        fun setSecondaryFacing(face: CameraFace) = apply {
            config.secondaryFacing = face
        }

        fun build(): Camera = Camera(context).apply {
            Camera.config.replaceWith(config)
        }

        fun build(other: CameraConfig): Camera = Camera(context).apply {
            config.replaceWith(other)
            build()
        }
    }

    fun getVideoData(): List<VideoData> {
        val dbEngine = Engine.Builder(context)
                .setEncryptionKey(config.dbEncryptionKey)
                .setHost(config.dbHost)
                .setPath(config.dbPath)
                .setType(config.dbType)
                .build()

        val data = dbEngine?.getAll(VideoData.TABLE_NAME)

        dbEngine?.close()

        val list = ArrayList<VideoData>()
        data?.forEach {
            it.withData<VideoData> {
                list.add(it)
            }
        }

        return list
    }
}