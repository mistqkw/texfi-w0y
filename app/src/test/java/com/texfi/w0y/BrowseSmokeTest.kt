package com.texfi.w0y

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.YtJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Живые запросы: карточка артиста, страница альбома и разделы поиска.
 *
 * Разбор ответов YouTube ломается не от наших правок, а от их изменений,
 * поэтому единственная честная проверка — сходить к ним по-настоящему.
 * В CI не входит: `./gradlew testDebugUnitTest --tests '*BrowseSmokeTest*'`.
 */
class BrowseSmokeTest {
    private fun client() =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun opensArtistAndAlbum() = runBlocking {
        val http = client()
        val innerTube = InnerTube(httpClient = http)

        val artistsResponse =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = "daft punk",
                    params = YouTubeRepository.ARTISTS_FILTER,
                ).body<JsonObject>()
        val artists = YtJson.artistCards(artistsResponse)
        artists.take(3).forEach { println("АРТИСТ: ${it.name} [${it.browseId}]") }
        assertTrue("Поиск артистов вернул пусто", artists.isNotEmpty())

        val artistPage =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = artists.first().browseId)
                .body<JsonObject>()
        val artist = YtJson.artistPage(artistPage, artists.first().browseId)
        println("СТРАНИЦА: ${artist.name} · треков ${artist.songs.size} · релизов ${artist.releases.size} · похожих ${artist.similar.size}")
        assertTrue("У артиста нет треков", artist.songs.isNotEmpty())
        assertTrue("У артиста нет релизов", artist.releases.isNotEmpty())

        val albumId = artist.releases.first { it.isAlbum }.browseId
        val albumResponse =
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = albumId).body<JsonObject>()
        val album = YtJson.albumPage(albumResponse, albumId)
        println("АЛЬБОМ: ${album.title} · ${album.subtitle} · треков ${album.songs.size}")
        assertTrue("В альбоме нет треков", album.songs.isNotEmpty())
        assertNotNull("У альбома нет обложки", album.thumbnailUrl)
        assertTrue("Трекам альбома не досталось обложки", album.songs.all { it.thumbnailUrl != null })

        http.close()
    }

    @Test
    fun findsAlbums() = runBlocking {
        val http = client()
        val innerTube = InnerTube(httpClient = http)
        val response =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = "random access memories",
                    params = YouTubeRepository.ALBUMS_FILTER,
                ).body<JsonObject>()
        val albums = YtJson.playlistCards(response)
        albums.take(3).forEach { println("АЛЬБОМ: ${it.title} — ${it.subtitle} [${it.browseId}]") }
        http.close()
        assertTrue("Поиск альбомов вернул пусто", albums.isNotEmpty())
    }

    @Test
    fun songsCarryArtistAndAlbumLinks() = runBlocking {
        val http = client()
        val innerTube = InnerTube(httpClient = http)
        val response =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = "daft punk instant crush",
                    params = YouTubeRepository.SONGS_FILTER,
                ).body<JsonObject>()
        val songs = YtJson.songs(response)
        songs.take(5).forEach { println("ТРЕК: ${it.title} · артист=${it.artistId} · альбом=${it.albumId}") }
        http.close()
        assertTrue("Ни у одного трека нет ссылки на артиста", songs.any { it.artistId != null })
    }

    @Test
    fun buildsRadioQueue() = runBlocking {
        val http = client()
        val innerTube = InnerTube(httpClient = http)
        // «Instant Crush» — первый попавшийся живой трек, станция строится
        // по любому: важно, что ответ вообще разбирается.
        val videoId = "a5uQMwRMHcs"
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
        val queue = YtJson.queueSongs(response)
        queue.take(5).forEach { println("РАДИО: ${it.title} — ${it.artist} [${it.id}]") }
        http.close()
        assertTrue("Радио вернуло пусто", queue.size > 1)
    }

    @Test
    fun readsHomeShelves() = runBlocking {
        val http = client()
        val innerTube = InnerTube(httpClient = http)
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_home")
                .body<JsonObject>()
        val shelves = YtJson.shelves(response)
        shelves.take(6).forEach {
            println("ЛЕНТА: ${it.title} · треков ${it.songs.size} · карточек ${it.cards.size} · артистов ${it.artists.size}")
        }
        http.close()
        assertTrue("Главная не вернула ни одной ленты", shelves.isNotEmpty())
    }

    /** Размер зашит в саму ссылку — проверяем, что мы просим крупную картинку. */
    @Test
    fun upscalesThumbnails() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w384-h384-l90-rj",
            Thumbnails.sized("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj", 384),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            Thumbnails.sized("https://i.ytimg.com/vi/abc/default.jpg", 384),
        )
        assertEquals(null, Thumbnails.sized(null, 384))
    }
}
