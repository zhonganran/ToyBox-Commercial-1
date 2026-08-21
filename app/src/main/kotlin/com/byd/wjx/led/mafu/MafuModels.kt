package com.byd.wjx.led.mafu

import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType

enum class MafuRemoteModel(
    val modelCode: Int,
    val keyCount: Int,
    val firstProgramId: Int,
    val title: String,
) {
    YS_17_KEY(0xAABF, 17, 76, "17键遥控器"),
    YS_20_KEY(0xABBF, 20, 76, "20键遥控器"),
    YS_9_KEY(0xACBF, 9, 87, "9键遥控器"),
    ;

    val programRange: IntRange
        get() = firstProgramId until firstProgramId + keyCount

    val supportedKeys: List<MafuRemoteKey>
        get() = MafuRemoteKey.entries.take(keyCount)

    fun programId(key: MafuRemoteKey): Int {
        require(key.keyNumber <= keyCount) { "$title 不支持遥控键 ${key.keyNumber}" }
        return firstProgramId + key.keyNumber - 1
    }

    companion object {
        fun fromModelCode(modelCode: Int): MafuRemoteModel? =
            entries.firstOrNull { it.modelCode == modelCode }
    }
}

enum class MafuRemoteKey(val keyNumber: Int) {
    KEY_1(1),
    KEY_2(2),
    KEY_3(3),
    KEY_4(4),
    KEY_5(5),
    KEY_6(6),
    KEY_7(7),
    KEY_8(8),
    KEY_9(9),
    KEY_10(10),
    KEY_11(11),
    KEY_12(12),
    KEY_13(13),
    KEY_14(14),
    KEY_15(15),
    KEY_16(16),
    KEY_17(17),
    KEY_18(18),
    KEY_19(19),
    KEY_20(20),
    ;

    val title: String get() = "遥控键 $keyNumber"

    companion object {
        fun fromKeyNumber(keyNumber: Int): MafuRemoteKey? =
            entries.firstOrNull { it.keyNumber == keyNumber }
    }
}

enum class MafuVehicleLinkageSlot(
    val programId: Int,
    val materialTag: Int,
    val title: String,
) {
    LEFT_TURN(67, 59, "左转"),
    RIGHT_TURN(68, 60, "右转"),
    BRAKE(69, 61, "刹车"),
    HAZARD(70, 62, "双闪"),
    REVERSE(71, 63, "倒车"),
}

data class MafuLinkageStep(
    val remoteKey: MafuRemoteKey,
) {
    fun normalized(model: MafuRemoteModel): MafuLinkageStep = copy(
        remoteKey = remoteKey.takeIf { it.keyNumber <= model.keyCount } ?: MafuRemoteKey.KEY_1,
    )
}

data class MafuLinkageRule(
    val id: String,
    val enabled: Boolean = true,
    val triggerType: LedLinkageTriggerType,
    val conditionValue: String,
    val steps: List<MafuLinkageStep>,
) {
    val conditionTitle: String
        get() = LedLinkageCatalog.options(triggerType)
            .firstOrNull { it.value == conditionValue }
            ?.title
            ?: conditionValue

    val title: String get() = "${triggerType.title} $conditionTitle"

    fun normalized(model: MafuRemoteModel): MafuLinkageRule = copy(
        conditionValue = conditionValue.takeIf { value ->
            LedLinkageCatalog.options(triggerType).any { it.value == value }
        } ?: LedLinkageCatalog.options(triggerType).first().value,
        // A linkage is stateful: keep one scene active while its condition is true.
        steps = steps.take(1).map { it.normalized(model) }
            .ifEmpty { listOf(MafuLinkageStep(MafuRemoteKey.KEY_1)) },
    )
}

object MafuLinkageRuleFactory {
    fun newRule(id: String, model: MafuRemoteModel): MafuLinkageRule = MafuLinkageRule(
        id = id,
        triggerType = LedLinkageTriggerType.SPEED,
        conditionValue = LedLinkageCatalog.options(LedLinkageTriggerType.SPEED).first().value,
        steps = listOf(MafuLinkageStep(model.supportedKeys.first())),
    )
}

enum class MafuConnectionPhase {
    DISABLED,
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    SUBSCRIBING,
    INITIALIZING,
    READY,
    RETRYING,
    ERROR,
}

data class MafuDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

data class MafuConnectionState(
    val phase: MafuConnectionPhase = MafuConnectionPhase.IDLE,
    val address: String = "",
    val name: String = "",
    val message: String = "未连接",
    val retryInSeconds: Int? = null,
) {
    val ready: Boolean get() = phase == MafuConnectionPhase.READY
}

data class MafuPreferenceSnapshot(
    val enabled: Boolean = false,
    val deviceAddress: String = "",
    val deviceName: String = "",
    val brightness: Int = 12,
    val remoteModel: MafuRemoteModel = MafuRemoteModel.YS_17_KEY,
    val rules: List<MafuLinkageRule> = emptyList(),
)

data class MafuRuntimeState(
    val currentRemoteKey: MafuRemoteKey? = null,
    val lastProgramId: Int? = null,
    val lastPresetChangedAt: Long = 0L,
    val lastCommandAt: Long = 0L,
    val lastError: String? = null,
    val screenPowerEnabled: Boolean = true,
)
