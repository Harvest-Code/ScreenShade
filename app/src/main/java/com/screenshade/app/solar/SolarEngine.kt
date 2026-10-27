package com.screenshade.app.solar

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * 100% Offline Astronomical Solar Engine based on NOAA Solar Calculations.
 * Computes real-time solar elevation, sunrise, sunset, and civil twilight
 * without requiring any network connectivity or external dependencies.
 */
object SolarEngine {

    data class SolarData(
        val elevationDeg: Double,
        val azimuthDeg: Double,
        val sunriseMinutes: Double,
        val sunsetMinutes: Double,
        val civilTwilightDawnMinutes: Double,
        val civilTwilightDuskMinutes: Double,
        val isDaytime: Boolean
    )

    /**
     * Calculates solar position and events for a given latitude, longitude, and timestamp.
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance()
    ): SolarData {
        val timeZoneOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3600000.0
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val timeOfDayHours = hour + (minute / 60.0) + (second / 3600.0)

        // Fractional year in radians
        val gamma = (2 * Math.PI / 365.0) * (dayOfYear - 1 + (timeOfDayHours - 12.0) / 24.0)

        // Equation of time in minutes
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma)
                - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))

        // Solar declination angle in radians
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        // True solar time in minutes
        val timeOffset = eqTime + 4.0 * longitude - 60.0 * timeZoneOffsetHours
        val trueSolarTime = (timeOfDayHours * 60.0 + timeOffset + 1440.0) % 1440.0

        // Solar hour angle in degrees
        val hourAngle = if (trueSolarTime / 4.0 < 0) {
            trueSolarTime / 4.0 + 180.0
        } else {
            trueSolarTime / 4.0 - 180.0
        }
        val hourAngleRad = Math.toRadians(hourAngle)
        val latRad = Math.toRadians(latitude)

        // Solar zenith angle
        val cosZenith = sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(hourAngleRad)
        val zenithRad = acos(cosZenith.coerceIn(-1.0, 1.0))
        val elevationDeg = 90.0 - Math.toDegrees(zenithRad)

        // Solar azimuth angle
        val cosAzimuth = (sin(decl) - sin(latRad) * cos(zenithRad)) / (cos(latRad) * sin(zenithRad))
        var azimuthDeg = Math.toDegrees(acos(cosAzimuth.coerceIn(-1.0, 1.0)))
        if (hourAngle > 0) {
            azimuthDeg = 360.0 - azimuthDeg
        }

        // Calculate Sunrise, Sunset and Civil Twilight
        val sunriseMinutes = calculateSunEventTime(latitude, longitude, decl, eqTime, timeZoneOffsetHours, 90.833, isSunrise = true)
        val sunsetMinutes = calculateSunEventTime(latitude, longitude, decl, eqTime, timeZoneOffsetHours, 90.833, isSunrise = false)
        val dawnMinutes = calculateSunEventTime(latitude, longitude, decl, eqTime, timeZoneOffsetHours, 96.0, isSunrise = true)
        val duskMinutes = calculateSunEventTime(latitude, longitude, decl, eqTime, timeZoneOffsetHours, 96.0, isSunrise = false)

        val isDaytime = elevationDeg > 0.0

        return SolarData(
            elevationDeg = elevationDeg,
            azimuthDeg = azimuthDeg,
            sunriseMinutes = sunriseMinutes,
            sunsetMinutes = sunsetMinutes,
            civilTwilightDawnMinutes = dawnMinutes,
            civilTwilightDuskMinutes = duskMinutes,
            isDaytime = isDaytime
        )
    }

    private fun calculateSunEventTime(
        latitude: Double,
        longitude: Double,
        declRad: Double,
        eqTime: Double,
        timeZoneOffsetHours: Double,
        zenithDeg: Double,
        isSunrise: Boolean
    ): Double {
        val latRad = Math.toRadians(latitude)
        val zenithRad = Math.toRadians(zenithDeg)
        val cosH = (cos(zenithRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))

        if (cosH > 1.0) return -1.0 // Sun never rises (polar night)
        if (cosH < -1.0) return -2.0 // Sun never sets (midnight sun)

        val hRad = if (isSunrise) acos(cosH) else -acos(cosH)
        val hDeg = Math.toDegrees(hRad)

        // Approximate UTC time in minutes
        return 720.0 - 4.0 * (longitude + hDeg) - eqTime + (timeZoneOffsetHours * 60.0)
    }

    /**
     * Formats minutes from midnight into HH:mm format.
     */
    fun formatMinutesToTime(minutes: Double): String {
        if (minutes < 0) return "--:--"
        val totalMinutes = (minutes.toInt() % 1440 + 1440) % 1440
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format("%02d:%02d", h, m)
    }

    /**
     * Determines optimal brightness dimming and warm filter intensity
     * in dynamic solar mode based on current sun elevation.
     * @return Pair<Brightness (0.0 - 0.75), WarmIntensity (0.0 - 1.0)>
     */
    fun computeDynamicSolarValues(elevationDeg: Double): Pair<Float, Float> {
        return when {
            // High noon / bright sun: minimal software dimming, neutral tint
            elevationDeg >= 20.0 -> Pair(0.0f, 0.0f)
            // Afternoon / low sun: subtle dimming and warm tone
            elevationDeg in 0.0..20.0 -> {
                val factor = (1.0 - (elevationDeg / 20.0)).toFloat()
                Pair(factor * 0.25f, factor * 0.40f)
            }
            // Civil twilight (-6° to 0°): increasing darkness and anti-blue filter
            elevationDeg in -6.0..0.0 -> {
                val factor = ((0.0 - elevationDeg) / 6.0).toFloat()
                Pair(0.25f + factor * 0.25f, 0.40f + factor * 0.35f)
            }
            // Deep night (below -6°): maximum night comfort
            else -> Pair(0.60f, 0.85f)
        }
    }
}
