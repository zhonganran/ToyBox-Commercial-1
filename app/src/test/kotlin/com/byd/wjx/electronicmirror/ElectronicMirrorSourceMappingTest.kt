package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Test

class ElectronicMirrorSourceMappingTest {
    @Test
    fun `default order matches validated camera channels`() {
        val config = ElectronicMirrorConfig()

        assertEquals(ElectronicMirrorCameraSource.SOURCE_A, config.sourceFor(ElectronicMirrorViewpoint.LEFT))
        assertEquals(ElectronicMirrorCameraSource.SOURCE_B, config.sourceFor(ElectronicMirrorViewpoint.RIGHT))
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, config.sourceFor(ElectronicMirrorViewpoint.REAR))
        assertEquals(setOf(0, 1, 2, 3), ElectronicMirrorCameraSource.entries.map { it.stripTileIndex }.toSet())
    }

    @Test
    fun `assigning an occupied source swaps the two logical views`() {
        val changed = ElectronicMirrorConfig().withSource(
            ElectronicMirrorViewpoint.LEFT,
            ElectronicMirrorCameraSource.SOURCE_B,
        )

        assertEquals(ElectronicMirrorCameraSource.SOURCE_B, changed.leftSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_A, changed.rightSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, changed.rearSource)
    }

    @Test
    fun `fourth panorama tile can be assigned without disturbing the other views`() {
        val changed = ElectronicMirrorConfig().withSource(
            ElectronicMirrorViewpoint.RIGHT,
            ElectronicMirrorCameraSource.SOURCE_D,
        )

        assertEquals(ElectronicMirrorCameraSource.SOURCE_A, changed.leftSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_D, changed.rightSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, changed.rearSource)
        assertEquals(changed, changed.sanitized())
    }

    @Test
    fun `invalid duplicate sources reset to a safe one-to-one order`() {
        val safe = ElectronicMirrorConfig(
            leftSource = ElectronicMirrorCameraSource.SOURCE_A,
            rightSource = ElectronicMirrorCameraSource.SOURCE_A,
            rearSource = ElectronicMirrorCameraSource.SOURCE_A,
        ).sanitized()

        assertEquals(ElectronicMirrorCameraSource.SOURCE_A, safe.leftSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_B, safe.rightSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, safe.rearSource)
    }
}
