package com.byd.wjx.data.nativestack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BydSignalSemanticsTest {

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
