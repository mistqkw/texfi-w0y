package com.texfi.w0y.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Состояние одной загрузки для интерфейса. */
data class DownloadProgress(
    val songId: String,
    val percent: Float,
    val state: Int,
)

@Singleton
@OptIn(UnstableApi::class)
class DownloadsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    private val library: LibraryRepository,
    settings: SettingsRepository,
    @param:Named("download") private val downloadCache: SimpleCache,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    init {
        W0yDownloadService.ensureChannel(context)
        // Ограничение сети ставится самому менеджеру: он сам придержит
        // очередь до Wi-Fi и сам продолжит, когда тот появится.
        settings.settings
            .map { it.downloadOnWifiOnly }
            .distinctUntilChanged()
            .onEach { wifiOnly ->
                // Менеджер загрузок живёт на главном потоке приложения —
                // трогать его из фонового значит ловить гонку на пустом месте.
                withContext(Dispatchers.Main) {
                    manager.requirements =
                        Requirements(
                            if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK,
                        )
                }
            }.launchIn(scope)
        manager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    publish(downloadManager)
                    scope.launch {
                        when (download.state) {
                            Download.STATE_COMPLETED ->
                                library.markDownload(download.request.id, SongEntity.DOWNLOAD_DONE)

                            Download.STATE_FAILED, Download.STATE_REMOVING ->
                                library.markDownload(download.request.id, SongEntity.DOWNLOAD_NONE)

                            else ->
                                library.markDownload(download.request.id, SongEntity.DOWNLOAD_QUEUED)
                        }
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    publish(downloadManager)
                    scope.launch { library.markDownload(download.request.id, SongEntity.DOWNLOAD_NONE) }
                }
            },
        )
        publish(manager)
    }

    fun download(song: SongItem) {
        scope.launch { library.saveSong(song) }
        val request =
            DownloadRequest
                .Builder(song.id, android.net.Uri.parse("w0y://${song.id}"))
                .setCustomCacheKey(song.id)
                .build()
        DownloadService.sendAddDownload(
            context,
            W0yDownloadService::class.java,
            request,
            /* foreground = */ false,
        )
    }

    fun downloadAll(songs: List<SongItem>) = songs.forEach(::download)

    fun cancel(songId: String) {
        DownloadService.sendRemoveDownload(
            context,
            W0yDownloadService::class.java,
            songId,
            /* foreground = */ false,
        )
    }

    fun pauseAll() =
        DownloadService.sendPauseDownloads(context, W0yDownloadService::class.java, false)

    fun resumeAll() =
        DownloadService.sendResumeDownloads(context, W0yDownloadService::class.java, false)

    /** Сколько места занято скачанным — показывается на экране загрузок. */
    fun usedBytes(): Long = downloadCache.cacheSpace

    private fun publish(manager: DownloadManager) {
        val cursor = manager.downloadIndex.getDownloads()
        val map = mutableMapOf<String, DownloadProgress>()
        cursor.use {
            while (it.moveToNext()) {
                val download = it.download
                map[download.request.id] =
                    DownloadProgress(
                        songId = download.request.id,
                        percent = download.percentDownloaded.coerceAtLeast(0f),
                        state = download.state,
                    )
            }
        }
        _progress.value = map
    }
}
