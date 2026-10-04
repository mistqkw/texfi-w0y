package com.texfi.w0y.playback

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** Чем кончилась выгрузка: сколько треков легло в папку, сколько уже лежало, сколько не вышло. */
data class ExportResult(val saved: Int, val existed: Int, val failed: Int)

/**
 * «Сохранить на устройство»: скачанное — в обычную папку, откуда его видят
 * другие плееры и компьютер.
 *
 * Папку выбирает сам человек через системный выбор (доступ только к ней,
 * без разрешения на всю память); не выбрал — «Музыка/w0y music» через
 * MediaStore. Трек читается из хранилища загрузок тем же источником, что и
 * воспроизведение (только хранилище, в сеть не ходим), и пишется одним
 * файлом в исходном формате — без перекодирования, без потерь и без
 * ожидания.
 *
 * Имя — «Исполнитель - Название». Дубли не плодятся: если файл с таким
 * именем уже лежит, трек считается сохранённым. Внутрь m4a кладутся
 * название, исполнитель, альбом и обложка ([Mp4Tagger]); WebM/Opus без
 * сторонней библиотеки так не разметить — у него теги только в медиатеке.
 */
@Singleton
@OptIn(UnstableApi::class)
class MusicExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("download") private val cache: SimpleCache,
    private val settings: SettingsRepository,
    private val okHttp: OkHttpClient,
) {
    suspend fun export(songs: List<SongItem>): ExportResult = withContext(Dispatchers.IO) {
        val tree = settings.settings.first().exportTreeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        var saved = 0
        var existed = 0
        var failed = 0
        songs.forEach { song ->
            runCatching { exportOne(song, tree) }
                .onSuccess { if (it) saved++ else existed++ }
                .onFailure {
                    Timber.w(it, "Не выгрузился трек %s", song.id)
                    failed++
                }
        }
        ExportResult(saved, existed, failed)
    }

    /** true — файл записан, false — такой уже лежит в папке. */
    private fun exportOne(song: SongItem, tree: Uri?): Boolean {
        val bytes = readAll(song)
        val format = detect(bytes)
        val name = fileName(song, format.extension)
        val body =
            if (format.extension == "m4a") {
                Mp4Tagger.tag(bytes, AudioTags(song.title, song.artist, song.album, cover(song))) ?: bytes
            } else {
                bytes
            }
        val sink = (if (tree != null) openTree(tree, name, format.mime) else open(song, name, format.mime)) ?: return false
        var complete = false
        try {
            sink.stream.write(body)
            complete = true
        } finally {
            sink.stream.close()
            sink.finish(complete)
        }
        return true
    }

    /** Трек целиком из хранилища загрузок. Тегирование всё равно требует весь файл. */
    private fun readAll(song: SongItem): ByteArray {
        val source =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(null)
                .createDataSource()
        source.open(DataSpec.Builder().setUri(w0yUri(song)).setKey(song.id).build())
        try {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = source.read(buffer, 0, buffer.size)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            require(out.size() > 0) { "в хранилище нет данных трека" }
            return out.toByteArray()
        } finally {
            source.close()
        }
    }

    /** Обложка для тегов; не скачалась — файл просто без неё. */
    private fun cover(song: SongItem): ByteArray? {
        val url = Thumbnails.sized(song.thumbnailUrl, COVER_PX) ?: return null
        return runCatching {
            okHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()?.takeIf { it.size in 1..MAX_COVER }
            }
        }.getOrNull()
    }

    class Format(val extension: String, val mime: String)

    companion object {
        const val FOLDER = "w0y music"
        private const val BUFFER = 64 * 1024
        private const val COVER_PX = 600
        private const val MAX_COVER = 2 * 1024 * 1024

        fun detect(head: ByteArray): Format {
            fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
            return when {
                at(4) == 'f'.code && at(5) == 't'.code && at(6) == 'y'.code && at(7) == 'p'.code ->
                    Format("m4a", "audio/mp4")

                at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> Format("webm", "audio/webm")
                at(0) == 'O'.code && at(1) == 'g'.code && at(2) == 'g'.code && at(3) == 'S'.code ->
                    Format("ogg", "audio/ogg")

                else -> Format("m4a", "audio/mp4")
            }
        }

        /** «Исполнитель - Название.ext» без символов, которые запрещены в именах файлов. */
        fun fileName(song: SongItem, extension: String): String {
            val raw = listOf(song.artist, song.title).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" - ")
            val base =
                raw
                    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .trim('.', ' ')
                    .take(120)
            val clean = base.ifBlank { song.id }
            return "$clean.$extension"
        }
    }

    private class Sink(val stream: OutputStream, val finish: (Boolean) -> Unit)

    /** Своя папка через SAF: доступ выдан один раз и сохранён. */
    private fun openTree(tree: Uri, name: String, mime: String): Sink? {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver
            .query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) if (cursor.getString(0) == name) return null
            }
        val doc =
            DocumentsContract.createDocument(resolver, parent, mime, name)
                ?: error("папка не дала создать файл")
        val stream = resolver.openOutputStream(doc) ?: error("нельзя открыть файл на запись")
        return Sink(stream) { complete -> if (!complete) runCatching { DocumentsContract.deleteDocument(resolver, doc) } }
    }

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
}
