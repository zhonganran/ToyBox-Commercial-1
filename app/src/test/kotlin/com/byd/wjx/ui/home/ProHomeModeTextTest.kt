package com.byd.wjx.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ProHomeModeTextTest {

    @Test fun `home drive mode labels follow official BYD order`() {
        assertEquals("经济模式", driveModeText(1))
        assertEquals("运动模式", driveModeText(2))
        assertEquals("舒适模式", driveModeText(3))
        assertEquals("雪地模式", driveModeText(4))
        assertEquals("泥地模式", driveModeText(5))
        assertEquals("沙地模式", driveModeText(6))
        assertEquals("山地模式", driveModeText(7))
        assertEquals("竞速模式", driveModeText(11))
    }

    @Test fun `home energy mode labels follow instrument enum`() {
        assertEquals("EV", workModeText(1))
        assertEquals("强制 EV", workModeText(2))
        assertEquals("HEV", workModeText(3))
    }
}
