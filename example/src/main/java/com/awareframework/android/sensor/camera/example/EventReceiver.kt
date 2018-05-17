package com.awareframework.android.sensor.camera.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_USER_PRESENT
import android.os.Environment
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.camera.Camera

/**
 * Tries to record video on specific events.
 *
 * @author  sercant
 * @date 10/05/2018
 */
class EventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        when (intent.action) {
            ACTION_USER_PRESENT -> {
                val config = context.getSharedPreferences(MainActivity.SHARED_CAMERA_CONFIG, Context.MODE_PRIVATE)
                val json: String = config.getString(MainActivity.SHARED_CAMERA_CONFIG_KEY,
                        Camera.CameraConfig().apply {
                            dbType = Engine.DatabaseType.ROOM
                            contentPath = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).absolutePath
                        }.toJson())
                val camera = Camera.Builder(context)
                        .build(Camera.CameraConfig.fromJson(json))

                camera.start()
            }
        }
    }
}