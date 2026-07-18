package com.byd.wjx.ui.electronicmirror

import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorCoordinator
import com.byd.wjx.electronicmirror.ElectronicMirrorPreferences
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
        viewModel.updateSelectedGeometry { it.copy(widthPct = 72) }

        assertEquals(72, viewModel.config.value.clusterGeometry.widthPct)
        verify(exactly = 0) { preferences.save(any()) }
        verify(exactly = 0) { coordinator.applyPreferences() }

        viewModel.commitGeometry()

        verify(exactly = 1) { preferences.save(match { it.clusterGeometry.widthPct == 72 }) }
        verify(exactly = 1) { coordinator.applyPreferences() }
    }

    @Test
    fun `preview never persists unrelated draft values`() {
        viewModel.updateSelectedGeometry { it.copy(offsetXPct = 80) }
        viewModel.preview(ElectronicMirrorViewpoint.LEFT)

        verify(exactly = 0) { preferences.save(any()) }
        verify(exactly = 1) { coordinator.preview(ElectronicMirrorViewpoint.LEFT) }
    }
}
