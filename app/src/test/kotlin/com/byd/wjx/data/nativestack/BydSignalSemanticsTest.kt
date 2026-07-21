package com.byd.wjx.data.nativestack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BydSignalSemanticsTest {

    @Test fun `official drive and work mode enums remain canonical`() {
        assertEquals(1, BydSignalSemantics.DRIVE_ECONOMY)
        assertEquals(2, BydSignalSemantics.DRIVE_SPORT)
        assertEquals(3, BydSignalSemantics.DRIVE_COMFORT)
        assertTrue(BydSignalSemantics.isValidDriveMode(6))
        assertTrue(BydSignalSemantics.isValidDriveMode(7))
        assertTrue(BydSignalSemantics.isValidDriveMode(11))
        assertFalse(BydSignalSemantics.isValidDriveMode(0))

        assertEquals(1, BydSignalSemantics.ENERGY_EV)
        assertEquals(3, BydSignalSemantics.ENERGY_HEV)
        assertTrue(BydSignalSemantics.isValidWorkMode(0))
        assertFalse(BydSignalSemantics.isValidWorkMode(6))
    }

    @Test fun `terrain indicator overrides base mode only outside park`() {
        assertEquals(7, BydSignalSemantics.selectDriveMode(3, 7, 4))
        assertEquals(11, BydSignalSemantics.selectDriveMode(2, 11, 2))
        assertEquals(3, BydSignalSemantics.selectDriveMode(3, 7, 1))
        assertEquals(2, BydSignalSemantics.selectDriveMode(2, 3, 4))
        assertEquals(1, BydSignalSemantics.selectDriveMode(1, 99, 4))
    }

    @Test fun `direct work mode fid wins stale platform value and zero falls back`() {
        assertEquals(3, BydSignalSemantics.selectWorkMode(1, 3))
        assertEquals(1, BydSignalSemantics.selectWorkMode(3, 1))
        assertEquals(3, BydSignalSemantics.selectWorkMode(0, 3))
        assertEquals(1, BydSignalSemantics.selectWorkMode(null, 1))
        assertEquals(2, BydSignalSemantics.selectWorkMode(2, null))
        assertNull(BydSignalSemantics.selectWorkMode(8, -1))
    }

    @Test fun `official power levels are interpreted without remapping`() {
        assertFalse(BydSignalSemantics.isPowered(0))
        assertTrue(BydSignalSemantics.isPowered(1))
        assertTrue(BydSignalSemantics.isPowered(2))
        assertTrue(BydSignalSemantics.isDriveReady(3))
        assertTrue(BydSignalSemantics.isDriveReady(4))
        assertFalse(BydSignalSemantics.isDriveReady(2))
    }

    @Test fun `charging and body predicates use official enums`() {
        assertTrue(BydSignalSemantics.isCharging(1))
        assertTrue(BydSignalSemantics.isCharging(13))
        assertFalse(BydSignalSemantics.isCharging(2))
        assertTrue(BydSignalSemantics.isCoverOpen(2))
        assertFalse(BydSignalSemantics.isCoverOpen(1))
        assertTrue(BydSignalSemantics.isDoorLocked(2))
        assertFalse(BydSignalSemantics.isDoorLocked(1))
        assertTrue(BydSignalSemantics.isTrunkNotClosed(3))
        assertFalse(BydSignalSemantics.isTrunkNotClosed(2))
    }
}
