package com.example.keepscreenon

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class ScreenOnWidgetProvider : AppWidgetProvider() {

    private val TAG = "ScreenOnWidgetProvider"
    // A custom action for the widget button to toggle the service
    private val ACTION_TOGGLE_SERVICE = "com.example.keepscreenon.action.WIDGET_TOGGLE"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widgets when the phone reboots or the widget is added
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "Widget received intent with action: ${intent.action}")

        // Handle our custom toggle action
        if (ACTION_TOGGLE_SERVICE == intent.action) {
            val serviceIntent = Intent(context, KeepScreenOnService::class.java)
            // --- CHANGE: Use the reliable static variable ---
            if (KeepScreenOnService.isServiceRunning) {
                serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
                context.startService(serviceIntent)
            } else {
                serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
        // Handle the status update from the service
        else if (KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE == intent.action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context, ScreenOnWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.app_widget_layout)
        // --- CHANGE: Use the reliable static variable ---
        val isRunning = KeepScreenOnService.isServiceRunning
        val iconResource = if (isRunning) R.drawable.ic_lightbulb_fill else R.drawable.ic_lightbulb_outline

        views.setImageViewResource(R.id.widget_toggle_button, iconResource)

        // Set up the intent that will be broadcast when the widget is clicked
        val intent = Intent(context, ScreenOnWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_SERVICE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget $appWidgetId updated. Service running: $isRunning")
    }

    // --- REMOVED: The old, unreliable isServiceRunning method is no longer needed. ---
}
