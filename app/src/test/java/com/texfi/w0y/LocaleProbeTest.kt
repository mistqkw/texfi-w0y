package com.texfi.w0y

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.YouTubeLocale
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
 * Какие hl/gl YouTube принимает. Тест ходит в сеть и в CI не входит:
 * `./gradlew testDebugUnitTest --tests '*LocaleProbeTest*'`.
 *
 * Зафиксировано ради того, чтобы больше не искать эту ошибку вслепую:
 * пара «язык + страна» в hl (en-PL) валит любой запрос, язык отдельно —
 * работает с любой страной в gl.
 */
class LocaleProbeTest {
    @Test
    fun languageOnlyHlWorksWithAnyCountry() = runBlocking {
        val cases =
            listOf(
                YouTubeLocale(hl = "en", gl = "PL") to true,
                YouTubeLocale(hl = "ru", gl = "PL") to true,
                YouTubeLocale(hl = "en-PL", gl = "PL") to false,
            )
        for ((locale, shouldWork) in cases) {
            val http =
                HttpClient(OkHttp) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val innerTube = InnerTube(httpClient = http).apply { this.locale = locale }
            val songs =
                runCatching {
                    innerTube
                        .search(
                            client = YouTubeClient.WEB_REMIX,
                            query = "династия",
                            params = SearchParser.SONGS_FILTER,
                        ).body<SearchResponse>()
                }.map { SearchParser.songs(it) }
            println("hl=${locale.hl} gl=${locale.gl} → ${songs.map { it.size }}")
            http.close()
            assertTrue(
                "hl=${locale.hl} gl=${locale.gl}: ожидали работоспособность=$shouldWork",
                songs.isSuccess == shouldWork,
            )
        }
    }
}
