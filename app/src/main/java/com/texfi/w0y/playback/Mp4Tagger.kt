package com.texfi.w0y.playback

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Теги, которые кладём внутрь файла. */
data class AudioTags(
    val title: String,
    val artist: String,
    val album: String? = null,
    /** JPEG или PNG обложки; null — без обложки. */
    val cover: ByteArray? = null,
)

/**
 * Теги внутри m4a без сторонних библиотек.
 *
 * В `moov` добавляется `udta/meta/ilst` с названием, исполнителем,
 * альбомом и обложкой — так их видят обычные плееры и проводник. Вставка
 * сдвигает всё, что лежит после `moov`, поэтому абсолютные смещения
 * поправляются: таблицы `stco`/`co64` и `base_data_offset` во фрагментах.
 * Если файл устроен непривычно, тегирование отменяется и файл остаётся
 * как был — лучше без тегов, чем битый.
 *
 * WebM/Opus так не размечается: там пришлось бы переписывать индекс
 * кластеров. Для него теги идут только в медиатеку телефона.
 */
object Mp4Tagger {
    fun isMp4(head: ByteArray): Boolean =
        head.size >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()

    /** Файл с тегами или null, если разметить не удалось. */
    fun tag(file: ByteArray, tags: AudioTags): ByteArray? =
        runCatching { tagOrThrow(file, tags) }.getOrNull()

    private fun tagOrThrow(file: ByteArray, tags: AudioTags): ByteArray? {
        val top = boxes(file, 0, file.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: return null
        val children = boxes(file, moov.bodyStart, moov.end)
        if (children.any { it.type == "udta" }) return null
        val udta = udta(tags)
        val delta = udta.size.toLong()
        val out = file.copyOf()
        // Поправить смещения во всём, что лежит после moov.
        if (moov.end < file.size) {
            fixOffsets(out, moov.bodyStart, moov.end, moov.end.toLong(), delta)
            top.filter { it.type == "moof" && it.start >= moov.end }.forEach { moof ->
                fixMoof(out, moof, delta)
            }
        }
        val result = ByteArrayOutputStream(file.size + udta.size)
        result.write(out, 0, moov.start)
        val newSize = moov.size + udta.size
        if (moov.headerSize == 8) {
            if (newSize > Int.MAX_VALUE) return null
            result.write(int32(newSize.toInt()))
            result.write(out, moov.start + 4, 4)
        } else {
            result.write(out, moov.start, 8)
            result.write(int64(newSize))
        }
        result.write(out, moov.bodyStart, moov.end - moov.bodyStart)
        result.write(udta)
        result.write(out, moov.end, out.size - moov.end)
        return result.toByteArray()
    }

    private class Box(val type: String, val start: Int, val size: Long, val headerSize: Int) {
        val bodyStart get() = start + headerSize
        val end get() = (start + size).toInt()
    }

    private fun boxes(data: ByteArray, from: Int, until: Int): List<Box> {
        val list = mutableListOf<Box>()
        var at = from
        while (at + 8 <= until) {
            var size = u32(data, at)
            val type = String(data, at + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                size = u64(data, at + 8)
                header = 16
            } else if (size == 0L) {
                size = (until - at).toLong()
            }
            if (size < header || at + size > until) error("битый бокс $type")
            list += Box(type, at, size, header)
            at += size.toInt()
        }
        return list
    }

    /** Таблицы смещений кусков внутри moov: всё, что указывает за moov, сдвигается. */
    private fun fixOffsets(data: ByteArray, from: Int, until: Int, threshold: Long, delta: Long) {
        for (box in boxes(data, from, until)) {
            when (box.type) {
                "trak", "mdia", "minf", "stbl", "edts", "mvex" -> fixOffsets(data, box.bodyStart, box.end, threshold, delta)
                "stco" -> {
                    val count = u32(data, box.bodyStart + 4).toInt()
                    var p = box.bodyStart + 8
                    repeat(count) {
                        val v = u32(data, p)
                        if (v >= threshold) {
                            val nv = v + delta
                            if (nv > 0xFFFFFFFFL) error("смещение не влезает в stco")
                            put32(data, p, nv)
                        }
                        p += 4
                    }
                }
                "co64" -> {
                    val count = u32(data, box.bodyStart + 4).toInt()
                    var p = box.bodyStart + 8
                    repeat(count) {
                        val v = u64(data, p)
                        if (v >= threshold) put64(data, p, v + delta)
                        p += 8
                    }
                }
            }
        }
    }

    /** Во фрагменте сдвигать нужно только явный base_data_offset в tfhd. */
    private fun fixMoof(data: ByteArray, moof: Box, delta: Long) {
        for (traf in boxes(data, moof.bodyStart, moof.end).filter { it.type == "traf" }) {
            for (tfhd in boxes(data, traf.bodyStart, traf.end).filter { it.type == "tfhd" }) {
                val flags = u32(data, tfhd.bodyStart) and 0xFFFFFF
                if (flags and 0x1L != 0L) {
                    val p = tfhd.bodyStart + 8
                    put64(data, p, u64(data, p) + delta)
                }
            }
        }
    }

    private fun udta(tags: AudioTags): ByteArray {
        val items = ByteArrayOutputStream()
        items.write(textItem("©nam", tags.title))
        items.write(textItem("©ART", tags.artist))
        tags.album?.takeIf { it.isNotBlank() }?.let { items.write(textItem("©alb", it)) }
        tags.cover?.let { items.write(coverItem(it)) }
        val ilst = box("ilst", items.toByteArray())
        // hdlr «mdir/appl» — без него часть плееров ilst не читает.
        val hdlr =
            box(
                "hdlr",
                int32(0) + int32(0) + "mdir".toByteArray(Charsets.ISO_8859_1) +
                    "appl".toByteArray(Charsets.ISO_8859_1) + ByteArray(9),
            )
        val meta = box("meta", int32(0) + hdlr + ilst)
        return box("udta", meta)
    }

    private fun textItem(type: String, value: String): ByteArray =
        box(type, box("data", int32(1) + int32(0) + value.toByteArray(Charsets.UTF_8)))

    private fun coverItem(image: ByteArray): ByteArray {
        val png = image.size > 3 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte()
        return box("covr", box("data", int32(if (png) 14 else 13) + int32(0) + image))
    }

    private fun box(type: String, body: ByteArray): ByteArray =
        int32(body.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + body

    private fun int32(v: Int): ByteArray = ByteBuffer.allocate(4).putInt(v).array()

    private fun int64(v: Long): ByteArray = ByteBuffer.allocate(8).putLong(v).array()

    private fun u32(d: ByteArray, p: Int): Long =
        ((d[p].toLong() and 0xFF) shl 24) or ((d[p + 1].toLong() and 0xFF) shl 16) or
            ((d[p + 2].toLong() and 0xFF) shl 8) or (d[p + 3].toLong() and 0xFF)

    private fun u64(d: ByteArray, p: Int): Long = (u32(d, p) shl 32) or u32(d, p + 4)

    private fun put32(d: ByteArray, p: Int, v: Long) {
        d[p] = (v shr 24).toByte()
        d[p + 1] = (v shr 16).toByte()
        d[p + 2] = (v shr 8).toByte()
        d[p + 3] = v.toByte()
    }

    private fun put64(d: ByteArray, p: Int, v: Long) {
        put32(d, p, v ushr 32)
        put32(d, p + 4, v and 0xFFFFFFFFL)
    }
}
