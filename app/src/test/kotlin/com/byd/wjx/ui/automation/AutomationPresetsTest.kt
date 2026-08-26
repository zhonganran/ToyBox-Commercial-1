package com.byd.wjx.ui.automation

import com.byd.wjx.data.automation.AutomationRuleRepository
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationPresetsTest {
    private val outsideSoundAction = ActionDef(
        command = "outside_sound",
        displayName = "车外播放：测试音效",
        kind = "outside_sound",
        payload = """{"path":"/tmp/test.mp3","name":"测试音效","loop":false}""",
    )

    @Test
    fun `default presets map to the requested vehicle events`() {
        val plugIn = AutomationPreset.PLUG_IN_SOUND.toEditingRule(outsideSoundAction)
        val unplug = AutomationPreset.UNPLUG_SOUND.toEditingRule(outsideSoundAction)
        val reverse = AutomationPreset.REVERSE_SOUND.toEditingRule(outsideSoundAction)
        val autoParking = AutomationPreset.AUTO_PARKING_SOUND.toEditingRule(outsideSoundAction)

        assertEquals(">=", plugIn.triggers.single().operator)
        assertEquals("2", plugIn.triggers.single().value)
        assertEquals(listOf(outsideSoundAction), plugIn.actions)
        assertFalse(plugIn.actions.single().outsideSoundLoop())

        assertTrue(unplug.actions.isEmpty())
        assertEquals(listOf(outsideSoundAction), unplug.exitActions)
        assertFalse(unplug.exitActions.single().outsideSoundLoop())
        assertEquals("充电枪已插入", automationTriggerSummary(plugIn.triggers.single()))
        assertEquals(
            "充电枪已拔出",
            automationTriggerSummary(unplug.triggers.single(), exitOnly = true),
        )

        assertEquals("Gear", reverse.triggers.single().param)
        assertEquals("2", reverse.triggers.single().value)
        assertTrue(reverse.actions.single().outsideSoundLoop())

        assertEquals("AdasApaParkingStatus", autoParking.triggers.single().param)
        assertEquals("4", autoParking.triggers.single().value)
        assertTrue(autoParking.actions.single().outsideSoundLoop())

        assertTrue(plugIn.hasMinimumSaveFields())
        assertTrue(unplug.hasMinimumSaveFields())
        assertTrue(reverse.hasMinimumSaveFields())
        assertTrue(autoParking.hasMinimumSaveFields())
    }

    @Test
    fun `first initialization inserts all presets once and records completion`() = runBlocking {
        val insertedRules = mutableListOf<RuleEntity>()
        val ruleRepository = mockk<AutomationRuleRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        coEvery {
            settingsRepository.getString(DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY, "false")
        } returns "false"
        coEvery { ruleRepository.count() } returns 0
        coEvery { ruleRepository.findByName(any()) } returns null
        coEvery { ruleRepository.create(capture(insertedRules)) } returns 1L
        coEvery {
            settingsRepository.setString(DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY, "true")
        } returns Unit

        val inserted = seedDefaultAutomationPresets(
            ruleRepository = ruleRepository,
            settingsRepository = settingsRepository,
            outsideSoundAction = outsideSoundAction,
            now = 1_000L,
        )

        assertEquals(4, inserted)
        assertEquals(AutomationPreset.entries.map { it.label }, insertedRules.map { it.name })
        assertEquals(listOf(1_000L, 999L, 998L, 997L), insertedRules.map { it.createdAt })
        assertTrue(insertedRules.all { !it.enabled })
        coVerify(exactly = 1) {
            settingsRepository.setString(DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY, "true")
        }
    }

    @Test
    fun `completed initialization does not recreate deleted presets`() = runBlocking {
        val ruleRepository = mockk<AutomationRuleRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        coEvery {
            settingsRepository.getString(DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY, "false")
        } returns "true"

        val inserted = seedDefaultAutomationPresets(
            ruleRepository = ruleRepository,
            settingsRepository = settingsRepository,
            outsideSoundAction = outsideSoundAction,
        )

        assertEquals(0, inserted)
        coVerify(exactly = 0) { ruleRepository.count() }
        coVerify(exactly = 0) { ruleRepository.create(any()) }
    }
}
