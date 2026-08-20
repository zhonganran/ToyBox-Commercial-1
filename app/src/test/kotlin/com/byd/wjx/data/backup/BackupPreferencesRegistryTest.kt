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
    fun `backup includes user-facing audio lyric and shortcut preferences`() {
        val files = BackupPreferencesRegistry.USER_CONFIGURATION_FILES

        listOf(
            "exterior_hailing",
            "key_shortcuts",
            "lyric_display_controls",
            "sound_library",
        ).forEach { expected -> assertTrue("missing $expected", expected in files) }
    }

    @Test
    fun `hardware probes and runtime caches remain device local`() {
        listOf(
            "wjx_range_prefs",
            "wjx_seat_channel",
            "wjx_sound_downloads",
        ).forEach { expected ->
            assertTrue("must remain device-local: $expected", expected in BackupPreferencesRegistry.DEVICE_LOCAL_FILES)
        }
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
    fun `backup includes desktop quick card configuration`() {
        assertTrue(
            "desktop card selection and layout are user configuration",
            "wjx_desktop_cards" in BackupPreferencesRegistry.USER_CONFIGURATION_FILES,
        )
    }

    @Test
    fun `backup registers independent LED settings without the retired mixed file`() {
        val files = BackupPreferencesRegistry.USER_CONFIGURATION_FILES

        assertTrue("wjx_led_ipixel_fa" in files)
        assertTrue("wjx_led_a950" in files)
        assertTrue("wjx_led_mafu" in files)
        assertFalse("wjx_led_link" in files)
    }

    @Test
    fun `no user configuration currently requires device-protected backup`() {
        assertTrue(BackupPreferencesRegistry.DEVICE_PROTECTED_USER_CONFIGURATION_FILES.isEmpty())
    }
}
