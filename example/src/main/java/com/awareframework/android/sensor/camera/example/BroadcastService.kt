package com.awareframework.android.sensor.camera.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import com.awareframework.android.sensor.camera.Camera
import com.awareframework.android.sensor.camera.R

/**
 * Persistent broadcast service
 *
 * @author  sercant
 * @date 12/06/2018
 */
class BroadcastService : Service() {

    class EntryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            intent ?: return

            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                ContextCompat.startForegroundService(context, Intent(context, BroadcastService::class.java))

                val config = MainActivity.getStoredConfig(context)
                if (config.enabled)
                    Camera.Builder(context).build(config).start()
            }
        }
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            intent ?: return

            if (intent.action == Intent.ACTION_USER_PRESENT)
                context.sendBroadcast(Intent(context, EventReceiver::class.java).apply {
                    action = EventReceiver.ACTION_USER_PRESENT
                })
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground()
            START_STICKY
        } else {
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForeground() {
        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_fiber_smart_record_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText("Aware broadcast service is running.")
                .build()
        startForeground(102, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "aware_broadcast_service"
        val channelName = "Aware Broadcast Service"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }
}