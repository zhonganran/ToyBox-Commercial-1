package com.byd.wjx.ui.automation

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.automation.AutomationJournalRepository
import com.byd.wjx.data.automation.AutomationRuleRepository
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.automation.ActionDispatcher
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val ruleRepository: AutomationRuleRepository,
    private val journalRepository: AutomationJournalRepository,
    private val vehicleApi: VehicleApi,
    private val actionDispatcher: ActionDispatcher,
    private val activationManager: ActivationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ruleRepository.observeAll().collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
        viewModelScope.launch {
            journalRepository.observeRecent(100).collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
    }

    fun setFilter(filter: RuleFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleEnabled(rule: RuleEntity) {
        viewModelScope.launch {
            val enabling = !rule.enabled
            if (enabling && !activationManager.canUse(PremiumFeature.AUTOMATION_UNLIMITED)) {
                val enabledCount = _uiState.value.rules.count { it.enabled }
                if (enabledCount >= ActivationManager.FREE_AUTOMATION_RULE_LIMIT) {
                    Toast.makeText(context, "未激活最多启用 3 条智能联动", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }
            ruleRepository.setEnabled(rule.id, enabling)
        }
    }

    /** Executes the exact editor action so media tests include their selected file and options. */
    fun executeNow(action: ActionDef) {
        viewModelScope.launch {
            val msg = if (action.kind == "param") {
                val result = vehicleApi.dispatch(action.command)
                if (result.isSuccess) "已发送"
                else "错误：${result.exceptionOrNull()?.message ?: "不可用"}"
            } else {
                val result = actionDispatcher.dispatch(
                    action = action,
                    data = null,
                    owner = "automation:preview",
                )
                if (result.success) result.reason ?: "已执行"
                else "错误：${result.reason ?: "不可用"}"
            }
            WjxFileLogger.i(
                "AutomationViewModel",
                "test action kind=${action.kind} command=${action.command} result=$msg",
            )
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Editor ---

    fun openNewRule() {
        if (_uiState.value.rules.size >= AUTOMATION_RULE_LIMIT) {
            Toast.makeText(context, "智能联动最多创建 $AUTOMATION_RULE_LIMIT 条", Toast.LENGTH_SHORT).show()
            return
        }
        if (!activationManager.canCreateAutomation(_uiState.value.rules.size)) {
            _uiState.update {
                it.copy(editorError = "试用已结束，未激活最多创建 3 条智能联动")
            }
            Toast.makeText(context, "试用已结束，未激活最多创建 3 条智能联动", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.update {
            it.copy(
                showEditor = true,
                editing = EditingRule(
                    triggers = emptyList(),
                    actions = emptyList()
                )
            )
        }
    }

    fun openEditRule(rule: RuleEntity) {
        _uiState.update {
            it.copy(
                showEditor = true,
                editing = EditingRule(
                    id = rule.id,
                    name = rule.name,
                    triggerLogic = rule.triggerLogic,
                    triggers = TriggerDef.listFromJson(rule.triggers),
                    actions = ActionDef.listFromJson(rule.actions),
                    exitActions = ActionDef.listFromJson(rule.exitActions),
                    cooldownSeconds = rule.cooldownSeconds,
                    repeatIntervalSeconds = rule.repeatIntervalSeconds,
                    requirePark = rule.requirePark,
                    confirmBeforeExecute = rule.confirmBeforeExecute,
                    fireOncePerTrip = rule.fireOncePerTrip,
                    isNew = false
                )
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false, editorError = null) }
    }

    fun updateEditing(transform: EditingRule.() -> EditingRule) {
        _uiState.update { it.copy(editing = it.editing.transform()) }
    }

    private fun validateActions(actions: List<ActionDef>): String? {
        actions.forEachIndexed { idx, a ->
            val n = idx + 1
            when (a.kind) {
                "param" -> {
                    if (a.command.isBlank()) return "动作 #$n：未指定命令"
                }
                "notification_silent", "notification_sound" -> {
                    if (a.notificationTitle().isBlank()) return "动作 #$n：通知标题为空"
                }
                "app_launch" -> {
                    if (a.appLaunchPackageName().isBlank()) return "动作 #$n：未选择应用"
                }
                "call" -> {
                    val phone = a.callPhone().trim()
                    if (phone.length !in 5..20) return "动作 #$n：电话号码不正确"
                }
                "navigate" -> {
                    val lat = a.navigateLat()
                    val lon = a.navigateLon()
                    if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) {
                        return "动作 #$n：未选择导航目的地"
                    }
                }
                "url" -> {
                    val u = a.urlString().trim()
                    if (u.isEmpty()) return "动作 #$n：链接为空"
                    // Allow any scheme: http(s)://, tel:, intent://, geo:, etc.
                    if (!u.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.+"))) {
                        return "动作 #$n：链接必须包含协议头（例如 http:// 或 tel:）"
                    }
                }
                "media_volume" -> {
                    val level = a.payload?.toIntOrNull()
                    if (level == null || level < 0) return "动作 #$n：未设置音量"
                }
                "outside_sound" -> {
                    if (a.outsideSoundPath().isBlank()) return "动作 #$n：未选择车外音效"
                }
                "inside_sound" -> {
                    if (a.insideSoundPath().isBlank()) return "动作 #$n：未选择车内音效"
                }
                "play_music" -> {
                    if (a.automationMediaPath().isBlank()) return "动作 #$n：未选择音乐"
                }
                "play_video" -> {
                    if (a.automationMediaPath().isBlank()) return "动作 #$n：未选择视频"
                }
                "sentry_mode" -> {
                    if (a.payload !in setOf("on", "off")) return "动作 #$n：哨兵模式设置无效"
                }
                "http" -> {
                    if (a.httpUrl().isBlank()) return "动作 #$n：HTTP 地址为空"
                }
                "auto_door" -> {
                    if (a.autoDoorActionOption() == null) return "动作 #$n：未选择自动滑门动作"
                }
            }
        }
        return null
    }

    fun saveRule() {
        val e = _uiState.value.editing
        if (!e.hasMinimumSaveFields()) {
            _uiState.value = _uiState.value.copy(editorError =
                "名称和条件不能为空，并至少添加一个进入或退出动作"
            )
            return
        }
        val actionError = validateActions(e.actions)
        val exitActionError = validateActions(e.exitActions)?.let { "退出$it" }
        if (actionError != null || exitActionError != null) {
            _uiState.value = _uiState.value.copy(editorError = actionError ?: exitActionError)
            return
        }
        if (e.repeatIntervalSeconds != 0 && e.repeatIntervalSeconds !in AUTOMATION_REPEAT_MIN_SECONDS..AUTOMATION_REPEAT_MAX_SECONDS) {
            _uiState.value = _uiState.value.copy(
                editorError = "循环触发间隔必须为 0（关闭）或 $AUTOMATION_REPEAT_MIN_SECONDS-$AUTOMATION_REPEAT_MAX_SECONDS 秒"
            )
            return
        }
        // Clear previous error on success path
        _uiState.value = _uiState.value.copy(editorError = null)

        viewModelScope.launch {
            if (e.isNew && !activationManager.canCreateAutomation(ruleRepository.count())) {
                _uiState.update { it.copy(editorError = "试用已结束，未激活最多创建 3 条智能联动") }
                return@launch
            }
            val existing = if (e.isNew) null else ruleRepository.find(e.id)
            if (!e.isNew && existing == null) {
                _uiState.update { it.copy(editorError = "规则已被删除，无法保存") }
                return@launch
            }
            val entity = e.toRuleEntity(existing)
            val savedId = if (e.isNew) {
                ruleRepository.create(entity)
            } else {
                ruleRepository.save(entity)
                entity.id
            }
            WjxFileLogger.i(
                "AutomationViewModel",
                "rule saved id=$savedId name='${entity.name}' enabled=${entity.enabled} " +
                    "enterActions=${e.actions.joinToString { it.kind }} exitActions=${e.exitActions.joinToString { it.kind }}",
            )
            Toast.makeText(context, "智能联动已保存", Toast.LENGTH_SHORT).show()
            closeEditor()
        }
    }

    // --- Duplicate / Delete ---

    fun duplicateRule(rule: RuleEntity) {
        viewModelScope.launch {
            val ruleCount = ruleRepository.count()
            if (ruleCount >= AUTOMATION_RULE_LIMIT) {
                Toast.makeText(context, "智能联动最多创建 $AUTOMATION_RULE_LIMIT 条", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!activationManager.canCreateAutomation(ruleCount)) {
                Toast.makeText(context, "试用已结束，未激活最多创建 3 条智能联动", Toast.LENGTH_SHORT).show()
                return@launch
            }
            ruleRepository.create(
                rule.copy(
                    id = 0,
                    name = "${rule.name} (${"副本"})",
                    enabled = false,
                    lastTriggeredAt = null,
                    triggerCount = 0,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun requestDelete(ruleId: Long) {
        _uiState.update { it.copy(showDeleteConfirm = ruleId) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun confirmDelete() {
        val ruleId = _uiState.value.showDeleteConfirm ?: return
        viewModelScope.launch {
            ruleRepository.find(ruleId)?.let { ruleRepository.remove(it) }
        }
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    // --- Journal ---

    fun showJournal() { _uiState.update { it.copy(showJournal = true) } }
    fun hideJournal() { _uiState.update { it.copy(showJournal = false) } }

}

internal fun EditingRule.toRuleEntity(existing: RuleEntity? = null): RuleEntity {
    val base = existing ?: RuleEntity(
        name = "",
        triggers = "[]",
        actions = "[]",
    )
    return base.copy(
        name = name.trim(),
        triggerLogic = triggerLogic,
        triggers = TriggerDef.listToJson(triggers),
        actions = ActionDef.listToJson(actions),
        exitActions = ActionDef.listToJson(exitActions),
        cooldownSeconds = cooldownSeconds.coerceIn(
            AUTOMATION_COOLDOWN_MIN_SECONDS,
            AUTOMATION_COOLDOWN_MAX_SECONDS,
        ),
        repeatIntervalSeconds = repeatIntervalSeconds.coerceIn(0, AUTOMATION_REPEAT_MAX_SECONDS),
        requirePark = requirePark,
        confirmBeforeExecute = confirmBeforeExecute,
        fireOncePerTrip = fireOncePerTrip,
    )
}
