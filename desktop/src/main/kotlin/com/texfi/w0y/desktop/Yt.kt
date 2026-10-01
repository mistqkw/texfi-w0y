package com.texfi.w0y.desktop

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.YouTubeLocale
import com.texfi.w0y.data.AlbumPage
import com.texfi.w0y.data.ArtistPage
import com.texfi.w0y.data.MixedResults
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.Shelf
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YtJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Доступ к YouTube Music для десктопа. Те же запросы и тот же разбор
 * (YtJson общий с телефоном), без Android: ни Hilt, ни кэша плеера.
 */
class Yt {
    private val http =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    val innerTube: InnerTube =
        InnerTube(httpClient = http).apply {
            val lang = Locale.getDefault().language.ifBlank { "en" }
            val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
            locale = YouTubeLocale(gl = country, hl = lang)
        }
    private val extractor: InnerTubeExtractor =
        run {
            val store = RemotePlayerConfigStore(httpClient = http, repository = PlayerConfigRepository.disabled())
            InnerTubeExtractor(
                configParser = YtConfigParserImpl(http, innerTube, store),
                cipherService = YouTubeCipherService(http, store),
                innerTube = innerTube,
            )
        }

    private val streams = ConcurrentHashMap<String, ExtractedStream>()

    /** Подставляет cookie аккаунта (или снимает, если null). */
    fun applySession(cookie: String?) {
        innerTube.replaceSession(
            cookie = cookie,
            visitorData = null,
            dataSyncId = null,
            authUser = "0",
            useLoginForBrowse = !cookie.isNullOrBlank(),
        )
    }

    suspend fun accountInfo(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val menu = innerTube.accountMenu(YouTubeClient.WEB_REMIX).body<JsonObject>()
        val name = with(YtJson) { menu.findAll("accountName").firstOrNull()?.firstString("text") }
        name to YtJson.accountAvatar(menu)
    }

    /**
     * Поиск тремя запросами с фильтрами, как на телефоне: в общей выдаче
     * в подписи трека первым стоит тип («Композиция», «Видео»), а не исполнитель.
     */
    suspend fun search(query: String): MixedResults = coroutineScope {
        suspend fun filtered(params: String) =
            innerTube.search(client = YouTubeClient.WEB_REMIX, query = query, params = params).body<JsonObject>()
        val songs = async(Dispatchers.IO) { YtJson.songs(filtered(SONGS_FILTER)) }
        val albums = async(Dispatchers.IO) { runCatching { YtJson.playlistCards(filtered(ALBUMS_FILTER)) }.getOrDefault(emptyList()) }
        val artists = async(Dispatchers.IO) { runCatching { YtJson.artistCards(filtered(ARTISTS_FILTER)) }.getOrDefault(emptyList()) }
        MixedResults(songs.await(), albums.await(), artists.await())
    }

    suspend fun home(): List<Shelf> = withContext(Dispatchers.IO) {
        YtJson.shelves(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", setLogin = true)
                .body<JsonObject>(),
        )
    }

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

    suspend fun artist(browseId: String): ArtistPage = withContext(Dispatchers.IO) {
        YtJson.artistPage(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>(),
            browseId,
        )
    }

    suspend fun album(browseId: String): AlbumPage = withContext(Dispatchers.IO) {
        YtJson.albumPage(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>(),
            browseId,
        )
    }

    /** Треки плейлиста со всеми страницами (до [maxPages]). */
    suspend fun playlistSongs(browseId: String, maxPages: Int = 10, params: String? = null): List<SongItem> = withContext(Dispatchers.IO) {
        val first =
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, params = params, setLogin = true).body<JsonObject>()
        val all = YtJson.songs(first).toMutableList()
        var token = YtJson.continuation(first)
        var page = 1
        while (token != null && page < maxPages) {
            val next =
                runCatching {
                    innerTube
                        .browse(client = YouTubeClient.WEB_REMIX, browseId = null, continuation = token, setLogin = true)
                        .body<JsonObject>()
                }.getOrNull() ?: break
            val more = YtJson.songs(next)
            if (more.isEmpty()) break
            all += more
            token = YtJson.continuation(next)
            page++
        }
        all.distinctBy { it.id }
    }

    /** Лайкнутые и плейлисты аккаунта. Нужен вход. */
    suspend fun likedSongs(): List<SongItem> = playlistSongs("FEmusic_liked_videos", 20)

    suspend fun accountPlaylists(): List<PlaylistCard> = withContext(Dispatchers.IO) {
        YtJson.playlistCards(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_liked_playlists", setLogin = true)
                .body<JsonObject>(),
        ).filterNot { it.isAlbum || it.browseId == "VLLM" || it.browseId == "VLSE" }
    }

    suspend fun like(videoId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        if (liked) innerTube.likeVideo(YouTubeClient.WEB_REMIX, videoId) else innerTube.unlikeVideo(YouTubeClient.WEB_REMIX, videoId)
        Unit
    }

    /** Ссылка на звук трека; кэшируется до истечения срока. */
    suspend fun stream(videoId: String): ExtractedStream {
        streams[videoId]?.let { cached ->
            val expires = cached.expiresAt
            if (expires == null || expires.minus(30.seconds) > Clock.System.now()) return cached
            streams.remove(videoId)
        }
        return withContext(Dispatchers.IO) {
            val extracted =
                extractor.extract(
                    videoId = videoId,
                    hints = ContentHints(wantVideo = false),
                    audioQuality = AudioQuality.HIGH,
                ) ?: error("YouTube не отдал поток для $videoId")
            streams[videoId] = extracted
            extracted
        }
    }

    private companion object {
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val ALBUMS_FILTER = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTISTS_FILTER = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
    }
}
