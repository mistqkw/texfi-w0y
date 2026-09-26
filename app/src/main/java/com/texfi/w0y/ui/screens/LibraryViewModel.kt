package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.R
import com.texfi.w0y.data.AccountRepository
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
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
import kotlinx.coroutines.flow.combine
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

    data object Stats : LibraryRoute
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val youtube: YouTubeRepository,
    val downloads: DownloadsRepository,
    private val playback: PlaybackStarter,
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

    /**
     * Быстрый набор: сначала закреплённое вручную, следом — то, что
     * слушается чаще всего. Закреплённое не дублируется в хвосте, иначе
     * один и тот же трек занимал бы две плитки из девяти.
     */
    val speedDial: StateFlow<List<DialItem>> =
        combine(
            library.pins,
            library.mostPlayed,
            library.liked,
            library.recent,
            library.playlists,
        ) { pins, played, liked, recent, playlists ->
            val pinned =
                pins.map { pin ->
                    DialItem(
                        kind = pin.kind,
                        id = pin.targetId,
                        title = pin.title,
                        subtitle = pin.subtitle,
                        thumbnailUrl = pin.thumbnailUrl,
                        pinned = true,
                        // Закреплённый трек играет из самого снимка: он мог
                        // быть закреплён из поиска и в локальной базе не лежать.
                        song =
                            if (pin.kind == PinEntity.KIND_SONG) {
                                SongItem(
                                    id = pin.targetId,
                                    title = pin.title,
                                    artist = pin.subtitle.orEmpty(),
                                    thumbnailUrl = pin.thumbnailUrl,
                                )
                            } else {
                                null
                            },
                    )
                }
            // Набор собирается из всего своего: закреплённое, частое, лайки,
            // недавнее, свои плейлисты. На одной истории страницы получались
            // полупустыми — листать было нечего.
            val songs =
                (played + liked + recent)
                    .distinctBy { it.id }
                    .map { song ->
                        DialItem(
                            kind = PinEntity.KIND_SONG,
                            id = song.id,
                            title = song.title,
                            subtitle = song.artist.takeIf { it.isNotBlank() },
                            thumbnailUrl = song.thumbnailUrl,
                            song = song,
                        )
                    }
            val localPlaylists =
                playlists.map { playlist ->
                    DialItem(
                        kind = PinEntity.KIND_PLAYLIST,
                        id = playlist.id.toString(),
                        title = playlist.name,
                        subtitle = context.getString(R.string.library_own_playlist),
                        localPlaylistId = playlist.id,
                    )
                }
            (pinned + songs + localPlaylists)
                .distinctBy { it.kind to it.id }
                .take(SPEED_DIAL_SIZE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Треки быстрого набора подряд — чтобы плитка запускала очередь, а не один трек. */
    fun playDial(item: DialItem) {
        val queue = speedDial.value.mapNotNull { it.song }
        val index = queue.indexOfFirst { it.id == item.id }
        if (index >= 0) playback.play(queue, index) else item.song?.let { playback.play(listOf(it), 0) }
    }

    fun togglePin(item: DialItem) = viewModelScope.launch {
        library.togglePin(item.kind, item.id, item.title, item.subtitle, item.thumbnailUrl)
    }

    /** Закрепляет трек из любого списка — например прямо из плеера. */
    fun pinSong(song: SongItem) = viewModelScope.launch {
        library.togglePin(
            kind = PinEntity.KIND_SONG,
            targetId = song.id,
            title = song.title,
            subtitle = song.artist.takeIf { it.isNotBlank() },
            thumbnailUrl = song.thumbnailUrl,
        )
    }

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

    fun play(songs: List<SongItem>, index: Int) = playback.play(songs, index)

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
                _syncError.value = context.getString(R.string.library_sync_failed, it.message.orEmpty())
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
            onFailure = { onResult(it.message ?: context.getString(R.string.library_login_rejected)) },
        )
    }
}

/** Плитка быстрого набора: трек, альбом, плейлист или артист. */
data class DialItem(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val pinned: Boolean = false,
    /** Заполнено только для треков: по нему плитка сразу играет. */
    val song: SongItem? = null,
    /** Заполнено для своих плейлистов: они открываются локально, а не через YouTube. */
    val localPlaylistId: Long? = null,
)

/** Пять страниц по девять плиток: столько влезает без прокрутки экрана. */
private const val SPEED_DIAL_PAGE = 9
private const val SPEED_DIAL_PAGES = 5
private const val SPEED_DIAL_SIZE = SPEED_DIAL_PAGE * SPEED_DIAL_PAGES
