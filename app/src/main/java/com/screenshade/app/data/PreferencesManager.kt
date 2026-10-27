package com.screenshade.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.screenshade.app.BuildConfig

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = run {
        val appContext = context.applicationContext ?: context
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val deviceContext = appContext.createDeviceProtectedStorageContext()
            try {
                // Automatically migrate settings from Credential Encrypted (CE) storage to Device Protected (DE) storage
                deviceContext.moveSharedPreferencesFrom(appContext, PREFS_NAME)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
            deviceContext
        } else {
            appContext
        }
        storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    var brightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, 0.40f)
        set(value) = prefs.edit().putFloat(
            KEY_BRIGHTNESS,
            if (value.isNaN() || !value.isFinite()) 0.40f else value.coerceIn(0.0f, 0.85f)
        ).apply()

    var filterType: String
        get() = prefs.getString(KEY_FILTER_TYPE, FILTER_WARM) ?: FILTER_WARM
        set(value) = prefs.edit().putString(KEY_FILTER_TYPE, value).apply()

    var filterIntensity: Float
        get() = prefs.getFloat(KEY_FILTER_INTENSITY, 0.60f)
        set(value) = prefs.edit().putFloat(KEY_FILTER_INTENSITY, value.coerceIn(0.0f, 1.0f)).apply()

    var isColorFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_ENABLED, value).apply()

    var transitionSpeedMs: Long
        get() = prefs.getLong(KEY_TRANSITION_SPEED, 1500L)
        set(value) = prefs.edit().putLong(KEY_TRANSITION_SPEED, value).apply()

    var isSolarEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOLAR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SOLAR_ENABLED, value).apply()

    var latitude: Double
        get() = prefs.getFloat(KEY_LATITUDE, 45.4642f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LATITUDE, value.toFloat()).apply()

    var longitude: Double
        get() = prefs.getFloat(KEY_LONGITUDE, 9.1900f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LONGITUDE, value.toFloat()).apply()

    var isBootEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_ENABLED, value).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    var hasAcceptedDisclaimer: Boolean
        get() = prefs.getBoolean(KEY_ACCEPTED_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCEPTED_DISCLAIMER, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var selectedLanguage: String
        get() = prefs.getString(KEY_SELECTED_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_SELECTED_LANGUAGE, value).apply()

    var isExternalAutomationEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXTERNAL_AUTOMATION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EXTERNAL_AUTOMATION_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "screenshade_preferences"

        const val KEY_OVERLAY_ENABLED = "key_overlay_enabled"
        const val KEY_BRIGHTNESS = "key_brightness"
        const val KEY_FILTER_TYPE = "color_filter_mode"
        const val KEY_FILTER_INTENSITY = "color_filter_intensity"
        const val KEY_FILTER_ENABLED = "color_filter_enabled"
        const val KEY_TRANSITION_SPEED = "key_transition_speed"
        const val KEY_SOLAR_ENABLED = "solar_dimming_enabled"
        const val KEY_LATITUDE = "key_latitude"
        const val KEY_LONGITUDE = "key_longitude"
        const val KEY_BOOT_ENABLED = "key_boot_enabled"
        const val KEY_SILENT_BOOT = "key_silent_boot"
        const val KEY_THEME_MODE = "key_theme_mode"
        const val KEY_ACCEPTED_DISCLAIMER = "has_accepted_disclaimer"
        const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        const val KEY_SELECTED_LANGUAGE = "key_selected_language"
        const val KEY_EXTERNAL_AUTOMATION_ENABLED = "key_external_automation_enabled"

        const val FILTER_NONE = "NONE"
        const val FILTER_WARM = "WARM"
        const val FILTER_SEPIA = "SEPIA"
        const val FILTER_MONO = "MONO"
        const val FILTER_CUSTOM = "CUSTOM"
    }
}
