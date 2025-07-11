package com.example.keepscreenon

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: ImageButton
    private lateinit var statusTextView: TextView
    private lateinit var autoOffDurationSpinner: Spinner
    private lateinit var sharedPrefs: SharedPreferences
    // ADDED: Button to handle battery optimization settings
    private lateinit var batteryOptimizationsButton: Button

    private val TAG = "MainActivity"

    // --- CHANGE: We no longer need a local isServiceRunning variable ---
    // We will now use KeepScreenOnService.isServiceRunning directly.

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                Log.d(TAG, "Received service status update broadcast.")
                updateUi() // Update UI whenever the service status changes
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted.")
            toggleKeepScreenOnService()
        } else {
            Log.d(TAG, "Notification permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Ensure your layout file has the new button

        toggleButton = findViewById(R.id.toggleScreenOnButton)
        statusTextView = findViewById(R.id.statusTextView)
        autoOffDurationSpinner = findViewById(R.id.autoOffDurationSpinner)
        // Make sure you have a button with this ID in your activity_main.xml
        // batteryOptimizationsButton = findViewById(R.id.batteryOptimizationsButton)

        sharedPrefs = getSharedPreferences("KeepScreenOnPrefs", Context.MODE_PRIVATE)

        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)

        setupAutoOffDurationSpinner()

        toggleButton.setOnClickListener {
            Log.d(TAG, "Button clicked.")
            checkPermissionsAndToggleService()
        }

        // ADDED: Logic for the battery optimization button
        // batteryOptimizationsButton.setOnClickListener {
        //     promptToDisableBatteryOptimizations()
        // }

        // ADDED: Proactively prompt user if optimizations are enabled
        promptToDisableBatteryOptimizations()

        updateUi()
        Log.d(TAG, "Initial UI setup complete. Service running: ${KeepScreenOnService.isServiceRunning}")
    }

    override fun onResume() {
        super.onResume()
        // The service state might have changed while the app was in the background.
        updateUi()
    }

    private fun checkPermissionsAndToggleService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    toggleKeepScreenOnService()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            toggleKeepScreenOnService()
        }
    }

    // --- ADDED: Method to handle battery optimization prompt ---
    // This is crucial for Samsung, OnePlus, etc.
    private fun promptToDisableBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("To ensure the app works correctly, please disable battery optimizations. This is required for the screen to stay on reliably.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }


    private fun toggleKeepScreenOnService() {
        val serviceIntent = Intent(this, KeepScreenOnService::class.java)
        // --- CHANGE: Use the reliable static variable ---
        if (KeepScreenOnService.isServiceRunning) {
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            startService(serviceIntent) // Use startService for both start and stop actions
            Log.d(TAG, "Attempting to stop service.")
        } else {
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "Attempting to start foreground service.")
        }
    }

    private fun updateUi() {
        // --- CHANGE: Use the reliable static variable ---
        val isRunning = KeepScreenOnService.isServiceRunning
        val statusText = if (isRunning) "Status: Activated" else "Status: Deactivated"
        val iconResource = if (isRunning) R.drawable.ic_lightbulb_fill else R.drawable.ic_lightbulb_outline

        Log.d(TAG, "Updating UI. Service running: $isRunning")

        toggleButton.setImageResource(iconResource)
        statusTextView.text = statusText
    }

    private fun setupAutoOffDurationSpinner() {
        val durationsArray = resources.getStringArray(R.array.auto_off_durations_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, durationsArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        autoOffDurationSpinner.adapter = adapter

        val savedDuration = sharedPrefs.getInt(KeepScreenOnService.KEY_AUTO_OFF_DURATION, KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES)
        val savedDurationString = if (savedDuration == 0) "Never" else "$savedDuration minutes"
        val selectionIndex = durationsArray.indexOf(savedDurationString).takeIf { it != -1 } ?: 0
        autoOffDurationSpinner.setSelection(selectionIndex)

        autoOffDurationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                val selectedMinutes = when (selectedItem) {
                    "Never" -> 0
                    else -> selectedItem.removeSuffix(" minutes").toIntOrNull() ?: KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES
                }

                sharedPrefs.edit().putInt(KeepScreenOnService.KEY_AUTO_OFF_DURATION, selectedMinutes).apply()

                if (KeepScreenOnService.isServiceRunning) {
                    Log.d(TAG, "Restarting service to apply new duration.")
                    // Stop the service
                    val stopIntent = Intent(this@MainActivity, KeepScreenOnService::class.java).apply {
                        action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
                    }
                    startService(stopIntent)

                    // Restart it after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        val startIntent = Intent(this@MainActivity, KeepScreenOnService::class.java).apply {
                            action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
                        }
                        ContextCompat.startForegroundService(this@MainActivity, startIntent)
                    }, 200)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
        Log.d(TAG, "MainActivity onDestroy.")
    }

    // --- REMOVED: The old, unreliable isServiceRunning method is no longer needed. ---
}
