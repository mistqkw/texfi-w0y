package com.texfi.w0y.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import com.texfi.w0y.R
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore("account")

/**
 * Вход в аккаунт YouTube Music.
 *
 * Токена для сторонних клиентов у YouTube нет, поэтому вход — это cookie
 * из настоящей формы Google, которую пользователь заполняет сам в окне
 * приложения. Мы эту форму не читаем и пароль не видим: забираем только
 * cookie, которые браузерный движок сохранил после успешного входа.
 */
@Singleton
class AccountRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val innerTube: InnerTube,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val cookie: Flow<String?> = context.accountStore.data.map { it[Keys.COOKIE] }
    val isSignedIn: Flow<Boolean> = cookie.map { !it.isNullOrBlank() }
    val accountName: Flow<String?> = context.accountStore.data.map { it[Keys.NAME] }

    /** Аватарка аккаунта — та же, что у ника на YouTube. */
    val accountAvatar: Flow<String?> = context.accountStore.data.map { it[Keys.AVATAR] }

    init {
        // Сессия восстанавливается при старте процесса: иначе первый запрос
        // после перезапуска уходит без аккаунта и возвращает чужую выдачу.
        scope.launch { applyStoredSession() }
    }

    suspend fun applyStoredSession() {
        val stored = cookie.first()
        applySession(stored)
        if (!stored.isNullOrBlank()) refreshAccountInfo()
    }

    /**
     * Принимает cookie — из формы Google или вставленную вручную — и сразу
     * проверяет её живым запросом к аккаунту.
     *
     * Проверка тут не формальность: вручную скопированная строка легко
     * оказывается обрезанной или от другого домена, и без проверки
     * приложение молча считало бы себя авторизованным, а библиотека
     * приходила бы пустой. Неподошедшую cookie не сохраняем.
     */
    suspend fun signIn(rawCookie: String): Result<String?> {
        val sanitized = rawCookie.trim()
        if (!sanitized.contains("SAPISID")) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.account_no_sapisid)))
        }
        applySession(sanitized)
        val info = runCatching { fetchAccountInfo() }
        if (info.isFailure) {
            applySession(null)
            Timber.w(info.exceptionOrNull(), "Cookie не подошла")
            return Result.failure(info.exceptionOrNull() ?: IllegalStateException(context.getString(R.string.account_cookie_rejected)))
        }
        val fetched = info.getOrNull()
        context.accountStore.edit {
            it[Keys.COOKIE] = sanitized
            if (!fetched?.name.isNullOrBlank()) it[Keys.NAME] = fetched!!.name!! else it.remove(Keys.NAME)
            if (!fetched?.avatar.isNullOrBlank()) it[Keys.AVATAR] = fetched!!.avatar!! else it.remove(Keys.AVATAR)
        }
        return Result.success(fetched?.name)
    }

    suspend fun signOut() {
        context.accountStore.edit { it.clear() }
        applySession(null)
    }

    /** Лайкнутые треки из аккаунта — первая страница, для быстрых мест. */
    suspend fun likedSongs(): List<SongItem> = browseSongs("FEmusic_liked_videos")

    /** Выдача, прочитанная целиком или нет: обрыв нельзя путать с концом списка. */
    data class Paged<T>(val items: List<T>, val complete: Boolean)

    /**
     * Все лайки аккаунта по страницам. Признак полноты нужен синхронизации:
     * по оборванному списку нельзя решать, что трек разлайкали.
     */
    suspend fun allLikedSongs(): Paged<SongItem> =
        paged("FEmusic_liked_videos", LIKED_PAGES) { page, _ -> YtJson.songs(page) }

    /** Плейлисты аккаунта, включая подписанные и альбомы, со всеми страницами. */
    suspend fun allPlaylists(): Paged<PlaylistCard> =
        paged("FEmusic_liked_playlists", PLAYLIST_PAGES) { page, _ -> YtJson.playlistCards(page) }

    /** Плейлисты аккаунта, включая подписанные. */
    suspend fun playlists(): List<PlaylistCard> = allPlaylists().items

    private suspend fun <T> paged(
        browseId: String,
        maxPages: Int,
        parse: (JsonObject, Boolean) -> List<T>,
    ): Paged<T> = withContext(Dispatchers.IO) {
        val first =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, setLogin = true)
                .body<JsonObject>()
        val collected = parse(first, true).toMutableList()
        var token = YtJson.continuation(first)
        var page = 1
        var complete = true
        while (token != null) {
            if (page >= maxPages) {
                complete = false
                break
            }
            val next =
                runCatching {
                    innerTube
                        .browse(client = YouTubeClient.WEB_REMIX, browseId = null, continuation = token, setLogin = true)
                        .body<JsonObject>()
                }.getOrNull()
            if (next == null) {
                complete = false
                break
            }
            val more = parse(next, false)
            if (more.isEmpty()) break
            collected += more
            token = YtJson.continuation(next)
            page++
        }
        Paged(collected, complete)
    }

    /** Какие лайки были в аккаунте при прошлой сверке; null — сверки ещё не было. */
    suspend fun likesBase(): Set<String>? =
        context.accountStore.data.first()[Keys.LIKES_BASE]
            ?.lineSequence()
            ?.filter { it.isNotEmpty() }
            ?.toSet()

    suspend fun setLikesBase(ids: Collection<String>) {
        context.accountStore.edit { it[Keys.LIKES_BASE] = ids.joinToString("\n") }
    }

    /**
     * Плейлисты, уже приведённые к составу аккаунта после ошибки разбора,
     * из-за которой в них попадали рекомендации (до 0.0.2 beta-2).
     */
    suspend fun cleanedPlaylists(): Set<String> =
        context.accountStore.data.first()[Keys.CLEANED]?.lineSequence()?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    suspend fun markPlaylistCleaned(remoteId: String) {
        context.accountStore.edit { prefs ->
            val now = prefs[Keys.CLEANED]?.lineSequence()?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            prefs[Keys.CLEANED] = (now + remoteId).joinToString("\n")
        }
    }

    suspend fun history(): List<SongItem> = browseSongs("FEmusic_history")

    private suspend fun browseSongs(browseId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, setLogin = true)
                .body<JsonObject>()
        YtJson.songs(response)
    }

    private fun applySession(cookie: String?) {
        innerTube.replaceSession(
            cookie = cookie,
            visitorData = null,
            dataSyncId = null,
            authUser = "0",
            useLoginForBrowse = !cookie.isNullOrBlank(),
        )
    }

    private suspend fun refreshAccountInfo() {
        val info =
            runCatching { fetchAccountInfo() }
                .onFailure { Timber.w(it, "Данные аккаунта не получены") }
                .getOrNull() ?: return
        context.accountStore.edit {
            if (!info.name.isNullOrBlank()) it[Keys.NAME] = info.name
            if (!info.avatar.isNullOrBlank()) it[Keys.AVATAR] = info.avatar
        }
    }

    private data class AccountInfo(val name: String?, val avatar: String?)

    /**
     * Имя и аватарка владельца. Бросает, если YouTube не принял сессию, —
     * это и есть проверка cookie на входе.
     */
    private suspend fun fetchAccountInfo(): AccountInfo = withContext(Dispatchers.IO) {
        val menu = innerTube.accountMenu(YouTubeClient.WEB_REMIX).body<JsonObject>()
        val name = with(YtJson) { menu.findAll("accountName").firstOrNull()?.firstString("text") }
        AccountInfo(name, YtJson.accountAvatar(menu))
    }

    private object Keys {
        val COOKIE = stringPreferencesKey("cookie")
        val NAME = stringPreferencesKey("name")
        val AVATAR = stringPreferencesKey("avatar")
        val LIKES_BASE = stringPreferencesKey("likes_base")
        val CLEANED = stringPreferencesKey("playlists_cleaned_v1")
    }

    private companion object {
        /** По сто штук на страницу: две тысячи лайков и триста плейлистов. */
        const val LIKED_PAGES = 20
        const val PLAYLIST_PAGES = 6
    }
}
