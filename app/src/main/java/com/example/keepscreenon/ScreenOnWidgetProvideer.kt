package com.example.keepscreenon // IMPORTANT: Ensure this package name matches your project's actual package name!

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import android.app.ActivityManager // Added for isServiceRunning
import android.util.Log
import android.content.BroadcastReceiver // ADDED import
import android.content.IntentFilter // ADDED import
import androidx.localbroadcastmanager.content.LocalBroadcastManager // ADDED import

class ScreenOnWidgetProvider : AppWidgetProvider() {

    private val TAG = "ScreenOnWidgetProvider"

    companion object {
        const val ACTION_TOGGLE_SERVICE = "com.example.keepscreenon.action.TOGGLE_SERVICE_FROM_WIDGET" // New action for widget toggle
    }

    // BroadcastReceiver to update the widget when the service status changes
    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try { // ADDED try-catch
                Log.d(TAG, "Widget BroadcastReceiver onReceive triggered for action: ${intent?.action}")
                if (context != null && intent?.action == KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE) {
                    val receivedIsActive = intent.getBooleanExtra(KeepScreenOnService.EXTRA_IS_ACTIVE, false)
                    // ADDED LOG: Detailed log of received broadcast data
                    Log.d(TAG, "Received service status update: $receivedIsActive from broadcast. Intent extras: ${intent.extras}")

                    // Get all widget IDs for this provider
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisAppWidget = this@ScreenOnWidgetProvider
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        android.content.ComponentName(context, thisAppWidget::class.java)
                    )

                    // Update all instances of this widget, passing the explicit service state
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, receivedIsActive) // MODIFIED
                    }
                }
            } catch (e: Exception) { // ADDED catch
                Log.e(TAG, "Error in serviceStatusReceiver onReceive: ${e.message}", e)
            }
        }
    }

    override fun onEnabled(context: Context?) { // MODIFIED: Changed from onUpdate to onEnabled
        super.onEnabled(context)
        try { // ADDED try-catch
            Log.d(TAG, "onEnabled called for widget. Registering receiver.")
            if (context != null) {
                val filter = IntentFilter(KeepScreenOnService.ACTION_SERVICE_STATUS_UPDATE)
                LocalBroadcastManager.getInstance(context).registerReceiver(serviceStatusReceiver, filter)
            }
        } catch (e: Exception) { // ADDED catch
            Log.e(TAG, "Error in onEnabled: ${e.message}", e)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        try { // ADDED try-catch
            Log.d(TAG, "onUpdate called for widget.")
            // No longer registering receiver here, handled by onEnabled

            // There may be multiple widgets active, so update all of them
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        } catch (e: Exception) { // ADDED catch
            Log.e(TAG, "Error in onUpdate: ${e.message}", e)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        try { // ADDED try-catch
            if (context == null || intent == null) return

            Log.d(TAG, "Widget onReceive called with action: ${intent.action}")

            if (ACTION_TOGGLE_SERVICE == intent.action) {
                val serviceIntent = Intent(context, KeepScreenOnService::class.java)
                val isCurrentlyRunning = isServiceRunning(context, KeepScreenOnService::class.java)

                if (isCurrentlyRunning) {
                    serviceIntent.action = KeepScreenOnService.ACTION_STOP_FOREGROUND_SERVICE
                    context.stopService(serviceIntent)
                    Log.d(TAG, "Widget: Attempting to stop service.")
                } else {
                    serviceIntent.action = KeepScreenOnService.ACTION_START_FOREGROUND_SERVICE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                        Log.d(TAG, "Widget: Attempting to start foreground service (API >= O) using ContextCompat.")
                    } else {
                        context.startService(serviceIntent)
                        Log.d(TAG, "Widget: Attempting to start service (API < O).")
                    }
                }
                // Immediately update the widget with the expected new state
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisAppWidget = android.content.ComponentName(context, this::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
                val newState = !isCurrentlyRunning // Calculate the expected new state
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, newState) // Pass the new state directly
                }
            }
        } catch (e: Exception) { // ADDED catch
            Log.e(TAG, "Error in onReceive: ${e.message}", e)
        }
    }

    // Helper function to update a single widget instance
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        serviceStateFromBroadcast: Boolean? = null // MODIFIED: Added optional parameter
    ) {
        try { // ADDED try-catch
            val views = RemoteViews(context.packageName, R.layout.app_widget_layout)
            // MODIFIED: Use serviceStateFromBroadcast if available, otherwise check service running state
            val isCurrentlyRunning = serviceStateFromBroadcast ?: isServiceRunning(context, KeepScreenOnService::class.java)

            // Set the appropriate lightbulb icon based on service status
            val iconResource = if (isCurrentlyRunning) R.drawable.ic_lightbulb_fill else R.drawable.ic_lightbulb_outline
            views.setImageViewResource(R.id.widget_toggle_button, iconResource)
            // ADDED LOG: Detailed log of icon being set
            Log.d(TAG, "Widget icon set to: ${if (isCurrentlyRunning) "filled" else "outline"} lightbulb for appWidgetId: $appWidgetId. isCurrentlyRunning: $isCurrentlyRunning (from broadcast: $serviceStateFromBroadcast).")


            // Create an Intent to be broadcast when the button is clicked
            val intent = Intent(context, ScreenOnWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_SERVICE
            }

            // Create a PendingIntent for the button click
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Set the PendingIntent to the ImageButton in the widget layout
            views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent)

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "Widget updated for appWidgetId: $appWidgetId")
        } catch (e: Exception) { // ADDED catch
            Log.e(TAG, "Error in updateAppWidget: ${e.message}", e)
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        try { // ADDED try-catch
            // Unregister the LocalBroadcastManager receiver when the last widget is disabled
            if (context != null) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceStatusReceiver)
                Log.d(TAG, "Widget BroadcastReceiver unregistered onDisabled.")
            }
        } catch (e: Exception) { // ADDED catch
            Log.e(TAG, "Error in onDisabled: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.d(TAG, "Service ${serviceClass.simpleName} is running (checked by widget).")
                return true
            }
        }
        Log.d(TAG, "Service ${serviceClass.simpleName} is NOT running (checked by widget).")
        return false
    }
}
