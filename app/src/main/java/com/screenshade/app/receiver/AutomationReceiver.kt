package com.screenshade.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.screenshade.app.BuildConfig
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.service.OverlayService

class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        // Security Gate: Ensure legal disclaimer is accepted and automation toggle is explicitly enabled
        if (!prefs.hasAcceptedDisclaimer || !prefs.isExternalAutomationEnabled) {
            return
        }

        val action = intent.action ?: return

        when (action) {
            "com.screenshade.ACTION_TOGGLE" -> {
                prefs.isOverlayEnabled = !prefs.isOverlayEnabled
                forwardToService(context, OverlayService.ACTION_TOGGLE)
            }
            "com.screenshade.ACTION_START" -> {
                prefs.isOverlayEnabled = true
                forwardToService(context, OverlayService.ACTION_START)
            }
            "com.screenshade.ACTION_STOP" -> {
                prefs.isOverlayEnabled = false
                forwardToService(context, OverlayService.ACTION_STOP)
            }
            "com.screenshade.ACTION_SET_BRIGHTNESS" -> {
                val rawBrightness = intent.getFloatExtra("brightness", prefs.brightness)
                if (rawBrightness.isNaN() || !rawBrightness.isFinite()) {
                    return
                }
                // Hard safety clamp: maximum safe opacity of 85% to prevent total screen blackout
                val safeBrightness = rawBrightness.coerceIn(0.0f, 0.85f)
                prefs.brightness = safeBrightness
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    this.action = OverlayService.ACTION_SET_BRIGHTNESS
                    putExtra(OverlayService.EXTRA_BRIGHTNESS, safeBrightness)
                }
                forwardIntent(context, serviceIntent)
            }
            "com.screenshade.ACTION_SET_FILTER" -> {
                val filter = intent.getStringExtra("filter") ?: prefs.filterType
                val safeFilter = when (filter.uppercase()) {
                    PreferencesManager.FILTER_NONE -> PreferencesManager.FILTER_NONE
                    PreferencesManager.FILTER_WARM -> PreferencesManager.FILTER_WARM
                    PreferencesManager.FILTER_SEPIA -> PreferencesManager.FILTER_SEPIA
                    PreferencesManager.FILTER_MONO -> PreferencesManager.FILTER_MONO
                    PreferencesManager.FILTER_CUSTOM -> PreferencesManager.FILTER_CUSTOM
                    else -> prefs.filterType
                }
                prefs.filterType = safeFilter
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    this.action = OverlayService.ACTION_SET_FILTER
                    putExtra(OverlayService.EXTRA_FILTER, safeFilter)
                }
                forwardIntent(context, serviceIntent)
            }
        }
    }

    private fun forwardToService(context: Context, serviceAction: String) {
        val serviceIntent = Intent(context, OverlayService::class.java).apply {
            action = serviceAction
        }
        forwardIntent(context, serviceIntent)
    }

    private fun forwardIntent(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }
        try {
            if (intent.action == OverlayService.ACTION_STOP) {
                context.stopService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
        }
    }
}
