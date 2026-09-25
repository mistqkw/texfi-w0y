package com.texfi.w0y.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.call.body
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Доступ к YouTube Music: поиск и получение потока.
 *
 * Ссылки на поток кэшируются с оглядкой на их собственный срок жизни
 * (`expiresAt`): повторное извлечение — это лишние сетевые запросы и
 * расшифровка cipher, то есть ровно та задержка между нажатием и звуком,
 * ради устранения которой всё и затевалось.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val innerTube: InnerTube,
    private val extractor: InnerTubeExtractor,
    private val settings: SettingsRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val streams = ConcurrentHashMap<String, ExtractedStream>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = query,
                    params = SONGS_FILTER,
                ).body<JsonObject>()
        YtJson.songs(response)
    }

    /** Треки плейлиста или альбома по его browseId. */
    suspend fun playlistSongs(browseId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId)
                .body<JsonObject>()
        YtJson.songs(response)
    }

    /**
     * Поток для трека. Блокировка на videoId нужна, чтобы одновременные
     * запросы (нажатие пользователя и предзагрузка очереди) не запускали
     * две расшифровки одного и того же трека.
     */
    suspend fun stream(videoId: String): ExtractedStream {
        cached(videoId)?.let { return it }
        val lock = locks.getOrPut(videoId) { Mutex() }
        return lock.withLock {
            cached(videoId) ?: withContext(Dispatchers.IO) {
                val extracted =
                    extractor.extract(
                        videoId = videoId,
                        hints = ContentHints(wantVideo = false),
                        audioQuality = currentQuality(),
                    // Библиотека возвращает null, когда YouTube отказал в
                    // воспроизведении (регион, возрастное ограничение,
                    // удалённое видео). Молчаливое null здесь превратилось бы
                    // в бесконечную «загрузку» в интерфейсе.
                    ) ?: error("YouTube не отдал поток для $videoId")
                streams[videoId] = extracted
                extracted
            }
        }
    }

    /** Греет кэш ссылки заранее — следующий трек должен стартовать мгновенно. */
    fun prefetch(videoId: String) {
        if (cached(videoId) != null) return
        prefetchScope.launch {
            runCatching { stream(videoId) }
                .onFailure { Timber.w(it, "Предзагрузка $videoId не удалась") }
        }
    }

    /** Громкость трека, измеренная YouTube, — для выравнивания уровня. */
    fun cachedLoudnessDb(videoId: String): Double? = streams[videoId]?.loudnessDb

    /**
     * Качество берётся отдельно для Wi-Fi и мобильной сети: на мобильной
     * важнее не сжечь трафик и быстрее начать, на Wi-Fi — качество.
     */
    private suspend fun currentQuality(): AudioQuality {
        val prefs = settings.settings.first()
        val onWifi =
            runCatching {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            }.getOrDefault(true)
        val quality = if (onWifi) prefs.qualityWifi else prefs.qualityMobile
        return when (quality) {
            Quality.LOW -> AudioQuality.LOW
            // Библиотека знает только LOW/HIGH/AUTO: «среднее» честнее
            // отдать её автоматике, чем выдумывать несуществующую ступень.
            Quality.MEDIUM -> AudioQuality.AUTO
            Quality.HIGH -> AudioQuality.HIGH
        }
    }

    private fun cached(videoId: String): ExtractedStream? {
        val stream = streams[videoId] ?: return null
        val expiresAt = stream.expiresAt ?: return stream
        // Небольшой запас: ссылка, истекающая через секунду, бесполезна —
        // плеер успеет получить 403 уже на первом запросе.
        return if (expiresAt.minus(EXPIRY_MARGIN) > Clock.System.now()) {
            stream
        } else {
            streams.remove(videoId)
            null
        }
    }

    companion object {
        /** Фильтр «только песни» в выдаче поиска. */
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

        private val EXPIRY_MARGIN = kotlin.time.Duration.parse("30s")
    }
}
