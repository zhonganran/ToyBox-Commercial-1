package com.byd.wjx.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.led.ipixelfa.IpixelFaPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class IpixelFaPresetPreviewCatalogTest {
    @Test
    fun `all firmware presets have unique 96 by 16 gif previews`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val resourceIds = IpixelFaPreset.entries.map(IpixelFaPresetPreviewCatalog::resourceFor)

        assertEquals((1..20).toList(), IpixelFaPreset.entries.map(IpixelFaPreset::presetId))
        assertEquals(IpixelFaPreset.entries.size, resourceIds.distinct().size)
        resourceIds.forEach { resourceId ->
            val header = resources.openRawResource(resourceId).use { stream ->
                ByteArray(GIF_HEADER_SIZE).also { bytes ->
                    assertEquals(GIF_HEADER_SIZE, stream.read(bytes))
                }
            }
            assertTrue(String(header, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a"))
            val dimensions = ByteBuffer.wrap(header, 6, 4).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(96, dimensions.short.toInt() and 0xffff)
            assertEquals(16, dimensions.short.toInt() and 0xffff)
        }
    }

    companion object {
        private const val GIF_HEADER_SIZE = 10
    }
}
