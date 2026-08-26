package com.byd.wjx.data.automation

import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AutomationPersistenceRepositoriesTest {
    @Test
    fun `rule repository is the single adapter for rule persistence`() = runTest {
        val rule = RuleEntity(id = 7, name = "test", triggers = "[]", actions = "[]")
        val rules = MutableStateFlow(listOf(rule))
        val dao = mockk<RuleDao> {
            every { getAll() } returns rules
            coEvery { getEnabled() } returns listOf(rule)
            coEvery { getById(7) } returns rule
            coEvery { getByName("test") } returns rule
            coEvery { insert(rule) } returns 7L
            coEvery { update(rule) } just Runs
            coEvery { delete(rule) } just Runs
            coEvery { setEnabled(7, false) } just Runs
            coEvery { getCount() } returns 1
            coEvery { updateLastTriggered(7, 123L) } just Runs
        }
        val repository = RoomAutomationRuleRepository(dao)

        assertSame(rules, repository.observeAll())
        assertEquals(listOf(rule), repository.enabled())
        assertEquals(rule, repository.find(7))
        assertEquals(rule, repository.findByName("test"))
        assertEquals(7L, repository.create(rule))
        repository.save(rule)
        repository.remove(rule)
        repository.setEnabled(7, false)
        assertEquals(1, repository.count())
        repository.markTriggered(7, 123L)

        verify(exactly = 1) { dao.getAll() }
        coVerify(exactly = 1) { dao.update(rule) }
        coVerify(exactly = 1) { dao.delete(rule) }
        coVerify(exactly = 1) { dao.setEnabled(7, false) }
        coVerify(exactly = 1) { dao.updateLastTriggered(7, 123L) }
    }

    @Test
    fun `journal repository owns recent log observation and recording`() = runTest {
        val log = RuleLogEntity(
            ruleId = 7,
            ruleName = "test",
            triggeredAt = 123L,
            triggersSnapshot = "{}",
            actionsResult = "[]",
            success = true,
        )
        val logs = MutableStateFlow(listOf(log))
        val dao = mockk<RuleLogDao> {
            every { getRecent(100) } returns logs
            coEvery { insert(log) } returns 9L
        }
        val repository = RoomAutomationJournalRepository(dao)

        assertSame(logs, repository.observeRecent(100))
        assertEquals(9L, repository.record(log))

        verify(exactly = 1) { dao.getRecent(100) }
        coVerify(exactly = 1) { dao.insert(log) }
    }
}
