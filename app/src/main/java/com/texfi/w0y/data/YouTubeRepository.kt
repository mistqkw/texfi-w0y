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
    /**
     * Перечитать язык запросов после его смены в настройках.
     *
     * InnerTube живёт синглтоном на весь процесс, а экран настроек только
     * пересоздаёт активити — без этого ленты YouTube оставались бы на
     * старом языке до следующего запуска приложения.
     */
    fun applyAppLocale() {
        innerTube.locale = YouTubeLocaleResolver.forApp(context)
    }

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

    /**
     * Главная страница YouTube Music: готовые ленты рекомендаций.
     *
     * С аккаунтом они личные, без него — общие по региону. Своего
     * «алгоритма» мы не выдумываем: у YouTube он есть, и он лучше.
     */
    suspend fun home(): List<Shelf> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", setLogin = true)
                .body<JsonObject>()
        YtJson.shelves(response)
    }

    /**
     * Радио по треку: чем YouTube продолжил бы этот трек.
     *
     * Идентификатор станции — `RDAMVM` плюс видео; это та же станция,
     * которую их приложение заводит по кнопке «начать радио».
     */
    suspend fun radio(videoId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .next(
                    client = YouTubeClient.WEB_REMIX,
                    videoId = videoId,
                    playlistId = "RDAMVM$videoId",
                    playlistSetVideoId = null,
                    index = null,
                    params = null,
                    continuation = null,
                ).body<JsonObject>()
        YtJson.queueSongs(response).filterNot { it.id == videoId }
    }

    /**
     * Чистая версия трека, если она вообще выложена.
     *
     * Вырезать слова из записи нельзя, но у половины таких треков есть
     * официальная clean-версия — её и ищем по названию и исполнителю.
     * Сверяем название, иначе поиск с готовностью подсунет чужой кавер.
     */
    suspend fun cleanVersion(song: SongItem): SongItem? {
        val candidates =
            runCatching { searchSongs("${song.title} ${song.artist} clean") }.getOrDefault(emptyList())
        return CleanMatch.pick(song, candidates)
    }

    /** Альбомы в выдаче поиска. */
    suspend fun searchAlbums(query: String): List<PlaylistCard> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(client = YouTubeClient.WEB_REMIX, query = query, params = ALBUMS_FILTER)
                .body<JsonObject>()
        YtJson.playlistCards(response)
    }

    /** Артисты в выдаче поиска. */
    suspend fun searchArtists(query: String): List<ArtistCard> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(client = YouTubeClient.WEB_REMIX, query = query, params = ARTISTS_FILTER)
                .body<JsonObject>()
        YtJson.artistCards(response)
    }

    /** Страница артиста: треки, релизы, похожие. */
    suspend fun artist(browseId: String): ArtistPage = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId)
                .body<JsonObject>()
        YtJson.artistPage(response, browseId)
    }

    /** Страница альбома: обложка, подпись, треклист. */
    suspend fun album(browseId: String): AlbumPage = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId)
                .body<JsonObject>()
        YtJson.albumPage(response, browseId)
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
        /**
         * Фильтры выдачи поиска. Это закодированные protobuf-параметры
         * самого YouTube: своего API у них нет, значения подобраны и
         * проверены сообществом и совпадают у всех открытых клиентов.
         */
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val ALBUMS_FILTER = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTISTS_FILTER = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"

        private val EXPIRY_MARGIN = kotlin.time.Duration.parse("30s")
    }
}
