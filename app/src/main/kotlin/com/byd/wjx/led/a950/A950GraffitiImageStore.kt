package com.byd.wjx.led.a950

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class A950GraffitiAsset(
    val previewPath: String,
    val payloadPath: String,
    val version: Long,
)

sealed interface A950GraffitiSaveResult {
    data class Success(val asset: A950GraffitiAsset) : A950GraffitiSaveResult
    data class Failure(val message: String) : A950GraffitiSaveResult
}

@Singleton
class A950GraffitiImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun save(uri: Uri): A950GraffitiSaveResult = withContext(Dispatchers.IO) {
        try {
            val gifHeader = readHeader(uri)
            val originalGif = if (gifCanvasSize(gifHeader) != null) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext A950GraffitiSaveResult.Failure("无法读取 GIF 文件")
            } else {
                null
            }
            if (originalGif != null && originalGif.size > MAX_GIF_BYTES) {
                return@withContext A950GraffitiSaveResult.Failure("GIF 文件不能超过 4 MB")
            }
            val gifSize = originalGif?.let(::gifCanvasSize)
            if (gifSize != null && (gifSize.first != WIDTH || gifSize.second != HEIGHT)) {
                return@withContext A950GraffitiSaveResult.Failure(
                    "GIF 尺寸必须是 96 × 16，当前为 ${gifSize.first} × ${gifSize.second}",
                )
            }

            val source = if (originalGif != null) {
                BitmapFactory.decodeByteArray(originalGif, 0, originalGif.size)
            } else {
                decodeSampled(uri)
            } ?: return@withContext A950GraffitiSaveResult.Failure("图片解码失败")
            val normalized = normalize(source)
            if (normalized !== source) source.recycle()
            val payloadBytes = originalGif ?: A950SingleFrameGifEncoder.encode(normalized)

            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val preview = File(directory, PREVIEW_FILE)
            val payload = File(directory, PAYLOAD_FILE)
            val previewTemp = File(directory, "$PREVIEW_FILE.tmp")
            val payloadTemp = File(directory, "$PAYLOAD_FILE.tmp")
            try {
                previewTemp.outputStream().use { output ->
                    check(normalized.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                payloadTemp.writeBytes(payloadBytes)
                replace(previewTemp, preview)
                replace(payloadTemp, payload)
            } finally {
                previewTemp.delete()
                payloadTemp.delete()
                normalized.recycle()
            }
            A950GraffitiSaveResult.Success(
                A950GraffitiAsset(
                    previewPath = preview.absolutePath,
                    payloadPath = payload.absolutePath,
                    version = System.currentTimeMillis(),
                )
            )
        } catch (e: Exception) {
            A950GraffitiSaveResult.Failure(e.message ?: "图片处理失败")
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DIRECTORY)
        File(directory, PREVIEW_FILE).delete()
        File(directory, PAYLOAD_FILE).delete()
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_DECODE_SIDE * 2 ||
            bounds.outHeight / sampleSize > MAX_DECODE_SIDE * 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun readHeader(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            ByteArray(GIF_HEADER_BYTES).let { header ->
                val count = input.read(header)
                if (count == header.size) header else header.copyOf(count.coerceAtLeast(0))
            }
        } ?: byteArrayOf()

    private fun replace(source: File, target: File) {
        if (target.exists()) check(target.delete())
        check(source.renameTo(target))
    }

    companion object {
        const val WIDTH = 96
        const val HEIGHT = 16
        private const val MAX_DECODE_SIDE = 2_048
        private const val MAX_GIF_BYTES = 4 * 1024 * 1024
        private const val GIF_HEADER_BYTES = 10
        private const val DIRECTORY = "led/a950"
        private const val PREVIEW_FILE = "condition_release.png"
        private const val PAYLOAD_FILE = "condition_release.gif"

        internal fun gifCanvasSize(payload: ByteArray): Pair<Int, Int>? {
            if (payload.size < GIF_HEADER_BYTES) return null
            val signature = payload.copyOfRange(0, 6).toString(Charsets.US_ASCII)
            if (signature != "GIF87a" && signature != "GIF89a") return null
            val width = (payload[6].toInt() and 0xFF) or
                ((payload[7].toInt() and 0xFF) shl 8)
            val height = (payload[8].toInt() and 0xFF) or
                ((payload[9].toInt() and 0xFF) shl 8)
            return width to height
        }

        internal fun normalize(source: Bitmap): Bitmap {
            val sourceAspect = source.width.toFloat() / source.height.toFloat()
            val targetAspect = WIDTH.toFloat() / HEIGHT.toFloat()
            val crop = if (sourceAspect > targetAspect) {
                val width = (source.height * targetAspect).roundToInt().coerceAtMost(source.width)
                val left = (source.width - width) / 2
                Rect(left, 0, left + width, source.height)
            } else {
                val height = (source.width / targetAspect).roundToInt().coerceAtMost(source.height)
                val top = (source.height - height) / 2
                Rect(0, top, source.width, top + height)
            }
            val cropped = Bitmap.createBitmap(
                source,
                crop.left,
                crop.top,
                crop.width(),
                crop.height(),
            )
            val scaled = Bitmap.createScaledBitmap(cropped, WIDTH, HEIGHT, true)
            val target = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (cropped !== source && cropped !== scaled && cropped !== target) cropped.recycle()
            if (scaled !== source && scaled !== target) scaled.recycle()

            val pixels = IntArray(WIDTH * HEIGHT)
            target.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            pixels.forEachIndexed { index, color ->
                val alpha = Color.alpha(color)
                if (alpha < 255) {
                    pixels[index] = Color.rgb(
                        Color.red(color) * alpha / 255,
                        Color.green(color) * alpha / 255,
                        Color.blue(color) * alpha / 255,
                    )
                }
            }
            target.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            return target
        }
    }
}

internal object A950SingleFrameGifEncoder {
    fun encode(bitmap: Bitmap): ByteArray {
        require(bitmap.width == A950GraffitiImageStore.WIDTH)
        require(bitmap.height == A950GraffitiImageStore.HEIGHT)
        val output = ByteArrayOutputStream()
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        output.writeLittleEndian(bitmap.width)
        output.writeLittleEndian(bitmap.height)
        output.write(0xF7) // Global 256-color table, 8-bit color resolution.
        output.write(0)
        output.write(0)
        writeRgb332Palette(output)

        output.write(0x2C)
        repeat(2) { output.writeLittleEndian(0) }
        output.writeLittleEndian(bitmap.width)
        output.writeLittleEndian(bitmap.height)
        output.write(0)
        output.write(8)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val compressed = BitWriter().apply {
            pixels.forEach { color ->
                write(CLEAR_CODE, CODE_SIZE)
                write(rgb332Index(color), CODE_SIZE)
            }
            write(END_CODE, CODE_SIZE)
        }.toByteArray()
        compressed.asList().chunked(255).forEach { block ->
            output.write(block.size)
            block.forEach { output.write(it.toInt() and 0xFF) }
        }
        output.write(0)
        output.write(0x3B)
        return output.toByteArray()
    }

