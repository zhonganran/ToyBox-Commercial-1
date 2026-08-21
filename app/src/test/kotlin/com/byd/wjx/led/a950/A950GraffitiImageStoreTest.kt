package com.byd.wjx.led.a950

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class A950GraffitiImageStoreTest {
    @Test
    fun `normalization center crops and scales to the A950 canvas`() {
        val source = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        repeat(source.height) { y ->
            val color = when {
                y < 50 -> Color.RED
                y < 150 -> Color.GREEN
                else -> Color.BLUE
            }
            repeat(source.width) { x -> source.setPixel(x, y, color) }
        }

        val normalized = A950GraffitiImageStore.normalize(source)

        assertEquals(96, normalized.width)
        assertEquals(16, normalized.height)
        assertTrue(Color.green(normalized.getPixel(48, 8)) > 200)
        assertTrue(Color.red(normalized.getPixel(48, 8)) < 40)
        source.recycle()
        normalized.recycle()
    }

    @Test
    fun `single frame payload is a decodable 96 by 16 GIF`() {
        val bitmap = Bitmap.createBitmap(96, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(24, 180, 240))
        }

        val payload = A950SingleFrameGifEncoder.encode(bitmap)
        val decoded = BitmapFactory.decodeByteArray(payload, 0, payload.size)

        assertTrue(payload.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray()))
        assertEquals(96, payload[6].toInt() and 0xFF)
        assertEquals(16, payload[8].toInt() and 0xFF)
        assertNotNull(decoded)
        assertEquals(96, decoded.width)
        assertEquals(16, decoded.height)
        bitmap.recycle()
        decoded.recycle()
    }

    @Test
    fun `an existing 96 by 16 GIF is copied without losing its frames`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(96, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val original = A950SingleFrameGifEncoder.encode(bitmap)
        val source = File(context.cacheDir, "animated-source.gif").apply { writeBytes(original) }

        val result = A950GraffitiImageStore(context).save(Uri.fromFile(source))

        assertTrue(result is A950GraffitiSaveResult.Success)
        val asset = (result as A950GraffitiSaveResult.Success).asset
        assertTrue(File(asset.payloadPath).readBytes().contentEquals(original))
        bitmap.recycle()
    }

    @Test
    fun `a GIF with the wrong canvas size is rejected instead of flattened`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(96, 16, Bitmap.Config.ARGB_8888)
        val wrongSize = A950SingleFrameGifEncoder.encode(bitmap).also {
            it[6] = 64
            it[7] = 0
        }
        val source = File(context.cacheDir, "wrong-size.gif").apply { writeBytes(wrongSize) }

        val result = A950GraffitiImageStore(context).save(Uri.fromFile(source))

        assertTrue(result is A950GraffitiSaveResult.Failure)
        assertTrue((result as A950GraffitiSaveResult.Failure).message.contains("96 × 16"))
        bitmap.recycle()
    }
}
