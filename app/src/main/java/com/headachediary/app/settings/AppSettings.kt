package com.headachediary.app.settings

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.headachediary.app.R
import java.util.Locale

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
}

enum class AppLanguage(val key: String) {
    SYSTEM("system"),
    RU("ru"),
    EN("en"),
}

enum class PressureUnit(val key: String, @StringRes val labelRes: Int) {
    MMHG("mmhg", R.string.unit_mmhg),
    HPA("hpa", R.string.unit_hpa),
}

/** Настройки хранятся в SharedPreferences: их нужно читать синхронно, ещё до создания интерфейса. */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_WEATHER = "weather"
    private const val KEY_PRESSURE_UNIT = "pressure_unit"
    private const val KEY_REMINDER = "reminder"
    private const val KEY_REMINDER_MINUTES = "reminder_minutes"
    private const val KEY_FORECAST_ALERT = "forecast_alert"

    fun themeMode(context: Context): ThemeMode {
        val key = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.key == key } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_THEME, mode.key).apply()
    }

    fun language(context: Context): AppLanguage {
        val key = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.key == key } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language.key).apply()
    }

    /** Записывать ли погоду при каждом приступе. По умолчанию выключено: нужен доступ к местоположению и сеть. */
    fun weatherEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_WEATHER, false)

    fun setWeatherEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WEATHER, enabled).apply()
    }

    /** Вечернее напоминание «была ли сегодня боль?». */
    fun reminderEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_REMINDER, false)

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_REMINDER, enabled).apply()
    }

    /** Время напоминания в минутах от полуночи; по умолчанию 21:00. */
    fun reminderMinutes(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_REMINDER_MINUTES, 21 * 60)

    fun setReminderMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_REMINDER_MINUTES, minutes).apply()
    }

    /** Утреннее предупреждение о ожидаемом перепаде давления. */
    fun forecastAlertEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_FORECAST_ALERT, false)

    fun setForecastAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_FORECAST_ALERT, enabled).apply()
    }

    /** Единицы давления; если не выбраны, для русского языка — мм рт. ст., для остальных — гПа. */
    fun pressureUnit(context: Context): PressureUnit {
        val key = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PRESSURE_UNIT, null)
        PressureUnit.entries.firstOrNull { it.key == key }?.let { return it }
        return if (context.resources.configuration.locales[0].language == "ru") PressureUnit.MMHG else PressureUnit.HPA
    }

    fun setPressureUnit(context: Context, unit: PressureUnit) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_PRESSURE_UNIT, unit.key).apply()
    }
}

/**
 * Возвращает контекст с выбранным в приложении языком (или сам контекст, если выбран язык системы).
 * Используется в Activity, виджете и везде, где нужны локализованные строки.
 */
fun Context.localized(): Context {
    val language = AppSettings.language(this)
    if (language == AppLanguage.SYSTEM) return this
    val locale = Locale.forLanguageTag(language.key)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
