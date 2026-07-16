package com.byd.wjx.data.backup

enum class PreferenceBackupPolicy {
    USER_CONFIGURATION,
    DEVICE_LOCAL,
}

enum class PreferenceStorageScope {
    CREDENTIAL_PROTECTED,
    DEVICE_PROTECTED,
}

data class PreferenceFilePolicy(
    val fileName: String,
    val owner: String,
    val backupPolicy: PreferenceBackupPolicy,
    val storageScope: PreferenceStorageScope = PreferenceStorageScope.CREDENTIAL_PROTECTED,
)

/** Canonical inventory of preference schemas, owners, storage scopes, and backup behavior. */
object BackupPreferencesRegistry {
    val FILE_POLICIES: List<PreferenceFilePolicy> = listOf(
        PreferenceFilePolicy("wjx_locale", "LocalePreferences", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_widget", "WidgetPreferences", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("cluster_projection", "ClusterProjectionPreferences", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("automation", "AutomationPreferences", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("update_prefs", "UpdateChecker", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_range_prefs", "SocInterpolatorPrefsImpl", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_lyrics", "LyricSettings", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("lock_sound", "LockSoundSettings", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("exterior_hailing", "ExteriorHailingSettings", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("auto_parking_sound", "AutoParkingSoundSettings", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("key_shortcuts", "KeyShortcutSettings", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy(
            "wjx_sound_floating",
            "SoundFloatingSettings",
            PreferenceBackupPolicy.USER_CONFIGURATION,
            PreferenceStorageScope.DEVICE_PROTECTED,
        ),
        PreferenceFilePolicy(
            "wjx_auto_door_floating",
            "AutoDoorFloatingSettings",
            PreferenceBackupPolicy.USER_CONFIGURATION,
            PreferenceStorageScope.DEVICE_PROTECTED,
        ),
        PreferenceFilePolicy("wjx_seat_channel", "SeatChannelStorePrefs", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("desktop_widget_controls", "DesktopWidgetPanelPreferences", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("sound_library", "SoundLibrary", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_sound_downloads", "SoundDownloadStore", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_led_link", "LedPreferences", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_energy_flow_stats", "EnergyFlowStatsStore", PreferenceBackupPolicy.USER_CONFIGURATION),
        PreferenceFilePolicy("wjx_activation", "ActivationManager", PreferenceBackupPolicy.DEVICE_LOCAL),
        PreferenceFilePolicy("wjx_widget_session", "SessionPersistence", PreferenceBackupPolicy.DEVICE_LOCAL),
        PreferenceFilePolicy(
            "boot_log",
            "BootDiagnosticsStore",
            PreferenceBackupPolicy.DEVICE_LOCAL,
            PreferenceStorageScope.DEVICE_PROTECTED,
        ),
        PreferenceFilePolicy("energydata_sync", "EnergyDataReader", PreferenceBackupPolicy.DEVICE_LOCAL),
        PreferenceFilePolicy("service_qr_cache", "ServiceQrCache", PreferenceBackupPolicy.DEVICE_LOCAL),
        PreferenceFilePolicy("osmdroid", "OsmdroidRuntime", PreferenceBackupPolicy.DEVICE_LOCAL),
        PreferenceFilePolicy("adb_config", "AdbOnDeviceClient", PreferenceBackupPolicy.DEVICE_LOCAL),
        PreferenceFilePolicy("wjx_disclaimer", "DisclaimerPreferences", PreferenceBackupPolicy.DEVICE_LOCAL),
    )

    val USER_CONFIGURATION_FILES: List<String> = FILE_POLICIES
        .filter { it.backupPolicy == PreferenceBackupPolicy.USER_CONFIGURATION }
        .map(PreferenceFilePolicy::fileName)

    val DEVICE_PROTECTED_USER_CONFIGURATION_FILES: Set<String> = FILE_POLICIES
        .filter {
            it.backupPolicy == PreferenceBackupPolicy.USER_CONFIGURATION &&
                it.storageScope == PreferenceStorageScope.DEVICE_PROTECTED
        }
        .mapTo(linkedSetOf(), PreferenceFilePolicy::fileName)

    val DEVICE_LOCAL_FILES: Set<String> = FILE_POLICIES
        .filter { it.backupPolicy == PreferenceBackupPolicy.DEVICE_LOCAL }
        .mapTo(linkedSetOf(), PreferenceFilePolicy::fileName)

    init {
        require(FILE_POLICIES.map(PreferenceFilePolicy::fileName).distinct().size == FILE_POLICIES.size) {
            "Preference files must have exactly one owner and backup policy"
        }
    }
}
