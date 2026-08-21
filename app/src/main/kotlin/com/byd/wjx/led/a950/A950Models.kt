package com.byd.wjx.led.a950

import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType

/** Vivid resource slots are 1..20; the A950 remote-control byte is the zero-based command code. */
enum class A950Preset(
    val commandCode: Int,
    val title: String,
) {
    LET_ME_GO_FIRST(0, "请让我先走"),
    TURN_OFF_HIGH_BEAM(1, "请关闭远光灯"),
    LEFT_TURN(2, "左转"),
    RIGHT_TURN(3, "右转"),
    TEMPORARY_STOP(4, "临时停靠"),
    SMILE(5, "微笑"),
    SLOW_DOWN(6, "减速慢行"),
    KEEP_DISTANCE(7, "保持车距"),
    BABY_IN_CAR(8, "车内有婴儿"),
    YIELD_TO_PEDESTRIANS(9, "礼让行人"),
    REVERSING(10, "倒车请注意"),
    PREGNANT_IN_CAR(11, "车内有孕妇"),
    RELAX_AT_RED_LIGHT(12, "红灯放松片刻"),
    AUTOPILOT(13, "自动驾驶"),
    REAR_END_WARNING(14, "追尾全责"),
    NEW_DRIVER(15, "新手上路"),
    NO_HONKING(16, "禁止鸣笛"),
    SLIPPERY_ROAD(17, "雨天路滑"),
    OPEN_DOOR_CAREFULLY(18, "开门请注意"),
    HAZARD_WARNING(19, "双闪危险报警"),
    ;

    val slotNumber: Int get() = commandCode + 1

    companion object {
        fun fromCommandCode(commandCode: Int): A950Preset? =
            entries.firstOrNull { it.commandCode == commandCode }
    }
}

data class A950LinkageStep(
    val preset: A950Preset,
) {
    fun normalized(): A950LinkageStep = this
}

data class A950LinkageRule(
    val id: String,
    val enabled: Boolean = true,
    val triggerType: LedLinkageTriggerType,
    val conditionValue: String,
    val steps: List<A950LinkageStep>,
) {
    val conditionTitle: String
        get() = LedLinkageCatalog.options(triggerType)
            .firstOrNull { it.value == conditionValue }
            ?.title
            ?: conditionValue

    val title: String get() = "${triggerType.title} $conditionTitle"

    fun normalized(): A950LinkageRule = copy(
        conditionValue = conditionValue.takeIf { value ->
            LedLinkageCatalog.options(triggerType).any { it.value == value }
        } ?: LedLinkageCatalog.options(triggerType).first().value,
        // A linkage is stateful: keep one scene active while its condition is true.
        steps = steps.take(1).map(A950LinkageStep::normalized)
            .ifEmpty { listOf(A950LinkageStep(A950Preset.TEMPORARY_STOP)) },
    )
}

object A950LinkageRuleFactory {
    fun newRule(id: String): A950LinkageRule = A950LinkageRule(
        id = id,
        triggerType = LedLinkageTriggerType.SPEED,
        conditionValue = LedLinkageCatalog.options(LedLinkageTriggerType.SPEED).first().value,
        steps = listOf(A950LinkageStep(A950Preset.TEMPORARY_STOP)),
    )

    /** Rules used when the A950 protocol has never been configured before. */
    fun defaultRules(): List<A950LinkageRule> = listOf(
        rule("light-hazard", LedLinkageTriggerType.LIGHT, "hazard", A950Preset.HAZARD_WARNING),
        rule("auto-parking", LedLinkageTriggerType.AUTO_PARKING, "active", A950Preset.REVERSING),
        rule("light-reverse", LedLinkageTriggerType.LIGHT, "reverse", A950Preset.REVERSING),
        rule("gear-reverse", LedLinkageTriggerType.GEAR, "R", A950Preset.REVERSING),
        rule("light-left-turn", LedLinkageTriggerType.LIGHT, "left_turn", A950Preset.LEFT_TURN),
        rule("light-right-turn", LedLinkageTriggerType.LIGHT, "right_turn", A950Preset.RIGHT_TURN),
        rule("door-front-left", LedLinkageTriggerType.DOOR, "front_left", A950Preset.OPEN_DOOR_CAREFULLY),
        rule("door-front-right", LedLinkageTriggerType.DOOR, "front_right", A950Preset.OPEN_DOOR_CAREFULLY),
        rule("gear-park", LedLinkageTriggerType.GEAR, "P", A950Preset.TEMPORARY_STOP),
        rule("pilot-assist", LedLinkageTriggerType.PILOT_ASSIST, "active", A950Preset.AUTOPILOT),
        rule("acc", LedLinkageTriggerType.ACC, "active", A950Preset.AUTOPILOT),
    )

    private fun rule(
        id: String,
        triggerType: LedLinkageTriggerType,
        conditionValue: String,
        preset: A950Preset,
    ): A950LinkageRule = A950LinkageRule(
        id = "default-$id",
        triggerType = triggerType,
        conditionValue = conditionValue,
        steps = listOf(A950LinkageStep(preset)),
    )
}

enum class A950ConnectionPhase {
    DISABLED,
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    INITIALIZING,
    READY,
    RETRYING,
    ERROR,
}

data class A950Device(
    val address: String,
    val name: String,
    val rssi: Int,
)

data class A950ConnectionState(
    val phase: A950ConnectionPhase = A950ConnectionPhase.IDLE,
    val address: String = "",
    val name: String = "",
    val message: String = "未连接",
    val retryInSeconds: Int? = null,
) {
    val ready: Boolean get() = phase == A950ConnectionPhase.READY
}

data class A950PreferenceSnapshot(
    val enabled: Boolean = false,
    val deviceAddress: String = "",
    val deviceName: String = "",
    val brightness: Int = 70,
    val graffitiPreviewPath: String = "",
    val graffitiPayloadPath: String = "",
    val graffitiImageVersion: Long = 0L,
    val rules: List<A950LinkageRule> = emptyList(),
)

data class A950RuntimeState(
    val currentPreset: A950Preset? = null,
    val showingGraffiti: Boolean = false,
    val graffitiUploadInProgress: Boolean = false,
    val lastPresetChangedAt: Long = 0L,
    val lastCommandAt: Long = 0L,
    val lastError: String? = null,
)
