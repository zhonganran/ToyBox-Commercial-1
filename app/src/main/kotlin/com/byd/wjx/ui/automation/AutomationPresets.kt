package com.byd.wjx.ui.automation

import com.byd.wjx.data.automation.AutomationRuleRepository
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.repository.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY =
    "default_automation_presets_initialized"
private val defaultAutomationPresetSeedMutex = Mutex()

internal enum class AutomationPreset(val label: String) {
    PLUG_IN_SOUND("插枪播放车外音效"),
    UNPLUG_SOUND("拔枪播放车外音效"),
    REVERSE_SOUND("倒车播放车外音效"),
    AUTO_PARKING_SOUND("自动泊车播放车外音效"),
}

internal fun AutomationPreset.toEditingRule(outsideSoundAction: ActionDef): EditingRule {
    val pluggedTrigger = TriggerDef(
        param = "ChargeGunState",
        chineseName = "充电枪",
        operator = ">=",
        value = "2",
        displayName = "充电枪已插入",
    )
    val loopingOutsideSoundAction = outsideSoundAction.withOutsideSoundLoop(true)
    return when (this) {
        AutomationPreset.PLUG_IN_SOUND -> EditingRule(
            name = label,
            triggers = listOf(pluggedTrigger),
            actions = listOf(outsideSoundAction),
            cooldownSeconds = 2,
        )
        AutomationPreset.UNPLUG_SOUND -> EditingRule(
            name = label,
            triggers = listOf(pluggedTrigger),
            exitActions = listOf(outsideSoundAction),
            cooldownSeconds = 2,
        )
        AutomationPreset.REVERSE_SOUND -> EditingRule(
            name = label,
            triggers = listOf(
                TriggerDef(
                    param = "Gear",
                    chineseName = "档位",
                    operator = "==",
                    value = "2",
                    displayName = "挂入倒车挡",
                ),
            ),
            actions = listOf(loopingOutsideSoundAction),
            cooldownSeconds = 2,
        )
        AutomationPreset.AUTO_PARKING_SOUND -> EditingRule(
            name = label,
            triggers = listOf(
                TriggerDef(
                    param = "AdasApaParkingStatus",
                    chineseName = "自动泊车",
                    operator = "==",
                    value = "4",
                    displayName = "自动泊车执行中",
                ),
            ),
            actions = listOf(loopingOutsideSoundAction),
            cooldownSeconds = 2,
        )
    }
}

internal suspend fun seedDefaultAutomationPresets(
    ruleRepository: AutomationRuleRepository,
    settingsRepository: SettingsRepository,
    outsideSoundAction: ActionDef,
    now: Long = System.currentTimeMillis(),
): Int = defaultAutomationPresetSeedMutex.withLock {
    if (settingsRepository.getString(DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY, "false") == "true") {
        return@withLock 0
    }

    var ruleCount = ruleRepository.count()
    var insertedCount = 0
    var completed = true

    for (preset in AutomationPreset.entries) {
        if (ruleRepository.findByName(preset.label) != null) continue
        if (ruleCount >= AUTOMATION_RULE_LIMIT) {
            completed = false
            break
        }

        ruleRepository.create(
            preset.toEditingRule(outsideSoundAction)
                .toRuleEntity()
                .copy(
                    enabled = false,
                    createdAt = now - preset.ordinal,
                ),
        )
        ruleCount += 1
        insertedCount += 1
    }

    if (completed) {
        settingsRepository.setString(DEFAULT_AUTOMATION_PRESETS_INITIALIZED_KEY, "true")
    }
    insertedCount
}
