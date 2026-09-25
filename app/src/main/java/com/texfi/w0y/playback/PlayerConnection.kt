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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Что показывает интерфейс о текущем воспроизведении. */
data class PlayerUiState(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val error: String? = null,
)

/**
 * Мост между интерфейсом и медиа-сессией сервиса.
 *
 * Интерфейс не держит плеер сам: сервис живёт дольше экрана, и всё
 * состояние приходит из сессии — поэтому после сворачивания приложения
 * музыка не прерывается, а вернувшись, экран сразу видит правду.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var queue: List<SongItem> = emptyList()

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
        queue = songs
        media.setMediaItems(songs.map(::toMediaItem), startIndex, 0L)
        media.prepare()
        media.play()
    }

    fun togglePlayPause() {
        val media = controller ?: return
        if (media.isPlaying) media.pause() else media.play()
    }

    fun skipNext() = controller?.seekToNextMediaItem()

    fun skipPrevious() = controller?.seekToPreviousMediaItem()

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    private fun push(player: Player) {
        val id = player.currentMediaItem?.mediaId
        _state.value =
            PlayerUiState(
                song = queue.firstOrNull { it.id == id } ?: _state.value.song.takeIf { it?.id == id },
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            )
    }

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
