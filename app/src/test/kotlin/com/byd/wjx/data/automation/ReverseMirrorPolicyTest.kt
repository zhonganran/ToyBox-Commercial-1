package com.byd.wjx.data.automation

import com.byd.wjx.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReverseMirrorPolicyTest {
    @Test
    fun `tilt payload round trips left right selection and signed offsets`() {
        val request = MirrorTiltRequest(
            leftEnabled = true,
            rightEnabled = false,
            leftHorizontalOffset = -6f,
            leftVerticalOffset = -12f,
            rightHorizontalOffset = 4f,
            rightVerticalOffset = 8f,
        )

        assertEquals(request, MirrorTiltRequest.parse(MirrorTiltRequest.payload(request)))
    }

    @Test
    fun `tilt rejects empty selection and zero selected offset`() {
        assertNull(
            MirrorTiltRequest.parse(
                MirrorTiltRequest.payload(MirrorTiltRequest(false, false, 0f, 10f, 0f, 10f)),
            ),
        )
        assertNull(
            MirrorTiltRequest.parse(
                MirrorTiltRequest.payload(MirrorTiltRequest(true, false, 0f, 0f, 0f, 10f)),
            ),
        )
    }

    @Test
    fun `horizontal-only offset is a valid mirror action`() {
        val request = MirrorTiltRequest(true, false, -8f, 0f, 0f, 0f)

        assertEquals(request, MirrorTiltRequest.parse(MirrorTiltRequest.payload(request)))
    }

    @Test
    fun `automation tilt requires reverse gear and low known speed`() {
        assertNull(
            ReverseMirrorController.mirrorTiltBlockReason(
                diParsData(gear = 2, speed = 3),
                preview = false,
            ),
        )
        assertNotNull(
            ReverseMirrorController.mirrorTiltBlockReason(
                diParsData(gear = 4, speed = 0),
                preview = false,
            ),
        )
        assertNotNull(
            ReverseMirrorController.mirrorTiltBlockReason(
                diParsData(gear = 2, speed = 8),
                preview = false,
            ),
        )
    }

    @Test
    fun `preview tilt requires park gear`() {
        assertNull(
            ReverseMirrorController.mirrorTiltBlockReason(
                diParsData(gear = 1, speed = 0),
                preview = true,
            ),
        )
        assertNotNull(
            ReverseMirrorController.mirrorTiltBlockReason(
                diParsData(gear = 2, speed = 0),
                preview = true,
            ),
        )
    }

    @Test
    fun `restore remains available at low speed but defers at road speed`() {
        assertNull(ReverseMirrorController.mirrorRestoreBlockReason(diParsData(gear = 4, speed = 5)))
        assertNotNull(ReverseMirrorController.mirrorRestoreBlockReason(diParsData(gear = 4, speed = 30)))
    }
}
