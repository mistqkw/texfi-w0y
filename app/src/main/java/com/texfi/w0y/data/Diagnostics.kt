package com.texfi.w0y.data

import android.content.Context
import android.os.LocaleList
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Диагностика прямо с устройства: три запроса к YouTube Music мимо
 * библиотеки, с разными hl/gl.
 *
 * Нужна потому, что телефон у пользователя, логи читать неоткуда, а
 * сообщение «HTTP 400» само по себе не говорит, что именно не понравилось
 * YouTube. Живёт только в debug-сборке.
 */
@Singleton
class Diagnostics @Inject constructor(
    private val okHttp: OkHttpClient,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun run(query: String): String = withContext(Dispatchers.IO) {
        val locale = Locale.getDefault()
        buildString {
            val effective = YouTubeLocaleResolver.forDevice(context)
            appendLine("локаль процесса: ${locale.toLanguageTag()} | системный список: ${LocaleList.getDefault().toLanguageTags()}")
            appendLine("уходит в YouTube: hl=${effective.hl} gl=${effective.gl}")
            appendLine()
            appendLine(probe("как сейчас", locale.toLanguageTag(), locale.country, query))
            appendLine(probe("ru-RU", "ru-RU", "RU", query))
            appendLine(probe("en-US", "en-US", "US", query))
            appendLine(probe("без hl/gl", null, null, query))
        }
    }

    private fun probe(label: String, hl: String?, gl: String?, query: String): String {
        val client =
            buildString {
                append("""{"clientName":"WEB_REMIX","clientVersion":"$CLIENT_VERSION"""")
                if (hl != null) append(""","hl":"$hl"""")
                if (gl != null) append(""","gl":"$gl"""")
                append("}")
            }
        val body =
            """{"context":{"client":$client},"query":${quote(query)},"params":"$FILTER"}"""
        val request =
            Request
                .Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("User-Agent", USER_AGENT)
                .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val hint =
                    if (response.isSuccessful) {
                        "ответ ${text.length} байт"
                    } else {
                        text.replace(Regex("\\s+"), " ").take(220)
                    }
                "$label → HTTP ${response.code}: $hint"
            }
        }.getOrElse { "$label → упал: ${it::class.simpleName}: ${it.message}" }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val CLIENT_VERSION = "1.20260707.12.00"
        const val FILTER = SearchParser.SONGS_FILTER
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
