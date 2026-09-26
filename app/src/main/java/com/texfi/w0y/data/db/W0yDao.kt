package com.texfi.w0y.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface W0yDao {
    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun likedFlow(id: String): Flow<Boolean?>

    @Query("UPDATE songs SET liked = :liked, likedAt = :likedAt WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, likedAt: Long?)

    @Query("UPDATE songs SET downloadState = :state WHERE id = :id")
    suspend fun setDownloadState(id: String, state: Int)

    @Query("SELECT * FROM songs WHERE downloadState = :state ORDER BY title")
    fun songsWithDownloadState(state: Int = SongEntity.DOWNLOAD_DONE): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :id")
    fun playlistSize(id: Long): Flow<Int>

    @Query(
        """
        SELECT songs.* FROM songs
        JOIN playlist_songs ON songs.id = playlist_songs.songId
        WHERE playlist_songs.playlistId = :playlistId
        ORDER BY playlist_songs.position
        """,
    )
    fun playlistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(entry: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: String)

    @Transaction
    suspend fun addSongToPlaylist(playlistId: Long, song: SongEntity, now: Long) {
        upsertSong(song)
        addToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                position = nextPosition(playlistId),
                addedAt = now,
            ),
        )
    }

    @Insert
    suspend fun addHistory(entry: HistoryEntity)

    @Query(
        """
        SELECT songs.* FROM songs
        JOIN history ON songs.id = history.songId
        GROUP BY songs.id
        ORDER BY MAX(history.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun recentSongs(limit: Int = 50): Flow<List<SongEntity>>

    /**
     * Что слушается чаще всего. Сортировка вторым ключом по свежести:
     * иначе два трека с равным счётом менялись бы местами при каждом
     * запросе, и плитки на главной прыгали бы без причины.
     */
    @Query(
        """
        SELECT songs.*, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        GROUP BY songs.id
        ORDER BY plays DESC, MAX(history.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun mostPlayed(limit: Int = 12): Flow<List<SongPlays>>

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM pins ORDER BY pinnedAt")
    fun pins(): Flow<List<PinEntity>>

    @Upsert
    suspend fun pin(pin: PinEntity)

    @Query("DELETE FROM pins WHERE key = :key")
    suspend fun unpin(key: String)

    @Query("SELECT COUNT(*) FROM pins WHERE key = :key")
    suspend fun isPinned(key: String): Int
}
