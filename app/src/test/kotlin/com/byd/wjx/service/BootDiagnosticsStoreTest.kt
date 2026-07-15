package com.byd.wjx.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BootDiagnosticsStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BootState.deviceProtectedContext(context)
            .getSharedPreferences(BootDiagnosticsStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `boot event and method are read from device protected storage`() {
        assertTrue(BootDiagnosticsStore.recordBootEvent(context, "locked_boot"))
        assertTrue(BootDiagnosticsStore.updateBootMethod(context, "KeepAlive"))

        val snapshot = requireNotNull(BootDiagnosticsStore.read(context))
        assertTrue(snapshot.lastBootTimestamp > 0L)
        assertEquals("locked_boot", snapshot.lastBootAction)
        assertEquals("KeepAlive", snapshot.lastBootMethod)
    }

    @Test
    fun `chain log keeps only the latest twenty entries`() {
        repeat(25) { index ->
            assertTrue(BootDiagnosticsStore.appendChainEntry(context, "entry-$index"))
        }

        val lines = requireNotNull(BootDiagnosticsStore.read(context)?.chainLog).lines()
        assertEquals(20, lines.size)
        assertTrue(lines.first().endsWith("entry-5"))
        assertTrue(lines.last().endsWith("entry-24"))
    }
}
