package com.texfi.w0y

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import android.net.Uri
import com.texfi.w0y.data.StatsBucket
import com.texfi.w0y.data.StatsPeriod
import com.texfi.w0y.data.StatsRepository
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.ListenRow
import com.texfi.w0y.playback.DownloadFailure
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.RangePlan
import com.texfi.w0y.playback.RangedHttpDataSource
import com.texfi.w0y.playback.ResolvedAudio
import com.texfi.w0y.playback.StartupMetrics
import com.texfi.w0y.playback.StartupSample
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Загрузка кусками: докачка, новый адрес, повторы; разбор причин; метрики и график. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadsAndMetricsTest {
    private val file = ByteArray(3_000_000) { (it * 31 + 7).toByte() }

    /** Сервер с диапазонами. [expired] — адреса, на которые он отвечает 403; [flaky] — сколько раз оборвать связь. */
    private fun server(
        expired: Set<String> = emptySet(),
        flaky: AtomicInteger = AtomicInteger(0),
        hits: MutableList<String>? = null,
        sendTotal: Boolean = true,
    ) =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                synchronized(this) { hits?.add(request.header("Range") + " " + request.url.queryParameter("v")) }
                if (expired.any { url.contains(it) }) {
                    return@addInterceptor Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                }
                if (flaky.getAndDecrement() > 0) throw IOException("обрыв")
                val range = request.header("Range")!!.removePrefix("bytes=").split('-')
                val from = range[0].toInt()
                val to = minOf(range[1].toInt(), file.size - 1)
                if (from >= file.size) {
                    return@addInterceptor Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(416)
                        .message("Range Not Satisfiable")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                }
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial")
                    .header("Content-Range", if (sendTotal) "bytes $from-$to/${file.size}" else "bytes $from-$to/*")
                    .body(file.copyOfRange(from, to + 1).toResponseBody("audio/mp4".toMediaType()))
                    .build()
            }.build()

    private fun readAll(source: RangedHttpDataSource, spec: DataSpec): ByteArray {
        source.open(spec)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        source.close()
        return out.toByteArray()
    }

    private fun spec(url: String, position: Long = 0) =
        DataSpec.Builder().setUri(Uri.parse(url)).setKey("vid").setPosition(position).build()

    private val plan = RangePlan(firstChunk = 256 * 1024, chunk = 512 * 1024, parallel = 3, retries = 2)

    @Test
    fun readsWholeFileInRanges() {
        val hits = mutableListOf<String>()
        val source = RangedHttpDataSource(server(hits = hits), plan, { null }, { null })
        assertArrayEquals(file, readAll(source, spec("https://x.test/a?v=1")))
        assertTrue("файл должен идти кусками, а не одним запросом", hits.size > 3)
    }

    @Test
    fun readsWholeFileWhenServerHidesSize() {
        // Живой случай: сервер не назвал полный размер — раньше файл обрывался
        // на первом куске, а загрузка числилась готовой.
        val source = RangedHttpDataSource(server(sendTotal = false), plan, { null }, { null })
        assertArrayEquals(file, readAll(source, spec("https://x.test/a?v=1")))
    }

    @Test
    fun resumesFromPosition() {
        val source = RangedHttpDataSource(server(), plan, { null }, { null })
        val tail = readAll(source, spec("https://x.test/a?v=1", position = 1_234_567))
        assertArrayEquals(file.copyOfRange(1_234_567, file.size), tail)
    }

    @Test
    fun expiredUrlIsRefreshedAndReadingContinues() {
        val refreshed = AtomicInteger()
        val source =
            RangedHttpDataSource(
                server(expired = setOf("v=old")),
                plan,
                { null },
                {
                    refreshed.incrementAndGet()
                    ResolvedAudio("https://x.test/a?v=new")
                },
            )
        assertArrayEquals(file, readAll(source, spec("https://x.test/a?v=old")))
        assertTrue(refreshed.get() >= 1)
    }

    @Test
    fun networkDropsAreRetried() {
        val source = RangedHttpDataSource(server(flaky = AtomicInteger(0)), plan, { null }, { null })
        source.open(spec("https://x.test/a?v=1"))
        source.close()
        // Обрывы на кусках после первого: повторов хватает.
        val flaky = AtomicInteger(0)
        val client = server(flaky = flaky)
        val retrying = RangedHttpDataSource(client, plan.copy(parallel = 1), { null }, { null })
        retrying.open(spec("https://x.test/a?v=1"))
        flaky.set(2)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = retrying.read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        retrying.close()
        assertArrayEquals(file, out.toByteArray())
    }

    @Test
    fun retriesAreLimited() {
        val flaky = AtomicInteger(0)
        val source = RangedHttpDataSource(server(flaky = flaky), plan.copy(parallel = 1, retries = 1), { null }, { null })
        source.open(spec("https://x.test/a?v=1"))
        // Связь пропала насовсем: после повторов — ошибка, а не вечное ожидание.
        flaky.set(Int.MAX_VALUE)
        val buf = ByteArray(64 * 1024)
        try {
            while (source.read(buf, 0, buf.size) >= 0) Unit
            fail("бесконечных повторов быть не должно")
        } catch (_: IOException) {
        } finally {
            source.close()
        }
    }

    @Test
    fun failureReasons() {
        fun http(code: Int) =
            HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), DataSpec(Uri.EMPTY), ByteArray(0))
        assertEquals(DownloadFailure.EXPIRED, DownloadFailure.of(IOException(http(403))))
        assertEquals(DownloadFailure.RESTRICTED, DownloadFailure.of(http(404)))
        assertEquals(DownloadFailure.SERVER, DownloadFailure.of(http(503)))
        assertEquals(DownloadFailure.NETWORK, DownloadFailure.of(UnknownHostException("rr1.googlevideo.com")))
        assertEquals(DownloadFailure.STORAGE, DownloadFailure.of(IOException("write failed: ENOSPC")))
        assertEquals(DownloadFailure.NO_STREAM, DownloadFailure.of(YouTubeRepository.NoStreamException("vid")))
        assertEquals(DownloadFailure.UNKNOWN, DownloadFailure.of(null))
        assertTrue(!DownloadFailure.RESTRICTED.retryable && DownloadFailure.NETWORK.retryable)
    }

    @Test
    fun speedFromWindow() {
        assertEquals(0L, DownloadsRepository.speedOf(listOf(0L to 0L)))
        assertEquals(500_000L, DownloadsRepository.speedOf(listOf(0L to 0L, 1_000L to 200_000L, 2_000L to 1_000_000L)))
    }

    @Test
    fun startupMedianAndWorst() {
        val metrics = StartupMetrics()
        listOf(400L, 100L, 300L, 200L).forEach { metrics.record(StartupSample(it, it / 2, null, null, fromDisk = it < 250)) }
        val b = metrics.breakdown.value!!
        assertEquals(250L, b.total!!.median)
        assertEquals(400L, b.total!!.worst)
        assertEquals(200L, b.resolve!!.worst)
        assertEquals(2, b.fromDisk)
        assertEquals(null, b.firstByte)
    }

    @Test
    fun weekChartHasSevenDaysIncludingEmpty() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val noon = today.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000L
        val rows =
            listOf(
                ListenRow(noon, 120_000, legacy = false, counted = true, durationMs = 200_000, durationText = null),
                ListenRow(noon - 2 * 86_400_000L, 0, legacy = true, counted = true, durationMs = null, durationText = "3:00"),
            )
        val bars = StatsRepository.bars(rows, StatsPeriod.WEEK, 0, zone)
        assertEquals(7, bars.size)
        assertEquals(today, bars.last().start)
        assertEquals(2, bars.last().minutes)
        assertEquals(3, bars[bars.size - 3].minutes)
        assertEquals(0, bars.first().minutes)
        assertEquals(StatsBucket.MONTH, StatsPeriod.YEAR.bucket)
    }
}
