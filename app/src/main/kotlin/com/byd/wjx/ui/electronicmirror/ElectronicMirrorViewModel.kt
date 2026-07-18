package com.byd.wjx.ui.electronicmirror

import androidx.lifecycle.ViewModel
import com.byd.wjx.electronicmirror.ElectronicMirrorCalibration
import com.byd.wjx.electronicmirror.ElectronicMirrorCameraSource
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

    private fun updateDraft(transform: (ElectronicMirrorConfig) -> ElectronicMirrorConfig) {
        _config.value = transform(_config.value).sanitized()
    }

    fun updateAndApply(transform: (ElectronicMirrorConfig) -> ElectronicMirrorConfig) {
        updateDraft(transform)
        persistAndApply()
    }

    fun updateSelectedGeometry(transform: (ElectronicMirrorGeometry) -> ElectronicMirrorGeometry) {
        updateDraft { config ->
            when (config.displayTarget) {
                ElectronicMirrorDisplayTarget.CLUSTER ->
                    config.copy(clusterGeometry = transform(config.clusterGeometry).sanitized())
                ElectronicMirrorDisplayTarget.CENTER ->
                    config.copy(centerGeometry = transform(config.centerGeometry).sanitized())
            }
        }
    }

    fun commitGeometry() {
        persistAndApply()
    }

    fun updateCalibration(
        viewpoint: ElectronicMirrorViewpoint,
        transform: (ElectronicMirrorCalibration) -> ElectronicMirrorCalibration,
    ) {
        updateDraft { config ->
            config.withCalibration(
                viewpoint,
                transform(config.calibrationFor(viewpoint)).sanitized(),
            )
        }
    }

    fun commitCalibration() {
        persistAndApply()
    }

    fun resetCalibration(viewpoint: ElectronicMirrorViewpoint) {
        updateDraft { config -> config.withCalibration(viewpoint, ElectronicMirrorCalibration.DEFAULT) }
        persistAndApply()
    }

    fun assignSource(
        viewpoint: ElectronicMirrorViewpoint,
        source: ElectronicMirrorCameraSource,
    ) {
        updateDraft { config -> config.withSource(viewpoint, source) }
        persistAndApply()
    }

    fun resetSources() {
        updateDraft(ElectronicMirrorConfig::resetSources)
        persistAndApply()
    }

    fun preview(viewpoint: ElectronicMirrorViewpoint) {
        coordinator.preview(viewpoint)
    }

    fun stopPreview() {
        coordinator.stopPreview()
    }

    private fun persistAndApply() {
        preferences.save(_config.value)
        coordinator.applyPreferences()
    }
}
