package com.awareframework.android.sensor.camera.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_USER_PRESENT
import android.os.Environment
import com.awareframework.android.sensor.camera.Camera

/**
 * Class description
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
                val camera = Camera.Builder(context)
                        .setContentPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath)
                        .build()

                camera.start()
            }
        }
    }
}