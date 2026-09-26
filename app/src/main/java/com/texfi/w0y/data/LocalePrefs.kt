package com.texfi.w0y.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Выбранный язык, доступный синхронно.
 *
 * Локаль нужна в `Activity.attachBaseContext`, то есть до того, как в
 * приложении вообще появится корутина: ждать DataStore там нельзя.
 * Поэтому выбор дублируется в крошечный SharedPreferences-файл — он
 * читается синхронно и не мешает быстрому старту.
 *
 * Своя реализация вместо `AppCompatDelegate.setApplicationLocales`: тот
 * применяет язык сам только к AppCompat-активити, а здесь Compose поверх
 * ComponentActivity, и на Android ниже 13 выбор просто не сработал бы.
 */
object LocalePrefs {
    private const val FILE = "w0y_locale"
    private const val KEY = "language"

    fun save(context: Context, language: Language) {
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.name)
            .apply()
    }

    fun current(context: Context): Language {
        val name = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        return Language.entries.firstOrNull { it.name == name } ?: Language.SYSTEM
    }

    /**
     * Контекст с выбранным языком.
     *
     * Для [Language.SYSTEM] контекст возвращается как есть: подменять
     * системную локаль своей копией значило бы игнорировать смену языка
     * телефона на ходу.
     */
    fun wrap(context: Context): Context {
        val tag = current(context).tag ?: return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Накладывает выбранный язык на уже существующий контекст.
     *
     * Нужно после системной смены конфигурации: там ресурсы приложения
     * перечитываются с системной локалью, и выбор пользователя пропал бы.
     */
    fun apply(context: Context) {
        val tag = current(context).tag ?: return
        val locale = Locale.forLanguageTag(tag)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(
            Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            },
            context.resources.displayMetrics,
        )
    }
}
