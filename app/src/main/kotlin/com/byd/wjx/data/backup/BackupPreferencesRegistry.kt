package com.byd.wjx.data.backup

/** User-authored preferences that should survive export and restore. */
object BackupPreferencesRegistry {
    val USER_CONFIGURATION_FILES: List<String> = listOf(
        "wjx_locale",
        "wjx_widget",
        "cluster_projection",
        "automation",
        "update_prefs",
        "wjx_range_prefs",
        "wjx_lyrics",
        "lock_sound",
        "exterior_hailing",
        "auto_parking_sound",
        "key_shortcuts",
        "wjx_sound_floating",
        "wjx_auto_door_floating",
        "wjx_seat_channel",
        "desktop_widget_controls",
        "sound_library",
        "wjx_sound_downloads",
        "wjx_led_link",
    )

    /** Preferences needed before user unlock are stored in device-protected storage. */
    val DEVICE_PROTECTED_USER_CONFIGURATION_FILES: Set<String> = setOf(
        "wjx_sound_floating",
        "wjx_auto_door_floating",
    )

    /** Licensing, runtime anchors, diagnostics and remote caches must remain device-local. */
    val DEVICE_LOCAL_FILES: Set<String> = setOf(
        "wjx_activation",
        "wjx_widget_session",
        "boot_log",
        "energydata_sync",
        "service_qr_cache",
        "osmdroid",
        "adb_config",
        "wjx_disclaimer",
    )
}
