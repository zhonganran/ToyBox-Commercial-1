package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.remote.diParsData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTriggerEvaluatorTest {
    @Test
    fun `parameter triggers use vehicle values and configured logic`() {
        val evaluator = AutomationTriggerEvaluator()
        val triggers = listOf(
            paramTrigger("Speed", ">", "40"),
            paramTrigger("ExtTemp", "==", "25"),
        )

        val results = evaluator.evaluate(
            triggers = triggers,
            data = diParsData(speed = 50, exteriorTemp = 25),
        )

        assertEquals(listOf(true, true), results)
        assertEquals(true, evaluator.combineByLogic(results, "AND"))
        assertFalse(evaluator.matchedViaEventTrigger(triggers, results, "AND"))

        val snapshot = JSONObject(
            evaluator.buildSnapshot(triggers, diParsData(speed = 50, exteriorTemp = 25)),
        )
        assertEquals(50.0, snapshot.getDouble("Speed"), 0.0)
        assertEquals(25.0, snapshot.getDouble("ExtTemp"), 0.0)
    }

    @Test
    fun `or rule is event driven only when an event trigger actually matches`() {
        val evaluator = AutomationTriggerEvaluator()
        val triggers = listOf(
            eventTrigger("service_start"),
            paramTrigger("Speed", ">", "40"),
        )

        val stateDriven = evaluator.evaluate(
            triggers = triggers,
            data = diParsData(speed = 50),
            serviceStartActive = false,
        )
        assertEquals(true, evaluator.combineByLogic(stateDriven, "OR"))
        assertFalse(evaluator.matchedViaEventTrigger(triggers, stateDriven, "OR"))

        val eventDriven = evaluator.evaluate(
            triggers = triggers,
            data = diParsData(speed = 0),
            serviceStartActive = true,
        )
        assertEquals(true, evaluator.combineByLogic(eventDriven, "OR"))
        assertTrue(evaluator.matchedViaEventTrigger(triggers, eventDriven, "OR"))
        assertEquals(false, evaluator.persistentMatch(triggers, eventDriven, "OR"))

        val bothDriven = evaluator.evaluate(
            triggers = triggers,
            data = diParsData(speed = 50),
            serviceStartActive = true,
        )
        assertEquals(true, evaluator.persistentMatch(triggers, bothDriven, "OR"))
    }

    @Test
    fun `missing telemetry stays unknown through boolean composition`() {
        val evaluator = AutomationTriggerEvaluator()
        val missingSpeed = evaluator.evaluate(
            triggers = listOf(paramTrigger("Speed", ">", "40")),
            data = diParsData(speed = null),
        )

        assertEquals(listOf<Boolean?>(null), missingSpeed)
        assertEquals(null, evaluator.combineByLogic(missingSpeed, "AND"))
        assertEquals(
            false,
            evaluator.combineByLogic(listOf(false, null), "AND"),
        )
        assertEquals(
            true,
            evaluator.combineByLogic(listOf(true, null), "OR"),
        )
    }

    @Test
    fun `interval trigger pulses when due and reseeds after rule cleanup`() {
        val evaluator = AutomationTriggerEvaluator()
        val trigger = eventTrigger("interval", value = "15")

        assertEquals(listOf(false), evaluator.evaluate(listOf(trigger), now = 1_000L))
        assertEquals(listOf(false), evaluator.evaluate(listOf(trigger), now = 15_999L))
        assertEquals(listOf(true), evaluator.evaluate(listOf(trigger), now = 16_000L))

        evaluator.retainRules(emptySet())

        assertEquals(listOf(false), evaluator.evaluate(listOf(trigger), now = 31_000L))
    }

    private fun AutomationTriggerEvaluator.evaluate(
        triggers: List<TriggerDef>,
        data: DiParsData = diParsData(),
        serviceStartActive: Boolean = false,
        now: Long = 0L,
    ): List<Boolean?> = evaluateEachTrigger(
        ruleId = 1L,
        triggers = triggers,
        data = data,
        location = null,
        places = emptyMap(),
        serviceStartActive = serviceStartActive,
        networkAvailableEdge = false,
        boardVehicleEdge = false,
        now = now,
        allowIntervalPulse = true,
    )

    private fun paramTrigger(param: String, operator: String, value: String) = TriggerDef(
        param = param,
        chineseName = param,
        operator = operator,
        value = value,
        displayName = param,
    )

    private fun eventTrigger(kind: String, value: String = "") = TriggerDef(
        param = "",
        chineseName = kind,
        operator = "",
        value = value,
        displayName = kind,
        kind = kind,
    )
}
