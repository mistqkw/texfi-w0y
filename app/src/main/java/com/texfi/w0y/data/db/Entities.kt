package com.texfi.w0y.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.texfi.w0y.data.SongItem

/**
 * Трек, который приложение когда-либо видело: в плейлисте, в лайках,
 * в истории или в загрузках. Одна запись на трек, связи — отдельными
 * таблицами, иначе один и тот же трек хранился бы по разу на каждый список.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val downloadState: Int = DOWNLOAD_NONE,
) {
    fun toItem(): SongItem =
        SongItem(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationText = durationText,
            thumbnailUrl = thumbnailUrl,
        )

    companion object {
        const val DOWNLOAD_NONE = 0
        const val DOWNLOAD_QUEUED = 1
        const val DOWNLOAD_DONE = 2

        fun from(song: SongItem): SongEntity =
            SongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationText = song.durationText,
                thumbnailUrl = song.thumbnailUrl,
            )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Идентификатор плейлиста в аккаунте, если он пришёл синхронизацией. */
    val remoteId: String? = null,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId"), Index("playlistId", "position")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "history", indices = [Index("playedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
)
