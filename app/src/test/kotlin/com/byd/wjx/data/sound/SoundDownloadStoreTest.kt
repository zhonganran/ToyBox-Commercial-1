package com.byd.wjx.data.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.storage.WjxDirectories
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SoundDownloadStoreTest {
    private lateinit var context: Context
    private lateinit var soundFile: File
    private lateinit var nestedSoundFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        WjxDirectories.ensureBaseDirs()
        soundFile = File(WjxDirectories.soundDir, "download-store-test.wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        nestedSoundFile = File(WjxDirectories.soundDir, "nested/download-store-test.wav").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }
    }

    @After
    fun tearDown() {
        soundFile.delete()
        nestedSoundFile.delete()
        nestedSoundFile.parentFile?.delete()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `download records use the unified WJX sound directory`() {
        val store = SoundDownloadStore(context)
        store.add(record(soundFile))

        assertEquals(soundFile.name, store.findExistingRecord(SOUND_ID)?.fileName)

        store.removeByFileName(soundFile.name)
        assertTrue(store.loadRecords().isEmpty())
    }

    @Test
    fun `missing downloaded files are removed from records`() {
        val store = SoundDownloadStore(context)
        store.add(record(soundFile))
        soundFile.delete()

        assertTrue(store.loadRecords().isEmpty())
    }

    @Test
    fun `managed sound validation only accepts audio files directly in WJX`() {
        assertTrue(SoundLibrary.isManagedSoundFile(soundFile.absolutePath))
        assertFalse(SoundLibrary.isManagedSoundFile(nestedSoundFile.absolutePath))
        assertFalse(SoundLibrary.isManagedSoundFile(""))

        val temporarilyMissing = File(WjxDirectories.soundDir, "temporarily-missing.wav")
        assertTrue(SoundLibrary.isManagedSoundPath(temporarilyMissing.absolutePath))
        assertFalse(SoundLibrary.isManagedSoundFile(temporarilyMissing.absolutePath))
    }

    private fun record(file: File): SoundDownloadRecord =
        SoundDownloadRecord(
            soundId = SOUND_ID,
            name = file.nameWithoutExtension,
            fileName = file.name,
            sizeLabel = "${file.length()} B",
            finishedAt = System.currentTimeMillis(),
        )

    private companion object {
        const val SOUND_ID = 900_001L
        const val PREF_NAME = "wjx_sound_downloads"
    }
}
