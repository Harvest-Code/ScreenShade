package com.screenshade.app

import android.Manifest
import com.screenshade.app.BuildConfig
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.children
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.databinding.ActivityMainBinding
import com.screenshade.app.service.OverlayService
import com.screenshade.app.solar.SolarEngine
import com.screenshade.app.ui.AutomationDialog
import com.screenshade.app.ui.DisclaimerDialog
import com.screenshade.app.ui.OnboardingDialog
import android.content.res.Configuration
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.screenshade.app.util.HapticHelper
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    private var dotPulseAnimator: ObjectAnimator? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handled
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchCoarseLocation()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferencesManager(newBase)
        val lang = prefs.selectedLanguage
        val locale = if (lang == "zh") Locale.SIMPLIFIED_CHINESE else Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
        }
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        prefs = PreferencesManager(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        setupPresets()
        setupScrollBehavior()
        loadPreferencesIntoUI()
        playEntranceAnimation()

        // Legal disclaimer gate first, then first launch onboarding
        if (!prefs.hasAcceptedDisclaimer) {
            showLegalDisclaimer(firstRun = true) { showOnboardingIfNeeded() }
        } else {
            showOnboardingIfNeeded()
        }
    }

    private fun showOnboardingIfNeeded() {
        if (!prefs.isOnboardingCompleted) {
            OnboardingDialog(this) {
                val locCtx = getLocalizedContext(prefs.selectedLanguage)
                updateAllUiTexts(locCtx)
                loadPreferencesIntoUI()
                checkOverlayPermission()
                checkNotificationPermission()
            }.show()
        }
    }

    private fun showLegalDisclaimer(firstRun: Boolean, onAccepted: () -> Unit = {}) {
        val ctx = getLocalizedContext(prefs.selectedLanguage)
        val bg = com.google.android.material.shape.MaterialShapeDrawable(
            com.google.android.material.shape.ShapeAppearanceModel.builder()
                .setAllCornerSizes(28 * resources.displayMetrics.density).build()
        ).apply {
            fillColor = ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(
                    this@MainActivity,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.WHITE
                )
            )
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setBackground(bg)
            .setTitle(ctx.getString(R.string.legal_disclaimer_title))
            .setMessage(ctx.getString(R.string.legal_disclaimer_automotive))
        if (firstRun) {
            builder.setCancelable(false)
                .setPositiveButton(ctx.getString(R.string.legal_accept)) { _, _ ->
                    prefs.hasAcceptedDisclaimer = true
                    onAccepted()
                }
                .setNegativeButton(ctx.getString(R.string.legal_exit)) { _, _ -> finishAffinity() }
        } else {
            builder.setPositiveButton(ctx.getString(R.string.btn_close), null)
                .setNeutralButton(ctx.getString(R.string.legal_dialog_title)) { _, _ -> DisclaimerDialog(this).show() }
        }
        builder.show()
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        checkNotificationPermission()
        updateSolarInfo()
        loadPreferencesIntoUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDotPulse()
    }

    private fun playEntranceAnimation() {
        binding.layoutMainContent.alpha = 0f
        binding.layoutMainContent.translationY = 40f
        binding.layoutMainContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        binding.fabMasterToggle.scaleX = 0.85f
        binding.fabMasterToggle.scaleY = 0.85f
        binding.fabMasterToggle.alpha = 0f
        binding.fabMasterToggle.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(400L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem: MenuItem ->
            HapticHelper.performConfirm(binding.topAppBar)
            when (menuItem.itemId) {
                R.id.action_language -> {
                    showLanguageDialog()
                    true
                }
                R.id.action_kofi -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SUPPORT_URL))
                    startActivity(intent)
                    true
                }
                R.id.action_automation -> {
                    AutomationDialog(this).show()
                    true
                }
                R.id.action_disclaimer -> {
                    showLegalDisclaimer(firstRun = false)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupScrollBehavior() {
        // Shrink FAB on scroll down, extend on scroll up
        binding.nestedScrollMain.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY + 12 && binding.fabMasterToggle.isExtended) {
                binding.fabMasterToggle.shrink()
            } else if (scrollY < oldScrollY - 12 && !binding.fabMasterToggle.isExtended) {
                binding.fabMasterToggle.extend()
            }
        }
    }

    private fun setupListeners() {
        // Support / GitHub Bottom Card Button
        binding.btnKofiBottom.setOnClickListener {
            HapticHelper.performConfirm(it)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SUPPORT_URL))
            startActivity(intent)
        }

        // Permission Warning Button
        binding.btnGrantOverlay.setOnClickListener {
            HapticHelper.performConfirm(it)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Master Switch in Hero Card
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasOverlayPermission()) {
                binding.switchOverlay.isChecked = false
                Toast.makeText(this, R.string.permission_overlay_title, Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            HapticHelper.performConfirm(binding.switchOverlay)
            prefs.isOverlayEnabled = isChecked
            syncServiceState()
            updateUIState(animate = true)
        }

        // Floating Pill Action Button (FAB)
        binding.fabMasterToggle.setOnClickListener {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, R.string.permission_overlay_title, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HapticHelper.performConfirm(binding.fabMasterToggle)
            val newState = !prefs.isOverlayEnabled
            prefs.isOverlayEnabled = newState
            binding.switchOverlay.isChecked = newState
            syncServiceState()
            updateUIState(animate = true)
        }

        // Brightness / Dimming Slider
        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            val fraction = value / 100.0f
            binding.tvDimPercent.text = "${value.toInt()}%"
            if (fromUser) {
                if (value.toInt() % 5 == 0) {
                    HapticHelper.performTick(binding.sliderBrightness)
                }
                prefs.brightness = fraction
                notifyServiceUpdate()
            }
        }

        // Filter Type ChipGroup
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            HapticHelper.performConfirm(binding.chipGroupFilters)
            val filter = when (checkedIds.first()) {
                R.id.chipFilterNone -> PreferencesManager.FILTER_NONE
                R.id.chipFilterSepia -> PreferencesManager.FILTER_SEPIA
                R.id.chipFilterMono -> PreferencesManager.FILTER_MONO
                R.id.chipFilterCustom -> PreferencesManager.FILTER_CUSTOM
                else -> PreferencesManager.FILTER_WARM
            }
            prefs.filterType = filter
            if (filter != PreferencesManager.FILTER_NONE && !binding.switchColorFilter.isChecked) {
                binding.switchColorFilter.isChecked = true // listener persists, enables controls, notifies
            } else {
                notifyServiceUpdate()
            }
        }

        // Filter Intensity Slider
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            binding.tvIntensityPercent.text = "${value.toInt()}%"
            if (fromUser) {
                if (value.toInt() % 5 == 0) {
                    HapticHelper.performTick(binding.sliderIntensity)
                }
                prefs.filterIntensity = value / 100.0f
                notifyServiceUpdate()
            }
        }

        // Transition Speed Selector
        binding.chipGroupSpeed.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            HapticHelper.performConfirm(binding.chipGroupSpeed)
            val speedMs = when (checkedIds.first()) {
                R.id.chipSpeedFast -> 500L
                R.id.chipSpeedSlow -> 5000L
                else -> 1500L
            }
            prefs.transitionSpeedMs = speedMs
        }

        // Dynamic Solar Mode Switch
        binding.switchSolar.setOnCheckedChangeListener { _, isChecked ->
            HapticHelper.performConfirm(binding.switchSolar)
            prefs.isSolarEnabled = isChecked
            binding.sliderBrightness.isEnabled = !isChecked
            updateSolarInfo()
            updateUIState(animate = true)
            notifyServiceUpdate()
        }

        // Independent Color Filter Switch
        binding.switchColorFilter.setOnCheckedChangeListener { _, isChecked ->
            HapticHelper.performConfirm(binding.switchColorFilter)
            prefs.isColorFilterEnabled = isChecked
            setColorFilterControlsEnabled(isChecked)
            notifyServiceUpdate()
        }

        // Update Location button
        binding.btnUpdateLocation.setOnClickListener {
            HapticHelper.performConfirm(it)
            requestOrFetchLocation()
        }

        // Boot and MCU Startup Switches
        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            HapticHelper.performConfirm(binding.switchBoot)
            prefs.isBootEnabled = isChecked
            if (isChecked) {
                com.screenshade.app.service.BootWatchdogJobService.schedule(this)
            } else {
                com.screenshade.app.service.BootWatchdogJobService.cancel(this)
            }
        }

        // Theme Selector
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            HapticHelper.performConfirm(binding.rgTheme)
            val mode = when (checkedId) {
                R.id.rbThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                R.id.rbThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (prefs.themeMode != mode) {
                prefs.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    private fun setupPresets() {
        val presetCards = listOf(
            Triple(binding.cardPresetDay, 0.15f, Pair(PreferencesManager.FILTER_WARM, 0.20f)),
            Triple(binding.cardPresetNight, 0.70f, Pair(PreferencesManager.FILTER_WARM, 0.85f)),
            Triple(binding.cardPresetCinema, 0.85f, Pair(PreferencesManager.FILTER_NONE, 0.0f)),
            Triple(binding.cardPresetReading, 0.40f, Pair(PreferencesManager.FILTER_SEPIA, 0.75f)),
            Triple(binding.cardPresetMono, 0.50f, Pair(PreferencesManager.FILTER_MONO, 1.0f))
        )

        presetCards.forEach { (card, brightness, filterConfig) ->
            card.setOnClickListener {
                animateCardBounce(card) {
                    applyPreset(brightness, filterConfig.first, filterConfig.second)
                }
            }
        }
    }

    private fun animateCardBounce(view: View, onEnd: () -> Unit) {
        view.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(90L)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220L)
                    .setInterpolator(OvershootInterpolator(2.0f))
                    .start()
                onEnd()
            }.start()
    }

    private fun setColorFilterControlsEnabled(enabled: Boolean) {
        binding.chipGroupFilters.children.forEach { it.isEnabled = enabled }
        binding.sliderIntensity.isEnabled = enabled
    }

    private fun applyPreset(brightness: Float, filter: String, intensity: Float) {
        HapticHelper.performConfirm(binding.fabMasterToggle)
        if (!hasOverlayPermission()) {
            Toast.makeText(this, R.string.permission_overlay_title, Toast.LENGTH_SHORT).show()
            return
        }

        prefs.brightness = brightness
        prefs.filterType = filter
        prefs.isColorFilterEnabled = true
        prefs.filterIntensity = intensity

        // Auto-enable overlay if it was off
        if (!prefs.isOverlayEnabled) {
            prefs.isOverlayEnabled = true
            syncServiceState()
        }

        // Disable solar mode when user explicitly picks a preset
        if (prefs.isSolarEnabled) {
            prefs.isSolarEnabled = false
            binding.switchSolar.isChecked = false
        }

        loadPreferencesIntoUI()
        notifyServiceUpdate()
    }

    private fun loadPreferencesIntoUI() {
        binding.switchOverlay.isChecked = prefs.isOverlayEnabled
        val brightnessPercent = (prefs.brightness * 100).toInt()
        binding.sliderBrightness.value = brightnessPercent.toFloat()
        binding.tvDimPercent.text = "$brightnessPercent%"

        when (prefs.filterType) {
            PreferencesManager.FILTER_NONE -> binding.chipFilterNone.isChecked = true
            PreferencesManager.FILTER_SEPIA -> binding.chipFilterSepia.isChecked = true
            PreferencesManager.FILTER_MONO -> binding.chipFilterMono.isChecked = true
            PreferencesManager.FILTER_CUSTOM -> binding.chipFilterCustom.isChecked = true
            else -> binding.chipFilterWarm.isChecked = true
        }

        val intensityPercent = (prefs.filterIntensity * 100).toInt()
        binding.sliderIntensity.value = intensityPercent.toFloat()
        binding.tvIntensityPercent.text = "$intensityPercent%"

        when (prefs.transitionSpeedMs) {
            500L -> binding.chipSpeedFast.isChecked = true
            5000L -> binding.chipSpeedSlow.isChecked = true
            else -> binding.chipSpeedNormal.isChecked = true
        }

        binding.switchSolar.isChecked = prefs.isSolarEnabled
        binding.sliderBrightness.isEnabled = !prefs.isSolarEnabled
        binding.switchColorFilter.isChecked = prefs.isColorFilterEnabled
        setColorFilterControlsEnabled(prefs.isColorFilterEnabled)

        binding.switchBoot.isChecked = prefs.isBootEnabled

        when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rbThemeDark.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rbThemeLight.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }

        binding.cardKofiBottom.visibility = View.VISIBLE

        val locCtx = getLocalizedContext(prefs.selectedLanguage)
        updateAllUiTexts(locCtx)
    }

    /**
     * Updates header status card and morphs the Floating Pill Action Button.
     */
    private fun updateUIState(animate: Boolean, ctx: Context = this) {
        val isShadeActive = prefs.isOverlayEnabled

        // 1. Resolve Badge Status Colors
        val badgeTargetBg = if (isShadeActive) {
            ContextCompat.getColor(this, R.color.status_active_container)
        } else {
            ContextCompat.getColor(this, R.color.status_inactive_container)
        }

        // 2. Animate Status Badge background crossfade
        if (animate) {
            val currentBadgeBg = binding.layoutStatusBadge.backgroundTintList?.defaultColor ?: badgeTargetBg
            ValueAnimator.ofObject(ArgbEvaluator(), currentBadgeBg, badgeTargetBg).apply {
                duration = 300L
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    binding.layoutStatusBadge.backgroundTintList = ColorStateList.valueOf(animator.animatedValue as Int)
                }
                start()
            }
        } else {
            binding.layoutStatusBadge.backgroundTintList = ColorStateList.valueOf(badgeTargetBg)
        }

        // 3. Status Badge Text & Glowing Dot with High Contrast White text
        binding.tvStatusText.setTextColor(Color.WHITE)
        if (isShadeActive) {
            binding.tvStatusText.text = ctx.getString(R.string.status_pill_active)
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
            startDotPulse()
        } else {
            binding.tvStatusText.text = ctx.getString(R.string.status_pill_inactive)
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CFD8DC"))
            stopDotPulse()
        }

        // Mode Chip
        binding.tvModeChip.text = if (prefs.isSolarEnabled) {
            ctx.getString(R.string.status_mode_solar)
        } else {
            ctx.getString(R.string.status_mode_manual)
        }

        // 4. Extended Floating Action Button Morphing
        val targetText = if (isShadeActive) ctx.getString(R.string.fab_active) else ctx.getString(R.string.fab_activate)
        val targetIconRes = if (isShadeActive) R.drawable.ic_shade_active else R.drawable.ic_shade_inactive

        val tv = TypedValue()
        theme.resolveAttribute(
            if (isShadeActive) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorSurfaceVariant,
            tv,
            true
        )
        val targetFabBg = tv.data

        theme.resolveAttribute(
            if (isShadeActive) com.google.android.material.R.attr.colorOnPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant,
            tv,
            true
        )
        val targetFabContent = tv.data

        if (animate) {
            val prevFabBg = binding.fabMasterToggle.backgroundTintList?.defaultColor ?: targetFabBg
            val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), prevFabBg, targetFabBg).apply {
                duration = 320L
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    binding.fabMasterToggle.backgroundTintList = ColorStateList.valueOf(animator.animatedValue as Int)
                }
            }
            colorAnimator.start()

            // Smooth crossfade of text and icon
            binding.fabMasterToggle.animate()
                .alpha(0.3f)
                .setDuration(120L)
                .withEndAction {
                    binding.fabMasterToggle.text = targetText
                    binding.fabMasterToggle.setIconResource(targetIconRes)
                    binding.fabMasterToggle.setTextColor(ColorStateList.valueOf(targetFabContent))
                    binding.fabMasterToggle.iconTint = ColorStateList.valueOf(targetFabContent)
                    binding.fabMasterToggle.elevation = if (isShadeActive) 10f else 4f
                    binding.fabMasterToggle.animate()
                        .alpha(1f)
                        .setDuration(180L)
                        .start()
                }.start()
        } else {
            binding.fabMasterToggle.text = targetText
            binding.fabMasterToggle.setIconResource(targetIconRes)
            binding.fabMasterToggle.backgroundTintList = ColorStateList.valueOf(targetFabBg)
            binding.fabMasterToggle.setTextColor(ColorStateList.valueOf(targetFabContent))
            binding.fabMasterToggle.iconTint = ColorStateList.valueOf(targetFabContent)
            binding.fabMasterToggle.elevation = if (isShadeActive) 10f else 4f
        }
    }

    private fun startDotPulse() {
        if (dotPulseAnimator?.isRunning == true) return
        val alphaHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 1.0f)
        val scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.20f)
        val scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.20f)

        dotPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.viewStatusDot,
            alphaHolder,
            scaleXHolder,
            scaleYHolder
        ).apply {
            duration = 750L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }

    private fun stopDotPulse() {
        dotPulseAnimator?.cancel()
        dotPulseAnimator = null
        binding.viewStatusDot.alpha = 1.0f
        binding.viewStatusDot.scaleX = 1.0f
        binding.viewStatusDot.scaleY = 1.0f
    }

    private fun updateSolarInfo(ctx: Context = this) {
        val data = SolarEngine.calculate(prefs.latitude, prefs.longitude, Calendar.getInstance())
        val sunriseStr = SolarEngine.formatMinutesToTime(data.sunriseMinutes)
        val sunsetStr = SolarEngine.formatMinutesToTime(data.sunsetMinutes)

        // Update Hero Card symmetrical chips
        binding.tvSunriseChip.text = ctx.getString(R.string.solar_sunrise_chip, sunriseStr)
        binding.tvSunsetChip.text = ctx.getString(R.string.solar_sunset_chip, sunsetStr)

        val phaseText = when {
            data.elevationDeg > 0.0 -> ctx.getString(R.string.solar_phase_day)
            data.elevationDeg in -6.0..0.0 -> ctx.getString(R.string.solar_phase_twilight)
            else -> ctx.getString(R.string.solar_phase_night)
        }
        binding.tvSolarPhaseChip.text = ctx.getString(R.string.solar_phase_format, phaseText)

        // Update Section 5 Solar details
        binding.tvSolarElevation.text = ctx.getString(R.string.solar_status_elevation, data.elevationDeg)
        val timesFormatted = "${ctx.getString(R.string.solar_status_sunrise, sunriseStr)} | ${ctx.getString(R.string.solar_status_sunset, sunsetStr)}"
        binding.tvSolarTimes.text = timesFormatted
    }

    private fun requestOrFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fetchCoarseLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun fetchCoarseLocation() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            location?.let {
                prefs.latitude = it.latitude
                prefs.longitude = it.longitude
                val locCtx = getLocalizedContext(prefs.selectedLanguage)
                updateSolarInfo(locCtx)
                notifyServiceUpdate()
                Toast.makeText(this, locCtx.getString(R.string.solar_location_manual, it.latitude, it.longitude), Toast.LENGTH_SHORT).show()
            } ?: run {
                val locCtx = getLocalizedContext(prefs.selectedLanguage)
                Toast.makeText(this, locCtx.getString(R.string.solar_location_unavailable), Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
        }
    }

    private fun checkOverlayPermission() {
        val hasPermission = hasOverlayPermission()
        binding.cardPermissionWarning.visibility = if (hasPermission) View.GONE else View.VISIBLE
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun syncServiceState() {
        val intent = Intent(this, OverlayService::class.java)
        if (prefs.isOverlayEnabled) {
            intent.action = OverlayService.ACTION_START
            ContextCompat.startForegroundService(this, intent)
            if (prefs.isBootEnabled) {
                com.screenshade.app.service.BootWatchdogJobService.schedule(this)
            }
        } else {
            intent.action = OverlayService.ACTION_STOP
            stopService(intent)
        }
    }

    private fun notifyServiceUpdate() {
        if (prefs.isOverlayEnabled) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            "🇬🇧 English",
            "🇮🇹 Italiano",
            "🇪🇸 Español",
            "🇩🇪 Deutsch",
            "🇷🇺 Русский",
            "🇯🇵 日本語",
            "🇨🇳 中文 (简体)"
        )
        val codes = arrayOf("en", "it", "es", "de", "ru", "ja", "zh")
        val currentLang = prefs.selectedLanguage
        val checkedIndex = codes.indexOf(currentLang).let { if (it >= 0) it else 0 }

        val locCtx = getLocalizedContext(currentLang)
        MaterialAlertDialogBuilder(this)
            .setTitle(locCtx.getString(R.string.menu_language))
            .setSingleChoiceItems(languages, checkedIndex) { dialog, which ->
                val selectedCode = codes[which]
                HapticHelper.performConfirm(binding.root)
                prefs.selectedLanguage = selectedCode

                val localizedContext = getLocalizedContext(selectedCode)
                updateAllUiTexts(localizedContext)

                dialog.dismiss()
            }
            .setNegativeButton(locCtx.getString(R.string.btn_close), null)
            .show()
    }

    fun getLocalizedContext(langCode: String): Context {
        val locale = if (langCode == "zh") Locale.SIMPLIFIED_CHINESE else Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration).apply {
            setLocale(locale)
        }
        return createConfigurationContext(config)
    }

    private fun updateAllUiTexts(context: Context) {
        // 1. Toolbar title & subtitle & menu titles
        binding.topAppBar.title = context.getString(R.string.toolbar_title)
        binding.topAppBar.subtitle = context.getString(R.string.toolbar_subtitle)
        binding.topAppBar.menu.findItem(R.id.action_language)?.title = context.getString(R.string.menu_language)
        val supportMenuItem = binding.topAppBar.menu.findItem(R.id.action_kofi)
        supportMenuItem?.isVisible = true
        supportMenuItem?.title = if (BuildConfig.ENABLE_DONATIONS) {
            context.getString(R.string.menu_kofi)
        } else {
            context.getString(R.string.menu_github)
        }
        binding.topAppBar.menu.findItem(R.id.action_automation)?.title = context.getString(R.string.menu_automation)
        binding.topAppBar.menu.findItem(R.id.action_disclaimer)?.title = context.getString(R.string.menu_disclaimer)

        // 2. Permission Banner (if visible)
        binding.tvPermissionTitle.text = context.getString(R.string.permission_overlay_title)
        binding.tvPermissionDesc.text = context.getString(R.string.permission_overlay_desc)
        binding.btnGrantOverlay.text = context.getString(R.string.permission_grant_btn)

        // 3. Hero Card Description, Status Badge, Mode Chip & Solar Info
        binding.tvHeroDesc.text = context.getString(R.string.hero_desc)
        updateUIState(animate = false, ctx = context)
        updateSolarInfo(context)

        // 4. Presets Header & Items
        binding.tvSectionPresets.text = context.getString(R.string.section_presets)
        binding.tvPresetDay.text = context.getString(R.string.preset_day)
        binding.tvPresetNight.text = context.getString(R.string.preset_night)
        binding.tvPresetCinema.text = context.getString(R.string.preset_cinema)
        binding.tvPresetReading.text = context.getString(R.string.preset_reading)
        binding.tvPresetMono.text = context.getString(R.string.preset_mono)

        // 5. Software Dimming Card
        binding.tvLabelBrightness.text = context.getString(R.string.label_brightness)
        binding.tvDescBrightness.text = context.getString(R.string.desc_brightness)
        binding.tvLabelTransitionSpeed.text = context.getString(R.string.label_transition_speed)
        binding.chipSpeedFast.text = context.getString(R.string.transition_fast)
        binding.chipSpeedNormal.text = context.getString(R.string.transition_normal)
        binding.chipSpeedSlow.text = context.getString(R.string.transition_slow)

        // 6. Chromatic & Anti-Blue Filters Card
        binding.tvSectionFilters.text = context.getString(R.string.section_filters)
        binding.chipFilterNone.text = context.getString(R.string.filter_none)
        binding.chipFilterWarm.text = context.getString(R.string.filter_warm)
        binding.chipFilterSepia.text = context.getString(R.string.filter_sepia)
        binding.chipFilterMono.text = context.getString(R.string.filter_mono)
        binding.chipFilterCustom.text = context.getString(R.string.filter_custom)
        binding.tvLabelFilterIntensity.text = context.getString(R.string.label_filter_intensity)

        // 7. Astronomical Solar Engine Card
        binding.tvSwitchSolar.text = context.getString(R.string.switch_solar)
        binding.tvSwitchSolarDesc.text = context.getString(R.string.switch_solar_desc)
        binding.btnUpdateLocation.text = context.getString(R.string.solar_location_btn)

        // 8. Car MCU & Boot Behavior Card
        binding.tvSectionBoot.text = context.getString(R.string.section_boot)
        binding.tvSwitchBoot.text = context.getString(R.string.switch_boot)
        binding.tvSwitchBootDesc.text = context.getString(R.string.switch_boot_desc)

        // 9. Appearance & Theme Card
        binding.tvSectionAppearance.text = context.getString(R.string.section_appearance)
        binding.rbThemeDark.text = context.getString(R.string.theme_dark)
        binding.rbThemeLight.text = context.getString(R.string.theme_light)
        binding.rbThemeSystem.text = context.getString(R.string.theme_system)

        // 10. Support / GitHub Card (100% Google Play compliant on play flavor)
        binding.cardKofiBottom.visibility = View.VISIBLE
        if (BuildConfig.ENABLE_DONATIONS) {
            binding.tvKofiCardTitle.text = context.getString(R.string.kofi_card_title)
            binding.tvKofiCardDesc.text = context.getString(R.string.kofi_card_description)
            binding.btnKofiBottom.text = context.getString(R.string.kofi_button_label)
        } else {
            binding.tvKofiCardTitle.text = context.getString(R.string.github_card_title)
            binding.tvKofiCardDesc.text = context.getString(R.string.github_card_description)
            binding.btnKofiBottom.text = context.getString(R.string.github_button_label)
        }

        // 11. Extended Floating Action Button
        val isShadeActive = prefs.isOverlayEnabled
        binding.fabMasterToggle.text = if (isShadeActive) context.getString(R.string.fab_active) else context.getString(R.string.fab_activate)
    }
}
