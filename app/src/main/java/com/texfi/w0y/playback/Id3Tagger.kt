package com.texfi.w0y.playback

import java.io.ByteArrayOutputStream

/**
 * Тег ID3v2.3 для начала MP3: название, исполнитель, альбом и обложка.
 *
 * Версия 2.3, а не 2.4: её читают все плееры, включая автомагнитолы и
 * Windows. Текст — UTF-16 с BOM, иначе кириллица в старых плеерах
 * превращается в знаки вопроса.
 */
object Id3Tagger {
    fun tag(tags: AudioTags): ByteArray {
        val frames = ByteArrayOutputStream()
        text(frames, "TIT2", tags.title)
        text(frames, "TPE1", tags.artist)
        tags.album?.takeIf { it.isNotBlank() }?.let { text(frames, "TALB", it) }
        tags.cover?.let { picture(frames, it) }
        val body = frames.toByteArray()
        val out = ByteArrayOutputStream(body.size + 10)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        // Размер всего тега — «synchsafe»: по 7 бит в байте.
        val size = body.size
        out.write(byteArrayOf((size shr 21 and 0x7F).toByte(), (size shr 14 and 0x7F).toByte(), (size shr 7 and 0x7F).toByte(), (size and 0x7F).toByte()))
        out.write(body)
        return out.toByteArray()
    }

    private fun text(out: ByteArrayOutputStream, id: String, value: String) {
        if (value.isBlank()) return
        val data = ByteArrayOutputStream()
        data.write(1) // UTF-16 с BOM
        data.write(0xFF)
        data.write(0xFE)
        data.write(value.trim().toByteArray(Charsets.UTF_16LE))
        frame(out, id, data.toByteArray())
    }

    private fun picture(out: ByteArrayOutputStream, image: ByteArray) {
        val mime = if (image.size > 3 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
        val data = ByteArrayOutputStream(image.size + 32)
        data.write(0) // описание в ISO-8859-1
        data.write(mime.toByteArray(Charsets.ISO_8859_1))
        data.write(0)
        data.write(3) // передняя обложка
        data.write(0) // пустое описание
        data.write(image)
        frame(out, "APIC", data.toByteArray())
    }

    /** Кадр 2.3: размер — обычные 32 бита, не synchsafe. */
    private fun frame(out: ByteArrayOutputStream, id: String, data: ByteArray) {
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        val n = data.size
        out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        out.write(byteArrayOf(0, 0))
        out.write(data)
    }
}
