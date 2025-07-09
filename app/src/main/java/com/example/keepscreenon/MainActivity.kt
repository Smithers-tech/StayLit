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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import android.app.NotificationManager
import android.Manifest
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton // ADDED

class MainActivity : AppCompatActivity() {

    private var isServiceRunning: Boolean = false
    private lateinit var toggleButton: ImageButton // CHANGED from Button to ImageButton
    private lateinit var statusTextView: TextView
    private lateinit var autoOffDurationSpinner: Spinner
    private lateinit var sharedPrefs: SharedPreferences

    private val TAG = "MainActivity"

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "BroadcastReceiver onReceive triggered for action: ${intent?.action}")

            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                val receivedIsActive = intent.getBooleanExtra(KeepScreenOnService.EXTRA_IS_ACTIVE, false)
                Log.d(TAG, "Received service status update: $receivedIsActive")
                if (isServiceRunning != receivedIsActive) {
                    isServiceRunning = receivedIsActive
                    updateUi()
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
            checkAndToggleService()
        } else {
            Log.d(TAG, "Notification permission denied. Cannot show status.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleScreenOnButton)
        statusTextView = findViewById(R.id.statusTextView)
        autoOffDurationSpinner = findViewById(R.id.autoOffDurationSpinner)

        sharedPrefs = getSharedPreferences("KeepScreenOnPrefs", Context.MODE_PRIVATE)

        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)

        Log.d(TAG, "BroadcastReceiver registered.")

        isServiceRunning = isServiceRunning(this, KeepScreenOnService::class.java)
        updateUi()
        Log.d(TAG, "Initial service running state: $isServiceRunning")

        setupAutoOffDurationSpinner()

        toggleButton.setOnClickListener {
            Log.d(TAG, "Button clicked.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelExists = notificationManager.getNotificationChannel(
                    (application as KeepScreenOnApplication).NOTIFICATION_CHANNEL_ID
                ) != null
                Log.d(TAG, "Notification channel exists: $channelExists")

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted.")
                    checkAndToggleService()
                } else {
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                Log.d(TAG, "API < 33, no POST_NOTIFICATIONS permission needed.")
                checkAndToggleService()
            }
        }
    }

    private fun setupAutoOffDurationSpinner() {
        val durationsArray = resources.getStringArray(R.array.auto_off_durations_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, durationsArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        autoOffDurationSpinner.adapter = adapter

        // Load saved preference and set spinner selection
        val savedDuration = sharedPrefs.getInt(KeepScreenOnService.KEY_AUTO_OFF_DURATION, KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES)
        val savedDurationString = if (savedDuration == 0) "Never" else "$savedDuration minutes"
        val selectionIndex = durationsArray.indexOf(savedDurationString)
        if (selectionIndex != -1) {
            autoOffDurationSpinner.setSelection(selectionIndex)
        } else {
            // Fallback to default if saved value is not in array (e.g., old value or direct edit)
            val defaultIndex = durationsArray.indexOf("${KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES} minutes")
            if (defaultIndex != -1) {
                autoOffDurationSpinner.setSelection(defaultIndex)
            }
        }


        autoOffDurationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                val selectedMinutes = when (selectedItem) {
                    "Never" -> 0
                    else -> selectedItem.replace(" minutes", "").toIntOrNull() ?: KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES
                }

                Log.d(TAG, "Selected auto-off duration: $selectedMinutes minutes")

                // Save the new preference
                sharedPrefs.edit().putInt(KeepScreenOnService.KEY_AUTO_OFF_DURATION, selectedMinutes).apply()

                // If service is running, stop and restart it to apply new duration
                if (isServiceRunning) {
                    Log.d(TAG, "Service is running, restarting to apply new auto-off duration.")
                    val serviceIntent = Intent(this@MainActivity, KeepScreenOnService::class.java)
                    serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
                    stopService(serviceIntent)
                    // Small delay before starting again to ensure stop is processed
                    Handler(Looper.getMainLooper()).postDelayed({
                        serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        Log.d(TAG, "Service restarted with new auto-off duration.")
                    }, 500) // 500ms delay
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
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
            }
        } else {
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
            }
        }
    }

    private fun updateUi() {
        val statusText = if (isServiceRunning) "Status: Activated" else "Status: Deactivated"

        Log.d(TAG, "updateUi called. isServiceRunning: $isServiceRunning, Setting status text to: '$statusText'")

        // Update the ImageButton's source based on service status
        val iconResource = if (isServiceRunning) R.drawable.ic_lightbulb_fill else R.drawable.ic_lightbulb_outline
        toggleButton.setImageResource(iconResource)
        toggleButton.contentDescription = if (isServiceRunning) "Turn Off Keep Screen On" else "Turn On Keep Screen On" // For accessibility

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
