package com.example.keepscreenon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class KeepScreenOnTileService : TileService() {

    private val TAG = "KeepScreenOnTileService"

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                val isActive = intent.getBooleanExtra(KeepScreenOnService.EXTRA_IS_ACTIVE, false)
                Log.d(TAG, "Tile received service status update: $isActive")
                updateTileState()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile is listening.")

        // Register for regular broadcasts (not LocalBroadcast)
        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStatusReceiver, filter)
        }

        // Update the tile as soon as it becomes visible
        updateTileState()
        Log.d(TAG, "Tile registered for service status updates and initial state set")
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile stopped listening.")

        try {
            unregisterReceiver(serviceStatusReceiver)
            Log.d(TAG, "Tile unregistered from service status updates")
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
            Log.d(TAG, "Receiver was not registered when trying to unregister")
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked. Current service state: ${KeepScreenOnService.isServiceRunning}")

        val serviceIntent = Intent(this, KeepScreenOnService::class.java)

        if (KeepScreenOnService.isServiceRunning) {
            Log.d(TAG, "Stopping service via tile")
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            startService(serviceIntent)
        } else {
            Log.d(TAG, "Starting service via tile")
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        // Immediately update tile state for faster visual feedback
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = KeepScreenOnService.isServiceRunning

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (isRunning) "On" else "Off"
        tile.icon = if (isRunning) {
            Icon.createWithResource(this, R.drawable.ic_qs_lightbulb_on)
        } else {
            Icon.createWithResource(this, R.drawable.ic_qs_lightbulb_off)
        }

        tile.updateTile()
        Log.d(TAG, "Tile state updated to: ${if (isRunning) "ACTIVE" else "INACTIVE"} (service running: $isRunning)")
    }
}