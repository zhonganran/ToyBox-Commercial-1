package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutomationConfigSnapshot(
    val enabledRules: List<RuleEntity> = emptyList(),
    val requiredVehicleFields: Set<String> = emptySet(),
    val initialized: Boolean = false,
)

interface AutomationConfigRepository {
    suspend fun current(): AutomationConfigSnapshot
}

/**
 * Process-level cache for automation definitions. Room invalidates the flows on edits, while the
 * 300 ms evaluation path reads memory instead of querying rules on every vehicle tick.
 */
@Singleton
class RoomAutomationConfigRepository @Inject constructor(
    private val ruleRepository: AutomationRuleRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) : AutomationConfigRepository {
    private val coldStartMutex = Mutex()
    @Volatile private var coldStartSnapshot: AutomationConfigSnapshot? = null

    private val state: StateFlow<AutomationConfigSnapshot> = ruleRepository.observeAll().map { rules ->
        val enabledRules = rules.filter(RuleEntity::enabled)
        AutomationConfigSnapshot(
            enabledRules = enabledRules,
            requiredVehicleFields = requiredAutomationVehicleFields(enabledRules),
            initialized = true,
        )
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = AutomationConfigSnapshot(),
    )

    override suspend fun current(): AutomationConfigSnapshot {
        state.value.takeIf { it.initialized }?.let { return it }
        coldStartSnapshot?.let { return it }
        return coldStartMutex.withLock {
            state.value.takeIf { it.initialized }
                ?: coldStartSnapshot
                ?: ruleRepository.enabled().let { enabledRules ->
                    AutomationConfigSnapshot(
                        enabledRules = enabledRules,
                        requiredVehicleFields = requiredAutomationVehicleFields(enabledRules),
                        initialized = true,
                    )
                }.also { coldStartSnapshot = it }
        }
    }
}
