package com.texfi.w0y.data

import android.content.Context
import android.os.LocaleList
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Приводит системную локаль к виду, который принимает YouTube.
 *
 * Библиотека отдаёт в запрос `hl = Locale.toLanguageTag()` и `gl = country`,
 * а Android подставляет туда то, от чего YouTube отвечает 400 (проверено
 * запросами, см. LocaleProbeTest):
 *  • тег с юникодными расширениями — `ru-RU-u-ca-gregory-nu-latn`,
 *    он появляется, когда пользователь трогал региональные настройки;
 *  • язык без страны — `ru`, тогда `gl` уходит пустым.
 *
 * Поэтому до первого запроса подменяем локаль процесса на чистую пару
 * «язык + страна». На форматирование дат и чисел это не влияет заметно,
 * зато поиск перестаёт зависеть от настроек конкретного телефона.
 */
object YouTubeLocaleFix {
    fun apply(context: Context) {
        val sanitized = sanitize(Locale.getDefault(), systemCountries(context))
        if (sanitized.toLanguageTag() != Locale.getDefault().toLanguageTag()) {
            Locale.setDefault(sanitized)
        }
    }

    /** Чистая функция: её и проверяет тест, без Android под рукой. */
    fun sanitize(current: Locale, countryCandidates: List<String>): Locale {
        val language = current.language.takeIf { it.isNotBlank() } ?: "en"
        val country =
            (listOf(current.country) + countryCandidates)
                .firstOrNull { it.length == 2 && it.all(Char::isLetter) }
                ?.uppercase(Locale.ROOT)
                ?: "US"
        return Locale.Builder().setLanguage(language).setRegion(country).build()
    }

    private fun systemCountries(context: Context): List<String> {
        val fromList =
            LocaleList.getDefault().let { list ->
                (0 until list.size()).map { list[it].country }
            }
        val fromNetwork =
            runCatching {
                val telephony = context.getSystemService(TelephonyManager::class.java)
                // Страна сети точнее системной локали: именно её YouTube
                // ожидает в gl для региональной выдачи.
                listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso)
            }.getOrDefault(emptyList())
        return fromList + fromNetwork
    }
}
