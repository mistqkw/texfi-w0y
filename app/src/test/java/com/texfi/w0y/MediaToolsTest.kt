package com.texfi.w0y

import com.texfi.w0y.data.SongItem
import com.texfi.w0y.playback.AudioTags
import com.texfi.w0y.playback.Id3Tagger
import com.texfi.w0y.playback.Mp3Encoder
import com.texfi.w0y.playback.Mp4Tagger
import com.texfi.w0y.playback.MusicExporter
import com.texfi.w0y.playback.SpectrumAnalyzer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Имена при выгрузке, теги m4a и спектр. */
class MediaToolsTest {
    private fun box(type: String, body: ByteArray): ByteArray =
        ByteBuffer.allocate(8).putInt(body.size + 8).put(type.toByteArray(Charsets.ISO_8859_1)).array() + body

    private fun int32(v: Int) = ByteBuffer.allocate(4).putInt(v).array()

    /** Минимальный m4a: ftyp, moov с одной таблицей stco, mdat после moov. */
    private fun mp4(mdatPayload: ByteArray): Pair<ByteArray, Int> {
        val ftyp = box("ftyp", "M4A ".toByteArray() + int32(0))
        fun moovWith(offset: Int) =
            box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stco", int32(0) + int32(1) + int32(offset)))))))
        val moovSize = moovWith(0).size
        val chunkOffset = ftyp.size + moovSize + 8
        val out = ByteArrayOutputStream()
        out.write(ftyp)
        out.write(moovWith(chunkOffset))
        out.write(box("mdat", mdatPayload))
        return out.toByteArray() to chunkOffset
    }

    private fun stcoOffset(file: ByteArray): Int {
        val at = String(file, Charsets.ISO_8859_1).indexOf("stco")
        return ByteBuffer.wrap(file, at + 4 + 8, 4).int
    }

    @Test
    fun taggerKeepsAudioReachable() {
        val payload = "AUDIO-PAYLOAD".toByteArray()
        val (file, _) = mp4(payload)
        val tagged = Mp4Tagger.tag(file, AudioTags("Название", "Артист", "Альбом", null))
        assertNotNull(tagged)
        tagged!!
        val offset = stcoOffset(tagged)
        // Таблица смещений обязана указывать на те же байты звука после вставки тегов.
        assertEquals("AUDIO-PAYLOAD", String(tagged, offset, payload.size))
        val text = String(tagged, Charsets.UTF_8)
        assertTrue("Название" in text && "Артист" in text && "Альбом" in text)
        // Повторно не тегируем: udta уже есть.
        assertNull(Mp4Tagger.tag(tagged, AudioTags("x", "y", null, null)))
    }

    @Test
    fun taggerRejectsNonMp4() {
        assertNull(Mp4Tagger.tag(ByteArray(64) { 7 }, AudioTags("x", "y", null, null)))
    }

    @Test
    fun exportNameIsArtistDashTitleAndSafe() {
        val song = SongItem(id = "abc", title = "Who/What?: \"Live\"", artist = "AC|DC")
        assertEquals("AC_DC - Who_What__ _Live_.m4a", MusicExporter.fileName(song, "m4a"))
        assertEquals("abc.webm", MusicExporter.fileName(SongItem(id = "abc", title = "", artist = ""), "webm"))
        assertEquals("Title.m4a", MusicExporter.fileName(SongItem(id = "abc", title = "Title", artist = ""), "m4a"))
    }

    @Test
    fun sameSongGivesSameName() {
        // Дубли ловятся по имени файла, поэтому оно обязано быть стабильным.
        val song = SongItem(id = "abc", title = "  Song   name ", artist = "Artist")
        assertEquals(MusicExporter.fileName(song, "m4a"), MusicExporter.fileName(song.copy(), "m4a"))
        assertEquals("Artist - Song name.m4a", MusicExporter.fileName(song, "m4a"))
    }

