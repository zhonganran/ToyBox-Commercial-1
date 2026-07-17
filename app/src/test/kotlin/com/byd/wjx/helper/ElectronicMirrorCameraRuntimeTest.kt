package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorCameraRuntimeTest {
    @Test
    fun `camera sort parser accepts colon separated entries`() {
        assertEquals(3, cameraIdFromProperty("ims:0;apa:3;pano_h:5", "apa"))
    }

    @Test
    fun `camera sort parser accepts equals and comma variants`() {
        assertEquals(7, cameraIdFromProperty("rear=2, BYD_APA = 7", "byd_apa"))
    }

    @Test
    fun `camera sort parser rejects missing invalid and negative ids`() {
        assertNull(cameraIdFromProperty("apa:-1;pano_h:not-a-number", "apa"))
        assertNull(cameraIdFromProperty("apa:-1;pano_h:not-a-number", "pano_h"))
        assertNull(cameraIdFromProperty("apa:3", "pano_l"))
    }

    @Test
    fun `reflection matcher accepts primitive wrappers and assignable surface types`() {
        assertTrue(
            electronicMirrorArgumentsCompatible(
                arrayOf(Int::class.javaPrimitiveType!!, CharSequence::class.java),
                arrayOf<Any?>(3, "apa"),
            ),
        )
    }

    @Test
    fun `reflection matcher rejects null primitive and incompatible overloads`() {
        assertFalse(
            electronicMirrorArgumentsCompatible(
                arrayOf(Int::class.javaPrimitiveType!!),
                arrayOf(null),
            ),
        )
        assertFalse(
            electronicMirrorArgumentsCompatible(
                arrayOf(String::class.java),
                arrayOf(3),
            ),
        )
    }

    @Test
    fun `electronic mirror viewpoints select physical single channels`() {
        assertEquals(4, electronicMirrorPreviewChannel(2000))
        assertEquals(2, electronicMirrorPreviewChannel(2002))
        assertEquals(3, electronicMirrorPreviewChannel(2003))
        assertNull(electronicMirrorPreviewChannel(0))
    }

    @Test
    fun `all supported viewpoints forbid stitched panorama channel zero`() {
        listOf(2000, 2002, 2003).forEach { viewpoint ->
            assertTrue(requireNotNull(electronicMirrorPreviewChannel(viewpoint)) > 0)
        }
    }

    @Test
    fun `single preview uses AVMCamera constants when valid`() {
        assertEquals(
            ElectronicMirrorPreviewSize(width = 1280, height = 720),
            electronicMirrorSinglePreviewSize(1280, 720),
        )
        assertEquals(
            ElectronicMirrorPreviewSize(width = 1920, height = 1080),
            electronicMirrorSinglePreviewSize(1920, 1080),
        )
    }

    @Test
    fun `stitched or missing preview size falls back to low cost single resolution`() {
        val expected = ElectronicMirrorPreviewSize(width = 1280, height = 720)
        assertEquals(expected, electronicMirrorSinglePreviewSize(7680, 1300))
        assertEquals(expected, electronicMirrorSinglePreviewSize(null, null))
        assertEquals(expected, electronicMirrorSinglePreviewSize(0, 0))
    }

    @Test
    fun `channel switch always removes old route before binding new route`() {
        assertEquals(
            listOf(
                ElectronicMirrorSurfaceStep(ElectronicMirrorSurfaceAction.REMOVE, 2),
                ElectronicMirrorSurfaceStep(ElectronicMirrorSurfaceAction.BIND, 3),
            ),
            electronicMirrorSurfaceSwitchPlan(currentChannel = 2, targetChannel = 3),
        )
        assertEquals(
            listOf(ElectronicMirrorSurfaceStep(ElectronicMirrorSurfaceAction.BIND, 2)),
            electronicMirrorSurfaceSwitchPlan(currentChannel = null, targetChannel = 2),
        )
        assertTrue(electronicMirrorSurfaceSwitchPlan(currentChannel = 3, targetChannel = 3).isEmpty())
    }
}
