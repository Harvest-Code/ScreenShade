package com.screenshade.app.util

import android.graphics.ColorMatrix
import com.screenshade.app.data.PreferencesManager

/**
 * Mathematical color matrix utilities implementing exact Rec.709 grayscale,
 * authentic Sepia, and proportional blue-light attenuation.
 */
object ColorFilterUtils {

    private val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private val REC709_MONO_MATRIX = floatArrayOf(
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0f,      0f,      0f,      1f, 0f
    )

    private val AUTHENTIC_SEPIA_MATRIX = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f,     0f,     0f,     1f, 0f
    )

    /**
     * Builds a ColorMatrix for the requested filter type and intensity (0.0 to 1.0).
     */
    fun createFilterMatrix(filterType: String, intensity: Float): ColorMatrix {
        val clampedIntensity = intensity.coerceIn(0.0f, 1.0f)
        val matrix = ColorMatrix()

        when (filterType) {
            PreferencesManager.FILTER_WARM -> {
                // R = 1.0f, G = 0.85f + (0.15f * (1 - intensity)), B = 0.4f + (0.6f * (1 - intensity))
                val r = 1.0f
                val g = 0.85f + (0.15f * (1.0f - clampedIntensity))
                val b = 0.40f + (0.60f * (1.0f - clampedIntensity))
                matrix.setScale(r, g, b, 1.0f)
            }
            PreferencesManager.FILTER_SEPIA -> {
                // Blend IDENTITY with AUTHENTIC_SEPIA based on intensity
                val blended = FloatArray(20)
                for (i in 0 until 20) {
                    blended[i] = IDENTITY_MATRIX[i] + (AUTHENTIC_SEPIA_MATRIX[i] - IDENTITY_MATRIX[i]) * clampedIntensity
                }
                matrix.set(blended)
            }
            PreferencesManager.FILTER_MONO -> {
                // Blend IDENTITY with Rec.709 Luminance based on intensity
                val blended = FloatArray(20)
                for (i in 0 until 20) {
                    blended[i] = IDENTITY_MATRIX[i] + (REC709_MONO_MATRIX[i] - IDENTITY_MATRIX[i]) * clampedIntensity
                }
                matrix.set(blended)
            }
            PreferencesManager.FILTER_CUSTOM -> {
                // Deep Amber / Night Vision tint
                val r = 1.0f
                val g = 0.75f + (0.25f * (1.0f - clampedIntensity))
                val b = 0.20f + (0.80f * (1.0f - clampedIntensity))
                matrix.setScale(r, g, b, 1.0f)
            }
            else -> {
                matrix.reset()
            }
        }
        return matrix
    }
}
