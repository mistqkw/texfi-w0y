package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.R
import com.texfi.w0y.data.AlbumPage
import com.texfi.w0y.data.ArtistPage
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Одна страница артиста или альбома.
 *
 * Своя модель на каждый экран стека: возврат назад должен показывать
 * уже загруженную страницу мгновенно, а не собирать её заново.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val youtube: YouTubeRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadsRepository,
    private val playback: PlaybackStarter,
    val player: PlayerConnection,
) : ViewModel() {
    private val _artist = MutableStateFlow<ArtistPage?>(null)
    val artist: StateFlow<ArtistPage?> = _artist.asStateFlow()

    private val _album = MutableStateFlow<AlbumPage?>(null)
    val album: StateFlow<AlbumPage?> = _album.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val pinnedKeys: StateFlow<Set<String>> =
        library.pins
            .map { pins -> pins.map { it.key }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loaded: String? = null

    /** Загружает страницу один раз на маршрут: возврат назад не дёргает сеть. */
    fun loadArtist(browseId: String) = load(browseId) {
        _artist.value = youtube.artist(browseId)
    }

    fun loadAlbum(browseId: String) = load(browseId) {
        _album.value = youtube.album(browseId)
    }

    private fun load(browseId: String, block: suspend () -> Unit) {
        if (loaded == browseId) return
        loaded = browseId
        viewModelScope.launch {
            _error.value = null
            runCatching { block() }.onFailure {
                Timber.w(it, "Страница $browseId не открылась")
                loaded = null
                _error.value = context.getString(R.string.browse_failed)
            }
        }
    }

    fun retry(browseId: String, isArtist: Boolean) {
        loaded = null
        if (isArtist) loadArtist(browseId) else loadAlbum(browseId)
    }

    fun play(songs: List<SongItem>, index: Int) = playback.play(songs, index)

    /** Явная кнопка «перемешать» сильнее выбранного режима: её только что нажали. */
    fun shuffle(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        player.play(songs.shuffled(), 0)
    }

    fun download(song: SongItem) = downloads.download(song)

    fun downloadAll(songs: List<SongItem>) = downloads.downloadAll(songs)

    fun togglePin(kind: String, id: String, title: String, subtitle: String?, thumbnailUrl: String?) =
        viewModelScope.launch { library.togglePin(kind, id, title, subtitle, thumbnailUrl) }

    fun isPinned(kind: String, id: String, keys: Set<String>) = PinEntity.key(kind, id) in keys
}
