package com.texfi.w0y.playback

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.texfi.w0y.data.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Чем кончилась выгрузка: сколько треков легло в папку, сколько уже лежало, сколько не вышло. */
data class ExportResult(val saved: Int, val existed: Int, val failed: Int)

/**
 * Выгрузка скачанного в обычную папку «Музыка/w0y music».
 *
 * Скачанное лежит в кэше плеера кусками без имён, и открыть его нечем. Здесь
 * трек читается из кэша тем же источником данных, что и воспроизведение
 * (только кэш, в сеть не ходим), и пишется одним файлом через MediaStore: на
 * Android 10+ разрешений для этого не нужно, а другие плееры и проводник
 * видят файл сразу. Формат остаётся тем, что отдал YouTube (m4a или
 * webm/opus): перекодирования нет, поэтому нет потерь и нет ожидания.
 *
 * Теги title/artist/album попадают в медиатеку телефона, а сам файл
 * несёт их в имени «Исполнитель - Название»: встроить теги внутрь
 * контейнера без отдельной библиотеки нельзя.
 */
@Singleton
@OptIn(UnstableApi::class)
class MusicExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("download") private val cache: SimpleCache,
) {
    suspend fun export(songs: List<SongItem>): ExportResult = withContext(Dispatchers.IO) {
        var saved = 0
        var existed = 0
        var failed = 0
        songs.forEach { song ->
            runCatching { exportOne(song) }
                .onSuccess { if (it) saved++ else existed++ }
                .onFailure {
                    Timber.w(it, "Не выгрузился трек %s", song.id)
                    failed++
                }
        }
        ExportResult(saved, existed, failed)
    }

    /** true — файл записан, false — такой уже лежит в папке. */
    private fun exportOne(song: SongItem): Boolean {
        val source =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(null)
                .createDataSource()
        source.open(DataSpec.Builder().setUri(w0yUri(song)).setKey(song.id).build())
        try {
            val buffer = ByteArray(BUFFER)
            val first = source.read(buffer, 0, buffer.size)
            require(first > 0) { "в кэше нет данных трека" }
            val format = detect(buffer, first)
            val name = fileName(song, format.extension)
            val sink = open(song, name, format.mime) ?: return false
            var complete = false
            try {
                sink.stream.write(buffer, 0, first)
                while (true) {
                    val n = source.read(buffer, 0, buffer.size)
                    if (n < 0) break
                    sink.stream.write(buffer, 0, n)
                }
                complete = true
            } finally {
                sink.stream.close()
                sink.finish(complete)
            }
            return true
        } finally {
            source.close()
        }
    }

    private class Format(val extension: String, val mime: String)

    private fun detect(head: ByteArray, size: Int): Format {
        fun at(i: Int) = if (i < size) head[i].toInt() and 0xFF else -1
        return when {
            at(4) == 'f'.code && at(5) == 't'.code && at(6) == 'y'.code && at(7) == 'p'.code ->
                Format("m4a", "audio/mp4")

            at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> Format("webm", "audio/webm")
            at(0) == 'O'.code && at(1) == 'g'.code && at(2) == 'g'.code && at(3) == 'S'.code ->
                Format("ogg", "audio/ogg")

            else -> Format("m4a", "audio/mp4")
        }
    }

    private fun fileName(song: SongItem, extension: String): String {
        val base = "${song.artist} - ${song.title}".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120)
        return "${base.ifBlank { song.id }}.$extension"
    }

    private class Sink(val stream: OutputStream, val finish: (Boolean) -> Unit)

    /** Открывает файл для записи или null, если в папке уже лежит файл с таким именем. */
    private fun open(song: SongItem, name: String, mime: String): Sink? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openMediaStore(song, name, mime) else openLegacy(name)

    private fun openMediaStore(song: SongItem, name: String, mime: String): Sink? {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = "${Environment.DIRECTORY_MUSIC}/$FOLDER"
        resolver
            .query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(name, "$relative/"),
                null,
            )?.use { if (it.moveToFirst()) return null }
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                song.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        val uri = resolver.insert(collection, values) ?: error("MediaStore не создал запись")
        val stream = resolver.openOutputStream(uri) ?: error("нельзя открыть файл на запись")
        return Sink(stream) { complete ->
            if (complete) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
        }
    }

    /** До Android 10 — обычный файл в публичной папке; нужно разрешение на запись. */
    @Suppress("DEPRECATION")
    private fun openLegacy(name: String): Sink? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
        check(dir.exists() || dir.mkdirs()) { "нельзя создать папку $FOLDER" }
        val file = File(dir, name)
        if (file.exists()) return null
        return Sink(FileOutputStream(file)) { complete -> if (!complete) file.delete() }
    }

    private companion object {
        const val FOLDER = "w0y music"
        const val BUFFER = 64 * 1024
    }
}
