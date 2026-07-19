package com.byd.wjx.ui.electronicmirror

import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorCoordinator
import com.byd.wjx.electronicmirror.ElectronicMirrorPreferences
import com.byd.wjx.electronicmirror.ElectronicMirrorProjectionSpec
import com.byd.wjx.electronicmirror.ElectronicMirrorRuntimeStatus
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ElectronicMirrorViewModelTest {
    private val initial = ElectronicMirrorConfig()
    private val preferences = mockk<ElectronicMirrorPreferences> {
        every { snapshot() } returns initial
        every { save(any()) } just Runs
    }
    private val coordinator = mockk<ElectronicMirrorCoordinator>(relaxed = true) {
        every { status } returns MutableStateFlow(ElectronicMirrorRuntimeStatus())
        every { active } returns MutableStateFlow(false)
    }
    private val viewModel = ElectronicMirrorViewModel(preferences, coordinator)

    @Test
    fun `click settings save and apply immediately`() {
        viewModel.updateAndApply { it.copy(enabled = true) }

        assertEquals(true, viewModel.config.value.enabled)
        verify(exactly = 1) { preferences.save(match { it.enabled }) }
        verify(exactly = 1) { coordinator.applyPreferences() }
    }

    @Test
    fun `geometry drag remains in memory until release`() {
        viewModel.updateSelectedGeometry { it.copy(widthPct = 42) }

        assertEquals(42, viewModel.config.value.centerGeometry.widthPct)
        verify(exactly = 0) { preferences.save(any()) }
        verify(exactly = 0) { coordinator.applyPreferences() }

        viewModel.commitGeometry()

        verify(exactly = 1) { preferences.save(match { it.centerGeometry.widthPct == 42 }) }
        verify(exactly = 1) { coordinator.applyPreferences() }
    }

    @Test
    fun `preview never persists unrelated draft values`() {
        viewModel.updateSelectedGeometry { it.copy(offsetXPct = 80) }
        viewModel.preview(ElectronicMirrorViewpoint.LEFT)

        verify(exactly = 0) { preferences.save(any()) }
        verify(exactly = 1) { coordinator.preview(ElectronicMirrorViewpoint.LEFT) }
    }

    @Test
    fun `streaming geometry saves only when slider is released`() {
        viewModel.updateStreamingRearGeometry { it.copy(heightPct = 50) }

        assertEquals(50, viewModel.config.value.streamingRearCenterGeometry.heightPct)
        verify(exactly = 0) { preferences.save(any()) }

        viewModel.commitStreamingRearGeometry()

        verify(exactly = 1) {
            preferences.save(match { it.streamingRearCenterGeometry.heightPct == 50 })
        }
        verify(exactly = 1) { coordinator.applyPreferences() }
    }

    @Test
    fun `projection saves only when slider is released`() {
        viewModel.updateProjection(ElectronicMirrorViewpoint.REAR) {
            it.copy(viewScalePermille = 1350)
        }

        assertEquals(
            ElectronicMirrorProjectionSpec.DEFAULT_REAR.copy(viewScalePermille = 1350),
            viewModel.config.value.rearProjection,
        )
        verify(exactly = 0) { preferences.save(any()) }

        viewModel.commitProjection()

        verify(exactly = 1) {
            preferences.save(match { it.rearProjection.viewScalePermille == 1350 })
        }
        verify(exactly = 1) { coordinator.applyPreferences() }
    }
}
