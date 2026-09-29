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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/**
 * Состояния media3 — нестабильное API, поэтому интерфейсу отдаются свои
 * три смысла, а не семь чужих констант.
 */
object DownloadState {
    const val WAITING = 0
    const val RUNNING = 2
    const val DONE = 3
}

/** Что сказать человеку после нажатия «скачать» и по итогам. */
sealed interface DownloadNotice {
    /** [waitingForWifi] — очередь стоит, потому что в настройках «только Wi-Fi». */
    data class Queued(val title: String, val count: Int, val waitingForWifi: Boolean) : DownloadNotice

    data class Finished(val title: String?, val count: Int) : DownloadNotice

    data class Failed(val title: String) : DownloadNotice
}

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

    private val _notices = MutableSharedFlow<DownloadNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<DownloadNotice> = _notices.asSharedFlow()

    private var wifiOnly = true
    private var finishedInBatch = 0
    private var lastFinishedTitle: String? = null
    private var ticker: Job? = null

    init {
        W0yDownloadService.ensureChannel(context)
        // Ограничение сети ставится самому менеджеру: он сам придержит
        // очередь до Wi-Fi и сам продолжит, когда тот появится.
        settings.settings
            .map { it.downloadOnWifiOnly }
            .distinctUntilChanged()
            .onEach { wifiOnly ->
                this.wifiOnly = wifiOnly
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
                    announce(downloadManager, download)
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
        enqueue(song)
        _notices.tryEmit(DownloadNotice.Queued(song.title, 1, waitingForWifi()))
    }

    fun downloadAll(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        songs.forEach(::enqueue)
        _notices.tryEmit(DownloadNotice.Queued(songs.first().title, songs.size, waitingForWifi()))
    }

    private fun enqueue(song: SongItem) {
        scope.launch { library.saveSong(song) }
        val request =
            DownloadRequest
                .Builder(song.id, w0yUri(song))
                .setCustomCacheKey(song.id)
                // Название едет вместе с запросом: по нему потом говорится,
                // что именно скачалось, без похода в базу.
                .setData(song.title.toByteArray())
                .build()
        DownloadService.sendAddDownload(
            context,
            W0yDownloadService::class.java,
            request,
            /* foreground = */ false,
        )
    }

    /** Стоит ли очередь прямо сейчас из-за «только Wi-Fi». */
    private fun waitingForWifi(): Boolean =
        wifiOnly && Requirements(Requirements.NETWORK_UNMETERED).getNotMetRequirements(context) != 0

    /**
     * Итог говорится один раз на пачку: альбом из двенадцати треков — это
     * одно «скачано: 12», а не двенадцать всплывающих строк подряд.
     */
    private fun announce(manager: DownloadManager, download: Download) {
        // У загрузок из прошлой сборки названия в запросе нет — тогда id.
        val title = download.request.data.decodeToString().ifBlank { download.request.id }
        when (download.state) {
            Download.STATE_COMPLETED -> {
                finishedInBatch++
                lastFinishedTitle = title
            }
            Download.STATE_FAILED -> _notices.tryEmit(DownloadNotice.Failed(title))
            Download.STATE_DOWNLOADING -> startTicker(manager)
        }
        val busy = manager.currentDownloads.any { it.state != Download.STATE_COMPLETED && it.state != Download.STATE_FAILED }
        if (!busy && finishedInBatch > 0) {
            _notices.tryEmit(
                DownloadNotice.Finished(lastFinishedTitle.takeIf { finishedInBatch == 1 }, finishedInBatch),
            )
            finishedInBatch = 0
            lastFinishedTitle = null
        }
    }

    /**
     * Менеджер сообщает о смене состояния, но не о байтах: проценты он
     * считает молча. Пока что-то качается, раз в полсекунды забираем их
     * из памяти менеджера — без чтения базы на главном потоке.
     */
    private fun startTicker(manager: DownloadManager) {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch(Dispatchers.Main) {
                while (true) {
                    val running = manager.currentDownloads.filter { it.state == Download.STATE_DOWNLOADING }
                    if (running.isEmpty()) break
                    _progress.value =
                        _progress.value + running.associate {
                            it.request.id to
                                DownloadProgress(it.request.id, it.percentDownloaded.coerceAtLeast(0f), DownloadState.RUNNING)
                        }
                    delay(TICK_MS)
                }
            }
    }

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
                        state =
                            when (download.state) {
                                Download.STATE_COMPLETED -> DownloadState.DONE
                                Download.STATE_DOWNLOADING -> DownloadState.RUNNING
                                Download.STATE_FAILED, Download.STATE_REMOVING -> continue
                                else -> DownloadState.WAITING
                            },
                    )
            }
        }
        _progress.value = map
    }
}

private const val TICK_MS = 500L
