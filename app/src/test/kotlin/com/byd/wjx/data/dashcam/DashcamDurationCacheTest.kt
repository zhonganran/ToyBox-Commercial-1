package com.byd.wjx.data.dashcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashcamDurationCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `duration survives repository recreation`() {
        val cacheFile = temporaryFolder.newFile("durations.properties")
        val fingerprint = DashcamVideoFingerprint("content://video/1", 1024L, 2000L)
        val cache = DashcamDurationCache(cacheFile)

        cache.put(fingerprint, 125_000L)
        assertTrue(cache.persist())

        assertEquals(125_000L, DashcamDurationCache(cacheFile)[fingerprint])
    }

    @Test
    fun `changed or removed files invalidate cached duration`() {
        val cacheFile = temporaryFolder.newFile("durations.properties")
        val original = DashcamVideoFingerprint("content://video/1", 1024L, 2000L)
        val changed = original.copy(length = 2048L)
        val cache = DashcamDurationCache(cacheFile)
        cache.put(original, 125_000L)

        assertNull(cache[changed])
        cache.put(changed, 126_000L)
        cache.retainAll(emptySet())
        assertTrue(cache.persist())
        assertFalse(cacheFile.exists())
    }

    @Test
    fun `corrupt entries are ignored without losing valid entries`() {
        val cacheFile = temporaryFolder.newFile("durations.properties").apply {
            writeText(
                "content\\://video/valid=1024\\t2000\\t125000\n" +
                    "content\\://video/corrupt=not-a-cache-record\n",
            )
        }
        val cache = DashcamDurationCache(cacheFile)

        assertEquals(
            125_000L,
            cache[DashcamVideoFingerprint("content://video/valid", 1024L, 2000L)],
        )
        assertNull(cache[DashcamVideoFingerprint("content://video/corrupt", 1024L, 2000L)])
    }
}
