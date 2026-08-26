package com.byd.wjx.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.audio.LockSoundConfig
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.data.sound.LocalSoundLibraryCoordinator
import com.byd.wjx.storage.SoundLibrary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class VehicleEventSoundCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        context.getSharedPreferences("lock_sound", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `premium denial keeps event disabled and returns structured error`() = runTest {
        val activationManager = mockk<ActivationManager>()
        val soundLibrary = mockk<SoundLibrary>()
        every { activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS) } returns false
        every { soundLibrary.listSounds() } returns emptyList()

        val change = coordinator(activationManager, soundLibrary).setEnabled(LockSoundEvent.Lock, true)

        assertFalse(change.state.config.lockEnabled)
        assertTrue(change.state.statusIsError)
        assertTrue(change.state.status.orEmpty().contains("需要激活"))
        assertNull(change.syncReason)
    }

    @Test
    fun `enabling event selects first local sound before requesting sync`() = runTest {
        val activationManager = mockk<ActivationManager>()
        val soundLibrary = mockk<SoundLibrary>()
        val file = File(context.cacheDir, "lock-event.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val sound = SoundLibrary.SoundItem(file)
        every { activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS) } returns true
        every { soundLibrary.isManagedSound(any()) } returns false
        every { soundLibrary.listSounds() } returns listOf(sound)

        val change = coordinator(activationManager, soundLibrary).setEnabled(LockSoundEvent.Lock, true)

        assertTrue(change.state.config.lockEnabled)
        assertEquals(file.absolutePath, change.state.config.lockPath)
        assertEquals("settings:lock:enabled", change.syncReason)
        assertFalse(change.state.statusIsError)
    }

    @Test
    fun `deleting selected sound clears every event that references it`() = runTest {
        val activationManager = mockk<ActivationManager>()
        val soundLibrary = mockk<SoundLibrary>()
        val file = File(context.cacheDir, "shared-event.wav").apply { writeBytes(byteArrayOf(1)) }
        LockSoundSettings(context).apply {
            setSoundPath(LockSoundEvent.Lock, file.absolutePath)
            setEnabled(LockSoundEvent.Lock, true)
            setSoundPath(LockSoundEvent.Unlock, file.absolutePath)
            setEnabled(LockSoundEvent.Unlock, true)
        }
        every { activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS) } returns true
        every { soundLibrary.deleteSound(file.absolutePath) } returns true
        every { soundLibrary.listSounds() } returns emptyList()
        val configSync = mockk<LockSoundConfigSync>()
        coEvery { configSync.sync("local_sound_delete") } returns true

        val change = coordinator(activationManager, soundLibrary, configSync).deleteSound(file.absolutePath)

        assertFalse(change.state.config.lockEnabled)
        assertFalse(change.state.config.unlockEnabled)
        assertEquals("", change.state.config.lockPath)
        assertEquals("", change.state.config.unlockPath)
        assertNull(change.syncReason)
        assertFalse(change.state.statusIsError)
        coVerify(exactly = 1) { configSync.sync("local_sound_delete") }
    }

    @Test
    fun `failed helper sync is exposed as an error without losing saved state`() = runTest {
        val activationManager = mockk<ActivationManager>()
        val soundLibrary = mockk<SoundLibrary>()
        val configSync = mockk<LockSoundConfigSync>()
        every { activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS) } returns true
        every { soundLibrary.listSounds() } returns emptyList()
        coEvery { configSync.sync("manual") } returns false
        LockSoundSettings(context).setEnabled(LockSoundEvent.Lock, true)

        val state = coordinator(activationManager, soundLibrary, configSync).sync("manual")

        assertTrue(state.statusIsError)
        assertTrue(state.config.lockEnabled)
        assertTrue(state.status.orEmpty().contains("稍后会自动重试"))
        coVerify(exactly = 1) { configSync.sync("manual") }
    }

    @Test
    fun `ui state preserves prior status on refresh and always exposes syncing`() {
        val previous = SettingsVehicleEventSoundUiState(status = "已保存")
        val refreshed = previous.merge(
            VehicleEventSoundState(
                config = LockSoundConfig(lockEnabled = true),
                choices = emptyList(),
            ),
            syncing = true,
        )

        assertEquals("已保存", refreshed.status)
        assertEquals("同步中…", refreshed.displayStatus)
        assertTrue(refreshed.config.lockEnabled)
    }

    private fun coordinator(
        activationManager: ActivationManager,
        soundLibrary: SoundLibrary,
        configSync: LockSoundConfigSync = mockk(relaxed = true),
    ) = VehicleEventSoundCoordinator(
        context,
        activationManager,
        soundLibrary,
        LocalSoundLibraryCoordinator(context, soundLibrary, configSync),
        configSync,
    )
}
