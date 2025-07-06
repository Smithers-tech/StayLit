package com.example.keepscreenon // IMPORTANT: Ensure this package name matches your project's actual package name!

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
        Log.d(TAG, "KeepScreenOnApplication onCreate called. Creating notification channel.")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Keep Screen On Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created.")
        } else {
            Log.d(TAG, "Notification channels not needed for API < O.")
        }
    }
}