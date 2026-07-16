package com.byd.wjx.ui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AutomationCatalogContractTest {
    @Test
    fun `steering heat trigger uses official raw off and on values`() {
        val option = TRIGGER_PARAMS.firstOrNull { it.param == "SteeringHeat" }
        assertNotNull(option)
        assertEquals(listOf("1" to "关闭", "2" to "开启"), option!!.enumValues)
    }
}
