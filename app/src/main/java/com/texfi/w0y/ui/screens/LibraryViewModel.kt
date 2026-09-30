package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.R
import com.texfi.w0y.data.AccountRepository
import com.texfi.w0y.data.AccountSync
import com.texfi.w0y.data.DialHiddenRepository
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.YtPlaylistSync
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
import kotlinx.coroutines.flow.map
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
    private val sync: YtPlaylistSync,
    private val accountSync: AccountSync,
    val downloads: DownloadsRepository,
    private val playback: PlaybackStarter,
    val player: PlayerConnection,
    private val dialHidden: DialHiddenRepository,
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
    val accountAvatar: StateFlow<String?> =
        account.accountAvatar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Обложки плейлистов: своя из аккаунта или картинка первого трека. */
    val playlistCovers: StateFlow<Map<Long, String>> =
        library.playlistCovers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Быстрый набор: сначала закреплённое вручную, следом — то, что
     * слушается чаще всего. Закреплённое не дублируется в хвосте, иначе
     * один и тот же трек занимал бы две плитки из девяти.
     */
    private val rawDial: kotlinx.coroutines.flow.Flow<List<DialItem>> =
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
                // С запасом: убранные плитки вычитаются ниже, и на их место
                // должны встать следующие кандидаты, а не пустые клетки.
                .take(SPEED_DIAL_SIZE + SPEED_DIAL_SPARE)
        }

    /** Быстрый набор без временно убранных плиток; просроченные скрытия уже не считаются. */
    val speedDial: StateFlow<List<DialItem>> =
        combine(rawDial, dialHidden.hidden) { items, hidden ->
            val now = System.currentTimeMillis()
            val gone = hidden.filter { it.untilMs > now }.map { it.kind to it.id }.toSet()
            items.filterNot { (it.kind to it.id) in gone }.take(SPEED_DIAL_SIZE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Убирает плитку из набора на время. Закреплённая сначала открепляется —
     * иначе она вернулась бы в начало набора, как только скрытие кончится.
     */
    fun hideDial(item: DialItem) = viewModelScope.launch {
        if (item.pinned) library.togglePin(item.kind, item.id, item.title, item.subtitle, item.thumbnailUrl)
        dialHidden.hide(item.kind, item.id, item.title)
    }

    /** Отмена «убрать»: плитка возвращается на место, закрепление тоже. */
    fun undoHideDial(item: DialItem) = viewModelScope.launch {
        dialHidden.restore(item.kind, item.id)
        if (item.pinned) library.togglePin(item.kind, item.id, item.title, item.subtitle, item.thumbnailUrl)
    }

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

    /** Сохранённые альбомы аккаунта: плейлисты аккаунта живут среди обычных. */
    val accountAlbums: StateFlow<List<PlaylistCard>> = accountSync.albums

    val syncing: StateFlow<Boolean> = accountSync.running

    val syncError: StateFlow<String?> =
        accountSync.error
            .map { it?.let { message -> context.getString(R.string.library_sync_failed, message) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Последняя неудачная запись плейлиста в аккаунт — её видно в библиотеке. */
    val mirrorFailure: StateFlow<String?> = sync.failure

    private val _pushResult = MutableStateFlow<String?>(null)

    /** Чем кончилась ручная догрузка плейлиста в аккаунт. */
    val pushResult: StateFlow<String?> = _pushResult.asStateFlow()

    fun clearMirrorFailure() = sync.clearFailure()

    private val _currentPlaylistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val currentPlaylistSongs: StateFlow<List<SongItem>> = _currentPlaylistSongs.asStateFlow()

    fun open(route: LibraryRoute) {
        _route.value = route
        when (route) {
            is LibraryRoute.Local -> {
                viewModelScope.launch {
                    library.playlistSongs(route.playlistId).collect { _currentPlaylistSongs.value = it }
                }
                // Состав сверяется с аккаунтом при открытии: правили его там — увидим сразу.
                viewModelScope.launch { accountSync.refreshPlaylist(route.playlistId) }
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

    /**
     * Догрузить плейлист в аккаунт руками.
     *
     * Нужно для тех плейлистов, что появились до входа, и на случай, когда
     * YouTube не принял часть треков: молча оставлять расхождение —
     * то же самое, что врать о синхронизации.
     */
    fun pushPlaylist(id: Long) {
        _pushResult.value = null
        viewModelScope.launch {
            val added = library.pushPlaylist(id)
            _pushResult.value =
                when {
                    added == null -> context.getString(R.string.playlist_push_failed)
                    added == 0 -> context.getString(R.string.playlist_push_same)
                    else -> context.getString(R.string.playlist_push_done, added)
                }
        }
    }

    fun consumePushResult() {
        _pushResult.value = null
    }

    fun play(songs: List<SongItem>, index: Int) = playback.play(songs, index)

    fun download(song: SongItem) = downloads.download(song)

    fun downloadAll(songs: List<SongItem>) = downloads.downloadAll(songs)

    fun cancelDownload(songId: String) = downloads.cancel(songId)

    fun clearHistory() = viewModelScope.launch { library.clearHistory() }

    /**
     * Сверка с аккаунтом. Вход в «Моё» запускает её сам, без [force] и не
     * чаще раза в минуту; кнопка «обновить» — принудительная.
     */
    fun sync(force: Boolean = true) {
        viewModelScope.launch { accountSync.run(force) }
    }

    fun signOut() = viewModelScope.launch {
        account.signOut()
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

/** Запас кандидатов на случай скрытых плиток. */
private const val SPEED_DIAL_SPARE = 40
