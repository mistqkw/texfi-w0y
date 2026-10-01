package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.SongItem
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Скачивание в обычную папку «Музыка/w0y music»: один файл на трек, формат тот,
 * что отдал YouTube (m4a или webm/opus), без перекодирования. Скачанное играет
 * с диска и открывается любым плеером — отдельной «выгрузки» не нужно.
 */
class Downloads(private val scope: CoroutineScope, private val yt: Yt, private val app: AppState) {
    /** Идущие загрузки: id → доля 0..1 (−1 — ждёт очереди). */
    var progress by mutableStateOf<Map<String, Float>>(emptyMap())
        private set
    var failed by mutableStateOf<Set<String>>(emptySet())
        private set
    var notice by mutableStateOf<String?>(null)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val slots = Semaphore(2)

    val folder: File by lazy {
        val xdg =
            runCatching {
                File(System.getProperty("user.home"), ".config/user-dirs.dirs").readLines()
                    .firstOrNull { it.startsWith("XDG_MUSIC_DIR=") }
                    ?.substringAfter('=')?.trim('"')?.replace("\$HOME", System.getProperty("user.home"))
            }.getOrNull()
        File(xdg ?: "${System.getProperty("user.home")}/Music", "w0y music")
    }

    /** Сколько места занято скачанным, МБ. */
    fun usedMb(): Long = app.lib.downloads.values.sumOf { runCatching { File(it.path).length() }.getOrDefault(0L) } / 1024 / 1024

    fun openFolder() {
        folder.mkdirs()
        runCatching { ProcessBuilder("xdg-open", folder.absolutePath).start() }
    }

    fun isDone(id: String) = app.downloadedPath(id) != null

    fun download(song: SongItem) {
        if (isDone(song.id) || song.id in progress) return
        progress = progress + (song.id to -1f)
        failed = failed - song.id
        scope.launch(Dispatchers.IO) {
            slots.withPermit {
                runCatching { fetch(song) }
                    .onSuccess { app.markDownloaded(song, it.absolutePath) }
                    .onFailure { failed = failed + song.id }
            }
            progress = progress - song.id
        }
    }

    fun downloadAll(songs: List<SongItem>) {
        songs.forEach(::download)
        notice = "В очередь добавлено: ${songs.size}. Файлы появятся в ${folder.absolutePath}"
    }

    fun remove(id: String) {
        app.downloadedPath(id)?.let { runCatching { File(it).delete() } }
        app.unmarkDownloaded(id)
    }

    private suspend fun fetch(song: SongItem): File = withContext(Dispatchers.IO) {
        val stream = yt.stream(song.id)
        val mime = stream.mimeType.orEmpty()
        val ext = if (mime.contains("webm")) "webm" else if (mime.contains("mp4")) "m4a" else "audio"
        folder.mkdirs()
        val name = "${song.artist} - ${song.title}".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120)
        val target = File(folder, "$name.$ext")
        val part = File(folder, "$name.$ext.part")
        val total = stream.contentLengthBytes
        val chunk = if (stream.useRangeChunks) stream.rangeChunkSizeBytes.coerceAtLeast(256 * 1024L) else 4L * 1024 * 1024
        var done = 0L
        part.outputStream().use { out ->
            while (true) {
                val end = done + chunk - 1
                val request =
                    HttpRequest.newBuilder(URI.create(stream.audioUrl)).apply {
                        stream.headers.forEach { (k, v) -> if (!k.equals("range", true) && !k.equals("host", true)) header(k, v) }
                        header("Range", "bytes=$done-$end")
                        timeout(Duration.ofSeconds(30))
                    }.build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                require(response.statusCode() in listOf(200, 206)) { "HTTP ${response.statusCode()}" }
                val range = response.headers().firstValue("Content-Range").orElse(null)
                val size = range?.substringAfter('/')?.toLongOrNull() ?: total
                val got = response.body().use { it.copyTo(out) }
                done += got
                if (size != null && size > 0) progress = progress + (song.id to (done.toFloat() / size).coerceIn(0f, 1f))
                // 200 без Range — сервер отдал всё сразу.
                if (response.statusCode() == 200 || got == 0L || (size != null && done >= size)) break
            }
        }
        part.renameTo(target)
        target
    }
}
