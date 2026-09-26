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
    val artistId: String? = null,
    val albumId: String? = null,
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
            artistId = artistId,
            albumId = albumId,
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
                artistId = song.artistId,
                albumId = song.albumId,
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

/**
 * Закреплённая плитка на главной.
 *
 * Хранит не ссылку на трек, а самодостаточный снимок: закрепить можно
 * альбом или артиста, которых в локальной библиотеке вообще нет, и плитка
 * должна рисоваться без сети.
 */
@Entity(tableName = "pins")
data class PinEntity(
    /** «вид:идентификатор» — один объект нельзя закрепить дважды. */
    @PrimaryKey val key: String,
    val kind: String,
    val targetId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val pinnedAt: Long,
) {
    companion object {
        const val KIND_SONG = "song"
        const val KIND_ALBUM = "album"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_ARTIST = "artist"

        fun key(kind: String, targetId: String) = "$kind:$targetId"
    }
}

/** Трек вместе с числом прослушиваний — для «часто слушаешь». */
data class SongPlays(
    @androidx.room.Embedded val song: SongEntity,
    val plays: Int,
)

/** Исполнитель и сколько его слушали — сигнал вкуса для рекомендаций. */
data class ArtistPlays(
    val artist: String,
    val plays: Int,
)

/** Длительность трека и сколько раз его включали — сырьё для «итогов». */
data class DurationPlays(
    val durationText: String?,
    val plays: Int,
)
