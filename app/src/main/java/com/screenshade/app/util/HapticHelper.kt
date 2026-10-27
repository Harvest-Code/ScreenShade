package com.screenshade.app.util

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback utility for tactility on touch sliders, switches, and buttons.
 */
object HapticHelper {

    fun performTick(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    fun performConfirm(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONFIRM,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    fun performKeyClick(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }
}
