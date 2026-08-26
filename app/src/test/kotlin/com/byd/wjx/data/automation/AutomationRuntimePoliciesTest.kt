package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRuntimePoliciesTest {
    @Test fun `first network observation seeds watermark without firing`() {
        val state = AutomationRuleRuntimeState()

        assertFalse(state.consumeNetworkEdge(ruleId = 7, edgeAt = 100, probePending = false))
        assertFalse(state.consumeNetworkEdge(ruleId = 7, edgeAt = 100, probePending = false))
        assertTrue(state.consumeNetworkEdge(ruleId = 7, edgeAt = 101, probePending = false))
    }

    @Test fun `pending network probe defers watermark seeding`() {
        val state = AutomationRuleRuntimeState()

        assertFalse(state.consumeNetworkEdge(ruleId = 7, edgeAt = 0, probePending = true))
        assertFalse(state.consumeNetworkEdge(ruleId = 7, edgeAt = 100, probePending = false))
        assertTrue(state.consumeNetworkEdge(ruleId = 7, edgeAt = 101, probePending = false))
    }

    @Test fun `stable transition requires two matching samples`() {
        val state = AutomationRuleRuntimeState(stableSampleCount = 2)
        val initial = state.stabilize(null, definitionHash = 1, rawMatched = false, edgeEligibleNow = true)

        val candidate = state.stabilize(
            previous = initial.state,
            definitionHash = 1,
            rawMatched = true,
            edgeEligibleNow = true,
        )
        assertFalse(candidate.entered)
        assertFalse(candidate.state.matched)

        val entered = state.stabilize(
            previous = candidate.state,
            definitionHash = 1,
            rawMatched = true,
            edgeEligibleNow = true,
        )
        assertTrue(entered.entered)
        assertTrue(entered.state.matched)
    }

    @Test fun `definition change reseeds state without an enter edge`() {
        val state = AutomationRuleRuntimeState()
        val previous = AutomationEvalState(definitionHash = 1, matched = false)

        val transition = state.stabilize(
            previous = previous,
            definitionHash = 2,
            rawMatched = true,
            edgeEligibleNow = true,
        )

        assertFalse(transition.entered)
        assertFalse(transition.exited)
        assertTrue(transition.state.matched)
        assertEquals(2, transition.state.definitionHash)
    }

    @Test fun `once per trip gate only applies while option is enabled`() {
        val state = AutomationRuleRuntimeState()
        val oncePerTripRule = rule(fireOncePerTrip = true)
        val tripStartedAt = 123L
        state.recordEnter(oncePerTripRule, tripStartedAt, now = 1_000L)

        assertFalse(
            state.isEnterEdgeEligible(
                rule = oncePerTripRule,
                data = diParsData(gear = 1),
                tripStartedAt = tripStartedAt,
                now = 2_000L,
            ),
        )
        assertTrue(
            state.isEnterEdgeEligible(
                rule = oncePerTripRule.copy(fireOncePerTrip = false),
                data = diParsData(gear = 1),
                tripStartedAt = tripStartedAt,
                now = 2_000L,
            ),
        )
    }

    @Test fun `media owners use stable enter and exit namespaces`() {
        val media = ActionDef("play_music", "music", kind = "play_music")
        val command = ActionDef("车窗全开", "windows")

        assertEquals("automation:9:enter:2", automationActionOwner(9, "enter", 2, media))
        assertEquals("automation:9:enter:2", automationActionOwner(9, "repeat", 2, media))
        assertEquals("automation:9:exit:2", automationActionOwner(9, "exit", 2, media))
        assertNull(automationActionOwner(9, "enter", 2, command))
    }

    private fun rule(fireOncePerTrip: Boolean) = RuleEntity(
        id = 7,
        name = "rule",
        triggers = "[]",
        actions = "[]",
        cooldownSeconds = 0,
        fireOncePerTrip = fireOncePerTrip,
    )
}
