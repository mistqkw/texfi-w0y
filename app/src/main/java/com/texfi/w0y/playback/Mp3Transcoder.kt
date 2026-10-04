package com.texfi.w0y.playback

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * Любой звук, который понимает Android (Opus/WebM, AAC/m4a), — в MP3.
 *
 * Декодирует системным MediaCodec в PCM и сразу, кусками, отдаёт в
 * [Mp3Encoder]: весь трек в несжатом виде в памяти не лежит.
 */
object Mp3Transcoder {
    fun transcode(input: File, out: OutputStream, kbps: Int) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(input.path)
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("в файле нет звуковой дорожки")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var encoder: Mp3Encoder? = null
            var channels = 0
            var outChannels = 0
            var float = false
            var pcm = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        outChannels = if (channels == 1) 1 else 2
                        float =
                            outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        if (encoder == null) {
                            encoder = Mp3Encoder(outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), outChannels, kbps, out)
                        }
                    }

                    index >= 0 -> {
                        val buffer = decoder.getOutputBuffer(index)!!
                        if (info.size > 0) {
                            val enc = encoder ?: error("декодер не сообщил формат")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val ordered = buffer.order(ByteOrder.nativeOrder())
                            val samples = if (float) info.size / 4 else info.size / 2
                            val frames = samples / channels
                            if (pcm.size < frames * outChannels) pcm = ShortArray(frames * outChannels)
                            if (float) {
                                val src = ordered.asFloatBuffer()
                                for (f in 0 until frames) {
                                    for (c in 0 until outChannels) {
                                        val v = src.get(f * channels + c).coerceIn(-1f, 1f)
                                        pcm[f * outChannels + c] = (v * Short.MAX_VALUE).toInt().toShort()
                                    }
                                }
                            } else {
                                val src = ordered.asShortBuffer()
                                if (channels == outChannels) {
                                    src.get(pcm, 0, frames * channels)
                                } else {
                                    // Больше двух каналов — берём передние левый и правый.
                                    for (f in 0 until frames) {
                                        pcm[f * 2] = src.get(f * channels)
                                        pcm[f * 2 + 1] = src.get(f * channels + 1)
                                    }
                                }
                            }
                            enc.encode(pcm, frames)
                        }
                        decoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            (encoder ?: error("в файле нет звука")).finish()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private const val TIMEOUT_US = 10_000L
}
