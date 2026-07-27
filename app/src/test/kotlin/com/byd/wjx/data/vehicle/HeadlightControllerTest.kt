package com.byd.wjx.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlightControllerTest {
    @Test
    fun `official headlight values map to four supported modes`() {
        assertEquals(HeadlightMode.OFF, HeadlightMode.fromServiceValue(16))
        assertEquals(HeadlightMode.AUTO, HeadlightMode.fromServiceValue(2))
        assertEquals(HeadlightMode.LOW_BEAM, HeadlightMode.fromServiceValue(4))
        assertEquals(HeadlightMode.HIGH_BEAM, HeadlightMode.fromServiceValue(8))
        assertNull(HeadlightMode.fromServiceValue(0))
    }

    @Test
    fun `service errors produce actionable labels`() {
        assertEquals("车辆拒绝访问或权限不足", HeadlightController.serviceCodeMessage(11_003))
        assertEquals("当前车型或车机版本不支持", HeadlightController.serviceCodeMessage(20_005))
    }
}
