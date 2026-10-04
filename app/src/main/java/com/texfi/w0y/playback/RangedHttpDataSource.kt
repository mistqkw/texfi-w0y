package com.texfi.w0y.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

/**
 * Как скачивать поток: размер первого куска, обычного куска и сколько
 * кусков держать в полёте одновременно.
 *
 * Первый кусок маленький — до первого звука нужно лишь начало файла, и чем
 * меньше запрос, тем раньше приходит первый байт. Дальше куски крупнее и
 * идут параллельно: пока плеер или загрузчик читает один, следующие уже
 * качаются.
 */
data class RangePlan(
    val firstChunk: Long,
    val chunk: Long,
    val parallel: Int,
    val retries: Int,
)

/** Что источник знает о потоке, кроме адреса: размер куска и полная длина. */
data class StreamHint(val chunkLimit: Long?, val contentLength: Long?)

/**
 * HTTP-источник, который читает поток ограниченными диапазонами, а не одним
 * открытым запросом.
 *
 * Сервер видео отдаёт непрерывный ответ со скоростью воспроизведения —
 * десятки килобайт в секунду, — а тот же файл, запрошенный кусками
 * `Range: bytes=a-b`, приходит на скорости канала. Отсюда и медленные
 * загрузки, и долгий буфер после перемотки: запас наполнить было нечем.
 *
 * Сверху это один непрерывный поток той длины, что попросили: кэш и
 * загрузчик ничего не замечают. Внутри:
 *  - первый кусок читается прямо из ответа, без ожидания конца — звук может
 *    начаться с первых килобайт;
 *  - следующие куски качаются заранее, по [RangePlan.parallel] сразу;
 *  - адрес, истёкший посреди файла (403/410), получается заново через
 *    [refresh], и чтение продолжается с того же байта, а не с начала;
 *  - обрыв сети — ограниченное число повторов с паузой, без бесконечного цикла.
 *
 * Идея ограниченных диапазонов подсмотрена у BitChord (GPL-3.0); код свой.
 */
