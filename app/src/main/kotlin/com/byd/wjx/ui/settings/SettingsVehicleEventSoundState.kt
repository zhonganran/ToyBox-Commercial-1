package com.byd.wjx.ui.settings

import com.byd.wjx.audio.LockSoundConfig

data class SettingsVehicleEventSoundUiState(
    val config: LockSoundConfig = LockSoundConfig(),
    val choices: List<LockSoundChoice> = emptyList(),
    val syncing: Boolean = false,
    val status: String? = null,
    val statusIsError: Boolean = false,
) {
    internal fun merge(state: VehicleEventSoundState, syncing: Boolean = false) = copy(
        config = state.config,
        choices = state.choices,
        syncing = syncing,
        status = state.status ?: status,
        statusIsError = if (state.status != null) state.statusIsError else statusIsError,
    )

    val displayStatus: String?
        get() = if (syncing) "同步中…" else status
}

data class LockSoundChoice(
    val path: String,
    val name: String,
    val sizeLabel: String,
)
