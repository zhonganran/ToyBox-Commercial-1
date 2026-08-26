package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationConfigRepositoryTest {

    @Test
    fun `room flow cache updates enabled rules without polling queries`() = runTest {
        val enabled = rule(
            id = 1,
            enabled = true,
            triggers = TriggerDef.listToJson(
                listOf(TriggerDef("InsideTemp", "车内温度", ">", "26", "车内温度")),
            ),
        )
        val disabled = rule(id = 2, enabled = false)
        val rules = MutableStateFlow(listOf(enabled, disabled))
        val ruleRepository = mockk<AutomationRuleRepository> {
            every { observeAll() } returns rules
            coEvery { enabled() } returns listOf(enabled)
        }
        val repository = RoomAutomationConfigRepository(ruleRepository, backgroundScope)

        runCurrent()
        val initial = repository.current()
        assertEquals(listOf(1L), initial.enabledRules.map { it.id })
        assertEquals(setOf("insideTemp"), initial.requiredVehicleFields)

        rules.value = listOf(enabled.copy(enabled = false), disabled.copy(enabled = true))
        runCurrent()

        val updated = repository.current()
        assertEquals(listOf(2L), updated.enabledRules.map { it.id })
        assertEquals(emptySet<String>(), updated.requiredVehicleFields)
    }

    private fun rule(id: Long, enabled: Boolean, triggers: String = "[]") = RuleEntity(
        id = id,
        name = "r$id",
        enabled = enabled,
        triggers = triggers,
        actions = "[]",
    )

}
