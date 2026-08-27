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
        assertEquals(1f, config.fontScale)
        assertEquals(1f, config.heightScale)
    }

    @Test
    fun `two card slots have independent labels and default positions`() {
        assertEquals("仪表卡片", ClusterCardSlot.PRIMARY.label)
        assertEquals("仪表卡片2", ClusterCardSlot.SECONDARY.label)
        assertEquals(50f, ClusterCardSlot.PRIMARY.defaultXPct, 0f)
        assertEquals(50f, ClusterCardSlot.PRIMARY.defaultYPct, 0f)
        assertEquals(100f, ClusterCardSlot.SECONDARY.defaultXPct, 0f)
        assertEquals(50f, ClusterCardSlot.SECONDARY.defaultYPct, 0f)
    }

    @Test
    fun `card is hidden until vehicle and all display prerequisites are available`() {
        val config = ClusterCardConfig(
            enabled = true,
            metricIds = listOf(ClusterCardMetric.SPEED.id),
        )

        assertTrue(ClusterCardVisibility.shouldShow(config, true, true))
        assertFalse(ClusterCardVisibility.shouldShow(config, false, true))
        assertFalse(ClusterCardVisibility.shouldShow(config, true, false))
        assertFalse(ClusterCardVisibility.shouldShow(config.copy(enabled = false), true, true))
        assertFalse(ClusterCardVisibility.shouldShow(config.copy(metricIds = emptyList()), true, true))
        assertFalse(ClusterCardVisibility.shouldShow(config.copy(metricIds = listOf("missing")), true, true))
    }

    @Test
    fun `sanitize removes invalid duplicate and excess metrics`() {
        val ids = ClusterCardMetricCatalog.all.take(CLUSTER_CARD_MAX_METRICS + 1).map(ClusterCardMetric::id)
        val result = sanitizeClusterCardConfig(
            ClusterCardConfig(
                metricIds = listOf(ids[0], ids[0], "missing") + ids.drop(1),
                scale = 0.1f,
                widthScale = 2f,
                xPct = -20f,
                yPct = 140f,
                fontScale = 3f,
                heightScale = 0.1f,
            ),
        )

        assertEquals(ids.take(CLUSTER_CARD_MAX_METRICS), result.metricIds)
        assertEquals(0.45f, result.scale)
        assertEquals(1.4f, result.widthScale)
        assertEquals(0f, result.xPct)
        assertEquals(100f, result.yPct)
        assertEquals(CLUSTER_CARD_MAX_FONT_SCALE, result.fontScale)
        assertEquals(CLUSTER_CARD_MIN_HEIGHT_SCALE, result.heightScale)
    }

    @Test
    fun `font scale is clamped`() {
        val result = sanitizeClusterCardConfig(ClusterCardConfig(fontScale = 0.1f))

        assertEquals(CLUSTER_CARD_MIN_FONT_SCALE, result.fontScale)
    }

    @Test
    fun `drive modes render in Chinese`() {
        val modes = mapOf(
            1 to "经济",
            2 to "运动",
            3 to "舒适",
            4 to "雪地",
            5 to "泥地",
            6 to "沙地",
            7 to "山地",
            11 to "赛道",
        )

        modes.forEach { (code, expected) ->
            val data = diParsData().copy(driveMode = code)
            assertEquals(expected, ClusterCardMetric.DRIVE_MODE.displayValue(data, null))
        }
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

    @Test
    fun `card height follows font size and independent height setting`() {
        val base = ClusterCardConfig(
            metricIds = listOf(ClusterCardMetric.SPEED.id, ClusterCardMetric.SOC.id),
            scale = 0.45f,
            fontScale = 1f,
        )
        val reducedFont = base.copy(fontScale = CLUSTER_CARD_MIN_FONT_SCALE)
        val enlarged = base.copy(fontScale = 1.5f)
        val compact = enlarged.copy(heightScale = CLUSTER_CARD_MIN_HEIGHT_SCALE)

        assertTrue(ClusterCardLayout.heightDp(reducedFont) < ClusterCardLayout.heightDp(base))
        assertTrue(ClusterCardLayout.heightDp(enlarged) > ClusterCardLayout.heightDp(base))
        assertTrue(ClusterCardLayout.heightDp(compact) < ClusterCardLayout.heightDp(enlarged))
        assertEquals(39.80f, ClusterCardLayout.heightDp(reducedFont), 0.01f)
        assertEquals(72.54f, ClusterCardLayout.heightDp(enlarged), 0.01f)
        assertEquals(44.92f, ClusterCardLayout.heightDp(compact), 0.01f)
    }
}
