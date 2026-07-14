package com.byd.wjx.data.automation

import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.repository.PlaceRepository
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
    fun `room flow cache updates rules and places without polling queries`() = runTest {
        val enabled = rule(
            id = 1,
            enabled = true,
            triggers = TriggerDef.listToJson(
                listOf(TriggerDef("InsideTemp", "车内温度", ">", "26", "车内温度")),
            ),
        )
        val disabled = rule(id = 2, enabled = false)
        val rules = MutableStateFlow(listOf(enabled, disabled))
        val places = MutableStateFlow(listOf(place(id = 7, name = "家")))
        val ruleDao = mockk<RuleDao> {
            every { getAll() } returns rules
            coEvery { getEnabled() } returns listOf(enabled)
        }
        val placeRepository = mockk<PlaceRepository> {
            every { getAll() } returns places
            coEvery { getAllSnapshot() } returns places.value
        }
        val repository = RoomAutomationConfigRepository(ruleDao, placeRepository, backgroundScope)

        runCurrent()
        val initial = repository.current()
        assertEquals(listOf(1L), initial.enabledRules.map { it.id })
        assertEquals("家", initial.placesById[7L]?.name)
        assertEquals(setOf("insideTemp"), initial.requiredVehicleFields)

        rules.value = listOf(enabled.copy(enabled = false), disabled.copy(enabled = true))
        places.value = listOf(place(id = 8, name = "公司"))
        runCurrent()

        val updated = repository.current()
        assertEquals(listOf(2L), updated.enabledRules.map { it.id })
        assertFalse(updated.placesById.containsKey(7L))
        assertEquals("公司", updated.placesById[8L]?.name)
    }

    private fun rule(id: Long, enabled: Boolean, triggers: String = "[]") = RuleEntity(
        id = id,
        name = "r$id",
        enabled = enabled,
        triggers = triggers,
        actions = "[]",
    )

    private fun place(id: Long, name: String) = PlaceEntity(
        id = id,
        name = name,
        lat = 30.0,
        lon = 120.0,
    )
}
