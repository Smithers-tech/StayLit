package com.example.keepscreenon

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class KeepScreenOnService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private lateinit var NOTIFICATION_CHANNEL_ID: String
    private val NOTIFICATION_ID = 101
    private val TAG = "KeepScreenOnService"

    private val handler = Handler(Looper.getMainLooper())
    private val autoOffRunnable = Runnable {
        Log.d(TAG, "Auto-off timer expired. Stopping service.")
        stopService()
    }

    // Aggressive poke every 10 seconds to prevent dimming
    private val aggressivePokeRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning) {
                simulateUserActivity()
                handler.postDelayed(this, 10000) // Every 10 seconds
            }
        }
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        Log.d(TAG, "KeepScreenOnService onCreate called.")
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand with action: $action")

        when (action) {
            ACTION_START_FOREGROUND_SERVICE -> startService()
            ACTION_STOP_FOREGROUND_SERVICE -> stopService()
            null -> {
                if (isServiceRunning) {
                    Log.w(TAG, "Service restarted by system. Re-initializing...")
                    startService()
                }
            }
        }
        return START_STICKY
    }

    private fun startService() {
        if (isServiceRunning) {
            Log.d(TAG, "Service already running.")
            return
        }

        Log.d(TAG, "Starting KeepScreenOnService.")
        isServiceRunning = true

        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

        if (isSamsung) {
            // Samsung device - no toast message needed

            // Method 1: Overlay with FLAG_KEEP_SCREEN_ON
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                createPersistentOverlay()
            }

            // Method 2: Bright wake lock
            acquireBrightWakeLock()

            // Method 3: Start aggressive user activity simulation
            handler.post(aggressivePokeRunnable)

        } else {
            Log.d(TAG, "Non-Samsung device. Using standard wake lock.")
            acquireBrightWakeLock()
        }

        setupAutoOff()
        startForegroundNotification()
        broadcastServiceStatus(true)

        // Send activity flag broadcast
        sendActivityFlagBroadcast(true)
    }

    private fun createPersistentOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay already exists")
            return
        }

        try {
            // Create a minimal overlay that won't interfere with user
            overlayView = View(this).apply {
                // Make it truly invisible
                alpha = 0f
                visibility = View.VISIBLE // Must be VISIBLE for FLAG_KEEP_SCREEN_ON to work
                keepScreenOn = true
            }

            val params = WindowManager.LayoutParams().apply {
                width = 1
                height = 1
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                // Don't set any brightness values - let system handle it
            }

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Persistent overlay created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay", e)
        }
    }

    private fun acquireBrightWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "StayLitApp::BrightLock"
            )

            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours

            Log.d(TAG, "Bright wake lock acquired")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun simulateUserActivity() {
        try {
            // Method 1: Update overlay to trigger screen activity
            overlayView?.let { view ->
                // Toggle visibility to trigger window update
                view.visibility = View.INVISIBLE
                view.postDelayed({
                    view.visibility = View.VISIBLE
                }, 50)
            }

            // Method 2: Refresh wake lock
            wakeLock?.let {
                if (it.isHeld) {
                    // Don't release/reacquire, just ensure it's held
                    Log.d(TAG, "Wake lock active")
                }
            }

            // Method 3: Send keep-alive broadcast
            val intent = Intent("com.example.keepscreenon.KEEP_ALIVE")
            sendBroadcast(intent)

            Log.d(TAG, "User activity simulated")

        } catch (e: Exception) {
            Log.e(TAG, "Error simulating activity", e)
        }
    }

    private fun sendActivityFlagBroadcast(enable: Boolean) {
        val intent = Intent("com.example.keepscreenon.SET_SCREEN_ON_FLAG")
        intent.putExtra("enable", enable)
        sendBroadcast(intent)
        Log.d(TAG, "Activity flag broadcast sent: $enable")
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoOff() {
        val sharedPrefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoOffMinutes = sharedPrefs.getInt(KEY_AUTO_OFF_DURATION, DEFAULT_AUTO_OFF_MINUTES)
        val autoOffDelayMillis = if (autoOffMinutes == 0) 0L else autoOffMinutes * 60 * 1000L

        handler.removeCallbacks(autoOffRunnable)
        if (autoOffDelayMillis > 0) {
            handler.postDelayed(autoOffRunnable, autoOffDelayMillis)
            Log.d(TAG, "Auto-off scheduled for $autoOffMinutes minutes.")
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("Screen On", "Keeping your screen awake").build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground service started.")
    }

    private fun stopService() {
        Log.d(TAG, "Stopping KeepScreenOnService.")
        isServiceRunning = false

        handler.removeCallbacksAndMessages(null)
        sendActivityFlagBroadcast(false)

        overlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Overlay removed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayView = null
        }

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released.")
            }
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        broadcastServiceStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called.")

        if (isServiceRunning) {
            isServiceRunning = false
            broadcastServiceStatus(false)
        }

        handler.removeCallbacksAndMessages(null)

        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay in onDestroy", e)
            }
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
    }

    private fun broadcastServiceStatus(isActive: Boolean) {
        val statusIntent = Intent(ACTION_SERVICE_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent)
        Log.d(TAG, "Service status broadcast: $isActive")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}