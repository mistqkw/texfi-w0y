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

    /**
     * Порядок, в котором ссылки попадали в кэш.
     *
     * Кэш держит их с оглядкой на срок жизни, но пока он не истёк, запись
     * не уходит сама. За долгий вечер это сотни ссылок в памяти процесса,
     * который обещает быть быстрым, — поэтому старое вытесняется.
     */
    private val streamOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
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

    /**
     * Треки плейлиста или альбома по его browseId.
     *
     * Длинные списки YouTube отдаёт порциями по сто треков, поэтому дальше
     * идём по токену продолжения. Предел в страницах нужен: «все треки»
     * популярного артиста иначе тянулись бы десятком запросов подряд, и
     * экран стоял бы всё это время.
     */
    suspend fun playlistSongs(
        browseId: String,
        params: String? = null,
        maxPages: Int = 1,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val first =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, params = params, setLogin = true)
                .body<JsonObject>()
        val collected = YtJson.songs(first).toMutableList()
        var token = YtJson.continuation(first)
        var page = 1
        while (token != null && page < maxPages) {
            val next =
                runCatching {
                    innerTube
                        .browse(
                            client = YouTubeClient.WEB_REMIX,
                            browseId = null,
                            continuation = token,
                            setLogin = true,
                        ).body<JsonObject>()
                }.getOrNull() ?: break
            val more = YtJson.songs(next)
            if (more.isEmpty()) break
            collected += more
            token = YtJson.continuation(next)
            page++
        }
        collected.distinctBy { it.id }
    }

    /**
     * Все треки артиста — тот же плейлист, что за «Показать все» на
     * странице артиста. Если YouTube такой ссылки не дал, у артиста её
     * просто нет: выдумывать список из поиска по имени не будем, туда
     * попадают чужие треки.
     */
    suspend fun artistSongs(page: ArtistPage): List<SongItem> {
        val browseId = page.allSongsBrowseId ?: return emptyList()
        return playlistSongs(browseId, page.allSongsParams, maxPages = ARTIST_SONG_PAGES)
    }

    /**
     * Подсказки поиска — те же, что подсказывает сам YouTube Music.
     *
     * Своих не придумываем: по трём буквам название трека угадывает только
     * тот, у кого есть их индекс.
     */
    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .getSearchSuggestions(YouTubeClient.WEB_REMIX, query, false)
                .body<JsonObject>()
        YtJson.searchSuggestions(response).take(SUGGESTION_LIMIT)
    }

    /**
     * Выдача без фильтра: треки, альбомы и артисты одним запросом.
     *
     * Раздельные вкладки — это три отдельных запроса к YouTube, и на
     * «просто поискать» уходит три ожидания вместо одного. Здесь ответ
     * приходит смешанным, а разбираем мы его по типам сами.
     */
    suspend fun searchEverything(query: String): MixedResults = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(client = YouTubeClient.WEB_REMIX, query = query)
                .body<JsonObject>()
        YtJson.mixed(response)
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
                remember(videoId, extracted)
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

    private fun remember(videoId: String, stream: ExtractedStream) {
        streams[videoId] = stream
        synchronized(streamOrder) {
            streamOrder.remove(videoId)
            streamOrder.add(videoId)
            while (streamOrder.size > STREAM_CACHE_SIZE) {
                val oldest = streamOrder.removeAt(0)
                streams.remove(oldest)
                // Замок снимаем только свободный: занятый значит, что этот
                // трек прямо сейчас извлекают, и выкидывать его нельзя.
                locks[oldest]?.takeIf { !it.isLocked }?.let { locks.remove(oldest) }
            }
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

        /** Три страницы — до трёхсот треков: дальше ждать дольше, чем слушать. */
        private const val ARTIST_SONG_PAGES = 3
        private const val SUGGESTION_LIMIT = 8

        /**
         * Сколько ссылок держим. Хватает на длинную очередь с запасом на
         * возвраты назад, и при этом память не растёт весь вечер.
         */
        private const val STREAM_CACHE_SIZE = 80
    }
}

/** Смешанная выдача одного запроса: всё, что нашлось, по типам. */
data class MixedResults(
    val songs: List<SongItem> = emptyList(),
    val albums: List<PlaylistCard> = emptyList(),
    val artists: List<ArtistCard> = emptyList(),
)
