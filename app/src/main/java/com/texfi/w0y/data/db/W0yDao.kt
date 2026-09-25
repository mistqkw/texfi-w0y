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

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
