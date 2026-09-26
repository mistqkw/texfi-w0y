package com.texfi.w0y.data

import android.content.Context
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object YouTubeModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            // Таймауты короче стандартных: зависший запрос к YouTube лучше
            // оборвать и повторить, чем держать пользователя в тишине.
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun httpClient(okHttp: OkHttpClient): HttpClient =
        HttpClient(OkHttp) {
            engine { preconfigured = okHttp }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    @Provides
    @Singleton
    fun innerTube(
        httpClient: HttpClient,
        @ApplicationContext context: Context,
    ): InnerTube {
        return InnerTube(httpClient = httpClient).apply {
            // Своя локаль вместо системной: библиотека по умолчанию кладёт
            // в hl полный языковой тег устройства, а на паре вроде en-PL
            // YouTube отвечает 400 на любой запрос. Язык берётся тот, что
            // выбран в приложении, иначе интерфейс на русском получал бы
            // ленты на языке телефона.
            locale = YouTubeLocaleResolver.forApp(context)
        }
    }

    @Provides
    @Singleton
    fun extractor(innerTube: InnerTube, httpClient: HttpClient): InnerTubeExtractor {
        val configStore =
            RemotePlayerConfigStore(
                httpClient = httpClient,
                repository = PlayerConfigRepository.disabled(),
            )
        return InnerTubeExtractor(
            configParser = YtConfigParserImpl(httpClient, innerTube, configStore),
            cipherService = YouTubeCipherService(httpClient, configStore),
            innerTube = innerTube,
        )
    }
}
