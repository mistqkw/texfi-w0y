package com.texfi.w0y.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.YouTubeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
@OptIn(UnstableApi::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun databaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    /**
     * Кэш прослушанного: вытесняется по мере заполнения, лимит берётся
     * из настроек при первом обращении.
     */
    @Provides
    @Singleton
    @Named("stream")
    fun streamCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        settings: SettingsRepository,
    ): SimpleCache {
        // Лимит читается один раз при создании кэша: у SimpleCache нельзя
        // поменять вытеснитель на живом объекте, поэтому новая величина
        // вступает в силу после перезапуска — так и написано в настройках.
        val limitMb = runBlocking { settings.settings.first().cacheLimitMb }
        return SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(limitMb.toLong() * 1024 * 1024),
            databaseProvider,
        )
    }

    /**
     * Скачанное — отдельное хранилище без вытеснения и во внутренней папке
     * приложения: иначе система однажды вычистит кэш и «скачанная» музыка
     * молча исчезнет.
     */
    @Provides
    @Singleton
    @Named("download")
    fun downloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache =
        SimpleCache(
            File(context.filesDir, "downloads"),
            NoOpCacheEvictor(),
            databaseProvider,
        )

    /** Сеть плюс подстановка настоящей ссылки вместо схемы w0y://. */
    @Provides
    @Singleton
    @Named("resolving")
    fun resolvingFactory(
        okHttpClient: OkHttpClient,
        repository: YouTubeRepository,
    ): DataSource.Factory =
        ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(okHttpClient),
            StreamResolver(repository),
        )

    /**
     * Цепочка для воспроизведения: сначала скачанное, потом кэш, и только
     * потом сеть. Скачанный трек играет офлайн и не перекачивается.
     */
    @Provides
    @Singleton
    @Named("player")
    fun playerFactory(
        @Named("download") downloadCache: SimpleCache,
        @Named("stream") streamCache: SimpleCache,
        @Named("resolving") resolving: DataSource.Factory,
    ): DataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(streamCache)
                    .setUpstreamDataSourceFactory(resolving)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Provides
    @Singleton
    fun downloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @Named("download") downloadCache: SimpleCache,
        @Named("resolving") resolving: DataSource.Factory,
    ): DownloadManager =
        DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            DefaultDownloaderFactory(
                CacheDataSource
                    .Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(resolving),
                Executors.newFixedThreadPool(2),
            ),
        ).apply {
            // Две загрузки разом: больше упирается в отдачу YouTube и мешает
            // воспроизведению, меньше — заметно медленнее на плейлистах.
            maxParallelDownloads = 2
        }
}
