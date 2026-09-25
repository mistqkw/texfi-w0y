package com.texfi.w0y.data

import android.content.Context
import android.os.LocaleList
import android.telephony.TelephonyManager
import com.metrolist.innertubex.models.YouTubeLocale
import java.util.Locale

/**
 * Собирает локаль запроса к YouTube: язык отдельно, страна отдельно.
 *
 * Проверено живыми запросами (см. LocaleProbeTest): YouTube принимает в `hl`
 * код языка — `en`, `ru`, `pl`, а также известные региональные варианты вроде
 * `en-GB`, но на произвольную пару «язык + страна» отвечает 400. У телефона
 * с английским языком и польским регионом системный тег как раз `en-PL`,
 * и поэтому падал вообще любой запрос.
 *
 * `gl` при этом может быть любой страной — региональная выдача сохраняется.
 */
object YouTubeLocaleResolver {
    fun forDevice(context: Context): YouTubeLocale = resolve(Locale.getDefault(), systemCountries(context))

    /** Чистая функция: её проверяет тест, без Android под рукой. */
    fun resolve(system: Locale, countryCandidates: List<String>): YouTubeLocale {
        val language = system.language.lowercase(Locale.ROOT).takeIf { it.isNotBlank() } ?: "en"
        val country =
            (listOf(system.country) + countryCandidates)
                .firstOrNull { it.length == 2 && it.all(Char::isLetter) }
                ?.uppercase(Locale.ROOT)
                ?: "US"
        return YouTubeLocale(gl = country, hl = language)
    }

    private fun systemCountries(context: Context): List<String> {
        val fromList =
            LocaleList.getDefault().let { list ->
                (0 until list.size()).map { list[it].country }
            }
        val fromNetwork =
            runCatching {
                val telephony = context.getSystemService(TelephonyManager::class.java)
                // Страна сети точнее системных настроек: именно её YouTube
                // ждёт в gl для региональной выдачи.
                listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso)
            }.getOrDefault(emptyList())
        return fromList + fromNetwork
    }
}
