package com.example.keepscreenon // IMPORTANT: Ensure this package name matches your project's actual package name!

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences

class KeepScreenOnService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var NOTIFICATION_CHANNEL_ID: String
    private val NOTIFICATION_ID = 101
    private val TAG = "KeepScreenOnService"

    private val handler = Handler(Looper.getMainLooper())
    private val autoOffRunnable = Runnable {
        Log.d(TAG, "Auto-off timer expired. Stopping service.")
        stopService()
    }

    // SharedPreferences constants
    private val PREFS_NAME = "KeepScreenOnPrefs"


    companion object {
        const val ACTION_START_FOREGROUND_SERVICE = "com.example.keepscreenon.action.START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "com.example.keepscreenon.action.STOP_FOREGROUND_SERVICE"
        const val ACTION_SERVICE_STATUS_UPDATE = "com.example.keepscreenon.action.SERVICE_STATUS_UPDATE"
        const val EXTRA_IS_ACTIVE = "is_active"
        const val KEY_AUTO_OFF_DURATION = "auto_off_duration" // in minutes
        const val DEFAULT_AUTO_OFF_MINUTES = 30 // Default auto-off duration
    }

    override fun onCreate() {
        super.onCreate()
        NOTIFICATION_CHANNEL_ID = (application as KeepScreenOnApplication).NOTIFICATION_CHANNEL_ID
        Log.d(TAG, "KeepScreenOnService onCreate called. Channel ID: $NOTIFICATION_CHANNEL_ID")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KeepScreenOnService onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_FOREGROUND_SERVICE -> startService()
            ACTION_STOP_FOREGROUND_SERVICE -> stopService()
        }
        return START_STICKY
    }

    private fun startService() {
        Log.d(TAG, "Starting KeepScreenOnService.")
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Ensure both flags are used for robust wake lock
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "StayLitApp::MyWakeLockTag"
        )
        wakeLock?.acquire()
        Log.d(TAG, "WakeLock acquired.")

        // Read auto-off duration from SharedPreferences
        val sharedPrefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoOffMinutes = sharedPrefs.getInt(KEY_AUTO_OFF_DURATION, DEFAULT_AUTO_OFF_MINUTES)
        val autoOffDelayMillis = if (autoOffMinutes == 0) 0L else autoOffMinutes * 60 * 1000L // 0 minutes means "Never"

        if (autoOffDelayMillis > 0) {
            handler.postDelayed(autoOffRunnable, autoOffDelayMillis)
            Log.d(TAG, "Auto-off scheduled for ${autoOffMinutes} minutes.")
        } else {
            handler.removeCallbacks(autoOffRunnable) // Ensure no old callbacks are pending
            Log.d(TAG, "Auto-off is set to Never. No timer scheduled.")
        }

        val notification = buildNotification(
            getString(R.string.app_name),
            "Screen will stay on."
        ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground service started with notification.")

        broadcastServiceStatus(true)
    }

    private fun stopService() {
        Log.d(TAG, "Stopping KeepScreenOnService.")
        handler.removeCallbacks(autoOffRunnable)
        Log.d(TAG, "Removed pending auto-off callbacks.")

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released.")
        } else {
            Log.d(TAG, "WakeLock was not held, no need to release.")
        }
        wakeLock = null

        stopForeground(true)
        stopSelf()
        Log.d(TAG, "Foreground service stopped.")

        Log.d(TAG, "Attempting to broadcast service status: false to widget and tile.")
        broadcastServiceStatus(false)
    }

    private fun buildNotification(title: String, content: String): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, KeepScreenOnService::class.java).apply {
            action = ACTION_STOP_FOREGROUND_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_qs_lightbulb_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_lightbulb_outline, "Turn Off", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
    }

    private fun broadcastServiceStatus(isActive: Boolean) {
        val statusIntent = Intent(ACTION_SERVICE_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent)
        Log.d(TAG, "Broadcasted service status: $isActive using LocalBroadcastManager.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KeepScreenOnService onDestroy called.")
        handler.removeCallbacks(autoOffRunnable)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released in onDestroy.")
        } else {
            Log.d(TAG, "WakeLock was not held in onDestroy, no need to release.")
        }
        wakeLock = null
        broadcastServiceStatus(false)
    }
}
