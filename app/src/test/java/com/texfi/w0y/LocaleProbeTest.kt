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
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test

/** Какие системные локали YouTube принимает, а на каких отвечает 400. */
class LocaleProbeTest {
    @Test
    fun probeLocales() = runBlocking {
        val tags =
            listOf(
                "ru-RU",
                "ru-RU-u-ca-gregory-nu-latn",
                "ru",
                "en-US",
            )
        val original = Locale.getDefault()
        for (tag in tags) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val http =
                HttpClient(OkHttp) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val innerTube = InnerTube(httpClient = http)
            val result =
                runCatching {
                    innerTube
                        .search(
                            client = YouTubeClient.WEB_REMIX,
                            query = "династия",
                            params = SearchParser.SONGS_FILTER,
                        ).body<SearchResponse>()
                }
            val verdict =
                result.fold(
                    onSuccess = { "OK, треков: ${SearchParser.songs(it).size}" },
                    onFailure = { "ПАДАЕТ: ${it.message}" },
                )
            println("ЛОКАЛЬ '$tag' (hl=${Locale.getDefault().toLanguageTag()}, gl=${Locale.getDefault().country}) → $verdict")
            http.close()
        }
        Locale.setDefault(original)
    }
}
