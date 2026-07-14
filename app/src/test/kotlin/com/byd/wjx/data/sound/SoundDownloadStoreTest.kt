package com.byd.wjx.data.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.storage.SoundDestination
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.storage.WjxDirectories
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var personalFile: File
    private lateinit var vehicleFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        WjxDirectories.ensureVehicleSoundDir()
        personalFile = File(WjxDirectories.soundDir, "download-store-personal-test.wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        vehicleFile = File(WjxDirectories.vehicleSoundDir, "download-store-vehicle-test.wav").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
    }

    @After
    fun tearDown() {
        personalFile.delete()
        vehicleFile.delete()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `same remote sound can be stored in personal and vehicle destinations`() {
        val store = SoundDownloadStore(context)
        store.add(record(personalFile, SoundDestination.PERSONAL))
        store.add(record(vehicleFile, SoundDestination.VEHICLE))

        assertEquals(2, store.loadRecords().size)
        assertNotNull(store.findExistingRecord(SOUND_ID, SoundDestination.PERSONAL))
        assertNotNull(store.findExistingRecord(SOUND_ID, SoundDestination.VEHICLE))

        store.removeByFileName(personalFile.name)
        assertEquals(listOf(SoundDestination.VEHICLE), store.loadRecords().map { it.destination })
    }

    @Test
    fun `legacy download record without destination remains personal`() {
        val legacy = JSONObject()
            .put("soundId", SOUND_ID)
            .put("name", "旧音效")
            .put("fileName", personalFile.name)
            .put("sizeLabel", "3 B")
            .put("finishedAt", 1L)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS, JSONArray().put(legacy).toString())
            .commit()

        val record = SoundDownloadStore(context).loadRecords().single()

        assertEquals(SoundDestination.PERSONAL, record.destination)
    }

    @Test
    fun `vehicle sound validation only accepts files in vehicle directory`() {
        assertTrue(SoundLibrary.isVehicleSoundFile(vehicleFile.absolutePath))
        assertFalse(SoundLibrary.isVehicleSoundFile(personalFile.absolutePath))
        assertFalse(SoundLibrary.isVehicleSoundFile(""))

        val temporarilyMissing = File(WjxDirectories.vehicleSoundDir, "temporarily-missing.wav")
        assertTrue(SoundLibrary.isVehicleSoundPath(temporarilyMissing.absolutePath))
        assertFalse(SoundLibrary.isVehicleSoundFile(temporarilyMissing.absolutePath))
    }

    private fun record(file: File, destination: SoundDestination): SoundDownloadRecord =
        SoundDownloadRecord(
            soundId = SOUND_ID,
            name = file.nameWithoutExtension,
            fileName = file.name,
            sizeLabel = "${file.length()} B",
            finishedAt = System.currentTimeMillis(),
            destination = destination,
        )

    private companion object {
        const val SOUND_ID = 900_001L
        const val PREF_NAME = "wjx_sound_downloads"
        const val KEY_RECORDS = "records"
    }
}
