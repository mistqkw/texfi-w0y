package com.texfi.w0y

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.YtJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тот же поиск, но под Android-классами: воспроизводит ровно ту сборку
 * библиотеки, которая едет в APK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSearchSmokeTest {
    @Test
    fun findsSongsOnAndroidVariant() = runBlocking {
        requireLiveNetwork()
        val okHttp =
            OkHttpClient
                .Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        val http =
            HttpClient(OkHttp) {
                engine { preconfigured = okHttp }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val innerTube = InnerTube(httpClient = http)
        val result =
            runCatching {
                innerTube
                    .search(
                        client = YouTubeClient.WEB_REMIX,
                        query = "династия",
                        params = YouTubeRepository.SONGS_FILTER,
                    ).body<kotlinx.serialization.json.JsonObject>()
            }
        result.fold(
            onSuccess = { response ->
                val songs = YtJson.songs(response)
                println("ANDROID-ВАРИАНТ: найдено ${songs.size}")
                songs.take(3).forEach { println("  ${it.title} — ${it.artist}") }
            },
            onFailure = { error ->
                println("ANDROID-ВАРИАНТ УПАЛ: ${error::class.qualifiedName}: ${error.message}")
                error.cause?.let { println("  причина: ${it::class.qualifiedName}: ${it.message}") }
            },
        )
        http.close()
    }
}
