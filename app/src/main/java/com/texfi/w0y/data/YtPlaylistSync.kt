package com.texfi.w0y.data

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * Зеркалирование своих плейлистов в аккаунт YouTube.
 *
 * Правило одно: локальная библиотека — источник истины, аккаунт — её
 * отражение. Поэтому ни одна ошибка записи в YouTube не отменяет того,
 * что человек сделал на телефоне: плейлист остаётся, трек остаётся, а
 * о неудаче говорим прямо и даём кнопку «залить заново». Обратное
 * поведение (откатывать своё, потому что не ответил сервер) в офлайне
 * означало бы, что приложением нельзя пользоваться без сети.
 */
@Singleton
class YtPlaylistSync @Inject constructor(
    private val innerTube: InnerTube,
    private val account: AccountRepository,
    private val settings: SettingsRepository,
) {
    private val _failure = MutableStateFlow<String?>(null)

    /** Последняя неудачная запись в аккаунт — её показывает библиотека. */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    fun clearFailure() {
        _failure.value = null
    }

    /**
     * Зеркалить ли вообще. Без входа в аккаунт записывать некуда, и
     * настройка при этом остаётся включённой: она про желание, а не про
     * возможность.
     */
    suspend fun enabled(): Boolean =
        settings.settings.first().syncPlaylists && account.isSignedIn.first()

    /** Создаёт плейлист в аккаунте и возвращает его идентификатор. */
    suspend fun create(name: String): String? = attempt("create") {
        val response =
            innerTube.createPlaylist(YouTubeClient.WEB_REMIX, name).body<JsonObject>()
        YtJson.createdPlaylistId(response)
    }

    suspend fun add(remoteId: String, videoId: String): Boolean =
        attempt("add") {
            innerTube.addToPlaylist(YouTubeClient.WEB_REMIX, remoteId, videoId)
            true
        } ?: false

    /**
     * Убирает трек из плейлиста аккаунта.
     *
     * Одного videoId мало: один трек может лежать в плейлисте дважды, и
     * YouTube различает вхождения через setVideoId. Его приходится
     * вычитывать самим плейлистом — своего кэша у нас нет, а держать
     * его означало бы хранить чужое состояние и врать, когда плейлист
     * изменили с другого устройства.
     */
    suspend fun remove(remoteId: String, videoId: String): Boolean =
        attempt("remove") {
            val setVideoId = setVideoId(remoteId, videoId) ?: return@attempt false
            innerTube.removePlaylistSong(YouTubeClient.WEB_REMIX, remoteId, videoId, setVideoId)
            true
        } ?: false

    suspend fun rename(remoteId: String, name: String): Boolean =
        attempt("rename") {
            innerTube.renamePlaylist(YouTubeClient.WEB_REMIX, remoteId, name)
            true
        } ?: false

    suspend fun delete(remoteId: String): Boolean =
        attempt("delete") {
            innerTube.deletePlaylist(YouTubeClient.WEB_REMIX, remoteId)
            true
        } ?: false

    /**
     * Догоняет аккаунт до локального плейлиста: докладывает то, чего в нём
     * нет. Лишнего не убирает — в аккаунте могли добавить трек с другого
     * устройства, и удалять его нашей «синхронизацией» было бы грубо.
     */
    suspend fun push(remoteId: String, songIds: List<String>): Int? = attempt("push") {
        val present = remoteSongIds(remoteId)
        val missing = songIds.filterNot { it in present }
        missing.forEach { innerTube.addToPlaylist(YouTubeClient.WEB_REMIX, remoteId, it) }
        missing.size
    }

    private suspend fun setVideoId(remoteId: String, videoId: String): String? =
        withContext(Dispatchers.IO) {
            val response =
                innerTube
                    .browse(
                        client = YouTubeClient.WEB_REMIX,
                        browseId = browseId(remoteId),
                        setLogin = true,
                    ).body<JsonObject>()
            YtJson.setVideoIds(response)[videoId]
        }

    private suspend fun remoteSongIds(remoteId: String): Set<String> =
        withContext(Dispatchers.IO) {
            val response =
                innerTube
                    .browse(
                        client = YouTubeClient.WEB_REMIX,
                        browseId = browseId(remoteId),
                        setLogin = true,
                    ).body<JsonObject>()
            YtJson.songs(response).map { it.id }.toSet()
        }

    /** Читать плейлист нужно с приставкой «VL», менять — без неё. */
    private fun browseId(remoteId: String) =
        if (remoteId.startsWith("VL")) remoteId else "VL$remoteId"

    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? =
        runCatching { withContext(Dispatchers.IO) { block() } }
            .onSuccess { _failure.value = null }
            .onFailure { error ->
                Timber.w(error, "Синхронизация плейлиста ($what) не удалась")
                _failure.value = error.message ?: error::class.simpleName
            }.getOrNull()
}
