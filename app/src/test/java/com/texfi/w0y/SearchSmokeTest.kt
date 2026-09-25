package com.texfi.w0y

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.SearchResponse
import com.texfi.w0y.data.SearchParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Живой запрос к YouTube Music: проверяет, что разбор выдачи не отвалился
 * после очередного изменения на их стороне.
 *
 * Тест ходит в сеть, поэтому он не в CI: запускать руками командой
 * `./gradlew testDebugUnitTest --tests '*SearchSmokeTest*'`.
 */
class SearchSmokeTest {
    @Test
    fun findsSongs() = runBlocking {
        val http =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val innerTube = InnerTube(httpClient = http)
        val response =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = "daft punk instant crush",
                    params = SearchParser.SONGS_FILTER,
                ).body<SearchResponse>()
        val songs = SearchParser.songs(response)
        songs.take(5).forEach { println("НАЙДЕНО: ${it.title} — ${it.artist} [${it.id}] ${it.durationText}") }
        http.close()
        assertTrue("Поиск вернул пусто", songs.isNotEmpty())
    }
}