    private fun writeRgb332Palette(output: ByteArrayOutputStream) {
        repeat(256) { index ->
            val red = ((index ushr 5) and 0x07) * 255 / 7
            val green = ((index ushr 2) and 0x07) * 255 / 7
            val blue = (index and 0x03) * 255 / 3
            output.write(red)
            output.write(green)
            output.write(blue)
        }
    }

    private fun rgb332Index(color: Int): Int {
        val red = (Color.red(color) * 7 + 127) / 255
        val green = (Color.green(color) * 7 + 127) / 255
        val blue = (Color.blue(color) * 3 + 127) / 255
        return (red shl 5) or (green shl 2) or blue
    }

    private fun ByteArrayOutputStream.writeLittleEndian(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private class BitWriter {
        private val bytes = ByteArrayOutputStream()
        private var pending = 0
        private var pendingBits = 0

        fun write(value: Int, bitCount: Int) {
            pending = pending or (value shl pendingBits)
            pendingBits += bitCount
            while (pendingBits >= 8) {
                bytes.write(pending and 0xFF)
                pending = pending ushr 8
                pendingBits -= 8
            }
        }

        fun toByteArray(): ByteArray {
            if (pendingBits > 0) bytes.write(pending and 0xFF)
            pending = 0
            pendingBits = 0
            return bytes.toByteArray()
        }
    }

    private const val CLEAR_CODE = 256
    private const val END_CODE = 257
    private const val CODE_SIZE = 9
}
