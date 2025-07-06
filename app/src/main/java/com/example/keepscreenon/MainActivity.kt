package com.example.keepscreenon // IMPORTANT: Ensure this package name matches your project's actual package name!

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
// REMOVED: import android.widget.Toast // Removed Toast import
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import android.app.NotificationManager
import android.Manifest // Import for Manifest
import androidx.localbroadcastmanager.content.LocalBroadcastManager // ADDED import

class MainActivity : AppCompatActivity() {

    private var isServiceRunning: Boolean = false
    private lateinit var toggleButton: Button
    private lateinit var statusTextView: TextView
    private val TAG = "MainActivity"

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // ADDED: Log to confirm receiver is triggered
            Log.d(TAG, "BroadcastReceiver onReceive triggered for action: ${intent?.action}")
            // REMOVED: Toast for visual confirmation
            // Toast.makeText(context, "Broadcast received: ${intent?.action}", Toast.LENGTH_SHORT).show()

            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                val receivedIsActive = intent.getBooleanExtra(KeepScreenOnService.EXTRA_IS_ACTIVE, false)
                Log.d(TAG, "Received service status update: $receivedIsActive")
                if (isServiceRunning != receivedIsActive) { // Only update if state actually changed
                    isServiceRunning = receivedIsActive
                    Log.d(TAG, "isServiceRunning state changed to: $isServiceRunning")
                    updateUi() // Update button and status text
                } else {
                    Log.d(TAG, "Service status unchanged: $isServiceRunning. No UI update needed.")
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted.")
            // After notification permission, check special use permission and then toggle
            checkAndToggleService()
        } else {
            Log.d(TAG, "Notification permission denied. Cannot show status.")
            // REMOVED: Toast.makeText(this, "Notification permission denied. Cannot show status.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleScreenOnButton)
        statusTextView = findViewById(R.id.statusTextView)

        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        // MODIFIED: Use LocalBroadcastManager for registration
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)

        Log.d(TAG, "BroadcastReceiver registered.")

        isServiceRunning = isServiceRunning(this, KeepScreenOnService::class.java)
        updateUi()
        Log.d(TAG, "Initial service running state: $isServiceRunning")
        // REMOVED: Toast to show initial service state
        // Toast.makeText(this, "Initial status: ${if (isServiceRunning) "Activated" else "Deactivated"}", Toast.LENGTH_LONG).show()


        toggleButton.setOnClickListener {
            Log.d(TAG, "Button clicked.")
            // Request POST_NOTIFICATIONS permission if needed (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelExists = notificationManager.getNotificationChannel(
                    (application as KeepScreenOnApplication).NOTIFICATION_CHANNEL_ID
                ) != null
                Log.d(TAG, "Notification channel exists: $channelExists")

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted.")
                    checkAndToggleService() // Proceed to check special use permission
                } else {
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                Log.d(TAG, "API < 33, no POST_NOTIFICATIONS permission needed.")
                checkAndToggleService() // Proceed directly for older APIs
            }
        }
    }

    private fun checkAndToggleService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "FOREGROUND_SERVICE_SPECIAL_USE permission granted.")
                toggleKeepScreenOnService()
            } else {
                Log.e(TAG, "FOREGROUND_SERVICE_SPECIAL_USE permission NOT granted. Cannot start service. This is highly unusual.")
                // REMOVED: Toast.makeText(this, "Required 'Special Use' permission not granted. Please check app info or try reinstalling the app.", Toast.LENGTH_LONG).show()
            }
        } else {
            // For APIs < 34, FOREGROUND_SERVICE_SPECIAL_USE is not needed or handled differently
            toggleKeepScreenOnService()
        }
    }

    private fun toggleKeepScreenOnService() {
        val serviceIntent = Intent(this, KeepScreenOnService::class.java)
        if (isServiceRunning) {
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            stopService(serviceIntent)
            Log.d(TAG, "Attempting to stop service.")
        } else {
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
                Log.d(TAG, "Attempting to start foreground service (API >= O) using ContextCompat.")
            } else {
                startService(serviceIntent)
                Log.d(TAG, "Attempting to start service (API < O).")
            }
        }
    }

    private fun updateUi() {
        val buttonText = if (isServiceRunning) "Turn Off Keep Screen On" else "Turn On Keep Screen On"
        val statusText = if (isServiceRunning) "Status: Activated" else "Status: Deactivated"

        Log.d(TAG, "updateUi called. isServiceRunning: $isServiceRunning, Setting button text to: '$buttonText', Setting status text to: '$statusText'")

        toggleButton.text = buttonText
        statusTextView.text = statusText
        Log.d(TAG, "UI elements updated.")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent called.")
    }

    override fun onDestroy() {
        super.onDestroy()
        // MODIFIED: Use LocalBroadcastManager to unregister receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
        Log.d(TAG, "BroadcastReceiver unregistered. MainActivity onDestroy.")
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.d(TAG, "Service ${serviceClass.simpleName} is running.")
                return true
            }
        }
        Log.d(TAG, "Service ${serviceClass.simpleName} is NOT running.")
        return false
    }
}
