package com.byd.wjx.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorPositionTest {
    @Test
    fun `four axis offsets apply only to enabled mirrors`() {
        val original = MirrorPosition(20f, 40f, 80f, 60f)

        val target = original.withOffsets(
            leftEnabled = true,
            rightEnabled = false,
            leftHorizontalOffset = -5f,
            leftVerticalOffset = 12f,
            rightHorizontalOffset = 9f,
            rightVerticalOffset = -8f,
        )

        assertEquals(MirrorPosition(15f, 52f, 80f, 60f), target)
    }

    @Test
    fun `four axis offsets clamp to sane hardware range`() {
        val original = MirrorPosition(2f, 250f, 252f, 2f)

        val target = original.withOffsets(
            leftEnabled = true,
            rightEnabled = true,
            leftHorizontalOffset = -20f,
            leftVerticalOffset = 20f,
            rightHorizontalOffset = 20f,
            rightVerticalOffset = -20f,
        )

        assertEquals(0f, target.leftHorizontal)
        assertEquals(255f, target.leftVertical)
        assertEquals(255f, target.rightHorizontal)
        assertEquals(0f, target.rightVertical)
    }

    @Test
    fun `restore selected mirrors preserves current position of disabled mirror`() {
        val current = MirrorPosition(25f, 45f, 90f, 70f)
        val original = MirrorPosition(20f, 40f, 80f, 60f)

        val restored = current.restoreSelectedFrom(
            original = original,
            leftEnabled = true,
            rightEnabled = false,
        )

        assertEquals(MirrorPosition(20f, 40f, 90f, 70f), restored)
    }

    @Test
    fun `sane position rejects non finite and out of range values`() {
        assertTrue(MirrorPosition(0f, 10f, 200f, 255f).isSane())
        assertFalse(MirrorPosition(Float.NaN, 10f, 20f, 30f).isSane())
        assertFalse(MirrorPosition(0f, -1f, 20f, 30f).isSane())
        assertFalse(MirrorPosition(0f, 10f, 256f, 30f).isSane())
    }
}
