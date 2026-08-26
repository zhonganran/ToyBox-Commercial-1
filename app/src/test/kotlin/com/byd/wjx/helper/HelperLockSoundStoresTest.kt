package com.byd.wjx.helper

import com.byd.wjx.audio.ExteriorChannel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HelperLockSoundStoresTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `corrupt runtime config falls back to safe defaults`() {
        val file = temporaryFolder.newFile("lock-sound.json").apply { writeText("{broken") }

        val config = LockSoundConfigStore(file).read()

        assertEquals(ExteriorChannel.Channel1, config.channel)
        assertFalse(config.unlock.enabled)
        assertFalse(config.lock.enabled)
        assertFalse(config.sentry.enabled)
    }

    @Test
    fun `runtime config round trip only restores files that still exist`() {
        val configFile = temporaryFolder.newFile("lock-sound.json")
        val unlockFile = temporaryFolder.newFile("unlock.wav").apply { writeBytes(byteArrayOf(1)) }
        val pcmFile = temporaryFolder.newFile("unlock.pcm").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val missingFile = temporaryFolder.root.resolve("missing.wav")
        val store = LockSoundConfigStore(configFile)
        store.write(
            LockSoundRuntimeConfig(
                channel = ExteriorChannel.Channel0,
                unlock = EventConfig(
                    enabled = true,
                    path = unlockFile.absolutePath,
                    pcm = PcmConfig(
                        path = pcmFile.absolutePath,
                        sampleRate = 44_100,
                        channelCount = 2,
                        bytes = pcmFile.length(),
                    ),
                ),
                lock = EventConfig(enabled = true, path = missingFile.absolutePath),
            ),
        )

        val restored = store.read()

        assertEquals(ExteriorChannel.Channel0, restored.channel)
        assertTrue(restored.unlock.enabled)
        assertEquals(unlockFile.absolutePath, restored.unlock.path)
        assertEquals(pcmFile.absolutePath, restored.unlock.pcm.path)
        assertFalse(restored.lock.enabled)
        assertEquals(missingFile.absolutePath, restored.lock.path)
    }

    @Test
    fun `lock state round trip preserves pending transition and flash window`() {
        val file = temporaryFolder.newFile("lock-state.json")
        val store = LockSoundStateStore(file)
        val expected = PersistedLockState(
            state = LOCK_SOUND_DOOR_LOCKED,
            changedAtMs = 1_000L,
            sampledAtMs = 2_000L,
            flashValue = 9,
            flashAtMs = 1_900L,
            pendingState = LOCK_SOUND_DOOR_UNLOCKED,
            pendingSinceMs = 1_950L,
        )

        store.write(expected)

        assertEquals(expected, store.read())
        assertFalse(temporaryFolder.root.resolve("lock-state.json.tmp").exists())
        assertEquals(2, JSONObject(file.readText()).getInt("version"))
    }

    @Test
    fun `invalid or corrupt lock state is ignored`() {
        val file = temporaryFolder.newFile("lock-state.json")
        val store = LockSoundStateStore(file)
        file.writeText(JSONObject().put("state", 99).toString())
        assertNull(store.read())

        file.writeText("not-json")
        assertNull(store.read())
    }

    @Test
    fun `sentry reservation round trip keeps timestamp and bounds reason`() {
        val file = temporaryFolder.newFile("sentry-reservation.json")
        val store = SentryPlaybackReservationStore(file)

        store.write(nowMs = 12_345L, reason = "x".repeat(250))

        val json = JSONObject(file.readText())
        assertEquals(12_345L, store.readReservedAt())
        assertEquals(200, json.getString("reason").length)
        assertFalse(temporaryFolder.root.resolve("sentry-reservation.json.tmp").exists())
    }
}
