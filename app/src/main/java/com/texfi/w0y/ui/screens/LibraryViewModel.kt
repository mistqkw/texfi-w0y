package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.AccountRepository
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** Что открыто в разделе «моё»: список или конкретный плейлист. */
sealed interface LibraryRoute {
    data object Root : LibraryRoute

    data class Local(val playlistId: Long) : LibraryRoute

    data class Remote(val card: PlaylistCard) : LibraryRoute

    data object Liked : LibraryRoute

    data object History : LibraryRoute

    data object Downloads : LibraryRoute
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val youtube: YouTubeRepository,
    val downloads: DownloadsRepository,
    val player: PlayerConnection,
) : ViewModel() {
    val playlists: StateFlow<List<PlaylistEntity>> =
        library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val liked: StateFlow<List<SongItem>> =
        library.liked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<SongItem>> =
        library.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloaded: StateFlow<List<SongItem>> =
        library.downloaded.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isSignedIn: StateFlow<Boolean> =
        account.isSignedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val accountName: StateFlow<String?> =
        account.accountName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _route = MutableStateFlow<LibraryRoute>(LibraryRoute.Root)
    val route: StateFlow<LibraryRoute> = _route.asStateFlow()

    private val _remoteSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val remoteSongs: StateFlow<List<SongItem>> = _remoteSongs.asStateFlow()

    private val _accountPlaylists = MutableStateFlow<List<PlaylistCard>>(emptyList())
    val accountPlaylists: StateFlow<List<PlaylistCard>> = _accountPlaylists.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _currentPlaylistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val currentPlaylistSongs: StateFlow<List<SongItem>> = _currentPlaylistSongs.asStateFlow()

    fun open(route: LibraryRoute) {
        _route.value = route
        when (route) {
            is LibraryRoute.Local ->
                viewModelScope.launch {
                    library.playlistSongs(route.playlistId).collect { _currentPlaylistSongs.value = it }
                }

            is LibraryRoute.Remote ->
                viewModelScope.launch {
                    _remoteSongs.value = emptyList()
                    _remoteSongs.value =
                        runCatching { youtube.playlistSongs(route.card.browseId) }
                            .onFailure { Timber.w(it, "Плейлист ${route.card.title} не открылся") }
                            .getOrDefault(emptyList())
                }

            else -> Unit
        }
    }

    fun back() {
        _route.value = LibraryRoute.Root
        _currentPlaylistSongs.value = emptyList()
        _remoteSongs.value = emptyList()
    }

    fun createPlaylist(name: String) = viewModelScope.launch { library.createPlaylist(name) }

    fun deletePlaylist(id: Long) = viewModelScope.launch {
        library.deletePlaylist(id)
        back()
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { library.renamePlaylist(id, name) }

    fun removeFromPlaylist(playlistId: Long, songId: String) =
        viewModelScope.launch { library.removeFromPlaylist(playlistId, songId) }

    fun play(songs: List<SongItem>, index: Int) = player.play(songs, index)

    fun download(song: SongItem) = downloads.download(song)

    fun downloadAll(songs: List<SongItem>) = downloads.downloadAll(songs)

    fun cancelDownload(songId: String) = downloads.cancel(songId)

    fun clearHistory() = viewModelScope.launch { library.clearHistory() }

    /** Подтягивает лайки и плейлисты аккаунта в локальную библиотеку. */
    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncError.value = null
            runCatching {
                _accountPlaylists.value = account.playlists()
                val liked = account.likedSongs()
                liked.forEach { library.saveSong(it) }
                liked
            }.onFailure {
                Timber.w(it, "Синхронизация не удалась")
                _syncError.value = "Не получилось забрать данные аккаунта: ${it.message}"
            }
            _syncing.value = false
        }
    }

    fun signOut() = viewModelScope.launch {
        account.signOut()
        _accountPlaylists.value = emptyList()
    }

    /**
     * Проверяет cookie и сообщает наверх, чем кончилось: `null` — вход прошёл,
     * иначе текст для пользователя. Экран входа закрывается только при успехе,
     * чтобы не терять уже введённое.
     */
    fun onSignedIn(cookie: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val result = account.signIn(cookie)
        result.fold(
            onSuccess = {
                onResult(null)
                sync()
            },
            onFailure = { onResult(it.message ?: "YouTube не принял вход") },
        )
    }
}
