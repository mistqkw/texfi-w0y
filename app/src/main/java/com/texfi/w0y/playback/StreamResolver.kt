package com.texfi.w0y.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.texfi.w0y.data.YouTubeRepository
import kotlinx.coroutines.runBlocking

/**
 * Подменяет схему `w0y://<videoId>` на настоящую ссылку потока в момент
 * загрузки.
 *
 * Так очередь можно собрать мгновенно из одних идентификаторов, не дожидаясь
 * извлечения потоков, а ключ кэша остаётся стабильным (videoId), хотя сама
 * ссылка живёт несколько часов и меняется.
 */
@OptIn(UnstableApi::class)
class StreamResolver(
    private val repository: YouTubeRepository,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = dataSpec.key ?: dataSpec.uri.host ?: return dataSpec
        // Блокирующий вызов здесь уместен: метод вызывается на загрузочном
        // потоке ExoPlayer, который именно этого результата и ждёт.
        val stream = runBlocking { repository.stream(videoId) }
        return dataSpec
            .buildUpon()
            .setUri(stream.audioUrl)
            .setKey(videoId)
            .setHttpRequestHeaders(stream.headers)
            .build()
    }
}
