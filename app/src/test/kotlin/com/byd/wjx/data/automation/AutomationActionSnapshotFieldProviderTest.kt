package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationActionSnapshotFieldProviderTest {
    @Test
    fun `enabled param triggers select only their required vehicle fields`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("InsideTemp", "车内温度", ">", "26", "车内温度"),
                    TriggerDef("AvgBatTemp", "平均电池温度", ">", "40", "平均电池温度"),
                ),
            ),
        )

        assertEquals(setOf("insideTemp", "maxBatTemp", "minBatTemp"), fields)
    }

    @Test
    fun `event and location triggers do not expand the vehicle batch`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("", "启动", "==", "1", "服务启动", kind = "service_start"),
                    TriggerDef("", "到家", "==", "1", "到家", kind = "place_enter", placeId = 1),
                ),
            ),
        )

        assertTrue(fields.isEmpty())
        assertFalse("speed" in fields)
    }

    private fun rule(vararg triggers: TriggerDef) = RuleEntity(
        id = 1,
        name = "test",
        triggers = TriggerDef.listToJson(triggers.toList()),
        actions = "[]",
    )
}
