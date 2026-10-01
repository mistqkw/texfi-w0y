package com.texfi.w0y.desktop

import com.texfi.w0y.data.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: String? = null,
    val thumb: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
) {
    fun toItem() = SongItem(id, title, artist, album, duration, thumb, artistId, albumId)
}

fun SongItem.stored() = StoredSong(id, title, artist, album, durationText, thumbnailUrl, artistId, albumId)

@Serializable
data class StoredPlaylist(val name: String, val songs: List<StoredSong> = emptyList())

@Serializable
data class Library(
    val liked: List<StoredSong> = emptyList(),
    val history: List<StoredSong> = emptyList(),
    val playlists: List<StoredPlaylist> = emptyList(),
    val queue: List<StoredSong> = emptyList(),
    val queueIndex: Int = 0,
    val cookie: String? = null,
    val accountName: String? = null,
    val accountAvatar: String? = null,
    val volume: Int = 100,
)

/** Всё, что приложение помнит, — одним JSON-файлом в ~/.local/share/w0y. */
class Store {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir: Path =
        Path.of(System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share", "w0y")
    private val file = dir.resolve("library.json")

    fun load(): Library =
        runCatching { json.decodeFromString<Library>(Files.readString(file)) }.getOrDefault(Library())

    @Synchronized
    fun save(library: Library) {
        runCatching {
            Files.createDirectories(dir)
            // Через временный файл: оборванная запись не должна убить библиотеку.
            val tmp = dir.resolve("library.json.tmp")
            Files.writeString(tmp, json.encodeToString(Library.serializer(), library))
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
