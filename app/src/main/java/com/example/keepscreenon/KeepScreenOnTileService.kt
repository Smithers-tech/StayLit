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
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class KeepScreenOnTileService : TileService() {

    private val TAG = "KeepScreenOnTileService"

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                Log.d(TAG, "Tile received service status update.")
                updateTileState()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile is listening.")
        val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, filter)
        // Update the tile as soon as it becomes visible
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile stopped listening.")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked.")
        val serviceIntent = Intent(this, KeepScreenOnService::class.java)
        // --- CHANGE: Use the reliable static variable ---
        if (KeepScreenOnService.isServiceRunning) {
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            startService(serviceIntent)
        } else {
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
        // The broadcast receiver will handle the UI update, but we can call it
        // immediately for a faster visual response.
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        // --- CHANGE: Use the reliable static variable ---
        val isRunning = KeepScreenOnService.isServiceRunning

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (isRunning) "On" else "Off"
        tile.icon = if (isRunning)
            Icon.createWithResource(this, R.drawable.ic_qs_lightbulb_on)
        else
            Icon.createWithResource(this, R.drawable.ic_qs_lightbulb_off)

        tile.updateTile()
        Log.d(TAG, "Tile state updated to: ${tile.state}")
    }

    // --- REMOVED: The old, unreliable isServiceRunning method is no longer needed. ---
}
