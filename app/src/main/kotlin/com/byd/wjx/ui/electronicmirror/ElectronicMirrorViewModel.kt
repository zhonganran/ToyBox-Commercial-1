package com.byd.wjx.ui.electronicmirror

import androidx.lifecycle.ViewModel
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorCoordinator
import com.byd.wjx.electronicmirror.ElectronicMirrorDisplayTarget
import com.byd.wjx.electronicmirror.ElectronicMirrorGeometry
import com.byd.wjx.electronicmirror.ElectronicMirrorPreferences
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ElectronicMirrorViewModel @Inject constructor(
    private val preferences: ElectronicMirrorPreferences,
    private val coordinator: ElectronicMirrorCoordinator,
) : ViewModel() {
    private val _config = MutableStateFlow(preferences.snapshot())
    val config: StateFlow<ElectronicMirrorConfig> = _config.asStateFlow()

    val status = coordinator.status
    val active = coordinator.active

    private val _savedRevision = MutableStateFlow(0)
    val savedRevision: StateFlow<Int> = _savedRevision.asStateFlow()

    fun update(transform: (ElectronicMirrorConfig) -> ElectronicMirrorConfig) {
        _config.value = transform(_config.value).sanitized()
    }

    fun updateSelectedGeometry(transform: (ElectronicMirrorGeometry) -> ElectronicMirrorGeometry) {
        update { config ->
            when (config.displayTarget) {
                ElectronicMirrorDisplayTarget.CLUSTER ->
                    config.copy(clusterGeometry = transform(config.clusterGeometry).sanitized())
                ElectronicMirrorDisplayTarget.CENTER ->
                    config.copy(centerGeometry = transform(config.centerGeometry).sanitized())
            }
        }
    }

    fun save() {
        preferences.save(_config.value)
        coordinator.applyPreferences()
        _savedRevision.value += 1
    }

    fun preview(viewpoint: ElectronicMirrorViewpoint) {
        preferences.save(_config.value)
        coordinator.preview(viewpoint)
        _savedRevision.value += 1
    }

    fun stopPreview() {
        coordinator.stopPreview()
    }
}