    @Test
    fun formatDetection() {
        assertEquals("m4a", MusicExporter.detect(mp4(ByteArray(4)).first).extension)
        assertEquals("webm", MusicExporter.detect(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())).extension)
        assertEquals("ogg", MusicExporter.detect("OggS".toByteArray()).extension)
    }

    @Test
    fun fftFindsSine() {
        val rate = 44_100
        val analyzer = SpectrumAnalyzer(size = 1024, bands = 32)
        for (hz in listOf(110f, 1000f, 6000f)) {
            repeat(6) { frame ->
                for (i in analyzer.samples.indices) {
                    val t = (frame * analyzer.samples.size + i).toDouble() / rate
                    analyzer.samples[i] = (0.8 * sin(2 * PI * hz * t)).toFloat()
                }
                analyzer.analyze(rate)
            }
            val loudest = analyzer.levels.indices.maxBy { analyzer.levels[it] }
            val expected = analyzer.bandOf(hz, rate)
            assertTrue("$hz Гц: пик в полосе $loudest, ждали около $expected", kotlin.math.abs(loudest - expected) <= 1)
            // Сброс между частотами: оседание до тишины.
            repeat(200) { analyzer.settle() }
        }
    }

    @Test
    fun silenceIsCalm() {
        val analyzer = SpectrumAnalyzer()
        analyzer.samples.fill(0f)
        analyzer.analyze(44_100)
        assertTrue(analyzer.levels.all { it < 0.01f })
    }

    @Test
    fun id3TagHoldsTextAndCover() {
        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val tag = Id3Tagger.tag(AudioTags("Название", "Артист", "Альбом", cover))
        assertEquals("ID3", String(tag, 0, 3, Charsets.ISO_8859_1))
        assertEquals(3, tag[3].toInt())
        // Размер в заголовке — synchsafe и равен телу тега.
        val size = (tag[6].toInt() shl 21) or (tag[7].toInt() shl 14) or (tag[8].toInt() shl 7) or tag[9].toInt()
        assertEquals(tag.size - 10, size)
        val frames = mutableMapOf<String, ByteArray>()
        var at = 10
        while (at < tag.size) {
            val id = String(tag, at, 4, Charsets.ISO_8859_1)
            val n = ByteBuffer.wrap(tag, at + 4, 4).int
            frames[id] = tag.copyOfRange(at + 10, at + 10 + n)
            at += 10 + n
        }
        fun text(id: String) = frames.getValue(id).let { String(it, 3, it.size - 3, Charsets.UTF_16LE) }
        assertEquals("Название", text("TIT2"))
        assertEquals("Артист", text("TPE1"))
        assertEquals("Альбом", text("TALB"))
        val apic = frames.getValue("APIC")
        assertTrue(String(apic, 1, 10, Charsets.ISO_8859_1) == "image/jpeg")
        assertTrue(apic.copyOfRange(apic.size - cover.size, apic.size).contentEquals(cover))
    }

    @Test
    fun id3SkipsMissingAlbum() {
        val tag = Id3Tagger.tag(AudioTags("a", "b", null, null))
        assertTrue(String(tag, Charsets.ISO_8859_1).contains("TIT2"))
        assertTrue(!String(tag, Charsets.ISO_8859_1).contains("TALB"))
    }

    @Test
    fun mp3EncoderMakesConstant320() {
        val out = ByteArrayOutputStream()
        val rate = 48_000
        val encoder = Mp3Encoder(rate, 2, 320, out)
        val frames = 4096
        val pcm = ShortArray(frames * 2)
        var t = 0
        repeat(rate * 2 / frames) {
            for (i in 0 until frames) {
                val v = (8000 * sin(2 * PI * 440 * (t + i) / rate)).toInt().toShort()
                pcm[2 * i] = v
                pcm[2 * i + 1] = v
            }
            t += frames
            encoder.encode(pcm, frames)
        }
        encoder.finish()
        val mp3 = out.toByteArray()
        // Первый кадр: синхрослово, MPEG-1 Layer III, индекс битрейта 14 = 320 кбит/с, 48 кГц.
        assertEquals(0xFF, mp3[0].toInt() and 0xFF)
        assertEquals(0xFB, mp3[1].toInt() and 0xFF)
        assertEquals(14, (mp3[2].toInt() and 0xFF) shr 4)
        assertEquals(1, ((mp3[2].toInt() and 0xFF) shr 2) and 3)
        // Две секунды при 320 кбит/с — около 80 КБ.
        assertTrue("размер ${mp3.size}", mp3.size in 75_000..86_000)
    }
}
