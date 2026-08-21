package com.byd.wjx.led.ipixelfa

import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType

enum class IpixelFaPreset(
    val presetId: Int,
    val title: String,
) {
    EMERGENCY(1, "紧急情况"),
    TURN_OFF_HIGH_BEAM(2, "关闭远光灯"),
    TURN_LEFT(3, "左转"),
    TEMPORARY_STOP(4, "临时停靠"),
    TURN_RIGHT(5, "右转"),
    SMILE(6, "微笑"),
    SLOW_DOWN(7, "减速慢行"),
    KEEP_DISTANCE(8, "保持车距"),
    BABY_IN_CAR(9, "车内有婴儿"),
    YIELD_TO_PEDESTRIANS(10, "礼让行人"),
    REVERSING(11, "倒车"),
    PREGNANT_IN_CAR(12, "车内有孕妇"),
    DANCE_AT_RED_LIGHT(13, "等红灯跳舞"),
    AUTOPILOT(14, "自动驾驶"),
    REAR_END_WARNING(15, "追尾"),
    NEW_DRIVER(16, "实习"),
    NO_HONKING(17, "禁止鸣笛"),
    SLIPPERY_ROAD(18, "雨天路滑"),
    GET_OFF(19, "下车"),
    HAZARD_WARNING(20, "双闪危险报警"),
    ;

    companion object {
        fun fromPresetId(presetId: Int): IpixelFaPreset? =
            entries.firstOrNull { it.presetId == presetId }
    }
}

data class IpixelFaLinkageStep(
    val preset: IpixelFaPreset,
) {
    fun normalized(): IpixelFaLinkageStep = this
}

data class IpixelFaLinkageRule(
    val id: String,
    val enabled: Boolean = true,
    val triggerType: LedLinkageTriggerType,
    val conditionValue: String,
    val steps: List<IpixelFaLinkageStep>,
) {
    val conditionTitle: String
        get() = LedLinkageCatalog.options(triggerType)
            .firstOrNull { it.value == conditionValue }
            ?.title
            ?: conditionValue

    val title: String get() = "${triggerType.title} $conditionTitle"

    fun normalized(): IpixelFaLinkageRule = copy(
        conditionValue = conditionValue.takeIf { value ->
            LedLinkageCatalog.options(triggerType).any { it.value == value }
        } ?: LedLinkageCatalog.options(triggerType).first().value,
        // A linkage is stateful: keep one scene active while its condition is true.
        steps = steps.take(1).map(IpixelFaLinkageStep::normalized)
            .ifEmpty { listOf(IpixelFaLinkageStep(IpixelFaPreset.TEMPORARY_STOP)) },
    )
}

object IpixelFaLinkageRuleFactory {
    fun newRule(id: String): IpixelFaLinkageRule = IpixelFaLinkageRule(
        id = id,
        triggerType = LedLinkageTriggerType.SPEED,
        conditionValue = LedLinkageCatalog.options(LedLinkageTriggerType.SPEED).first().value,
        steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.TEMPORARY_STOP)),
    )

    /** Rules used when the FA protocol has never been configured before. */
    fun defaultRules(): List<IpixelFaLinkageRule> = listOf(
        rule("light-hazard", LedLinkageTriggerType.LIGHT, "hazard", IpixelFaPreset.HAZARD_WARNING),
        rule("auto-parking", LedLinkageTriggerType.AUTO_PARKING, "active", IpixelFaPreset.REVERSING),
        rule("light-reverse", LedLinkageTriggerType.LIGHT, "reverse", IpixelFaPreset.REVERSING),
        rule("gear-reverse", LedLinkageTriggerType.GEAR, "R", IpixelFaPreset.REVERSING),
        rule("light-left-turn", LedLinkageTriggerType.LIGHT, "left_turn", IpixelFaPreset.TURN_LEFT),
        rule("light-right-turn", LedLinkageTriggerType.LIGHT, "right_turn", IpixelFaPreset.TURN_RIGHT),
        rule("door-front-left", LedLinkageTriggerType.DOOR, "front_left", IpixelFaPreset.GET_OFF),
        rule("door-front-right", LedLinkageTriggerType.DOOR, "front_right", IpixelFaPreset.GET_OFF),
        rule("gear-park", LedLinkageTriggerType.GEAR, "P", IpixelFaPreset.TEMPORARY_STOP),
        rule("pilot-assist", LedLinkageTriggerType.PILOT_ASSIST, "active", IpixelFaPreset.AUTOPILOT),
        rule("acc", LedLinkageTriggerType.ACC, "active", IpixelFaPreset.AUTOPILOT),
    )

    private fun rule(
        id: String,
        triggerType: LedLinkageTriggerType,
        conditionValue: String,
        preset: IpixelFaPreset,
    ): IpixelFaLinkageRule = IpixelFaLinkageRule(
        id = "default-$id",
        triggerType = triggerType,
        conditionValue = conditionValue,
        steps = listOf(IpixelFaLinkageStep(preset)),
    )
}

enum class IpixelFaConnectionPhase {
    DISABLED,
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    READY,
    RETRYING,
    ERROR,
}

data class IpixelFaDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

data class IpixelFaConnectionState(
    val phase: IpixelFaConnectionPhase = IpixelFaConnectionPhase.IDLE,
    val address: String = "",
    val name: String = "",
    val message: String = "未连接",
    val retryInSeconds: Int? = null,
) {
    val ready: Boolean get() = phase == IpixelFaConnectionPhase.READY
}

data class IpixelFaPreferenceSnapshot(
    val enabled: Boolean = false,
    val deviceAddress: String = "",
    val deviceName: String = "",
    val brightness: Int = 70,
    val rules: List<IpixelFaLinkageRule> = emptyList(),
)

data class IpixelFaRuntimeState(
    val currentPreset: IpixelFaPreset? = null,
    val lastPresetChangedAt: Long = 0L,
    val lastCommandAt: Long = 0L,
    val lastError: String? = null,
)
