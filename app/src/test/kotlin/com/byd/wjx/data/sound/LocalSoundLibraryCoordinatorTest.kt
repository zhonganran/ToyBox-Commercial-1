package com.byd.wjx.data.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.storage.SoundLibrary
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LocalSoundLibraryCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        context.getSharedPreferences("lock_sound", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `import target accepts supported extension case insensitively`() {
        val target = resolveLocalSoundImportTarget("Welcome.MP3", "application/octet-stream")

        assertEquals("Welcome", target.baseName)
        assertEquals("mp3", target.extension)
    }

    @Test
    fun `import target uses audio mime when provider omits extension`() {
        val target = resolveLocalSoundImportTarget("欢迎音效", "audio/x-wav")

        assertEquals("欢迎音效", target.baseName)
        assertEquals("wav", target.extension)
    }

    @Test(expected = IllegalStateException::class)
    fun `import target rejects unsupported explicit extension`() {
        resolveLocalSoundImportTarget("not-a-sound.exe", "audio/mpeg")
    }

    @Test
    fun `deleting sound clears vehicle references and reports failed helper sync`() = runTest {
        val soundLibrary = mockk<SoundLibrary>()
        val configSync = mockk<LockSoundConfigSync>()
        val file = File(context.cacheDir, "shared.wav")
        LockSoundSettings(context).apply {
            setSoundPath(LockSoundEvent.Lock, file.absolutePath)
            setEnabled(LockSoundEvent.Lock, true)
            setSoundPath(LockSoundEvent.Sentry, file.absolutePath)
            setEnabled(LockSoundEvent.Sentry, true)
        }
        every { soundLibrary.deleteSound(file.absolutePath) } returns true
        every { soundLibrary.listSounds() } returns emptyList()
        coEvery { configSync.sync("local_sound_delete") } returns false

        val result = coordinator(soundLibrary, configSync).deleteSound(file.absolutePath)

        val config = LockSoundSettings(context).snapshot()
        assertTrue(result.deleted)
        assertEquals(false, result.eventSoundSyncSucceeded)
        assertFalse(config.lockEnabled)
        assertFalse(config.sentryEnabled)
        assertEquals("", config.lockPath)
        assertEquals("", config.sentryPath)
        coVerify(exactly = 1) { configSync.sync("local_sound_delete") }
    }

    @Test
    fun `failed deletion keeps vehicle references and skips helper sync`() = runTest {
        val soundLibrary = mockk<SoundLibrary>()
        val configSync = mockk<LockSoundConfigSync>()
        val file = File(context.cacheDir, "missing.mp3")
        val item = SoundLibrary.SoundItem(file)
        LockSoundSettings(context).apply {
            setSoundPath(LockSoundEvent.Unlock, file.absolutePath)
            setEnabled(LockSoundEvent.Unlock, true)
        }
        every { soundLibrary.deleteSound(file.absolutePath) } returns false
        every { soundLibrary.listSounds() } returns listOf(item)

        val result = coordinator(soundLibrary, configSync).deleteSound(file.absolutePath)

        val config = LockSoundSettings(context).snapshot()
        assertFalse(result.deleted)
        assertNull(result.eventSoundSyncSucceeded)
        assertTrue(config.unlockEnabled)
        assertEquals(file.absolutePath, config.unlockPath)
        coVerify(exactly = 0) { configSync.sync(any()) }
    }

    @Test
    fun `move returns refreshed library order`() = runTest {
        val soundLibrary = mockk<SoundLibrary>()
        val configSync = mockk<LockSoundConfigSync>()
        val first = SoundLibrary.SoundItem(File(context.cacheDir, "first.mp3"))
        val second = SoundLibrary.SoundItem(File(context.cacheDir, "second.mp3"))
        every { soundLibrary.moveSound(first.file.absolutePath, second.file.absolutePath) } just Runs
        every { soundLibrary.listSounds() } returns listOf(second, first)

        val sounds = coordinator(soundLibrary, configSync).moveSound(
            first.file.absolutePath,
            second.file.absolutePath,
        )

        assertEquals(listOf(second, first), sounds)
        verify(exactly = 1) {
            soundLibrary.moveSound(first.file.absolutePath, second.file.absolutePath)
        }
    }

    private fun coordinator(
        soundLibrary: SoundLibrary,
        configSync: LockSoundConfigSync,
    ) = LocalSoundLibraryCoordinator(context, soundLibrary, configSync)
}
