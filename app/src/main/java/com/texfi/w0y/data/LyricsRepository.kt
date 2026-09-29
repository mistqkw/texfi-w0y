package com.texfi.w0y.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** Строка синхронизированной лирики. */
data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val plain: String?,
    val synced: List<LyricLine>,
) {
    val isEmpty: Boolean get() = plain.isNullOrBlank() && synced.isEmpty()
}

/**
 * Лирика из LRCLIB — открытой базы без ключей и регистрации.
 *
 * Сначала точный запрос по исполнителю и названию, потом поиск: у YouTube
 * названия часто с приписками вроде «(Official Video)», и точное совпадение
 * не находится.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val okHttp: OkHttpClient,
) {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val translations = ConcurrentHashMap<String, List<String>>()

    /**
     * Перевод строк лирики на [lang]: по одной строке на каждую строку оригинала
     * (синхронизированную, если она есть, иначе строки простого текста).
     * Переводит неофициальный публичный endpoint Google Translate. При сбое возвращаем null,
     * а не выдумываем текст.
     */
    suspend fun translate(songId: String, lyrics: Lyrics, lang: String): List<String>? =
        withContext(Dispatchers.IO) {
            val key = "$songId|$lang|${lyrics.synced.size}"
            translations[key]?.let { return@withContext it }
            val source =
                if (lyrics.synced.isNotEmpty()) {
                    lyrics.synced.map { it.text }
                } else {
                    lyrics.plain.orEmpty().lines()
                }
            if (source.isEmpty()) return@withContext null
            val out = ArrayList<String>(source.size)
            for (chunk in source.chunked(40)) {
                val translated = runCatching { translateChunk(chunk, lang) }.getOrNull() ?: return@withContext null
                out += translated
            }
            out.also { translations[key] = it }
        }

    private fun translateChunk(lines: List<String>, lang: String): List<String>? {
        val body =
            okhttp3.FormBody
                .Builder()
                .add("q", lines.joinToString("\n"))
                .build()
        val request =
            Request
                .Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t")
                .header("User-Agent", "Mozilla/5.0")
                .post(body)
                .build()
        val raw =
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            } ?: return null
        val segments = JSONArray(raw).getJSONArray(0)
        val joined = StringBuilder()
        for (i in 0 until segments.length()) {
            joined.append(segments.getJSONArray(i).optString(0))
        }
        val result = joined.toString().split("\n")
        return if (result.size == lines.size) result else null
    }

    suspend fun lyrics(song: SongItem): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        val cleanTitle = song.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
        val result =
            runCatching { exact(song.artist, cleanTitle) ?: search(song.artist, cleanTitle) }
                .onFailure { Timber.w(it, "Лирика для ${song.title} не получена") }
                .getOrNull()
        result?.also { cache[song.id] = it }
    }

    private fun exact(artist: String, title: String): Lyrics? {
        val url =
            "https://lrclib.net/api/get"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("track_name", title)
                .build()
        val body = get(url.toString()) ?: return null
        return parse(JSONObject(body))
    }

    private fun search(artist: String, title: String): Lyrics? {
        val url =
            "https://lrclib.net/api/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", "$artist $title".trim())
                .build()
        val body = get(url.toString()) ?: return null
        val array = JSONArray(body)
        if (array.length() == 0) return null
        return parse(array.getJSONObject(0))
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "TexFi-w0y").build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun parse(obj: JSONObject): Lyrics? {
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() }
        val syncedRaw = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val synced = syncedRaw?.let(::parseLrc).orEmpty()
        val lyrics = Lyrics(plain = plain, synced = synced)
        return lyrics.takeUnless { it.isEmpty }
    }

    private fun parseLrc(raw: String): List<LyricLine> {
        val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        return raw
            .lineSequence()
            .mapNotNull { line ->
                val match = stamp.find(line) ?: return@mapNotNull null
                val (minutes, seconds, fraction) = match.destructured
                val millis =
                    minutes.toLong() * 60_000 +
                        seconds.toLong() * 1_000 +
                        (fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0)
                val text = line.substring(match.range.last + 1).trim()
                text.takeIf { it.isNotBlank() }?.let { LyricLine(millis, it) }
            }.sortedBy { it.timeMs }
            .toList()
    }
}
