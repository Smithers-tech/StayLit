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
import android.view.WindowManager
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
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                Log.d(TAG, "Received service status update broadcast.")
                updateUi()
            }
        }
    }

    // Receiver for window flag requests
    private val screenFlagReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.keepscreenon.SET_SCREEN_ON_FLAG") {
                val enable = intent.getBooleanExtra("enable", false)
                Log.d(TAG, "Received screen flag request: $enable")
                setScreenOnFlag(enable)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted.")
            checkPermissionsAndToggleService()
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

        // Register receivers
        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)

        val screenFlagFilter = IntentFilter("com.example.keepscreenon.SET_SCREEN_ON_FLAG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenFlagReceiver, screenFlagFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenFlagReceiver, screenFlagFilter)
        }

        setupListeners()
        setupAutoOffDurationSpinner()

        // Check if Samsung device needs overlay permission
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            checkOverlayPermission()
        }

        // Optionally prompt for battery optimization (though not required)
        promptToDisableBatteryOptimizations()
    }

    override fun onResume() {
        super.onResume()
        updateUi()

        // If service is running, also set the window flag
        if (KeepScreenOnService.isServiceRunning) {
            setScreenOnFlag(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Permission granted! You can now use StayLit.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission is required for StayLit to work properly", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setScreenOnFlag(enable: Boolean) {
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Added FLAG_KEEP_SCREEN_ON to window")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Removed FLAG_KEEP_SCREEN_ON from window")
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("StayLit needs \"Display over other apps\" permission to keep your screen on while using other apps.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Later", null)
                .setCancelable(false)
                .show()
        }
    }

    private fun setupListeners() {
        toggleButton.setOnClickListener {
            checkPermissionsAndToggleService()
        }

        batteryOptimizationsButton.setOnClickListener {
            openBatteryOptimizationSettings()
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to app settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun checkPermissionsAndToggleService() {
        // Check notification permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // For Samsung devices, check overlay permission
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (isSamsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("Please grant \"Display over other apps\" permission for StayLit to work properly.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            toggleKeepScreenOnService()
        }
    }

    private fun promptToDisableBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Optional: Battery Optimization")
                .setMessage("For best results, you can disable battery optimization for StayLit. This is optional as the app works well even with optimization enabled.")
                .setPositiveButton("Disable") { _, _ ->
                    openBatteryOptimizationSettings()
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    private fun toggleKeepScreenOnService() {
        val serviceIntent = Intent(this, KeepScreenOnService::class.java)
        if (KeepScreenOnService.isServiceRunning) {
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            startService(serviceIntent)
            setScreenOnFlag(false)
        } else {
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
            setScreenOnFlag(true)
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
        unregisterReceiver(screenFlagReceiver)
    }
}