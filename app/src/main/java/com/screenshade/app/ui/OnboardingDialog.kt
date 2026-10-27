package com.screenshade.app.ui

import com.screenshade.app.BuildConfig
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.screenshade.app.R
import com.screenshade.app.data.PreferencesManager
import com.screenshade.app.databinding.DialogOnboardingBinding
import com.screenshade.app.util.HapticHelper
import java.util.Locale

class OnboardingDialog(
    private val context: Context,
    private val onFinished: () -> Unit
) {

    private val dialog = Dialog(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
    private val binding: DialogOnboardingBinding =
        DialogOnboardingBinding.inflate(LayoutInflater.from(context))
    private val prefs = PreferencesManager(context)

    private var currentStep = 0
    private val totalSteps = 4
    private var activeContext: Context = context

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCancelable(false)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        setupRadioButtons()
        setupListeners()
        updateDialogTexts(prefs.selectedLanguage)
        updateStepUI()
    }

    fun show() {
        dialog.show()
        val dm = context.resources.displayMetrics
        val horizontalMargin = (24 * 2 * dm.density).toInt()
        val width = (dm.widthPixels - horizontalMargin).coerceAtMost((560 * dm.density).toInt())
        val verticalMargin = (24 * 2 * dm.density).toInt()
        val height = (dm.heightPixels - verticalMargin).coerceAtMost((560 * dm.density).toInt())
        dialog.window?.setLayout(width, height)
        checkOverlayPermissionStatus()
    }

    private fun setupRadioButtons() {
        val radioButtons = listOf(
            Pair("en", binding.rbLangEn),
            Pair("it", binding.rbLangIt),
            Pair("es", binding.rbLangEs),
            Pair("de", binding.rbLangDe),
            Pair("ru", binding.rbLangRu),
            Pair("ja", binding.rbLangJa),
            Pair("zh", binding.rbLangZh)
        )

        // Pre-select saved language
        val currentLang = prefs.selectedLanguage
        radioButtons.forEach { (code, rb) ->
            rb.isChecked = (code == currentLang)
        }

        // Mutual exclusion logic without flickering
        radioButtons.forEach { (code, rb) ->
            rb.setOnClickListener {
                HapticHelper.performConfirm(rb)
                radioButtons.forEach { (_, otherRb) ->
                    if (otherRb != rb) {
                        otherRb.isChecked = false
                    }
                }
                rb.isChecked = true

                prefs.selectedLanguage = code
                updateDialogTexts(code)
            }
        }
    }

    /**
     * Instantly updates all visible dialog strings using a localized context
     * without calling Activity.recreate() (zero flickering).
     */
    private fun updateDialogTexts(langCode: String) {
        val locale = if (langCode == "zh") Locale.SIMPLIFIED_CHINESE else Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        activeContext = context.createConfigurationContext(config)

        // Update all UI labels directly from localizedContext
        binding.tvSelectLangPrompt.text = activeContext.getString(R.string.onboarding_select_lang_prompt)
        binding.tvPrivacyTitle.text = activeContext.getString(R.string.privacy_title)
        binding.tvPrivacyBody.text = activeContext.getString(R.string.privacy_body)
        binding.tvDisclaimerTitle.text = activeContext.getString(R.string.disclaimer_safety_title)
        binding.tvDisclaimerBody.text = activeContext.getString(R.string.disclaimer_safety_body)
        binding.cbAcceptDisclaimer.text = activeContext.getString(R.string.disclaimer_accept_checkbox)
        binding.tvPermissionTitle.text = activeContext.getString(R.string.permission_overlay_title)
        binding.tvPermissionDesc.text = activeContext.getString(R.string.permission_overlay_desc)
        binding.btnWizardGrantOverlay.text = activeContext.getString(R.string.permission_grant_btn)
        binding.tvReadyTitle.text = activeContext.getString(R.string.onboarding_ready_title)
        binding.tvReadyDesc.text = activeContext.getString(R.string.onboarding_ready_desc)
        binding.cardWizardKofi.visibility = if (BuildConfig.ENABLE_DONATIONS) View.VISIBLE else View.GONE
        binding.tvKofiTitle.text = activeContext.getString(R.string.kofi_card_title)
        binding.tvKofiDesc.text = activeContext.getString(R.string.kofi_card_desc)
        binding.btnWizardKofi.text = activeContext.getString(R.string.kofi_btn_label)
        binding.btnWizardBack.text = activeContext.getString(R.string.btn_back)

        updateStepUI()
        checkOverlayPermissionStatus()
    }

    private fun setupListeners() {
        // Step 3: Grant Overlay Permission
        binding.btnWizardGrantOverlay.setOnClickListener {
            HapticHelper.performConfirm(it)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }

        // Step 4: Ko-fi link
        binding.btnWizardKofi.setOnClickListener {
            if (BuildConfig.ENABLE_DONATIONS) {
                HapticHelper.performConfirm(it)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SUPPORT_URL))
                context.startActivity(intent)
            }
        }

        // Navigation Back
        binding.btnWizardBack.setOnClickListener {
            HapticHelper.performKeyClick(it)
            if (currentStep > 0) {
                currentStep--
                binding.wizardFlipper.displayedChild = currentStep
                updateStepUI()
            }
        }

        // Navigation Next / Finish
        binding.btnWizardNext.setOnClickListener {
            HapticHelper.performConfirm(it)
            if (currentStep == 1 && !binding.cbAcceptDisclaimer.isChecked) {
                Toast.makeText(
                    activeContext,
                    activeContext.getString(R.string.disclaimer_accept_checkbox),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (currentStep < totalSteps - 1) {
                currentStep++
                binding.wizardFlipper.displayedChild = currentStep
                updateStepUI()
                if (currentStep == 2) {
                    checkOverlayPermissionStatus()
                }
            } else {
                prefs.isOnboardingCompleted = true
                dialog.dismiss()
                onFinished()
            }
        }
    }

    private fun checkOverlayPermissionStatus() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        if (hasPermission) {
            binding.tvOverlayPermissionStatus.text = activeContext.getString(R.string.permission_status_granted)
            binding.tvOverlayPermissionStatus.setTextColor(
                ContextCompat.getColor(context, android.R.color.holo_green_dark)
            )
        } else {
            binding.tvOverlayPermissionStatus.text = activeContext.getString(R.string.permission_status_missing)
            binding.tvOverlayPermissionStatus.setTextColor(
                ContextCompat.getColor(context, android.R.color.holo_red_dark)
            )
        }
    }

    private fun updateStepUI() {
        binding.tvWizardStepIndicator.text = activeContext.getString(
            R.string.onboarding_step_format,
            currentStep + 1,
            totalSteps
        )
        binding.btnWizardBack.visibility = if (currentStep > 0) View.VISIBLE else View.INVISIBLE

        when (currentStep) {
            0 -> {
                binding.tvWizardStepTitle.text = activeContext.getString(R.string.onboarding_step1_title)
                binding.btnWizardNext.text = activeContext.getString(R.string.btn_next)
            }
            1 -> {
                binding.tvWizardStepTitle.text = activeContext.getString(R.string.onboarding_step2_title)
                binding.btnWizardNext.text = activeContext.getString(R.string.btn_next)
            }
            2 -> {
                binding.tvWizardStepTitle.text = activeContext.getString(R.string.onboarding_step3_title)
                binding.btnWizardNext.text = activeContext.getString(R.string.btn_next)
                checkOverlayPermissionStatus()
            }
            3 -> {
                binding.tvWizardStepTitle.text = activeContext.getString(R.string.onboarding_step4_title)
                binding.btnWizardNext.text = activeContext.getString(R.string.btn_finish)
            }
        }
    }
}
