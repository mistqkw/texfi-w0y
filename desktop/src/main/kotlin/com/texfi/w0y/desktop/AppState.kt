package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.MixedResults
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.Shelf
import com.texfi.w0y.data.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Queue : Screen
    data class Album(val browseId: String, val title: String, val thumb: String?) : Screen
    data class Artist(val browseId: String, val name: String, val thumb: String?) : Screen
    data class Remote(val browseId: String, val title: String, val thumb: String?) : Screen
    data class Local(val index: Int) : Screen
    data object Liked : Screen
    data object History : Screen
    data object Downloaded : Screen
    data object Player : Screen
}

/** Всё состояние приложения в одном месте: экран, библиотека, аккаунт, плеер. */
class AppState(val scope: CoroutineScope, val yt: Yt, private val store: Store) {
    var lib by mutableStateOf(store.load())
        private set
    val player = PlayerCtl(scope, yt, this)
    val downloads = Downloads(scope, yt, this)
    val lyrics = LyricsRepo()

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var shelves by mutableStateOf<List<Shelf>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)

    var searchQuery by mutableStateOf("")
    var searchResult by mutableStateOf<MixedResults?>(null)
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var accountPlaylists by mutableStateOf<List<PlaylistCard>>(emptyList())
    var accountStatus by mutableStateOf<String?>(null)
    var syncing by mutableStateOf(false)

    val signedIn: Boolean get() = !lib.cookie.isNullOrBlank()
    val likedIds: Set<String> get() = lib.liked.mapTo(HashSet()) { it.id }

    fun start() {
        lib.cookie?.let { yt.applySession(it) }
        player.start(lib.queue.map { it.toItem() }, if (lib.queue.isEmpty()) -1 else lib.queueIndex, lib.volume)
        loadHome()
        if (signedIn) syncAccount()
    }

    fun shutdown() {
        player.shutdown()
        store.save(lib)
    }

    private fun update(block: (Library) -> Library) {
        lib = block(lib)
        store.save(lib)
    }

    // ---- навигация
    fun open(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun tab(screen: Screen) {
        stack.clear()
        stack.add(screen)
        if (screen == Screen.Library && signedIn) syncAccount()
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // ---- главная и поиск
    fun loadHome() {
        if (homeLoading) return
        scope.launch {
            homeLoading = true
            homeError = null
            runCatching { yt.home() }
                .onSuccess { shelves = it }
                .onFailure { homeError = "Не удалось загрузить: ${it.message}" }
            homeLoading = false
        }
    }

    private var searchJob: Job? = null

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob =
            scope.launch {
                searching = true
                searchError = null
                runCatching { yt.search(q) }
                    .onSuccess { searchResult = it }
                    .onFailure { searchError = "Поиск не удался: ${it.message}" }
                searching = false
            }
    }

    // ---- библиотека
    fun remember(song: SongItem) {
        update { it.copy(history = (listOf(song.stored()) + it.history.filterNot { h -> h.id == song.id }).take(300)) }
    }

    fun toggleLike(song: SongItem) {
        val liked = song.id !in likedIds
        update {
            it.copy(liked = if (liked) listOf(song.stored()) + it.liked else it.liked.filterNot { s -> s.id == song.id })
        }
        if (signedIn) scope.launch { runCatching { yt.like(song.id, liked) } }
    }

    private var queueSave: Job? = null

    fun saveQueue(queue: List<SongItem>, index: Int) {
        queueSave?.cancel()
        queueSave =
            scope.launch {
                delay(400)
                update { it.copy(queue = queue.map { s -> s.stored() }, queueIndex = index) }
            }
    }

    fun soundOf(id: String): com.texfi.w0y.data.SoundProfile =
        lib.sound[id]?.let {
            com.texfi.w0y.data.SoundProfile(
                it.speed,
                it.pitch,
                runCatching { com.texfi.w0y.data.Reverb.valueOf(it.reverb) }.getOrDefault(com.texfi.w0y.data.Reverb.OFF),
            )
        } ?: com.texfi.w0y.data.SoundProfile.Plain

    fun saveSound(id: String, profile: com.texfi.w0y.data.SoundProfile) = update {
        it.copy(
            sound = if (profile.isPlain) it.sound - id else it.sound + (id to StoredSound(profile.speed, profile.pitch, profile.reverb.name)),
        )
    }

    fun markDownloaded(song: SongItem, path: String) =
        update { it.copy(downloads = it.downloads + (song.id to StoredDownload(path, song.stored()))) }

    fun unmarkDownloaded(id: String) = update { it.copy(downloads = it.downloads - id) }

    fun downloadedPath(id: String): String? =
        lib.downloads[id]?.path?.takeIf { java.io.File(it).isFile }

    fun saveVolume(volume: Int) = update { it.copy(volume = volume) }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) update { it.copy(playlists = it.playlists + StoredPlaylist(clean)) }
    }

    fun deletePlaylist(index: Int) {
        update { it.copy(playlists = it.playlists.filterIndexed { i, _ -> i != index }) }
        back()
    }

    fun addToPlaylist(index: Int, song: SongItem) = update {
        val list = it.playlists.toMutableList()
        val pl = list.getOrNull(index) ?: return@update it
        if (pl.songs.none { s -> s.id == song.id }) list[index] = pl.copy(songs = pl.songs + song.stored())
        it.copy(playlists = list)
    }

    fun removeFromPlaylist(index: Int, songId: String) = update {
        val list = it.playlists.toMutableList()
        val pl = list.getOrNull(index) ?: return@update it
        list[index] = pl.copy(songs = pl.songs.filterNot { s -> s.id == songId })
        it.copy(playlists = list)
    }

    // ---- аккаунт
    fun signIn(cookie: String) {
        val clean = cookie.trim().removePrefix("Cookie:").trim()
        if (!clean.contains("SAPISID")) {
            accountStatus = "В cookie нет SAPISID: скопируй строку Cookie целиком."
            return
        }
        scope.launch {
            accountStatus = "Проверяю…"
            yt.applySession(clean)
            runCatching { yt.accountInfo() }
                .onSuccess { (name, avatar) ->
                    update { it.copy(cookie = clean, accountName = name, accountAvatar = avatar) }
                    accountStatus = null
                    syncAccount()
                }.onFailure {
                    yt.applySession(null)
                    accountStatus = "YouTube не принял cookie: ${it.message}"
                }
        }
    }

    fun signOut() {
        yt.applySession(null)
        accountPlaylists = emptyList()
        update { it.copy(cookie = null, accountName = null, accountAvatar = null) }
    }

    /** Лайки и плейлисты аккаунта подтягиваются при входе в «Моё». Лайки только добавляются. */
    fun syncAccount() {
        if (syncing || !signedIn) return
        scope.launch {
            syncing = true
            runCatching {
                accountPlaylists = yt.accountPlaylists()
                val remote = yt.likedSongs()
                val have = likedIds
                val fresh = remote.filter { it.id !in have }
                if (fresh.isNotEmpty()) update { it.copy(liked = it.liked + fresh.map { s -> s.stored() }) }
            }.onFailure { accountStatus = "Сверка не удалась: ${it.message}" }
            syncing = false
        }
    }
}
