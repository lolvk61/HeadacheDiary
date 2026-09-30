package com.headachediary.app.settings

import android.content.Context
import android.content.res.Configuration
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

/** Настройки хранятся в SharedPreferences: их нужно читать синхронно, ещё до создания интерфейса. */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"

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
