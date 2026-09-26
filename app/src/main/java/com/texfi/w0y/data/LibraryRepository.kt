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

    suspend fun createPlaylist(name: String): Long =
        dao.createPlaylist(PlaylistEntity(name = name.trim(), createdAt = now()))

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())

    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, song: SongItem) =
        dao.addSongToPlaylist(playlistId, SongEntity.from(song), now())

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        dao.removeFromPlaylist(playlistId, songId)

    suspend fun toggleLike(song: SongItem): Boolean {
        val stored = dao.song(song.id)
        if (stored == null) dao.upsertSong(SongEntity.from(song))
        val liked = stored?.liked != true
        dao.setLiked(song.id, liked, if (liked) now() else null)
        return liked
    }

    /**
     * История пишется на старте трека, а не на его конце: пользователю
     * важнее «что я недавно включал», чем «что я дослушал».
     */
    suspend fun remember(song: SongItem) {
        val stored = dao.song(song.id)
        val merged =
            stored?.copy(
                title = song.title.ifBlank { stored.title },
                artist = song.artist.ifBlank { stored.artist },
                thumbnailUrl = song.thumbnailUrl ?: stored.thumbnailUrl,
            ) ?: SongEntity.from(song)
        dao.upsertSong(merged)
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

    suspend fun saveSong(song: SongItem) = dao.upsertSong(SongEntity.from(song))

    private fun now() = System.currentTimeMillis()
}
