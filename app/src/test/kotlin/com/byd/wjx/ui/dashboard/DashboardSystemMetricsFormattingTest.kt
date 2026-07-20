package com.byd.wjx.ui.dashboard

import com.byd.wjx.data.system.StorageVolumeMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSystemMetricsFormattingTest {
    @Test
    fun `storage summary keeps percent literal outside formatter`() {
        assertEquals(
            "75% · 可用 16.5 / 总 64.0 GB",
            formatStorageVolumeSummary(
                StorageVolumeMetric(
                    label = "data",
                    totalGb = 64.0,
                    freeGb = 16.5,
                    usedPercent = 75,
                ),
            ),
        )
    }
}
