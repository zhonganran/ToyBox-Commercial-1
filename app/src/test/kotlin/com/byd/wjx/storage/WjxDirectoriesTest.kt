package com.byd.wjx.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WjxDirectoriesTest {
    @Test
    fun `shared WJX directory lives at local storage root`() {
        assertEquals("WJX", WjxDirectories.baseDir.name)
        assertEquals(WjxDirectories.baseDir, WjxDirectories.soundDir)
        assertEquals("wjx.log", WjxDirectories.appLog.name)
        assertFalse(WjxDirectories.baseDir.path.contains("Android/data"))
    }
}
