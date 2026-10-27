package com.screenshade.app.ui

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import com.screenshade.app.R
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.databinding.DialogDisclaimerBinding
import java.util.Locale

class DisclaimerDialog(private val context: Context) {

    private val dialog = Dialog(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
    private val binding: DialogDisclaimerBinding =
        DialogDisclaimerBinding.inflate(LayoutInflater.from(context))
    private val prefs = PreferencesManager(context)

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.btnCloseDisclaimer.setOnClickListener {
            dialog.dismiss()
        }

        updateTexts()
    }

    private fun updateTexts() {
        val lang = prefs.selectedLanguage
        val locale = if (lang == "zh") Locale.SIMPLIFIED_CHINESE else Locale(lang)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        val locCtx = context.createConfigurationContext(config)

        binding.tvDisclaimerTitle.text = locCtx.getString(R.string.legal_dialog_title)
        binding.tvDisclaimerContent.text = locCtx.getString(R.string.legal_dialog_content)
        binding.btnCloseDisclaimer.text = locCtx.getString(R.string.close_button)
    }

    fun show() {
        dialog.show()
        val dm = context.resources.displayMetrics
        val horizontalMargin = (24 * 2 * dm.density).toInt()
        val width = (dm.widthPixels - horizontalMargin).coerceAtMost((560 * dm.density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
