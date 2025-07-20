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

    companion object {
        private const val ACTION_TOGGLE_SERVICE = "com.example.keepscreenon.action.WIDGET_TOGGLE"

        // Static method to force update all widgets
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, ScreenOnWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, ScreenOnWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First widget added")
        // Update immediately when first widget is added
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context, ScreenOnWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
            ACTION_TOGGLE_SERVICE -> {
                handleToggleService(context)
                // Force immediate update after toggle
                forceUpdateAllWidgets(context)
            }
            KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE -> {
                val isActive = intent.getBooleanExtra(KeepScreenOnService.EXTRA_IS_ACTIVE, false)
                Log.d(TAG, "Service status update received: $isActive")
                forceUpdateAllWidgets(context)
            }
        }
    }

    private fun handleToggleService(context: Context) {
        val serviceIntent = Intent(context, KeepScreenOnService::class.java)

        if (KeepScreenOnService.isServiceRunning) {
            Log.d(TAG, "Stopping service via widget")
            serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
            context.startService(serviceIntent)
        } else {
            Log.d(TAG, "Starting service via widget")
            serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    private fun forceUpdateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ScreenOnWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        Log.d(TAG, "Force updating ${appWidgetIds.size} widgets")

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        // Also notify the widget manager that data changed
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_toggle_button)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val isRunning = KeepScreenOnService.isServiceRunning
        Log.d(TAG, "Updating widget $appWidgetId. Service running: $isRunning")

        val views = RemoteViews(context.packageName, R.layout.app_widget_layout)

        // Set the appropriate icon
        val iconResource = if (isRunning) {
            R.drawable.ic_lightbulb_fill
        } else {
            R.drawable.ic_lightbulb_outline
        }

        views.setImageViewResource(R.id.widget_toggle_button, iconResource)

        // Set content description for accessibility
        views.setContentDescription(
            R.id.widget_toggle_button,
            context.getString(if (isRunning) R.string.status_activated else R.string.status_deactivated)
        )

        // Set up click handling with unique request code
        val intent = Intent(context, ScreenOnWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_SERVICE
            // Add widget ID to make intent unique
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId, // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent)

        // Update the widget
        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "Widget $appWidgetId updated successfully with icon: ${if (isRunning) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget $appWidgetId", e)
        }
    }
}
