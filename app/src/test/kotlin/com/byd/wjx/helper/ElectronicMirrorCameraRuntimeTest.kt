package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorCameraRuntimeTest {
    @Test
    fun `vehicle discovered panorama camera is preferred before numeric fallbacks`() {
        assertEquals(listOf(1, 5, 2, 3), electronicMirrorCameraIdPriority(listOf(1, 5)))
        assertEquals(listOf(3, 2), electronicMirrorCameraIdPriority(listOf(3, -1)))
        assertEquals(listOf(2, 3), electronicMirrorCameraIdPriority(emptyList()))
    }

    @Test
    fun `manual camera selection is strict and ignores every automatic candidate`() {
        assertEquals(
            listOf(7),
            electronicMirrorCameraIdsForSelection(listOf(3, 2), cameraIdOverride = 7),
        )
        assertEquals(
            listOf(3, 2),
            electronicMirrorCameraIdsForSelection(listOf(3), cameraIdOverride = -1),
        )
    }

    @Test
    fun `camera sort parser accepts colon separated entries`() {
        assertEquals(3, cameraIdFromProperty("ims:0;apa:3;pano_h:5", "apa"))
        assertEquals(
            1,
            cameraIdFromProperty("pano_h:1;dms:0;rear:11001;", "pano_h"),
        )
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
                arrayOf<Any?>(3, "pano_h"),
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
}
