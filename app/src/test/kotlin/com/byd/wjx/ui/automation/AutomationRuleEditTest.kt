package com.byd.wjx.ui.automation

import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AutomationRuleEditTest {
    @Test
    fun `editing preserves runtime state and creation metadata`() {
        val existing = RuleEntity(
            id = 7,
            name = "old",
            enabled = false,
            triggers = "[]",
            actions = "[]",
            lastTriggeredAt = 123_456L,
            triggerCount = 9,
            createdAt = 42L,
        )
        val editing = EditingRule(
            id = existing.id,
            name = "  new name  ",
            triggers = listOf(TriggerDef("Speed", "车速", ">", "30", "车速")),
            actions = listOf(ActionDef("自动空调", "自动空调")),
            cooldownSeconds = 10,
            isNew = false,
        )

        val updated = editing.toRuleEntity(existing)

        assertEquals(existing.id, updated.id)
        assertFalse(updated.enabled)
        assertEquals(existing.lastTriggeredAt, updated.lastTriggeredAt)
        assertEquals(existing.triggerCount, updated.triggerCount)
        assertEquals(existing.createdAt, updated.createdAt)
        assertEquals("new name", updated.name)
        assertEquals(10, updated.cooldownSeconds)
    }
}
