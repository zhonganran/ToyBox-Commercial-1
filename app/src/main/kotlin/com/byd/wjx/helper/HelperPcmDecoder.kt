package com.byd.wjx.helper

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val PCM_BYTES_PER_SAMPLE = 2
private const val PCM_CODEC_TIMEOUT_US = 10_000L
private const val PCM_DECODE_TIMEOUT_MS = 8_000L
private const val PCM_MAX_CACHE_BYTES = 24L * 1024L * 1024L

internal data class PcmConfig(
    val path: String = "",
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val bytes: Long = 0L,
) {
    fun usable(): Boolean =
        path.isNotBlank() && sampleRate in 8_000..192_000 && channelCount in 1..2 && bytes > 0L
}

internal object HelperPcmDecoder {
    private const val TAG = "LockSound"

    fun decodeToPcm16(source: File, output: File, key: String): PcmConfig? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return runCatching {
            val mediaExtractor = MediaExtractor()
            extractor = mediaExtractor
            mediaExtractor.setDataSource(source.absolutePath)
            var selectedTrack = -1
            var selectedFormat: MediaFormat? = null
            for (index in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    selectedTrack = index
                    selectedFormat = format
                    break
                }
            }
            val inputFormat = selectedFormat ?: return@runCatching null
            if (selectedTrack < 0) return@runCatching null
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
            mediaExtractor.selectTrack(selectedTrack)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputRate = mediaFormatInt(inputFormat, MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var outputChannels = mediaFormatInt(inputFormat, MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var outputEncoding = mediaFormatInt(inputFormat, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            var totalBytes = 0L
            val startedAt = System.currentTimeMillis()
            output.parentFile?.mkdirs()
            output.outputStream().use { out ->
                while (!outputDone) {
                    if (System.currentTimeMillis() - startedAt > PCM_DECODE_TIMEOUT_MS) {
                        throw IllegalStateException("pcm decode timeout key=$key")
                    }
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(PCM_CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                            if (inputBuffer == null) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                inputBuffer.clear()
                                val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(inputIndex, 0, sampleSize, mediaExtractor.sampleTime, 0)
                                    mediaExtractor.advance()
                                }
                            }
                        }
                    }

                    when (val outputIndex = decoder.dequeueOutputBuffer(info, PCM_CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            outputRate = mediaFormatInt(format, MediaFormat.KEY_SAMPLE_RATE, outputRate)
                            outputChannels = mediaFormatInt(format, MediaFormat.KEY_CHANNEL_COUNT, outputChannels).coerceAtLeast(1)
                            outputEncoding = mediaFormatInt(format, MediaFormat.KEY_PCM_ENCODING, outputEncoding)
                            helperLog(TAG, "pcm format key=$key rate=$outputRate channels=$outputChannels encoding=$outputEncoding")
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (inputDone) runCatching { Thread.sleep(2L) }
                        }
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                val chunk = ByteArray(info.size)
                                outputBuffer.get(chunk)
                                totalBytes += writePcm16Chunk(
                                    out = out,
                                    chunk = chunk,
                                    encoding = outputEncoding,
                                    sourceChannels = outputChannels,
                                    outputChannels = outputChannels.coerceIn(1, 2),
                                )
                                if (totalBytes > PCM_MAX_CACHE_BYTES) {
                                    throw IllegalStateException("pcm cache too large key=$key bytes=$totalBytes")
                                }
                            }
                            outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            if (totalBytes <= 0L) {
                null
            } else {
                PcmConfig(
                    path = output.absolutePath,
                    sampleRate = outputRate,
                    channelCount = outputChannels.coerceIn(1, 2),
                    bytes = totalBytes,
                )
            }
        }.getOrElse {
            helperLog(TAG, "pcm decode failed key=$key source=${source.name}: ${throwableSummary(it)}")
            runCatching { output.delete() }
            null
        }.also {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun mediaFormatInt(format: MediaFormat, key: String, fallback: Int): Int =
        if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrDefault(fallback) else fallback

    internal fun writePcm16Chunk(
        out: OutputStream,
        chunk: ByteArray,
        encoding: Int,
        sourceChannels: Int,
        outputChannels: Int,
    ): Long = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> writePcm16Bytes(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_FLOAT -> writePcmFloatAs16(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_8BIT -> writePcm8As16(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> writePcm24PackedAs16(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_32BIT -> writePcm32As16(out, chunk, sourceChannels, outputChannels)
        else -> 0L
    }

    private fun writePcm16Bytes(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val sourceFrameBytes = srcChannels * PCM_BYTES_PER_SAMPLE
        val outputFrameBytes = dstChannels * PCM_BYTES_PER_SAMPLE
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        if (srcChannels == dstChannels) {
            val aligned = chunk.size - (chunk.size % sourceFrameBytes)
            if (aligned <= 0) return 0L
            out.write(chunk, 0, aligned)
            return aligned.toLong()
        }
        val frame = ByteArray(outputFrameBytes)
        var pos = 0
        var written = 0L
        while (pos + sourceFrameBytes <= chunk.size) {
            System.arraycopy(chunk, pos, frame, 0, outputFrameBytes.coerceAtMost(sourceFrameBytes))
            out.write(frame, 0, outputFrameBytes)
            written += outputFrameBytes
            pos += sourceFrameBytes
        }
        return written
    }

    private fun writePcm24PackedAs16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val bytesPerSourceSample = 3
        val sourceFrameBytes = srcChannels * bytesPerSourceSample
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var pos = 0
        var written = 0L
        while (pos + sourceFrameBytes <= chunk.size) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                val samplePos = pos + channel * bytesPerSourceSample
                val sample24 =
                    (chunk[samplePos].toInt() and 0xff) or
                        ((chunk[samplePos + 1].toInt() and 0xff) shl 8) or
                        ((chunk[samplePos + 2].toInt() and 0xff) shl 16)
                val signed24 = if ((sample24 and 0x800000) != 0) sample24 or -0x1000000 else sample24
                if (channel < dstChannels) {
                    writeInt16ToFrame(frame, channel, signed24 shr 8)
                }
            }
            out.write(frame)
            written += frame.size
            pos += sourceFrameBytes
        }
        return written
    }

    private fun writePcm32As16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val bytesPerSourceSample = 4
        val sourceFrameBytes = srcChannels * bytesPerSourceSample
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var written = 0L
        while (bb.remaining() >= sourceFrameBytes) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                val sample32 = bb.int
                if (channel < dstChannels) {
                    writeInt16ToFrame(frame, channel, sample32 shr 16)
                }
            }
            out.write(frame)
            written += frame.size
        }
        return written
    }

    private fun writePcmFloatAs16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val sourceFrameBytes = srcChannels * 4
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var written = 0L
        while (bb.remaining() >= sourceFrameBytes) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                val sample = bb.float
                if (channel < dstChannels) {
                    val intSample = (sample.coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    writeInt16ToFrame(frame, channel, intSample)
                }
            }
            out.write(frame)
            written += frame.size
        }
        return written
    }

    private fun writePcm8As16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        if (chunk.size < srcChannels) return 0L
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var pos = 0
        var written = 0L
        while (pos + srcChannels <= chunk.size) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                if (channel < dstChannels) {
                    val centered = (chunk[pos + channel].toInt() and 0xff) - 128
                    val intSample = (centered shl 8).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    writeInt16ToFrame(frame, channel, intSample)
                }
            }
            out.write(frame)
            written += frame.size
            pos += srcChannels
        }
        return written
    }

    private fun writeInt16ToFrame(frame: ByteArray, channel: Int, sample: Int) {
        val intSample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val dst = channel * PCM_BYTES_PER_SAMPLE
        if (dst + 1 >= frame.size) return
        frame[dst] = (intSample and 0xff).toByte()
        frame[dst + 1] = ((intSample ushr 8) and 0xff).toByte()
    }
}
