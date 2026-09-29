package com.texfi.w0y.playback

import com.texfi.w0y.data.FallbackSource
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Готовая ссылка на звук и заголовки, с которыми её нужно запрашивать. */
data class ResolvedAudio(val url: String, val headers: Map<String, String> = emptyMap())

/**
 * Запасной звук для треков, которые YouTube Music не отдал.
 *
 * Обложка, название и очередь остаются от исходного трека — меняется только
 * ссылка на аудио. Цепочка короткая и с жёсткими таймаутами: запасной путь,
 * который висит дольше основного, хуже тишины.
 */
@Singleton
class FallbackAudio @Inject constructor(
    private val repository: YouTubeRepository,
    okHttp: OkHttpClient,
) {
    private val http =
        okHttp
            .newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    private val cache = ConcurrentHashMap<String, Pair<ResolvedAudio, Long>>()
    private val meta = ConcurrentHashMap<String, Triple<String?, String?, String?>>()

    fun remember(videoId: String, title: String?, artist: String?, duration: String? = null) {
        if (title != null) meta[videoId] = Triple(title, artist, duration)
    }

    /** Запасной звук по уже известным названию и исполнителю трека. */
    suspend fun resolveKnown(videoId: String, source: FallbackSource): ResolvedAudio? {
        val (title, artist, _) = meta[videoId] ?: Triple(null, null, null)
        return resolve(videoId, title, artist, source)
    }

    suspend fun resolve(
        videoId: String,
        title: String?,
        artist: String?,
        source: FallbackSource,
    ): ResolvedAudio? {
        cache[videoId]?.let { (audio, at) ->
            if (System.currentTimeMillis() - at < CACHE_MS) return audio
        }
        val found =
            when (source) {
                FallbackSource.YOUTUBE -> otherUpload(videoId, title, artist)
                FallbackSource.PIPED -> piped(videoId)
                FallbackSource.AUTO -> otherUpload(videoId, title, artist) ?: piped(videoId)
            }
        if (found != null) cache[videoId] = found to System.currentTimeMillis()
        return found
    }

    /**
     * Тот же трек, залитый другим видео. Ищем «исполнитель название», а берём
     * только тех, у кого совпало и название, и исполнитель: иначе подмена
     * приносит другую песню с похожим заголовком.
     */
    private suspend fun otherUpload(videoId: String, title: String?, artist: String?): ResolvedAudio? {
        if (title.isNullOrBlank()) return null
        val mainArtist = artist.orEmpty().substringBefore(',').substringBefore('&').trim()
        val query = "$mainArtist - $title".trim(' ', '-')
        val songs = runCatching { repository.searchSongs(query) }.getOrDefault(emptyList())
        val videos = runCatching { repository.searchVideos(query) }.getOrDefault(emptyList())
        val wantedTitle = normalize(cleanTitle(title))
        val wantedArtist = normalize(mainArtist)
        val original = normalize(title)
        val ranked =
            (songs + videos)
                .filter { it.id != videoId }
                .distinctBy(SongItem::id)
                .mapNotNull { candidate ->
                    val cTitle = normalize(candidate.title)
                    if (wantedTitle.isEmpty() || !cTitle.contains(wantedTitle)) return@mapNotNull null
                    val artistOk =
                        wantedArtist.isEmpty() ||
                            normalize(candidate.artist).contains(wantedArtist) ||
                            cTitle.contains(wantedArtist)
                    if (!artistOk) return@mapNotNull null
                    // Ремиксы, каверы и караоке — другие записи, если оригинал таким не был.
                    val variant = VARIANT_WORDS.any { cTitle.contains(it) && !original.contains(it) }
                    if (variant) return@mapNotNull null
                    var score = 0
                    if (candidate in songs) score += 2
                    if (cTitle == wantedTitle) score += 3
                    if (normalize(candidate.artist).contains(wantedArtist)) score += 2
                    if (sameDuration(candidate.durationText, durationOf(videoId))) score += 2
                    candidate to score
                }.sortedByDescending { it.second }
                .take(3)
        for ((candidate, _) in ranked) {
            val stream = runCatching { repository.stream(candidate.id) }.getOrNull() ?: continue
            return ResolvedAudio(stream.audioUrl, stream.headers)
        }
        return null
    }

    private fun durationOf(videoId: String): String? = meta[videoId]?.third

    private fun sameDuration(a: String?, b: String?): Boolean {
        val x = seconds(a) ?: return false
        val y = seconds(b) ?: return false
        return kotlin.math.abs(x - y) <= 5
    }

    private fun seconds(text: String?): Int? {
        val parts = text?.split(':')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
        return parts.fold(0) { acc, n -> acc * 60 + n }
    }

    private fun cleanTitle(text: String) = text.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()

    /** Публичные зеркала Piped по тому же идентификатору: лучше, чем ничего, но они капризны. */
    private suspend fun piped(videoId: String): ResolvedAudio? =
        withContext(Dispatchers.IO) {
            for (host in PIPED_HOSTS) {
                val url =
                    runCatching {
                        val request = Request.Builder().url("$host/streams/$videoId").build()
                        http.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use null
                            val streams = JSONObject(response.body.string()).optJSONArray("audioStreams") ?: return@use null
                            var best: String? = null
                            var bestRate = -1
                            for (i in 0 until streams.length()) {
                                val item = streams.getJSONObject(i)
                                val link = item.optString("url")
                                if (link.isBlank()) continue
                                val rate = item.optInt("bitrate", 0)
                                if (rate > bestRate) {
                                    bestRate = rate
                                    best = link
                                }
                            }
                            best
                        }
                    }.onFailure { Timber.w(it, "Piped $host не ответил") }.getOrNull()
                if (url != null) return@withContext ResolvedAudio(url)
            }
            null
        }

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        val VARIANT_WORDS = listOf("remix", "cover", "karaoke", "instrumental", "slowed", "spedup", "nightcore", "reverb", "8d", "liveat", "acoustic")
        const val CACHE_MS = 60 * 60 * 1000L
        val PIPED_HOSTS =
            listOf(
                "https://api.piped.private.coffee",
                "https://pipedapi.kavin.rocks",
                "https://pipedapi.adminforge.de",
            )
    }
}
