package com.byd.wjx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExteriorRoutePlanTest {

    @Test
    fun `channel one route keeps lock sound and exterior switch order`() {
        val arm = ExteriorRoutePlan.armWrites(ExteriorChannel.Channel1)
        val disarm = ExteriorRoutePlan.disarmWrites(ExteriorChannel.Channel1)

        assertEquals(
            listOf(
                ExteriorRouteWrite(1000, 13, -1442839775),
                ExteriorRouteWrite(1002, -1442839775, 1),
                ExteriorRouteWrite(1000, 891293750, 1339031619),
                ExteriorRouteWrite(1000, 891293760, 2),
                ExteriorRouteWrite(1000, 372244513, 2),
            ),
            arm,
        )
        assertEquals(
            listOf(
                ExteriorRouteWrite(1000, 13, 1),
                ExteriorRouteWrite(1002, -1442839775, 0),
                ExteriorRouteWrite(1000, 891293750, 3),
                ExteriorRouteWrite(1000, 891293760, 1),
            ),
            disarm,
        )
    }

    @Test
    fun `channel zero route keeps primary and compatibility writes`() {
        val arm = ExteriorRoutePlan.armWrites(ExteriorChannel.Channel0)
        val disarm = ExteriorRoutePlan.disarmWrites(ExteriorChannel.Channel0)

        assertEquals(
            listOf(
                ExteriorRouteWrite(3, -1442839934, 1),
                ExteriorRouteWrite(1000, -1796699511, 1),
            ),
            arm,
        )
        assertEquals(
            listOf(
                ExteriorRouteWrite(3, -1442839934, 0),
                ExteriorRouteWrite(1000, -1796699511, 0),
            ),
            disarm,
        )
    }

    @Test
    fun `channel zero compatibility write cannot replace primary route confirmation`() {
        val writes = ExteriorRoutePlan.armWrites(ExteriorChannel.Channel0)
        val results = mapOf(
            writes[0] to false,
            writes[1] to true,
        )

        assertFalse(
            ExteriorRoutePlan.isStronglyConfirmed(
                channel = ExteriorChannel.Channel0,
                writeResults = results,
                speakerOk = true,
                lockStateOk = false,
                focusOk = true,
            )
        )
    }

    @Test
    fun `channel zero primary speaker and focus confirmation permits playback`() {
        val writes = ExteriorRoutePlan.armWrites(ExteriorChannel.Channel0)
        val results = mapOf(
            writes[0] to true,
            writes[1] to false,
        )

        assertTrue(
            ExteriorRoutePlan.isStronglyConfirmed(
                channel = ExteriorChannel.Channel0,
                writeResults = results,
                speakerOk = true,
                lockStateOk = false,
                focusOk = true,
            )
        )
    }

    @Test
    fun `channel one rejects playback when any exterior route write is missing`() {
        val writes = ExteriorRoutePlan.armWrites(ExteriorChannel.Channel1)
        val requiredIndexes = listOf(2, 3, 4)

        requiredIndexes.forEach { missingIndex ->
            val results = writes.associateWith { true }.toMutableMap().apply {
                this[writes[missingIndex]] = false
            }
            assertFalse(
                "missing write index=$missingIndex must block playback",
                ExteriorRoutePlan.isStronglyConfirmed(
                    channel = ExteriorChannel.Channel1,
                    writeResults = results,
                    speakerOk = true,
                    lockStateOk = true,
                    focusOk = true,
                )
            )
        }
    }

    @Test
    fun `channel one permits playback when all exterior route requirements are confirmed`() {
        val writes = ExteriorRoutePlan.armWrites(ExteriorChannel.Channel1)

        assertTrue(
            ExteriorRoutePlan.isStronglyConfirmed(
                channel = ExteriorChannel.Channel1,
                writeResults = writes.associateWith { true },
                speakerOk = true,
                lockStateOk = true,
                focusOk = true,
            )
        )
    }
}
