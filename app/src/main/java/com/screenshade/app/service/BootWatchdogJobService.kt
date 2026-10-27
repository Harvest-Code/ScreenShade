package com.screenshade.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.screenshade.app.BuildConfig
import com.screenshade.app.data.PreferencesManager

/**
 * Native Android JobService watchdog for automotive Android head units.
 * Wakes up periodically (every 15 min) or upon CPU resume from Deep Sleep (Suspend-to-RAM).
 * If the MCU killed OverlayService during standby and the user has auto-start enabled,
 * this watchdog transparently restarts OverlayService in headless mode.
 */
class BootWatchdogJobService : JobService() {

    companion object {
        const val JOB_ID = 2001

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val component = ComponentName(context, BootWatchdogJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(15 * 60 * 1000L) // 15 minutes minimum periodic interval
                .setPersisted(true)

            try {
                scheduler.schedule(builder.build())
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }

        fun cancel(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            try {
                scheduler.cancel(JOB_ID)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = PreferencesManager(this)

        if (prefs.hasAcceptedDisclaimer && prefs.isBootEnabled && prefs.isOverlayEnabled) {
            val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }

            if (canDraw && !OverlayService.isRunning) {
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return false // Work is finished
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Reschedule if aborted
    }
}
