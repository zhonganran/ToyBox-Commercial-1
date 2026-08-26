package com.byd.wjx.ui.settings

internal data class SettingsMaintenanceDiagnostics(
    val lastBootInfo: String?,
)

internal data class SettingsMaintenanceResult(
    val success: Boolean,
    val message: String,
)
