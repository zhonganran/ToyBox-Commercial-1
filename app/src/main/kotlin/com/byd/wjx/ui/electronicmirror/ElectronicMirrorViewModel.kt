package com.byd.wjx.ui.electronicmirror

import androidx.lifecycle.ViewModel
import com.byd.wjx.electronicmirror.ElectronicMirrorCameraSource
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorCoordinator
import com.byd.wjx.electronicmirror.ElectronicMirrorDisplayTarget
import com.byd.wjx.electronicmirror.ElectronicMirrorGeometry
import com.byd.wjx.electronicmirror.ElectronicMirrorPreferences
import com.byd.wjx.electronicmirror.ElectronicMirrorProjectionSpec
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

    fun updateStreamingRearGeometry(
        transform: (ElectronicMirrorGeometry) -> ElectronicMirrorGeometry,
    ) {
        updateDraft { config ->
            when (config.streamingRearDisplayTarget) {
                ElectronicMirrorDisplayTarget.CLUSTER -> config.copy(
                    streamingRearClusterGeometry = transform(config.streamingRearClusterGeometry)
                        .sanitized(),
                )
                ElectronicMirrorDisplayTarget.CENTER -> config.copy(
                    streamingRearCenterGeometry = transform(config.streamingRearCenterGeometry)
                        .sanitized(),
                )
            }
        }
    }

    fun commitStreamingRearGeometry() {
        persistAndApply()
    }

    fun updateProjection(
        viewpoint: ElectronicMirrorViewpoint,
        transform: (ElectronicMirrorProjectionSpec) -> ElectronicMirrorProjectionSpec,
    ) {
        updateDraft { config ->
            config.withProjection(
                viewpoint,
                transform(config.projectionFor(viewpoint)).sanitized(),
            )
        }
    }

    fun commitProjection() {
        persistAndApply()
    }

    fun resetProjection(viewpoint: ElectronicMirrorViewpoint) {
        updateDraft { config ->
            config.withProjection(viewpoint, ElectronicMirrorProjectionSpec.defaultFor(viewpoint))
        }
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

    fun stopAllPreviewOutputs() {
        val current = _config.value
        if (current.streamingRearEnabled && current.streamingRearVisible) {
            updateDraft { it.copy(streamingRearVisible = false) }
            preferences.save(_config.value)
        }
        coordinator.stopPreview()
    }

    private fun persistAndApply() {
        preferences.save(_config.value)
        coordinator.applyPreferences()
    }
}
