package com.byd.wjx.ui.automation

import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `adding mirror tilt configures edge driven execution`() {
        val editing = EditingRule(
            cooldownSeconds = 60,
            repeatIntervalSeconds = 30,
            requirePark = true,
            confirmBeforeExecute = true,
            fireOncePerTrip = true,
        )
        val updated = editing.withActionPhase(
            updatedActions = listOf(newMirrorTiltAction()),
            exitPhase = false,
        )

        assertEquals(0, updated.cooldownSeconds)
        assertEquals(0, updated.repeatIntervalSeconds)
        assertFalse(updated.requirePark)
        assertFalse(updated.confirmBeforeExecute)
        assertFalse(updated.fireOncePerTrip)
        assertTrue(updated.actions.any { it.kind == "mirror_tilt" })
        assertEquals(listOf("delay", "mirror_restore"), updated.exitActions.map { it.kind })
        assertEquals("1000", updated.exitActions.first().payload)
    }

}
