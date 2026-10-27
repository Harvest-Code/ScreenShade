package com.screenshade.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.databinding.DialogAutomationBinding
import com.screenshade.app.util.HapticHelper

class AutomationDialog(private val context: Context) {

    private val dialog = Dialog(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
    private val binding: DialogAutomationBinding =
        DialogAutomationBinding.inflate(LayoutInflater.from(context))
    private val prefs = PreferencesManager(context)

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.switchExternalAutomation.isChecked = prefs.isExternalAutomationEnabled
        binding.switchExternalAutomation.setOnCheckedChangeListener { _, isChecked ->
            HapticHelper.performConfirm(binding.switchExternalAutomation)
            prefs.isExternalAutomationEnabled = isChecked
        }

        binding.btnAutoClose.setOnClickListener {
            dialog.dismiss()
        }

        updateTexts()
    }

    private fun updateTexts() {
        val lang = prefs.selectedLanguage
        val locale = if (lang == "zh") java.util.Locale.SIMPLIFIED_CHINESE else java.util.Locale(lang)
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        val locCtx = context.createConfigurationContext(config)

        binding.tvAutomationTitle.text = locCtx.getString(com.screenshade.app.R.string.automation_title)
        binding.tvAutomationDesc.text = locCtx.getString(com.screenshade.app.R.string.automation_desc)
        binding.tvSwitchAutomationTitle.text = locCtx.getString(com.screenshade.app.R.string.switch_external_automation)
        binding.tvSwitchAutomationDesc.text = locCtx.getString(com.screenshade.app.R.string.switch_external_automation_desc)
        binding.tvCmdToggle.text = locCtx.getString(com.screenshade.app.R.string.automation_toggle)
        binding.tvCmdStart.text = locCtx.getString(com.screenshade.app.R.string.automation_start)
        binding.tvCmdStop.text = locCtx.getString(com.screenshade.app.R.string.automation_stop)
        binding.tvCmdDim.text = locCtx.getString(com.screenshade.app.R.string.automation_dim)
        binding.tvCmdFilter.text = locCtx.getString(com.screenshade.app.R.string.automation_filter)
        binding.btnAutoClose.text = locCtx.getString(com.screenshade.app.R.string.btn_close)
    }

    fun show() {
        dialog.show()
        val dm = context.resources.displayMetrics
        val horizontalMargin = (24 * 2 * dm.density).toInt()
        val width = (dm.widthPixels - horizontalMargin).coerceAtMost((560 * dm.density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
