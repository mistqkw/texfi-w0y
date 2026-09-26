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

    init {
        // Сессия восстанавливается при старте процесса: иначе первый запрос
        // после перезапуска уходит без аккаунта и возвращает чужую выдачу.
        scope.launch { applyStoredSession() }
    }

    suspend fun applyStoredSession() {
        val stored = cookie.first()
        applySession(stored)
        if (!stored.isNullOrBlank()) refreshAccountName()
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
        val name = runCatching { fetchAccountName() }
        if (name.isFailure) {
            applySession(null)
            Timber.w(name.exceptionOrNull(), "Cookie не подошла")
            return Result.failure(name.exceptionOrNull() ?: IllegalStateException(context.getString(R.string.account_cookie_rejected)))
        }
        context.accountStore.edit {
            it[Keys.COOKIE] = sanitized
            val fetched = name.getOrNull()
            if (!fetched.isNullOrBlank()) it[Keys.NAME] = fetched else it.remove(Keys.NAME)
        }
        return Result.success(name.getOrNull())
    }

    suspend fun signOut() {
        context.accountStore.edit { it.clear() }
        applySession(null)
    }

    /** Лайкнутые треки из аккаунта. */
    suspend fun likedSongs(): List<SongItem> = browseSongs("FEmusic_liked_videos")

    /** Плейлисты аккаунта, включая подписанные. */
    suspend fun playlists(): List<PlaylistCard> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_liked_playlists", setLogin = true)
                .body<JsonObject>()
        YtJson.playlistCards(response)
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

    private suspend fun refreshAccountName() {
        val name =
            runCatching { fetchAccountName() }
                .onFailure { Timber.w(it, "Имя аккаунта не получено") }
                .getOrNull()
        if (!name.isNullOrBlank()) {
            context.accountStore.edit { it[Keys.NAME] = name }
        }
    }

    /**
     * Имя владельца аккаунта. Бросает, если YouTube не принял сессию, —
     * это и есть проверка cookie на входе.
     */
    private suspend fun fetchAccountName(): String? = withContext(Dispatchers.IO) {
        val menu = innerTube.accountMenu(YouTubeClient.WEB_REMIX).body<JsonObject>()
        with(YtJson) { menu.findAll("accountName").firstOrNull()?.firstString("text") }
    }

    private object Keys {
        val COOKIE = stringPreferencesKey("cookie")
        val NAME = stringPreferencesKey("name")
    }
}
