package com.byd.wjx.ui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCatalogContractTest {
    @Test
    fun `drive mode trigger uses official BYD value order`() {
        val option = TRIGGER_PARAMS.firstOrNull { it.param == "DriveMode" }
        assertNotNull(option)
        assertEquals(
            listOf(
                "1" to "经济",
                "2" to "运动",
                "3" to "舒适",
                "4" to "雪地",
                "5" to "泥地",
                "6" to "沙地",
                "7" to "山地",
                "11" to "竞速",
            ),
            option!!.enumValues,
        )
    }

    @Test
    fun `steering heat trigger uses official raw off and on values`() {
        val option = TRIGGER_PARAMS.firstOrNull { it.param == "SteeringHeat" }
        assertNotNull(option)
        assertEquals(listOf("1" to "关闭", "2" to "开启"), option!!.enumValues)
    }

    @Test
    fun `hazard on and off are available as light actions`() {
        val hazardActions = ACTION_COMMANDS.filter { it.command.startsWith("双闪") }
        assertEquals(listOf("双闪打开", "双闪关闭"), hazardActions.map { it.command })
        assertTrue(hazardActions.all { it.category == "灯光" })
    }
}
