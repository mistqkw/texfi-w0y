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
        val http =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val innerTube = InnerTube(httpClient = http)
        val configStore =
            RemotePlayerConfigStore(httpClient = http, repository = PlayerConfigRepository.disabled())
        val extractor =
            InnerTubeExtractor(
                configParser = YtConfigParserImpl(http, innerTube, configStore),
                cipherService = YouTubeCipherService(http, configStore),
                innerTube = innerTube,
            )
        val stream =
            extractor.extract(
                videoId = "khnokW3Mw24",
                hints = ContentHints(wantVideo = false),
                audioQuality = AudioQuality.HIGH,
            )
        println("ПОТОК: itag=${stream?.itag} mime=${stream?.mimeType} bitrate=${stream?.bitrate}")
        println("ССЫЛКА ЖИВЁТ ДО: ${stream?.expiresAt}, длина=${stream?.contentLengthBytes}")
        http.close()
        assertNotNull("Поток не извлёкся", stream)
    }
}
