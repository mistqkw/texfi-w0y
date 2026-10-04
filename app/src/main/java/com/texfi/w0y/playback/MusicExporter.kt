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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * воспроизведение (только хранилище, в сеть не ходим), и перекодируется в
 * MP3 320 кбит/с ([Mp3Transcoder]) — этот формат открывает любой плеер,
 * магнитола и компьютер. Качество при этом не растёт: исходник с YouTube
 * сжат сильнее, 320 — просто чтобы ничего не потерять при перекодировании.
 *
 * Имя — «Исполнитель - Название.mp3». Дубли не плодятся: если файл с таким
 * именем уже лежит, трек считается сохранённым, и кодировать его заново не
 * нужно. Внутрь кладутся название, исполнитель, альбом и обложка
 * ([Id3Tagger]). Кодирование долгое, поэтому идёт по [PARALLEL] трека сразу.
 */
@Singleton
@OptIn(UnstableApi::class)
class MusicExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("download") private val cache: SimpleCache,
    private val settings: SettingsRepository,
    private val okHttp: OkHttpClient,
) {
    /** [onProgress] — сколько треков из всех уже обработано (в любом исходе). */
    suspend fun export(
        songs: List<SongItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportResult = withContext(Dispatchers.IO) {
        val tree = settings.settings.first().exportTreeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val saved = AtomicInteger()
        val existed = AtomicInteger()
        val failed = AtomicInteger()
        val done = AtomicInteger()
        onProgress(0, songs.size)
        val workers = Dispatchers.IO.limitedParallelism(PARALLEL)
        coroutineScope {
            songs.map { song ->
                async(workers) {
                    runCatching { exportOne(song, tree) }
                        .onSuccess { if (it) saved.incrementAndGet() else existed.incrementAndGet() }
                        .onFailure {
                            Timber.w(it, "Не выгрузился трек %s", song.id)
                            failed.incrementAndGet()
                        }
                    onProgress(done.incrementAndGet(), songs.size)
                }
            }.awaitAll()
        }
        ExportResult(saved.get(), existed.get(), failed.get())
    }

    /** true — файл записан, false — такой уже лежит в папке. */
    private fun exportOne(song: SongItem, tree: Uri?): Boolean {
        val name = fileName(song, MP3.extension)
        // Сначала место в папке: если файл уже есть, не тратим полминуты на кодирование.
        val sink = (if (tree != null) openTree(tree, name, MP3.mime) else open(song, name, MP3.mime)) ?: return false
        var complete = false
        val source = File(context.cacheDir, "export-${song.id}.src")
        try {
            source.writeBytes(readAll(song))
            val stream = BufferedOutputStream(sink.stream, BUFFER)
            stream.write(Id3Tagger.tag(AudioTags(song.title, song.artist, song.album, cover(song))))
            Mp3Transcoder.transcode(source, stream, KBPS)
            stream.flush()
            complete = true
        } finally {
            source.delete()
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
        val bytes =
            runCatching {
                okHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()?.takeIf { it.size in 1..MAX_COVER }
                }
            }.getOrNull() ?: return null
        return asJpegOrPng(bytes)
    }

    /** Плееры понимают в тегах JPEG и PNG; WebP и прочее пережимается в JPEG. */
    private fun asJpegOrPng(bytes: ByteArray): ByteArray? {
        val jpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        if (jpeg || png) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    class Format(val extension: String, val mime: String)

    companion object {
        const val FOLDER = "w0y music"
        private const val BUFFER = 64 * 1024
        private const val COVER_PX = 600
        private const val COVER_QUALITY = 92
        private const val MAX_COVER = 2 * 1024 * 1024
        private const val KBPS = 320

        /** Сколько треков кодируется одновременно: быстрее в разы, а памяти — по паре мегабайт на трек. */
        private const val PARALLEL = 3
        private val MP3 = Format("mp3", "audio/mpeg")

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
