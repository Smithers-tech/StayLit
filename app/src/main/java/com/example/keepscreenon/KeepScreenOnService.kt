package com.example.keepscreenon

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

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

    private val PREFS_NAME = "KeepScreenOnPrefs"

    companion object {
        const val ACTION_START_FOREGROUND_SERVICE = "com.example.keepscreenon.action.START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "com.example.keepscreenon.action.STOP_FOREGROUND_SERVICE"
        const val ACTION_SERVICE_STATUS_UPDATE = "com.example.keepscreenon.action.SERVICE_STATUS_UPDATE"
        const val EXTRA_IS_ACTIVE = "is_active"
        const val KEY_AUTO_OFF_DURATION = "auto_off_duration"
        const val DEFAULT_AUTO_OFF_MINUTES = 30

        @Volatile
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        NOTIFICATION_CHANNEL_ID = (application as KeepScreenOnApplication).NOTIFICATION_CHANNEL_ID
        Log.d(TAG, "KeepScreenOnService onCreate called.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand with action: $action, flags: $flags")

        // *** THE DEFINITIVE FIX FOR SAMSUNG DEVICES ***
        // If the intent is null, it means the service was killed and restarted by the OS.
        // We check our static flag to see if it *should* have been running.
        if (action == null && isServiceRunning) {
            Log.w(TAG, "Service was killed and restarted by the system. Re-initializing service...")
            startService() // Re-run the start logic to acquire the WakeLock again.
            return START_STICKY
        }

        when (action) {
            ACTION_START_FOREGROUND_SERVICE -> startService()
            ACTION_STOP_FOREGROUND_SERVICE -> stopService()
        }
        return START_STICKY
    }

    private fun startService() {
        if (isServiceRunning && wakeLock?.isHeld == true) {
            Log.d(TAG, "Service is already running with WakeLock held. Ignoring start command.")
            return
        }
        Log.d(TAG, "Starting KeepScreenOnService.")
        isServiceRunning = true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "StayLitApp::MyWakeLockTag"
        )
        wakeLock?.acquire()
        Log.d(TAG, "WakeLock acquired.")

        val sharedPrefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoOffMinutes = sharedPrefs.getInt(KEY_AUTO_OFF_DURATION, DEFAULT_AUTO_OFF_MINUTES)
        val autoOffDelayMillis = if (autoOffMinutes == 0) 0L else autoOffMinutes * 60 * 1000L

        handler.removeCallbacks(autoOffRunnable) // Remove any old timers
        if (autoOffDelayMillis > 0) {
            handler.postDelayed(autoOffRunnable, autoOffDelayMillis)
            Log.d(TAG, "Auto-off scheduled for $autoOffMinutes minutes.")
        } else {
            Log.d(TAG, "Auto-off is set to Never.")
        }

        val notification = buildNotification("Screen On", "The screen will stay on.").build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground service started.")

        broadcastServiceStatus(true)
    }

    private fun stopService() {
        Log.d(TAG, "Stopping KeepScreenOnService.")
        isServiceRunning = false

        handler.removeCallbacks(autoOffRunnable)
        Log.d(TAG, "Removed auto-off callbacks.")

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released.")
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Foreground service stopped.")

        broadcastServiceStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KeepScreenOnService onDestroy called.")
        // This is a final safeguard. If the service is destroyed for any reason,
        // ensure the state is correct.
        if (isServiceRunning) {
            isServiceRunning = false
            broadcastServiceStatus(false)
        }
        handler.removeCallbacks(autoOffRunnable)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun buildNotification(title: String, content: String): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, KeepScreenOnService::class.java).apply {
            action = ACTION_STOP_FOREGROUND_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
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
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
    }

    private fun broadcastServiceStatus(isActive: Boolean) {
        val statusIntent = Intent(ACTION_SERVICE_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent)
        Log.d(TAG, "Broadcasted service status: $isActive")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
