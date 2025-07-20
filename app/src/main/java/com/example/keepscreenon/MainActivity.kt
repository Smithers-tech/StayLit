package com.example.keepscreenon

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: MaterialButton
    private lateinit var statusTextView: TextView
    private lateinit var statusSubTextView: TextView
    private lateinit var toggleIcon: ImageView
    private lateinit var autoOffDurationSpinner: AutoCompleteTextView
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var samsungTroubleshootingCard: MaterialCardView
    private lateinit var glowBackground: View
    private lateinit var gradientBackground: View
    private lateinit var widgetChip: Chip
    private lateinit var tileChip: Chip

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

        // Initialize all views
        initializeViews()

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

        // Show Samsung troubleshooting card if needed
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            samsungTroubleshootingCard.visibility = View.VISIBLE
            checkOverlayPermission()
        }

        // Show battery optimization dialog on first launch only
        if (isFirstLaunch()) {
            promptToDisableBatteryOptimizations()
        }
    }

    private fun initializeViews() {
        // Existing views
        toggleButton = findViewById(R.id.toggleScreenOnButton)
        statusTextView = findViewById(R.id.statusTextView)
        statusSubTextView = findViewById(R.id.statusSubTextView)
        toggleIcon = findViewById(R.id.toggleIcon)
        autoOffDurationSpinner = findViewById(R.id.autoOffDurationSpinner)
        samsungTroubleshootingCard = findViewById(R.id.samsungTroubleshootingCard)

        // New views
        glowBackground = findViewById(R.id.glowBackground)
        gradientBackground = findViewById(R.id.gradientBackground)
        widgetChip = findViewById(R.id.widgetChip)
        tileChip = findViewById(R.id.tileChip)

        // Setup chip click listeners
        setupChipListeners()
    }

    private fun setupChipListeners() {
        widgetChip.setOnClickListener {
            // Show instructions for adding widget
            MaterialAlertDialogBuilder(this)
                .setTitle("Add Widget")
                .setMessage("To add the StayLit widget:\n\n1. Long press on your home screen\n2. Select 'Widgets'\n3. Find and drag the StayLit widget to your home screen")
                .setPositiveButton("Got it", null)
                .show()
        }

        tileChip.setOnClickListener {
            // Show instructions for quick settings tile
            MaterialAlertDialogBuilder(this)
                .setTitle("Quick Settings Tile")
                .setMessage("To add the quick tile:\n\n1. Swipe down twice from the top\n2. Tap the pencil icon\n3. Find StayLit and drag it to your quick settings")
                .setPositiveButton("Got it", null)
                .show()
        }
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

    private fun isFirstLaunch(): Boolean {
        val isFirst = sharedPrefs.getBoolean("is_first_launch", true)
        if (isFirst) {
            sharedPrefs.edit().putBoolean("is_first_launch", false).apply()
        }
        return isFirst
    }

    private fun promptToDisableBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Optimize Battery Usage")
                .setMessage("For best results, you may want to exclude StayLit from battery optimization. This helps ensure the app works reliably in the background.\n\nYou can do this manually in Settings > Apps > StayLit > Battery.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun toggleKeepScreenOnService() {
        // Add haptic feedback
        toggleButton.performHapticFeedback(
            android.view.HapticFeedbackConstants.VIRTUAL_KEY,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

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
            animateToActiveState()
        } else {
            animateToInactiveState()
        }
    }

    private fun animateToActiveState() {
        // Update text
        statusTextView.text = getString(R.string.status_activated)
        statusSubTextView.text = getString(R.string.status_activated_sub)
        toggleButton.text = getString(R.string.action_deactivate)

        // Icon animation
        toggleIcon.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                toggleIcon.setImageResource(R.drawable.ic_lightbulb_on)
                toggleIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.md_theme_light_primary)
                )
                toggleIcon.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        toggleIcon.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            .start()

        // Show and animate glow
        glowBackground.visibility = View.VISIBLE
        glowBackground.alpha = 0f
        glowBackground.animate()
            .alpha(1f)
            .setDuration(500)
            .start()

        // Pulse animation for glow
        val pulseAnimator = ValueAnimator.ofFloat(0.8f, 1.2f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                glowBackground.scaleX = scale
                glowBackground.scaleY = scale
            }
        }
        pulseAnimator.start()
        glowBackground.tag = pulseAnimator

        // Button state animation
        animateButtonState(true)

        // Gradient background animation
        gradientBackground.animate()
            .alpha(0.15f)
            .setDuration(500)
            .start()
    }

    private fun animateToInactiveState() {
        // Update text
        statusTextView.text = getString(R.string.status_deactivated)
        statusSubTextView.text = getString(R.string.status_deactivated_sub)
        toggleButton.text = getString(R.string.action_activate)

        // Icon animation
        toggleIcon.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                toggleIcon.setImageResource(R.drawable.ic_lightbulb_off)
                toggleIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.md_theme_light_onSurfaceVariant)
                )
                toggleIcon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()

        // Hide glow with animation
        (glowBackground.tag as? ValueAnimator)?.cancel()
        glowBackground.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                glowBackground.visibility = View.INVISIBLE
            }
            .start()

        // Button state animation
        animateButtonState(false)

        // Gradient background animation
        gradientBackground.animate()
            .alpha(0.1f)
            .setDuration(500)
            .start()
    }

    private fun animateButtonState(isActive: Boolean) {
        if (isActive) {
            // Change to outline style for deactivate
            toggleButton.strokeWidth = 2
            toggleButton.strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.md_theme_light_primary)
            )
            toggleButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            toggleButton.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_primary))
            toggleButton.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.md_theme_light_primary)
            )
        } else {
            // Change to filled style for activate
            toggleButton.strokeWidth = 0
            toggleButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.md_theme_light_primary)
            )
            toggleButton.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onPrimary))
            toggleButton.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.md_theme_light_onPrimary)
            )
        }

        // Subtle bounce animation
        toggleButton.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                toggleButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
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

        // Cancel any running animations
        (glowBackground.tag as? ValueAnimator)?.cancel()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
        unregisterReceiver(screenFlagReceiver)
    }
}
