package com.texfi.w0y.data

import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Всё, что приложение хранит у себя: плейлисты, лайки, история. */
@Singleton
class LibraryRepository @Inject constructor(
    private val dao: W0yDao,
    private val sync: YtPlaylistSync,
) {
    val playlists: Flow<List<PlaylistEntity>> = dao.playlists()
    val liked: Flow<List<SongItem>> = dao.likedSongs().map { list -> list.map(SongEntity::toItem) }
    val recent: Flow<List<SongItem>> = dao.recentSongs().map { list -> list.map(SongEntity::toItem) }
    val downloaded: Flow<List<SongItem>> =
        dao.songsWithDownloadState().map { list -> list.map(SongEntity::toItem) }

    /** Что слушается чаще всего — основа быстрого набора на главной. */
    val mostPlayed: Flow<List<SongItem>> =
        dao.mostPlayed().map { list -> list.map { it.song.toItem() } }

    val pins: Flow<List<PinEntity>> = dao.pins()

    fun playlist(id: Long) = dao.playlist(id)

    fun playlistSongs(id: Long): Flow<List<SongItem>> =
        dao.playlistSongs(id).map { list -> list.map(SongEntity::toItem) }

    fun playlistSize(id: Long) = dao.playlistSize(id)

    fun isLiked(id: String): Flow<Boolean> = dao.likedFlow(id).map { it == true }

    /**
     * Создаёт плейлист и, если включено зеркалирование, заводит такой же в
     * аккаунте. Сначала запись на телефон: если YouTube откажет, плейлист
     * всё равно останется, просто без отражения.
     */
    suspend fun createPlaylist(name: String): Long {
        val clean = name.trim()
        val id = dao.createPlaylist(PlaylistEntity(name = clean, createdAt = now()))
        if (sync.enabled()) dao.setRemoteId(id, sync.create(clean))
        return id
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        val clean = name.trim()
        dao.renamePlaylist(id, clean)
        remoteId(id)?.let { sync.rename(it, clean) }
    }

    suspend fun deletePlaylist(id: Long) {
        // Ссылку читаем до удаления: после него строки уже нет.
        val remote = remoteId(id)
        dao.deletePlaylist(id)
        remote?.let { sync.delete(it) }
    }

    suspend fun addToPlaylist(playlistId: Long, song: SongItem) {
        dao.addSongToPlaylist(playlistId, SongEntity.from(song), now())
        remoteId(playlistId)?.let { sync.add(it, song.id) }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) {
        dao.removeFromPlaylist(playlistId, songId)
        remoteId(playlistId)?.let { sync.remove(it, songId) }
    }

    /**
     * Ручная догрузка плейлиста в аккаунт: для тех, что появились до входа
     * или когда YouTube не принял часть треков. Возвращает, сколько
     * добавилось, или null — если не получилось.
     */
    suspend fun pushPlaylist(id: Long): Int? {
        if (!sync.enabled()) return null
        val playlist = dao.playlistOnce(id) ?: return null
        val remote = playlist.remoteId ?: sync.create(playlist.name)?.also { dao.setRemoteId(id, it) }
        remote ?: return null
        return sync.push(remote, dao.playlistSongIds(id))
    }

    /** Ищет по тому, что уже есть на телефоне. */
    suspend fun searchLocal(query: String): List<SongItem> =
        if (query.isBlank()) emptyList() else dao.searchLocal(query.trim()).map(SongEntity::toItem)

    private suspend fun remoteId(playlistId: Long): String? =
        if (!sync.enabled()) null else dao.playlistOnce(playlistId)?.remoteId

    suspend fun toggleLike(song: SongItem): Boolean {
        val stored = dao.song(song.id)
        if (stored == null) dao.saveSongMeta(SongEntity.from(song))
        val liked = stored?.liked != true
        dao.setLiked(song.id, liked, if (liked) now() else null)
        return liked
    }

    /**
     * История пишется на старте трека, а не на его конце: пользователю
     * важнее «что я недавно включал», чем «что я дослушал».
     */
    suspend fun remember(song: SongItem) {
        // Слияние со старой записью — внутри saveSongMeta: из плеера трек
        // приходит без альбома и длительности, а лайк и загрузка вообще не
        // его дело.
        dao.saveSongMeta(SongEntity.from(song))
        dao.addHistory(HistoryEntity(songId = song.id, playedAt = now()))
    }

    suspend fun clearHistory() = dao.clearHistory()

    /**
     * Закрепляет или снимает плитку быстрого набора.
     *
     * Снимок хранится целиком: закрепить можно артиста или альбом, которых
     * в локальной библиотеке нет, а плитка должна рисоваться и без сети.
     */
    suspend fun togglePin(
        kind: String,
        targetId: String,
        title: String,
        subtitle: String? = null,
        thumbnailUrl: String? = null,
    ): Boolean {
        val key = PinEntity.key(kind, targetId)
        if (dao.isPinned(key) > 0) {
            dao.unpin(key)
            return false
        }
        dao.pin(
            PinEntity(
                key = key,
                kind = kind,
                targetId = targetId,
                title = title,
                subtitle = subtitle,
                thumbnailUrl = thumbnailUrl,
                pinnedAt = now(),
            ),
        )
        return true
    }

    suspend fun markDownload(songId: String, state: Int) = dao.setDownloadState(songId, state)

    suspend fun saveSong(song: SongItem) = dao.saveSongMeta(SongEntity.from(song))

    /**
     * Лайки из аккаунта в локальную библиотеку.
     *
     * Локальные лайки при этом не снимаются: синхронизация добавляет то,
     * что есть в аккаунте, а не заменяет собой то, что человек отметил на
     * телефоне. Иначе один вход в аккаунт стирал бы всё, что накопилось
     * без него.
     */
    suspend fun importLikes(songs: List<SongItem>) {
        val now = now()
        songs.forEach { song ->
            dao.saveSongMeta(SongEntity.from(song))
            dao.setLiked(song.id, true, now)
        }
    }

    private fun now() = System.currentTimeMillis()
}
