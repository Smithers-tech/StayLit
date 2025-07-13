package com.example.keepscreenon

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

class KeepScreenOnApplication : Application() {

    val NOTIFICATION_CHANNEL_ID = "KeepScreenOnChannel"
    private val TAG = "KeepScreenOnApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepScreenOnApplication onCreate called.")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // FIX: Changed importance from IMPORTANCE_LOW to IMPORTANCE_DEFAULT.
            // A higher importance makes it less likely for the OS to interfere with the foreground service.
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "StayLit Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT // This was IMPORTANCE_LOW
            )
            serviceChannel.description = "Channel for the persistent StayLit notification"

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created with DEFAULT importance.")
        }
    }
}
