package com.texfi.w0y.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.texfi.w0y.data.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Что показывает интерфейс о текущем воспроизведении. */
data class PlayerUiState(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<SongItem> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

/**
 * Мост между интерфейсом и медиа-сессией сервиса.
 *
 * Состояние собирается из самого плеера, а не из того, что когда-то
 * отправил экран: сервис живёт дольше экрана, и после сворачивания или
 * перезапуска активности интерфейс должен видеть правду, а не свою копию.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sleepJob: Job? = null

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = push(player)

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = _state.value.copy(error = error.errorCodeName, isBuffering = false)
            }
        }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, W0yPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val media = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = media
            media.addListener(listener)
            push(media)
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(songs: List<SongItem>, startIndex: Int) {
        val media = controller ?: return
        media.setMediaItems(songs.map(::toMediaItem), startIndex, 0L)
        media.prepare()
        media.play()
    }

    fun togglePlayPause() {
        val media = controller ?: return
        if (media.isPlaying) media.pause() else media.play()
    }

    fun skipNext() = controller?.seekToNextMediaItem()

    fun skipPrevious() {
        val media = controller ?: return
        // Как у всех плееров: первое нажатие возвращает к началу трека,
        // и только в первые секунды — к предыдущему.
        if (media.currentPosition > 4_000) media.seekTo(0) else media.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    fun playAt(index: Int) {
        val media = controller ?: return
        media.seekTo(index, 0L)
        media.play()
    }

    fun toggleShuffle() {
        val media = controller ?: return
        media.shuffleModeEnabled = !media.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val media = controller ?: return
        media.repeatMode =
            when (media.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    /** Таймер сна: по истечении ставит паузу, а не глушит приложение. */
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val totalMs = minutes * 60_000L
        sleepJob =
            scope.launch {
                var left = totalMs
                while (left > 0) {
                    _sleepRemainingMs.value = left
                    delay(1_000)
                    left -= 1_000
                }
                _sleepRemainingMs.value = null
                controller?.pause()
            }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }

    private fun push(player: Player) {
        val queue =
            (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).toSong()
            }
        val index = player.currentMediaItemIndex
        _state.value =
            PlayerUiState(
                song = queue.getOrNull(index),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                queue = queue,
                currentIndex = index,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
    }

    private fun MediaItem.toSong(): SongItem =
        SongItem(
            id = mediaId,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            album = mediaMetadata.albumTitle?.toString(),
            thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        )

    private fun toMediaItem(song: SongItem): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(song.id)
            // Схему разворачивает StreamResolver уже во время загрузки:
            // очередь собирается мгновенно, без сетевых запросов.
            .setUri("w0y://${song.id}")
            .setCustomCacheKey(song.id)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.thumbnailUrl?.toUri())
                    .build(),
            ).build()
}