@OptIn(UnstableApi::class)
class RangedHttpDataSource(
    private val calls: Call.Factory,
    private val plan: RangePlan,
    private val hint: (videoId: String) -> StreamHint?,
    private val refresh: (videoId: String) -> ResolvedAudio?,
    private val limiter: RateLimiter? = null,
) : BaseDataSource(/* isNetwork = */ true) {
    private var spec: DataSpec? = null
    private var url: String = ""
    private var headers: Map<String, String> = emptyMap()
    private var videoId: String? = null
    private var chunkSize: Long = plan.chunk

    /** Позиция следующего байта, который отдадим наверх. */
    private var position = 0L

    /** Конец запрошенного куска файла (не включительно). */
    private var end = 0L

    /** Первый кусок читается из открытого ответа. */
    private var direct: InputStream? = null
    private var directResponse: Response? = null
    private var directLeft = 0L

    /** Скачанные заранее куски по порядку; голова — следующий за текущим. */
    private val ahead = ArrayDeque<Pending>()
    private var buffer: ByteArray? = null
    private var bufferPos = 0

    /** Где кончается то, что уже отдано в очередь на скачивание. */
    private var scheduledUntil = 0L
    private var opened = false
    private var passthrough = false

    /** Сервер не назвал полный размер: читаем кусками, пока он не кончится. */
    private var unknownLength = false

    private class Pending(val from: Long, val until: Long, val future: Future<ByteArray>)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        url = dataSpec.uri.toString()
        headers = dataSpec.httpRequestHeaders
        videoId = dataSpec.key
        position = dataSpec.position
        transferInitializing(dataSpec)

        val known = videoId?.let(hint)
        chunkSize = chunkFor(url, known?.chunkLimit)
        val first = minOf(plan.firstChunk, chunkSize)
        val requestedEnd =
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else null
        val firstEnd = requestedEnd?.let { minOf(it, position + first) } ?: (position + first)

        val response = execute(position, firstEnd - 1)
        if (response.code == HTTP_OK) {
            // Сервер диапазоны не понял и отдал файл целиком: читаем как есть.
            passthrough = true
            val body = response.body ?: throw IOException("пустой ответ")
            directResponse = response
            direct = body.byteStream()
            if (position > 0) skipFully(direct!!, position)
            val length = body.contentLength().takeIf { it >= 0 }?.minus(position)
            end = length?.let { position + it } ?: Long.MAX_VALUE
            directLeft = length ?: Long.MAX_VALUE
            if (requestedEnd != null) {
                end = minOf(end, requestedEnd)
                directLeft = end - position
            }
            opened = true
            transferStarted(dataSpec)
            return if (end == Long.MAX_VALUE) C.LENGTH_UNSET.toLong() else end - position
        }
        // Размер — только из ответа сервера или из адреса (clen). Раньше при
        // их отсутствии концом считался первый кусок, и файл обрывался на
        // первом мегабайте, а загрузка числилась готовой.
        val total = totalFrom(response) ?: clen(url)
        unknownLength = total == null && requestedEnd == null
        end =
            when {
                requestedEnd != null && total != null -> minOf(requestedEnd, total)
                requestedEnd != null -> requestedEnd
                total != null -> total
                else -> Long.MAX_VALUE
            }
        val body = response.body ?: throw IOException("пустой ответ")
        directResponse = response
        direct = body.byteStream()
        directLeft = minOf(firstEnd, end) - position
        scheduledUntil = position + directLeft
        opened = true
        transferStarted(dataSpec)
        scheduleAhead()
        return if (unknownLength) C.LENGTH_UNSET.toLong() else end - position
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= end) return C.RESULT_END_OF_INPUT
        // 1. Остаток первого куска — прямо из ответа.
        direct?.let { stream ->
            if (directLeft > 0) {
                val n = stream.read(target, offset, minOf(length.toLong(), directLeft).toInt())
                if (n > 0) {
                    directLeft -= n
                    advance(n)
                    if (directLeft == 0L) closeDirect()
                    return n
                }
                // Ответ кончился раньше обещанного: докачаем недостающее
                // обычным куском с того же места.
                closeDirect()
                if (passthrough) return C.RESULT_END_OF_INPUT
                if (unknownLength) {
                    // Файл короче первого куска — он весь уже отдан.
                    end = position
                    ahead.forEach { it.future.cancel(true) }
                    ahead.clear()
                    return C.RESULT_END_OF_INPUT
                }
                scheduledUntil = position
                ahead.forEach { it.future.cancel(true) }
                ahead.clear()
            } else {
                closeDirect()
            }
        }
        // 2. Кусок в памяти.
        val current = buffer
        if (current != null && bufferPos < current.size) {
            val n = minOf(length, current.size - bufferPos)
            System.arraycopy(current, bufferPos, target, offset, n)
            bufferPos += n
            advance(n)
            return n
        }
        // 3. Следующий скачанный кусок.
        scheduleAhead()
        val next = ahead.removeFirstOrNull() ?: return C.RESULT_END_OF_INPUT
        buffer =
            try {
                next.future.get()
            } catch (error: ExecutionException) {
                throw (error.cause as? IOException) ?: IOException(error.cause)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        bufferPos = 0
        val got = buffer?.size ?: 0
        if (unknownLength && got < next.until - next.from) {
            // Короткий кусок — это конец файла: дальше не просим.
            end = next.from + got
            ahead.forEach { it.future.cancel(true) }
            ahead.clear()
            if (got == 0) return C.RESULT_END_OF_INPUT
        }
        scheduleAhead()
        return read(target, offset, length)
    }

    private fun advance(n: Int) {
        limiter?.acquire(n)
        position += n
        bytesTransferred(n)
    }

    /** Держит в полёте до [RangePlan.parallel] кусков вперёд. */
    private fun scheduleAhead() {
        while (ahead.size < plan.parallel && scheduledUntil < end) {
            val from = scheduledUntil
            val until = minOf(end, from + chunkSize)
            ahead.addLast(Pending(from, until, POOL.submit(Callable { fetch(from, until - 1) })))
            scheduledUntil = until
        }
    }

    /** Один кусок целиком, с повторами и обновлением адреса. */
    private fun fetch(from: Long, to: Long): ByteArray {
        var attempt = 0
        var refreshed = false
        while (true) {
            try {
                execute(from, to).use { response ->
                    val body = response.body ?: throw IOException("пустой ответ")
                    val bytes = body.bytes()
                    val expected = (to - from + 1).toInt()
                    if (response.code == HTTP_PARTIAL && bytes.size >= expected) return bytes.copyOf(expected)
                    // Без известного размера короткий кусок — последний, а не обрыв.
                    if (response.code == HTTP_PARTIAL && unknownLength && bytes.size < expected) return bytes
                    if (response.code == HTTP_PARTIAL) throw IOException("кусок оборвался: ${bytes.size} из $expected")
                    throw IOException("сервер не отдал диапазон: HTTP ${response.code}")
                }
            } catch (error: HttpDataSource.InvalidResponseCodeException) {
                // За концом файла неизвестной длины — пустой кусок, то есть конец.
                if (error.responseCode == HTTP_RANGE_NOT_SATISFIABLE && unknownLength) return ByteArray(0)
                if (error.responseCode in EXPIRED_CODES && !refreshed && renew()) {
                    refreshed = true
                    continue
                }
                if (error.responseCode in RETRY_CODES && attempt < plan.retries) {
                    backoff(attempt++)
                    continue
                }
                throw error
            } catch (error: InterruptedIOException) {
                throw error
            } catch (error: IOException) {
                if (attempt >= plan.retries) throw error
                backoff(attempt++)
            }
        }
    }

    private fun backoff(attempt: Int) {
        try {
            Thread.sleep(BACKOFF_MS * (attempt + 1))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        }
    }

    /** Новый адрес для того же трека; true — получилось. */
    @Synchronized
    private fun renew(): Boolean {
        val id = videoId ?: return false
        val fresh = runCatching { refresh(id) }.getOrNull() ?: return false
        url = fresh.url
        headers = fresh.headers
        return true
    }

    /** Запрос диапазона; ошибки HTTP — в виде, который понимает media3. */
    private fun execute(from: Long, to: Long): Response {
        var expiredOnce = false
        while (true) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .header("Range", "bytes=$from-$to")
                    .build()
            val response = calls.newCall(request).execute()
            if (response.code == HTTP_OK || response.code == HTTP_PARTIAL) return response
            val code = response.code
            val message = response.message
            response.close()
            // Истёкший адрес на первом же запросе тоже обновляем: ссылка из
            // кэша могла дожить до последних секунд.
            if (code in EXPIRED_CODES && !expiredOnce && renew()) {
                expiredOnce = true
                continue
            }
            val spec = this.spec ?: DataSpec(Uri.parse(url))
            throw HttpDataSource.InvalidResponseCodeException(
                code,
                message,
                null,
                emptyMap(),
                spec,
                ByteArray(0),
            )
        }
    }

    private fun closeDirect() {
        runCatching { direct?.close() }
        runCatching { directResponse?.close() }
        direct = null
        directResponse = null
        directLeft = 0
    }

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        closeDirect()
        ahead.forEach { it.future.cancel(true) }
        ahead.clear()
        buffer = null
        spec = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val calls: Call.Factory,
        private val plan: () -> RangePlan,
        private val hint: (String) -> StreamHint?,
        private val refresh: (String) -> ResolvedAudio?,
        private val limiter: RateLimiter? = null,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RangedHttpDataSource(calls, plan(), hint, refresh, limiter)
    }

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val EXPIRED_CODES = setOf(403, 410)
        private val RETRY_CODES = setOf(429, 500, 502, 503, 504)
        private const val BACKOFF_MS = 600L

        /** Обычный кусок: на нём сервер ещё отдаёт на полной скорости. */
        const val CHUNK = 1024L * 1024

        /** Узкий кусок для клиентов, у которых сервер режет большие диапазоны. */
        const val NARROW_CHUNK = 512L * 1024

        private val POOL =
            Executors.newFixedThreadPool(
                6,
                object : ThreadFactory {
                    private val n = AtomicInteger()

                    override fun newThread(r: Runnable) = Thread(r, "w0y-range-${n.incrementAndGet()}").apply { isDaemon = true }
                },
            )

        /**
         * Размер куска под конкретный адрес. У части клиентов YouTube сам
         * задаёт предел диапазона — он важнее нашего числа.
         */
        fun chunkFor(url: String, limit: Long?): Long {
            val client = Uri.parse(url).getQueryParameter("c")?.uppercase().orEmpty()
            val base = if (client == "ANDROID_VR" || client.startsWith("TVHTML5_SIMPLY")) NARROW_CHUNK else CHUNK
            return if (limit != null && limit > 0) minOf(base, limit) else base
        }

        /** Полная длина из `Content-Range: bytes a-b/total`. */
        fun totalFrom(response: Response): Long? =
            response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()

        /** Длина файла из параметра `clen` адреса видео, если он есть. */
        fun clen(url: String): Long? = runCatching { Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() }.getOrNull()

        private fun skipFully(stream: InputStream, count: Long) {
            var left = count
            while (left > 0) {
                val skipped = stream.skip(left)
                if (skipped <= 0) {
                    if (stream.read() < 0) throw IOException("файл короче позиции")
                    left--
                } else {
                    left -= skipped
                }
            }
        }
    }
}

/**
 * Ограничитель скорости загрузок: ведро токенов на байты.
 *
 * Ноль — без ограничения. Держится одним на все загрузки сразу, а не на
 * каждую: иначе лимит умножался бы на число параллельных треков.
 */
class RateLimiter(private val bytesPerSecond: () -> Long) {
    private var available = 0.0
    private var last = System.nanoTime()

    fun acquire(bytes: Int) {
        val rate = bytesPerSecond()
        if (rate <= 0) return
        var wait: Long
        synchronized(this) {
            val now = System.nanoTime()
            available = minOf(rate.toDouble(), available + (now - last) / 1e9 * rate)
            last = now
            available -= bytes
            wait = if (available < 0) ((-available) / rate * 1000).toLong() else 0
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        }
    }
}
