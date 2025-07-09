package com.example.keepscreenon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Build

class KeepScreenOnTileService : TileService() {

    private val TAG = "KeepScreenOnTileService"
    private var isServiceRunning: Boolean = false

    // BroadcastReceiver to update the tile when the service status changes
    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                val receivedIsActive = intent.getBooleanExtra(KeepScreenOnService.EXTRA_IS_ACTIVE, false)
                Log.d(TAG, "Tile BroadcastReceiver received service status update: $receivedIsActive")
                if (isServiceRunning != receivedIsActive) {
                    isServiceRunning = receivedIsActive
                    updateTileState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepScreenOnTileService onCreate")
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "onTileAdded")
        // Initial update when tile is added
        isServiceRunning = isServiceRunning(this, KeepScreenOnService::class.java)
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        // Register receiver when the tile is visible
        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)

        // Update tile state immediately when listening starts
        isServiceRunning = isServiceRunning(this, KeepScreenOnService::class.java)
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening")
        // Unregister receiver when the tile is no longer visible
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick - current service state: $isServiceRunning")
        val serviceIntent = Intent(this, KeepScreenOnService::class.java)
        if (isServiceRunning) {
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            stopService(serviceIntent)
            Log.d(TAG, "Tile: Attempting to stop service.")
        } else {
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
                Log.d(TAG, "Tile: Attempting to start foreground service (API >= O).")
            } else {
                startService(serviceIntent)
            }
        }
        // Update tile state immediately after click, assuming the service will respond
        isServiceRunning = !isServiceRunning
        updateTileState()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "onTileRemoved")
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (isServiceRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        // First line (title)
        tile.label = getString(R.string.tile_label)

        // Second line (subtitle)
        tile.subtitle = if (isServiceRunning) "On" else "Off"

        tile.icon = if (isServiceRunning)
            Icon.createWithResource(this, R.drawable.ic_qs_lightbulb_on)
        else
            Icon.createWithResource(this, R.drawable.ic_qs_lightbulb_off)

        tile.updateTile()
        Log.d(TAG, "Tile state updated to: ${if (isServiceRunning) "ACTIVE" else "INACTIVE"}")
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.d(TAG, "Service ${serviceClass.simpleName} is running (checked by tile).")
                return true
            }
        }
        Log.d(TAG, "Service ${serviceClass.simpleName} is NOT running (checked by tile).")
        return false
    }
}
