package com.awareframework.android.sensor.camera.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_USER_PRESENT
import android.os.Environment
import android.preference.PreferenceManager
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.camera.Camera

/**
 * Tries to record video on specific events.
 *
 * @author  sercant
 * @date 10/05/2018
 */
class EventReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RECORD_NOW = "com.awareframework.android.sensor.camera.example.RECORD_NOW"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        when (intent.action) {
            ACTION_USER_PRESENT, ACTION_RECORD_NOW -> {
                val camera = Camera.Builder(context).build(MainActivity.getStoredConfig(context))
                camera.start()

                camera.startRecording()
            }
        }
    }
}