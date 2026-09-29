package com.texfi.w0y.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.texfi.w0y.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.IOException

/**
 * Сеть с запасным путём: если ссылка YouTube Music получена, но сервер
 * отказал в самом файле (403, 410, просроченный адрес), берём звук из другого
 * источника. Обложка и метаданные остаются исходными.
 */
@OptIn(UnstableApi::class)
class FallbackDataSource(
    private val upstream: DataSource.Factory,
    private val fallback: FallbackAudio,
    private val settings: SettingsRepository,
) : DataSource {
    private var current: DataSource = upstream.createDataSource()
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        current.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        try {
            return current.open(dataSpec)
        } catch (error: IOException) {
            val videoId = dataSpec.key ?: throw error
            val prefs = runBlocking { settings.settings.first() }
            if (!prefs.fallbackAudio) throw error
            Timber.w(error, "Файл $videoId не отдался, ищу запасной звук")
            runCatching { current.close() }
            val audio =
                runBlocking { fallback.resolveKnown(videoId, prefs.fallbackSource) } ?: throw error
            current = upstream.createDataSource().also { next -> listeners.forEach(next::addTransferListener) }
            return current.open(
                dataSpec.buildUpon().setUri(audio.url).setHttpRequestHeaders(audio.headers).build(),
            )
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current.read(buffer, offset, length)

    override fun getUri(): Uri? = current.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current.responseHeaders

    override fun close() = current.close()
}
