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
    data class Local(val id: String) : Screen
    data object Liked : Screen
    data object History : Screen
    data object Downloaded : Screen
    data object Player : Screen
    data object Settings : Screen
}

/** Всё состояние приложения в одном месте: экран, библиотека, аккаунт, плеер. */
class AppState(val scope: CoroutineScope, val yt: Yt, private val store: Store) {
    var lib by mutableStateOf(store.load())
        private set
    val player = PlayerCtl(scope, yt, this)
    val downloads = Downloads(scope, yt, this)
    val lyrics = LyricsRepo()
    val recommender = Recommender(this, yt)
    val sync = AccountSync(this, yt)

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var shelves by mutableStateOf<List<Shelf>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)

    var searchQuery by mutableStateOf("")
    var lastSearched by mutableStateOf("")
    var searchResult by mutableStateOf<MixedResults?>(null)
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var accountAlbums by mutableStateOf<List<PlaylistCard>>(emptyList())
    var accountStatus by mutableStateOf<String?>(null)
    var loggingIn by mutableStateOf(false)

    val st: DSettings get() = lib.settings

    fun setSt(block: (DSettings) -> DSettings) {
        val before = st.language
        update { it.copy(settings = block(it.settings)) }
        applyTheme()
        if (st.language != before) {
            yt.setLanguage(st.language.tag)
            loadHome()
        }
    }

    private fun applyTheme() {
        ThemeState.mode = st.theme
        ThemeState.accent = st.accent
        ThemeState.custom = st.customAccent
    }

    val signedIn: Boolean get() = !lib.cookie.isNullOrBlank()
    val likedIds: Set<String> get() = lib.liked.mapTo(HashSet()) { it.id }

    fun start() {
        applyTheme()
        yt.setLanguage(st.language.tag)
        // Куда попасть при запуске: выбранная вкладка или та, где закрыли в прошлый раз.
        when (if (st.startTab == StartTab.LAST) st.lastTab else st.startTab) {
            StartTab.SEARCH -> { stack.clear(); stack.add(Screen.Search) }
            StartTab.LIBRARY -> { stack.clear(); stack.add(Screen.Library) }
            else -> Unit
        }
        lib.cookie?.let { yt.applySession(it) }
        player.start(lib.queue.map { it.toItem() }, if (lib.queue.isEmpty()) -1 else lib.queueIndex, lib.volume)
        mpris = Mpris(this, scope).takeIf { it.start() }
        loadHome()
        if (signedIn) syncAccount(force = true)
    }

    private var mpris: Mpris? = null

    fun shutdown() {
        mpris?.stop()
        player.shutdown()
        store.save(lib)
    }

    fun update(block: (Library) -> Library) {
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
        when (screen) {
            Screen.Home -> StartTab.HOME
            Screen.Search -> StartTab.SEARCH
            Screen.Library -> StartTab.LIBRARY
            else -> null
        }?.let { t -> if (st.lastTab != t) update { it.copy(settings = it.settings.copy(lastTab = t)) } }
        if (screen == Screen.Library && signedIn) syncAccount(force = false)
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
                .onSuccess { list -> shelves = list.map { it.copy(songs = visible(it.songs)) } }
                .onFailure { homeError = "Не удалось загрузить: ${it.message}" }
            homeLoading = false
        }
    }

    private var searchJob: Job? = null

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        lastSearched = q
        if (st.saveSearchHistory) update { it.copy(searchHistory = (listOf(q) + it.searchHistory.filterNot { h -> h.equals(q, true) }).take(15)) }
        searchJob?.cancel()
        searchJob =
            scope.launch {
                searching = true
                searchError = null
                runCatching { yt.search(q) }
                    .onSuccess { searchResult = it.copy(songs = visible(it.songs)) }
                    .onFailure { searchError = "Поиск не удался: ${it.message}" }
                searching = false
            }
    }

    // ---- библиотека
    fun remember(song: SongItem) {
        if (!st.keepHistory) return
        val artist = song.artist.substringBefore(',').trim().lowercase()
        update {
            it.copy(
                history = (listOf(song.stored()) + it.history.filterNot { h -> h.id == song.id }).take(300),
                artistPlays = if (artist.isBlank()) it.artistPlays else it.artistPlays + (artist to ((it.artistPlays[artist] ?: 0) + 1)),
            )
        }
    }

    fun toggleLike(song: SongItem) {
        val liked = song.id !in likedIds
        update {
            it.copy(liked = if (liked) listOf(song.stored()) + it.liked else it.liked.filterNot { s -> s.id == song.id })
        }
        if (mirror) scope.launch { runCatching { yt.like(song.id, liked) } }
        if (liked && st.autoDownloadLiked) downloads.download(song)
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
        } ?: com.texfi.w0y.data.SoundProfile(st.speed, st.pitch, st.reverb)

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

    /** Прячет помеченные «E» записи, если так выбрано в настройках. */
    fun visible(songs: List<SongItem>): List<SongItem> = if (st.hideExplicit) songs.filterNot { it.explicit } else songs

    val mirror: Boolean get() = signedIn && st.syncPlaylists

    fun forgetSearch(query: String) = update { it.copy(searchHistory = it.searchHistory - query) }

    fun saveVolume(volume: Int) = update { it.copy(volume = volume) }

    fun playlist(id: String): StoredPlaylist? = lib.playlists.firstOrNull { it.id == id }

    private fun mutatePlaylist(id: String, block: (StoredPlaylist) -> StoredPlaylist) =
        update { l -> l.copy(playlists = l.playlists.map { if (it.id == id) block(it) else it }) }

    /** Куда можно добавлять: чужие плейлисты из библиотеки аккаунта YouTube править не даёт. */
    val editablePlaylists: List<StoredPlaylist> get() = lib.playlists.filter { it.remoteId == null || it.editable }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val pl = StoredPlaylist(name = clean)
        update { it.copy(playlists = it.playlists + pl) }
        // В аккаунт — следом: плейлист на телефоне не ждёт сети.
        if (mirror) {
            scope.launch {
                runCatching { yt.createPlaylist(clean) }.getOrNull()?.let { rid -> mutatePlaylist(pl.id) { it.copy(remoteId = rid) } }
            }
        }
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim()
        val pl = playlist(id) ?: return
        if (clean.isEmpty() || !pl.editable) return
        mutatePlaylist(id) { it.copy(name = clean) }
        if (mirror && pl.remoteId != null) scope.launch { runCatching { yt.renameRemote(pl.remoteId, clean) } }
    }

    fun deletePlaylist(id: String) {
        val pl = playlist(id)
        update { it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) }
        back()
        val rid = pl?.remoteId
        if (rid != null && mirror) {
            // Чужой плейлист не удаляют, а убирают из библиотеки.
            scope.launch { runCatching { if (pl.editable) yt.deleteRemote(rid) else yt.unsaveRemote(rid) } }
        }
    }

    fun addToPlaylist(id: String, song: SongItem) {
        val pl = playlist(id) ?: return
        if (!pl.editable || pl.songs.any { it.id == song.id }) return
        mutatePlaylist(id) { it.copy(songs = it.songs + song.stored()) }
        if (mirror && pl.remoteId != null) scope.launch { runCatching { yt.addToRemote(pl.remoteId, song.id) } }
    }

    fun removeFromPlaylist(id: String, songId: String) {
        val pl = playlist(id) ?: return
        if (!pl.editable) return
        mutatePlaylist(id) { it.copy(songs = it.songs.filterNot { s -> s.id == songId }) }
        val rid = pl.remoteId
        if (mirror && rid != null) {
            scope.launch {
                // Убрать трек из плейлиста YouTube можно только зная его место в нём.
                val place = yt.fetchPlaylist(rid, 30)?.setVideoIds?.get(songId) ?: return@launch
                runCatching { yt.removeFromRemote(rid, songId, place) }
            }
        }
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
                    syncAccount(force = true)
                }.onFailure {
                    yt.applySession(null)
                    accountStatus = "YouTube не принял cookie: ${it.message}"
                }
        }
    }

    /** Вход через окно Google в браузере (см. GoogleLogin). */
    fun signInWithGoogle() {
        if (loggingIn) return
        loggingIn = true
        scope.launch {
            val result = GoogleLogin.signIn { accountStatus = it }
            loggingIn = false
            result.onSuccess { cookie ->
                if (cookie == null) accountStatus = "Окно входа закрыли раньше, чем вход завершился." else signIn(cookie)
            }.onFailure { accountStatus = it.message ?: "Вход не удался" }
        }
    }

    fun signOut() {
        yt.applySession(null)
        accountAlbums = emptyList()
        update { it.copy(cookie = null, accountName = null, accountAvatar = null, likesBase = null) }
    }

    /** Двусторонняя сверка плейлистов и лайков с аккаунтом (см. AccountSync). */
    fun syncAccount(force: Boolean = true) {
        if (!signedIn) return
        scope.launch { sync.run(force) }
    }
}
