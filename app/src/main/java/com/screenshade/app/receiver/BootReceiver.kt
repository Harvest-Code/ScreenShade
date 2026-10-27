package com.screenshade.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.screenshade.app.BuildConfig
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.service.BootWatchdogJobService
import com.screenshade.app.service.OverlayService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        // Strict safety gate: user must have accepted legal disclaimer and explicitly enabled auto-start on boot/wake
        if (!prefs.hasAcceptedDisclaimer || !prefs.isBootEnabled) {
            return
        }

        // Overlay permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        val action = intent.action

        // Special handling for TIME_SET: this can trigger while driving (e.g. GPS / cellular RTC sync).
        // If the driver manually turned off the overlay during this trip, do NOT re-enable it.
        if (action == Intent.ACTION_TIME_CHANGED) {
            if (!prefs.isOverlayEnabled) {
                return
            }
            val serviceIntent = Intent(context, OverlayService::class.java).apply {
                this.action = if (OverlayService.isRunning) OverlayService.ACTION_UPDATE else OverlayService.ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
            return
        }

        // Vehicle power-on, ignition, and MCU wakeup events:
        // BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON, MCU broadcasts (ACC_ON, bootcheck, fyt.boot, etc.),
        // and ACTION_POWER_CONNECTED (cigarette lighter / 12V switched power on).
        // 1. Always force overlay enabled for the new trip (even if turned off in prior session)
        prefs.isOverlayEnabled = true

        // 2. Ensure the periodic watchdog job is active for Deep Sleep anti-kill resilience
        BootWatchdogJobService.schedule(context)

        // 3. Start foreground overlay service headless with user-configured filter & brightness
        val serviceIntent = Intent(context, OverlayService::class.java).apply {
            this.action = OverlayService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
        }
        // Never launch MainActivity from boot/wake broadcasts: always silent headless execution.
    }
}
