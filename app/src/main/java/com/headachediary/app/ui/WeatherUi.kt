package com.headachediary.app.ui

import android.content.Context
import androidx.annotation.StringRes
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.SHARP_PRESSURE_CHANGE_HPA
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.settings.PressureUnit
import kotlin.math.roundToInt

private const val MMHG_PER_HPA = 0.750062

private fun convert(unit: PressureUnit, hPa: Double) = if (unit == PressureUnit.MMHG) hPa * MMHG_PER_HPA else hPa

/** Давление в выбранных единицах, например «758 мм рт. ст.». */
fun formatPressure(context: Context, hPa: Double): String {
    val unit = AppSettings.pressureUnit(context)
    return "${convert(unit, hPa).roundToInt()} ${context.getString(unit.labelRes)}"
}

/** Изменение давления со знаком, например «−2,3 мм рт. ст.». */
fun formatPressureChange(context: Context, deltaHpa: Double): String {
    val unit = AppSettings.pressureUnit(context)
    return String.format(context.appLocale(), "%+.1f %s", convert(unit, deltaHpa), context.getString(unit.labelRes))
}

fun formatTemperature(context: Context, celsius: Double): String =
    String.format(context.appLocale(), "%+.0f °C", celsius)

@StringRes
fun weatherLabelRes(code: Int): Int = when (code) {
    0 -> R.string.wx_clear
    1, 2 -> R.string.wx_partly
    3 -> R.string.wx_cloudy
    45, 48 -> R.string.wx_fog
    in 51..57 -> R.string.wx_drizzle
    in 61..67, in 80..82 -> R.string.wx_rain
    in 71..77, 85, 86 -> R.string.wx_snow
    in 95..99 -> R.string.wx_thunder
    else -> R.string.wx_cloudy
}

/** Короткая строка для карточки записи: «758 мм рт. ст. ↓ · +12 °C»; null, если погоды нет. */
fun weatherSummary(context: Context, e: HeadacheEntry): String? {
    val parts = mutableListOf<String>()
    e.pressure?.let { p ->
        val arrow = when {
            (e.pressureChange3h ?: 0.0) <= -SHARP_PRESSURE_CHANGE_HPA -> " ↓"
            (e.pressureChange3h ?: 0.0) >= SHARP_PRESSURE_CHANGE_HPA -> " ↑"
            else -> ""
        }
        parts += formatPressure(context, p) + arrow
    }
    e.temperature?.let { parts += formatTemperature(context, it) }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
