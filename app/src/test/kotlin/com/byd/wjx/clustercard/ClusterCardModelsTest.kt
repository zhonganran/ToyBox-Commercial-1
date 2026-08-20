package com.byd.wjx.clustercard

import com.byd.wjx.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCardModelsTest {
    @Test
    fun `fresh install is disabled with no default metrics`() {
        val config = ClusterCardConfig()

        assertFalse(config.enabled)
        assertTrue(config.metricIds.isEmpty())
    }

    @Test
    fun `sanitize removes invalid duplicate and excess metrics`() {
        val ids = ClusterCardMetricCatalog.all.take(CLUSTER_CARD_MAX_METRICS + 1).map(ClusterCardMetric::id)
        val result = sanitizeClusterCardConfig(
            ClusterCardConfig(
                metricIds = listOf(ids[0], ids[0], "missing") + ids.drop(1),
                scale = 0.1f,
                widthScale = 2f,
                alpha = 0f,
                xPct = -20f,
                yPct = 140f,
            ),
        )

        assertEquals(ids.take(CLUSTER_CARD_MAX_METRICS), result.metricIds)
        assertEquals(0.45f, result.scale)
        assertEquals(1.4f, result.widthScale)
        assertEquals(0.4f, result.alpha)
        assertEquals(0f, result.xPct)
        assertEquals(100f, result.yPct)
    }

    @Test
    fun `only selected powertrain metrics request on demand fields`() {
        val config = ClusterCardConfig(
            metricIds = listOf(
                ClusterCardMetric.SPEED.id,
                ClusterCardMetric.ENGINE_RPM.id,
                ClusterCardMetric.COOLANT_TEMP.id,
            ),
        )

        assertEquals(setOf("engineRpm", "waterTemp"), ClusterCardMetricCatalog.telemetryFields(config))
    }

    @Test
    fun `range metrics render vehicle readings and their exact total`() {
        val data = diParsData().copy(
            elecDrivingRangeKm = 188,
            fuelDrivingRangeKm = 702,
        )

        assertEquals("188 km", ClusterCardMetric.ELECTRIC_RANGE.displayValue(data, null))
        assertEquals("702 km", ClusterCardMetric.FUEL_RANGE.displayValue(data, null))
        assertEquals("890 km", ClusterCardMetric.TOTAL_RANGE.displayValue(data, null))
        assertEquals(
            "188 km",
            ClusterCardMetric.TOTAL_RANGE.displayValue(
                data.copy(fuelDrivingRangeKm = null),
                null,
            ),
        )
    }

    @Test
    fun `layout grows from one to two rows without default surface`() {
        assertEquals(0, ClusterCardLayout.rowCount(0))
        assertEquals(1, ClusterCardLayout.rowCount(1))
        assertEquals(1, ClusterCardLayout.rowCount(2))
        assertEquals(2, ClusterCardLayout.rowCount(3))
        assertEquals(2, ClusterCardLayout.rowCount(4))
        assertEquals(4, ClusterCardLayout.rowCount(8))
    }
}
