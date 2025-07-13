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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: MaterialButton
    private lateinit var statusTextView: TextView
    private lateinit var statusSubTextView: TextView
    private lateinit var toggleIcon: ImageView
    private lateinit var autoOffDurationSpinner: AutoCompleteTextView
    private lateinit var batteryOptimizationsButton: MaterialButton
    private lateinit var sharedPrefs: SharedPreferences

    private val TAG = "MainActivity"

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                Log.d(TAG, "Received service status update broadcast.")
                updateUi()
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
        setContentView(R.layout.activity_main)

        // Initialize Views
        toggleButton = findViewById(R.id.toggleScreenOnButton)
        statusTextView = findViewById(R.id.statusTextView)
        statusSubTextView = findViewById(R.id.statusSubTextView)
        toggleIcon = findViewById(R.id.toggleIcon)
        autoOffDurationSpinner = findViewById(R.id.autoOffDurationSpinner)
        batteryOptimizationsButton = findViewById(R.id.batteryOptimizationsButton)

        sharedPrefs = getSharedPreferences("KeepScreenOnPrefs", Context.MODE_PRIVATE)

        // Register receiver
        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)

        setupListeners()
        setupAutoOffDurationSpinner()
        promptToDisableBatteryOptimizations()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun setupListeners() {
        toggleButton.setOnClickListener {
            checkPermissionsAndToggleService()
        }

        // *** FIX: This listener now uses a more reliable intent to open the app's detail settings page. ***
        // From this page, the user can always access the battery settings.
        batteryOptimizationsButton.setOnClickListener {
            Log.d(TAG, "Battery Optimizations button clicked. Opening app details settings.")
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not open app settings", e)
                Toast.makeText(this, "Could not open app settings.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndToggleService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        toggleKeepScreenOnService()
    }

    private fun promptToDisableBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.battery_dialog_title)
                .setMessage(R.string.battery_dialog_message)
                .setPositiveButton(R.string.battery_dialog_positive_button) { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton(R.string.battery_dialog_negative_button, null)
                .show()
        }
    }

    private fun toggleKeepScreenOnService() {
        val serviceIntent = Intent(this, KeepScreenOnService::class.java)
        if (KeepScreenOnService.isServiceRunning) {
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            startService(serviceIntent)
        } else {
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun updateUi() {
        val isRunning = KeepScreenOnService.isServiceRunning
        Log.d(TAG, "Updating UI. Service running: $isRunning")

        if (isRunning) {
            statusTextView.text = getString(R.string.status_activated)
            statusSubTextView.text = getString(R.string.status_activated_sub)
            toggleButton.text = getString(R.string.action_deactivate)
            toggleIcon.setImageResource(R.drawable.ic_lightbulb_on)
        } else {
            statusTextView.text = getString(R.string.status_deactivated)
            statusSubTextView.text = getString(R.string.status_deactivated_sub)
            toggleButton.text = getString(R.string.action_activate)
            toggleIcon.setImageResource(R.drawable.ic_lightbulb_off)
        }
    }

    private fun setupAutoOffDurationSpinner() {
        val durationsArray = resources.getStringArray(R.array.auto_off_durations_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, durationsArray)
        autoOffDurationSpinner.setAdapter(adapter)

        val savedDuration = sharedPrefs.getInt(KeepScreenOnService.KEY_AUTO_OFF_DURATION, KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES)

        val savedDurationString = when(savedDuration) {
            15 -> "15 minutes"
            30 -> "30 minutes"
            60 -> "1 hour"
            120 -> "2 hours"
            0 -> "Never"
            else -> "30 minutes"
        }
        autoOffDurationSpinner.setText(savedDurationString, false)

        autoOffDurationSpinner.setOnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position).toString()

            val selectedMinutes = when (selectedItem) {
                "15 minutes" -> 15
                "30 minutes" -> 30
                "1 hour" -> 60
                "2 hours" -> 120
                "Never" -> 0
                else -> KeepScreenOnService.DEFAULT_AUTO_OFF_MINUTES
            }

            sharedPrefs.edit().putInt(KeepScreenOnService.KEY_AUTO_OFF_DURATION, selectedMinutes).apply()

            if (KeepScreenOnService.isServiceRunning) {
                toggleKeepScreenOnService()
                Handler(Looper.getMainLooper()).postDelayed({
                    toggleKeepScreenOnService()
                }, 200)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
    }
}
