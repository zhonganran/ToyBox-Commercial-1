package com.byd.wjx.data.automation

import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface AutomationRuleRepository {
    fun observeAll(): Flow<List<RuleEntity>>
    suspend fun enabled(): List<RuleEntity>
    suspend fun find(id: Long): RuleEntity?
    suspend fun findByName(name: String): RuleEntity?
    suspend fun create(rule: RuleEntity): Long
    suspend fun save(rule: RuleEntity)
    suspend fun remove(rule: RuleEntity)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun count(): Int
    suspend fun markTriggered(id: Long, timestamp: Long)
}

interface AutomationJournalRepository {
    fun observeRecent(limit: Int = 50): Flow<List<RuleLogEntity>>
    suspend fun record(log: RuleLogEntity): Long
}

@Singleton
class RoomAutomationRuleRepository @Inject constructor(
    private val ruleDao: RuleDao,
) : AutomationRuleRepository {
    override fun observeAll(): Flow<List<RuleEntity>> = ruleDao.getAll()

    override suspend fun enabled(): List<RuleEntity> = ruleDao.getEnabled()

    override suspend fun find(id: Long): RuleEntity? = ruleDao.getById(id)

    override suspend fun findByName(name: String): RuleEntity? = ruleDao.getByName(name)

    override suspend fun create(rule: RuleEntity): Long = ruleDao.insert(rule)

    override suspend fun save(rule: RuleEntity) = ruleDao.update(rule)

    override suspend fun remove(rule: RuleEntity) = ruleDao.delete(rule)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = ruleDao.setEnabled(id, enabled)

    override suspend fun count(): Int = ruleDao.getCount()

    override suspend fun markTriggered(id: Long, timestamp: Long) {
        ruleDao.updateLastTriggered(id, timestamp)
    }
}

@Singleton
class RoomAutomationJournalRepository @Inject constructor(
    private val ruleLogDao: RuleLogDao,
) : AutomationJournalRepository {
    override fun observeRecent(limit: Int): Flow<List<RuleLogEntity>> = ruleLogDao.getRecent(limit)

    override suspend fun record(log: RuleLogEntity): Long = ruleLogDao.insert(log)
}
