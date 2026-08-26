package com.byd.wjx.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDoorCatalogTest {
    @Test
    fun `command catalog exposes only supported operations`() {
        assertEquals(
            mapOf(1 to "开门", 2 to "关门", 3 to "暂停"),
            AutoDoorCatalog.commandLabels,
        )
    }

    @Test
    fun `left and right targets keep the verified vehicle fids`() {
        val left = AutoDoorCatalog.targets.getValue("slide_l")
        val right = AutoDoorCatalog.targets.getValue("slide_r")

        assertEquals("0x4E400030", AutoDoorCatalog.fidHex(left.writeFid))
        assertEquals("0x4B100016", AutoDoorCatalog.fidHex(left.stateFid))
        assertEquals("0x4B100018", AutoDoorCatalog.fidHex(left.positionFid))
        assertEquals("0x40700028", AutoDoorCatalog.fidHex(left.coverFid))
        assertEquals("0x4E400032", AutoDoorCatalog.fidHex(right.writeFid))
        assertEquals("0x4B200016", AutoDoorCatalog.fidHex(right.stateFid))
        assertEquals("0x4B200018", AutoDoorCatalog.fidHex(right.positionFid))
        assertEquals("0x4070002A", AutoDoorCatalog.fidHex(right.coverFid))
    }

    @Test
    fun `target keys and protocol channels are unique`() {
        val targets = AutoDoorCatalog.targets.values.toList()

        assertEquals(AutoDoorCatalog.targets.keys, targets.map { it.key }.toSet())
        assertEquals(targets.size, targets.map { it.writeFid }.toSet().size)
        assertEquals(targets.size, targets.map { it.stateFid }.toSet().size)
        assertEquals(targets.size, targets.map { it.positionFid }.toSet().size)
        assertEquals(targets.size, targets.map { it.coverFid }.toSet().size)
        assertTrue(targets.all { it.maxSafeSpeedKmh > 0f })
        assertNotEquals(targets[0].shortLabel, targets[1].shortLabel)
    }
}
