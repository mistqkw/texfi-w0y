package com.texfi.w0y

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Живое извлечение потока: самая хрупкая часть всего проекта — YouTube
 * меняет шифрование и токены без предупреждения. Запуск руками:
 * `./gradlew testDebugUnitTest --tests '*StreamSmokeTest*'`.
 */
class StreamSmokeTest {
    @Test
    fun extractsAudioUrl() = runBlocking {
        requireLiveNetwork()
        val http =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val innerTube = InnerTube(httpClient = http)
        val configStore =
            RemotePlayerConfigStore(httpClient = http, repository = PlayerConfigRepository.disabled())
        val cipher = YouTubeCipherService(http, configStore)
        val extractor =
            InnerTubeExtractor(
                configParser = YtConfigParserImpl(http, innerTube, configStore, cipherService = cipher),
                cipherService = cipher,
                innerTube = innerTube,
            )
        val stream =
            try {
                extractor.extract(
                    videoId = "khnokW3Mw24",
                    hints = ContentHints(wantVideo = false),
                    audioQuality = AudioQuality.HIGH,
                )
            } catch (error: Throwable) {
                http.close()
                skipIfNativeCipherMissing(error)
                throw error
            }
        println("ПОТОК: itag=${stream?.itag} mime=${stream?.mimeType} bitrate=${stream?.bitrate}")
        println("ССЫЛКА ЖИВЁТ ДО: ${stream?.expiresAt}, длина=${stream?.contentLengthBytes}")
        http.close()
        assertNotNull("Поток не извлёкся", stream)
    }

    /**
     * Треки, которые YouTube отдаёт только по SABR: поток собирается из
     * кусков в целый файл — без дыр и ровно нужной длины.
     */
    @OptIn(com.metrolist.innertubex.sabr.ExperimentalSabrApi::class)
    @Test
    fun sabrStreamAssemblesWholeFile() = runBlocking {
        requireLiveNetwork()
        val http =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val innerTube = InnerTube(httpClient = http)
        val configStore = RemotePlayerConfigStore(httpClient = http, repository = PlayerConfigRepository.disabled())
        val cipher = YouTubeCipherService(http, configStore)
        val extractor =
            InnerTubeExtractor(
                configParser = YtConfigParserImpl(http, innerTube, configStore, cipherService = cipher),
                cipherService = cipher,
                innerTube = innerTube,
            )
        try {
            for (id in listOf("OeRhL--tVz0", "tRAmPLYjbGg", "khnokW3Mw24")) {
                val stream =
                    try {
                        extractor.extract(videoId = id, hints = ContentHints(wantVideo = false), audioQuality = AudioQuality.HIGH)
                    } catch (error: Throwable) {
                        skipIfNativeCipherMissing(error)
                        println("$id: не извлёкся — ${error.message}")
                        null
                    } ?: continue
                val scheme = stream.audioUrl.substringBefore(':')
                println("$id: схема=$scheme sabr=${stream.sabrBootstrap != null} длина=${stream.contentLengthBytes}")
                val bootstrap = stream.sabrBootstrap ?: continue
                if (scheme != "sabr") continue
                var next = 0L
                com.metrolist.innertubex.sabr.SabrAudioStream(http, bootstrap).chunks().collect { chunk ->
                    if (chunk.endRangeExclusive <= next) return@collect
                    org.junit.Assert.assertTrue("$id: дыра на байте $next, кусок с ${chunk.startRange}", chunk.startRange <= next)
                    next = chunk.endRangeExclusive
                }
                println("$id: собрано $next байт")
                bootstrap.contentLengthBytes?.let { assertEquals("$id: длина файла", it, next) }
            }
        } finally {
            http.close()
        }
    }
}
