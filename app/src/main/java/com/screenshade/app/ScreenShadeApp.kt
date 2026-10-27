package com.screenshade.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.screenshade.app.data.PreferencesManager

class ScreenShadeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        val prefs = PreferencesManager(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
    }
}
