package com.texfi.w0y.playback

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.OutputStream

/**
 * MP3 с постоянным битрейтом через LAME (порт jump3r на чистой Java).
 *
 * Высокоуровневый `LameEncoder` из jump3r завязан на `javax.sound`, которого
 * в Android нет, поэтому модули LAME собираются здесь вручную — в том же
 * порядке, что и у него. Сэмплы — 16 бит; внутрь LAME они идут 32-битными
 * на полную шкалу, как он и ждёт.
 */
class Mp3Encoder(
    sampleRate: Int,
    private val channels: Int,
    kbps: Int,
    private val out: OutputStream,
) {
    private val lame = Lame()
    private val flags: LameGlobalFlags
    private var left = IntArray(0)
    private var right = IntArray(0)
    private var buffer = ByteArray(0)

    init {
        require(channels == 1 || channels == 2) { "каналов: $channels" }
        val ga = GainAnalysis()
        val bs = BitStream()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val quantize = Quantize()
        val vbr = VBRTag()
        val version = Version()
        val id3 = ID3Tag()
        val reservoir = Reservoir()
        val takehiro = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()
        lame.setModules(ga, bs, presets, qupvt, quantize, vbr, version, id3, mpg)
        bs.setModules(ga, mpg, version, vbr)
        id3.setModules(bs, version)
        presets.setModules(lame)
        quantize.setModules(bs, reservoir, qupvt, takehiro)
        qupvt.setModules(takehiro, reservoir, lame.enc.psy)
        reservoir.setModules(bs)
        takehiro.setModules(qupvt)
        vbr.setModules(lame, bs, version)
        GetAudio().setModules(parse, mpg)
        parse.setModules(version, id3, presets)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        flags = lame.lame_init()
        flags.num_channels = channels
        flags.in_samplerate = sampleRate
        // MPEG-1 Layer III знает только 32/44.1/48 кГц; остальное LAME пересэмплирует.
        flags.out_samplerate = if (sampleRate in NATIVE_RATES) sampleRate else 44_100
        flags.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        flags.VBR = VbrMode.vbr_off
        flags.brate = kbps
        flags.quality = QUALITY
        id3.id3tag_init(flags)
        // Теги пишем сами (ID3v2.3 с обложкой), LAME не должен добавлять свои.
        flags.write_id3tag_automatic = false
        flags.findReplayGain = false
        flags.bWriteVbrTag = false
        check(lame.lame_init_params(flags) >= 0) { "LAME не принял параметры" }
    }

    /**
     * [pcm] — чередующиеся 16-битные сэмплы, [frames] — сколько их на канал.
     */
    fun encode(pcm: ShortArray, frames: Int) {
        if (frames <= 0) return
        if (left.size < frames) {
            left = IntArray(frames)
            right = IntArray(frames)
            buffer = ByteArray(frames * 5 / 4 + 7200)
        }
        if (channels == 2) {
            for (i in 0 until frames) {
                left[i] = pcm[2 * i].toInt() shl 16
                right[i] = pcm[2 * i + 1].toInt() shl 16
            }
        } else {
            for (i in 0 until frames) left[i] = pcm[i].toInt() shl 16
        }
        val n = lame.lame_encode_buffer_int(flags, left, if (channels == 2) right else left, frames, buffer, 0, buffer.size)
        check(n >= 0) { "LAME: ошибка кодирования $n" }
        out.write(buffer, 0, n)
    }

    /** Дописывает хвост; после него кодер не используется. */
    fun finish() {
        val tail = ByteArray(7200)
        val n = lame.lame_encode_flush(flags, tail, 0, tail.size)
        if (n > 0) out.write(tail, 0, n)
        lame.lame_close(flags)
    }

    private companion object {
        val NATIVE_RATES = setOf(32_000, 44_100, 48_000)

        /** 5 — стандартное качество LAME: заметно быстрее 2, на 320 кбит/с разницы на слух нет. */
        const val QUALITY = 5
    }
}
