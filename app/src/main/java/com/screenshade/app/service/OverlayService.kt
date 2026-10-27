package com.screenshade.app.service

import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.screenshade.app.BuildConfig
import com.screenshade.app.MainActivity
import com.screenshade.app.R
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.solar.SolarEngine
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ShadeOverlayView
    private lateinit var prefs: PreferencesManager

    private var currentAlpha = 0.0f
    private var targetAlpha = 0.0f
    private var fadeAnimator: ValueAnimator? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var solarJob: Job? = null

    /**
     * In-memory dynamic broadcast receiver registered with RECEIVER_EXPORTED.
     * Intercepts Android screen state transitions, configuration/orientation shifts,
     * RTC clock synchronizations, and Car Head-Unit MCU Wakeup broadcasts from Deep Sleep.
     */
    private val mcuWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_CONFIGURATION_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                "autochips.intent.action.ACC_ON",
                "com.ts.main.action.ACC_ON",
                "com.microntek.bootcheck",
                "com.fyt.boot",
                "com.fyt.system.acc.on",
                "com.syu.canbus.action.ACC_ON",
                "com.syu.ms.action.ACC_ON",
                "com.zjinnova.boot",
                "com.ts.car.acc.on",
                "com.microntek.canbus.acc" -> {
                    ensureOverlayAttached()
                    applySettings(immediate = true)
                    startSolarLoopIfNeeded()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "screenshade_overlay_channel_low"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.screenshade.ACTION_START"
        const val ACTION_STOP = "com.screenshade.ACTION_STOP"
        const val ACTION_UPDATE = "com.screenshade.ACTION_UPDATE"
        const val ACTION_SET_BRIGHTNESS = "com.screenshade.ACTION_SET_BRIGHTNESS"
        const val ACTION_SET_FILTER = "com.screenshade.ACTION_SET_FILTER"
        const val ACTION_TOGGLE = "com.screenshade.ACTION_TOGGLE"

        const val EXTRA_BRIGHTNESS = "brightness"
        const val EXTRA_FILTER = "filter"

        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true

        val mcuFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction("autochips.intent.action.ACC_ON")
            addAction("com.ts.main.action.ACC_ON")
            addAction("com.microntek.bootcheck")
            addAction("com.fyt.boot")
            addAction("com.fyt.system.acc.on")
            addAction("com.syu.canbus.action.ACC_ON")
            addAction("com.syu.ms.action.ACC_ON")
            addAction("com.zjinnova.boot")
            addAction("com.ts.car.acc.on")
            addAction("com.microntek.canbus.acc")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                mcuWakeReceiver,
                mcuFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(mcuWakeReceiver, mcuFilter)
        }

        setupOverlay()
        applySettings(immediate = true)
        startSolarLoopIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isRunning = false
                prefs.isOverlayEnabled = false
                // Note: Do NOT cancel BootWatchdogJobService here. It remains active to revive
                // overlay on next vehicle wake/ignition if prefs.isBootEnabled is true.
                if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
                    try {
                        windowManager.removeView(overlayView)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace()
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                isRunning = true
                prefs.isOverlayEnabled = true
                ensureOverlayAttached()
                applySettings(immediate = true)
                startSolarLoopIfNeeded()
            }
            ACTION_TOGGLE -> {
                prefs.isOverlayEnabled = !prefs.isOverlayEnabled
                if (!prefs.isOverlayEnabled) {
                    isRunning = false
                    if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
                        try {
                            windowManager.removeView(overlayView)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace()
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    isRunning = true
                    ensureOverlayAttached()
                    applySettings(immediate = false)
                    startSolarLoopIfNeeded()
                }
            }
            ACTION_SET_BRIGHTNESS -> {
                val b = intent.getFloatExtra(EXTRA_BRIGHTNESS, prefs.brightness)
                prefs.brightness = b
                ensureOverlayAttached()
                applySettings(immediate = false)
            }
            ACTION_SET_FILTER -> {
                val f = intent.getStringExtra(EXTRA_FILTER) ?: prefs.filterType
                prefs.filterType = f
                ensureOverlayAttached()
                applySettings(immediate = false)
            }
            ACTION_UPDATE -> {
                ensureOverlayAttached()
                applySettings(immediate = false)
                startSolarLoopIfNeeded()
            }
            else -> {
                ensureOverlayAttached()
                applySettings(immediate = true)
                startSolarLoopIfNeeded()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Automotive resilience: if overlay was enabled, schedule watchdog and quick alarm revival
        if (prefs.isOverlayEnabled && prefs.hasAcceptedDisclaimer) {
            BootWatchdogJobService.schedule(this)
            val restartIntent = Intent(applicationContext, OverlayService::class.java).apply {
                action = ACTION_START
            }
            try {
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    999,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000L,
                    pendingIntent
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return layoutParams
    }

    private fun setupOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        overlayView = ShadeOverlayView(this)
        try {
            windowManager.addView(overlayView, createLayoutParams())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            stopSelf()
        }
    }

    private fun ensureOverlayAttached() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        if (!::overlayView.isInitialized) {
            setupOverlay()
            return
        }
        if (!overlayView.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(overlayView)
            } catch (ignored: Exception) {
            }
            try {
                windowManager.addView(overlayView, createLayoutParams())
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun applySettings(immediate: Boolean) {
        if (!::overlayView.isInitialized) return

        // Channel 1: dimming (alpha of black veil). Solar only touches this.
        var brightness = prefs.brightness
        if (prefs.isSolarEnabled) {
            try {
                // Offline: uses stored/default lat-lon + system RTC clock only.
                val solarData = SolarEngine.calculate(prefs.latitude, prefs.longitude)
                brightness = SolarEngine.computeDynamicSolarValues(solarData.elevationDeg).first
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }

        // Channel 2: color filter (independent of solar tracking).
        val filterType = if (prefs.isColorFilterEnabled) prefs.filterType else PreferencesManager.FILTER_NONE

        // Unbreakable safety clamp: 0.85 maximum darkness
        targetAlpha = brightness.coerceIn(0.0f, 0.85f)
        overlayView.setFilter(filterType, prefs.filterIntensity)

        val duration = if (immediate) 0L else prefs.transitionSpeedMs.coerceIn(300L, 5000L)

        fadeAnimator?.cancel()
        if (duration > 0L) {
            fadeAnimator = ValueAnimator.ofFloat(currentAlpha, targetAlpha).apply {
                this.duration = duration
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    currentAlpha = animator.animatedValue as Float
                    overlayView.setDimAlpha(currentAlpha)
                }
                start()
            }
        } else {
            currentAlpha = targetAlpha
            overlayView.setDimAlpha(currentAlpha)
        }

        updateNotification()
    }

    private fun startSolarLoopIfNeeded() {
        solarJob?.cancel()
        if (prefs.isSolarEnabled) {
            solarJob = serviceScope.launch {
                while (isActive) {
                    delay(60_000L) // Update every minute
                    applySettings(immediate = false)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        if (!isRunning) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Instant emergency fail-safe to shut down overlay with a single tap
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dimPercent = (targetAlpha * 100).toInt()
        val locCtx = getLocalizedContext(prefs.selectedLanguage)
        val filterLabel = when (prefs.filterType) {
            PreferencesManager.FILTER_WARM -> locCtx.getString(R.string.filter_warm)
            PreferencesManager.FILTER_SEPIA -> locCtx.getString(R.string.filter_sepia)
            PreferencesManager.FILTER_MONO -> locCtx.getString(R.string.filter_mono)
            PreferencesManager.FILTER_CUSTOM -> locCtx.getString(R.string.filter_custom)
            else -> locCtx.getString(R.string.filter_none)
        }
        val statusText = locCtx.getString(R.string.notification_status_format, dimPercent, filterLabel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(locCtx.getString(R.string.notification_title))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, locCtx.getString(R.string.notification_action_stop), pendingStop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun getLocalizedContext(langCode: String): Context {
        val locale = if (langCode == "zh") java.util.Locale.SIMPLIFIED_CHINESE else java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration).apply {
            setLocale(locale)
        }
        return createConfigurationContext(config)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fadeAnimator?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(mcuWakeReceiver)
        } catch (ignored: Exception) {
        }
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Custom full-screen overlay view that handles black dimming veil
     * and chromatic color filters via ColorMatrix.
     */
    inner class ShadeOverlayView(context: Context) : View(context) {

        private val dimPaint = Paint()
        private val tintPaint = Paint()
        private var hasFilter = false

        init {
            setWillNotDraw(false)
        }

        fun setDimAlpha(alphaFraction: Float) {
            val safeFraction = if (alphaFraction.isNaN() || !alphaFraction.isFinite()) 0.0f else alphaFraction.coerceIn(0.0f, 0.85f)
            val alphaInt = (safeFraction * 255).toInt().coerceIn(0, 217) // 217 ~ 85% opacity limit
            dimPaint.color = Color.argb(alphaInt, 0, 0, 0)
            invalidate()
        }

        fun setFilter(filterType: String, intensity: Float) {
            hasFilter = filterType != PreferencesManager.FILTER_NONE
            if (!hasFilter) {
                tintPaint.colorFilter = null
                invalidate()
                return
            }

            val matrix = com.screenshade.app.util.ColorFilterUtils.createFilterMatrix(filterType, intensity)
            tintPaint.colorFilter = ColorMatrixColorFilter(matrix)
            val tintColor = when (filterType) {
                PreferencesManager.FILTER_SEPIA -> Color.rgb(180, 130, 70)
                PreferencesManager.FILTER_MONO -> Color.rgb(80, 80, 80)
                PreferencesManager.FILTER_CUSTOM -> Color.rgb(255, 75, 0)
                else -> Color.rgb(255, 140, 0)
            }
            val tintAlpha = (intensity * 140).toInt().coerceIn(0, 200)
            tintPaint.color = Color.argb(tintAlpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 1. Draw chromatic tint if active
            if (hasFilter) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
            }
            // 2. Draw black dimming veil for software brightness attenuation
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }
    }
}
