package com.byd.wjx.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferencesRegistryTest {
    @Test
    fun `every preference file has one named owner`() {
        val policies = BackupPreferencesRegistry.FILE_POLICIES

        assertEquals(policies.size, policies.map { it.fileName }.distinct().size)
        assertTrue(policies.all { it.owner.isNotBlank() })
    }

    @Test
    fun `backup includes every user-facing audio widget and shortcut preference`() {
        val files = BackupPreferencesRegistry.USER_CONFIGURATION_FILES

        listOf(
            "exterior_hailing",
            "auto_parking_sound",
            "key_shortcuts",
            "wjx_sound_floating",
            "wjx_auto_door_floating",
            "wjx_seat_channel",
            "desktop_widget_controls",
            "sound_library",
            "wjx_sound_downloads",
        ).forEach { expected -> assertTrue("missing $expected", expected in files) }
    }

    @Test
    fun `backup excludes activation runtime anchors and caches`() {
        val files = BackupPreferencesRegistry.USER_CONFIGURATION_FILES

        BackupPreferencesRegistry.DEVICE_LOCAL_FILES.forEach { excluded ->
            assertFalse("must remain device-local: $excluded", excluded in files)
        }
    }

    @Test
    fun `backup includes accumulated energy flow statistics`() {
        assertTrue(
            "energy-flow totals are user data and must be backed up",
            "wjx_energy_flow_stats" in BackupPreferencesRegistry.USER_CONFIGURATION_FILES,
        )
    }

    @Test
    fun `device-protected backup files are explicitly registered user configuration`() {
        val deviceProtected = BackupPreferencesRegistry.DEVICE_PROTECTED_USER_CONFIGURATION_FILES

        assertTrue("sound widget prefs must be device-protected", "wjx_sound_floating" in deviceProtected)
        assertTrue("door widget prefs must be device-protected", "wjx_auto_door_floating" in deviceProtected)
        deviceProtected.forEach { fileName ->
            assertTrue("device-protected file must be backed up: $fileName", fileName in BackupPreferencesRegistry.USER_CONFIGURATION_FILES)
        }
    }
}
