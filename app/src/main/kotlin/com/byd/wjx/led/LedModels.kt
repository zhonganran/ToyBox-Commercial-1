package com.byd.wjx.led

/** Fixed firmware scenes exposed by the vehicle LED panel. */
enum class LedScene(
    val title: String,
    val remotePresetId: Int,
    val priority: Int,
) {
    DEFAULT("微笑", 6, 0),
    LEFT_TURN("左转", 3, 70),
    RIGHT_TURN("右转", 5, 70),
    TEMPORARY_STOP("临时停靠", 4, 40),
    REVERSE_PARKING("倒车入库", 11, 90),
    AUTOPILOT("自动驾驶", 14, 60),
    EXIT_VEHICLE("下车", 19, 50),
    HAZARD("双闪", 20, 100),
    BRAKE_WARNING("刹车提醒", 8, 55),
}

enum class LedConnectionPhase {
    DISABLED,
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    READY,
    RETRYING,
    ERROR,
}

data class LedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

data class LedConnectionState(
    val phase: LedConnectionPhase = LedConnectionPhase.IDLE,
    val address: String = "",
    val name: String = "",
    val message: String = "未连接",
    val retryInSeconds: Int? = null,
) {
    val ready: Boolean get() = phase == LedConnectionPhase.READY
}

data class LedPreferenceSnapshot(
    val enabled: Boolean = false,
    val deviceAddress: String = "",
    val deviceName: String = "",
    val brightness: Int = 70,
    val rules: List<LedLinkageRule> = LedLinkageDefaults.rules,
)

data class LedVehicleSignals(
    val speed: Int? = null,
    val turnSignalFlash: Int? = null,
    val gear: Int? = null,
    val powerState: Int? = null,
    val doorFL: Int? = null,
    val doorFR: Int? = null,
    val doorRL: Int? = null,
    val doorRR: Int? = null,
    val trunk: Int? = null,
    val brakePedalDepth: Int? = null,
    val lightBrake: Int? = null,
    val lightReversing: Int? = null,
    val accelPedalDepth: Int? = null,
)

data class LedRuntimeState(
    val currentScene: LedScene = LedScene.DEFAULT,
    val lastSceneChangedAt: Long = 0L,
    val lastCommandAt: Long = 0L,
    val lastError: String? = null,
)

enum class LedLinkageTriggerType(val title: String) {
    SPEED("车速"),
    GEAR("挡位"),
    ACCELERATOR("油门"),
    BRAKE("制动"),
    DOOR("车门"),
    LIGHT("灯光"),
}

data class LedLinkageConditionOption(
    val value: String,
    val title: String,
)

data class LedLinkageStep(
    val scene: LedScene,
    val durationSeconds: Int = 5,
) {
    fun normalized(): LedLinkageStep = copy(durationSeconds = durationSeconds.coerceIn(1, 60))
}

data class LedLinkageRule(
    val id: String,
    val enabled: Boolean = true,
    val triggerType: LedLinkageTriggerType,
    val conditionValue: String,
    val steps: List<LedLinkageStep>,
) {
    val conditionTitle: String
        get() = LedLinkageCatalog.options(triggerType)
            .firstOrNull { it.value == conditionValue }
            ?.title
            ?: conditionValue

    val title: String get() = "${triggerType.title} $conditionTitle"

    fun normalized(): LedLinkageRule = copy(
        conditionValue = conditionValue.takeIf {
            value -> LedLinkageCatalog.options(triggerType).any { it.value == value }
        } ?: LedLinkageCatalog.options(triggerType).first().value,
        steps = steps.take(8).map(LedLinkageStep::normalized)
            .ifEmpty { listOf(LedLinkageStep(LedScene.DEFAULT)) },
    )
}

object LedLinkageCatalog {
    private val percentOptions = (10..90 step 10).map {
        LedLinkageConditionOption("gt_$it", "> $it%")
    }

    private val optionsByType = mapOf(
        LedLinkageTriggerType.SPEED to listOf(
            LedLinkageConditionOption("gt_30", "> 30 km/h"),
            LedLinkageConditionOption("gt_60", "> 60 km/h"),
            LedLinkageConditionOption("gt_80", "> 80 km/h"),
            LedLinkageConditionOption("gt_100", "> 100 km/h"),
            LedLinkageConditionOption("lt_20", "< 20 km/h"),
        ),
        LedLinkageTriggerType.GEAR to listOf(
            LedLinkageConditionOption("D", "D挡"),
            LedLinkageConditionOption("R", "R挡"),
            LedLinkageConditionOption("N", "N挡"),
            LedLinkageConditionOption("P", "P挡"),
            LedLinkageConditionOption("S", "S挡"),
            LedLinkageConditionOption("M", "M挡"),
        ),
        LedLinkageTriggerType.ACCELERATOR to percentOptions,
        LedLinkageTriggerType.BRAKE to percentOptions,
        LedLinkageTriggerType.DOOR to listOf(
            LedLinkageConditionOption("front_left", "左前门打开"),
            LedLinkageConditionOption("front_right", "右前门打开"),
            LedLinkageConditionOption("rear_left", "左后门打开"),
            LedLinkageConditionOption("rear_right", "右后门打开"),
            LedLinkageConditionOption("trunk", "后备箱打开"),
        ),
        LedLinkageTriggerType.LIGHT to listOf(
            LedLinkageConditionOption("left_turn", "左转灯"),
            LedLinkageConditionOption("right_turn", "右转灯"),
            LedLinkageConditionOption("hazard", "双闪"),
            LedLinkageConditionOption("brake", "刹车灯"),
            LedLinkageConditionOption("reverse", "倒车灯"),
        ),
    )

    fun options(type: LedLinkageTriggerType): List<LedLinkageConditionOption> =
        checkNotNull(optionsByType[type])
}

object LedLinkageDefaults {
    val rules: List<LedLinkageRule> = listOf(
        rule("default_hazard", LedLinkageTriggerType.LIGHT, "hazard", LedScene.HAZARD),
        rule("default_reverse", LedLinkageTriggerType.GEAR, "R", LedScene.REVERSE_PARKING),
        rule("default_left_turn", LedLinkageTriggerType.LIGHT, "left_turn", LedScene.LEFT_TURN),
        rule("default_right_turn", LedLinkageTriggerType.LIGHT, "right_turn", LedScene.RIGHT_TURN),
        rule("default_brake", LedLinkageTriggerType.BRAKE, "gt_10", LedScene.BRAKE_WARNING),
        rule("default_door_fl", LedLinkageTriggerType.DOOR, "front_left", LedScene.EXIT_VEHICLE),
        rule("default_door_fr", LedLinkageTriggerType.DOOR, "front_right", LedScene.EXIT_VEHICLE),
        rule("default_door_rl", LedLinkageTriggerType.DOOR, "rear_left", LedScene.EXIT_VEHICLE),
        rule("default_door_rr", LedLinkageTriggerType.DOOR, "rear_right", LedScene.EXIT_VEHICLE),
        rule("default_trunk", LedLinkageTriggerType.DOOR, "trunk", LedScene.EXIT_VEHICLE),
    )

    fun newRule(id: String): LedLinkageRule = LedLinkageRule(
        id = id,
        triggerType = LedLinkageTriggerType.SPEED,
        conditionValue = LedLinkageCatalog.options(LedLinkageTriggerType.SPEED).first().value,
        steps = listOf(LedLinkageStep(LedScene.TEMPORARY_STOP)),
    )

    private fun rule(
        id: String,
        type: LedLinkageTriggerType,
        condition: String,
        scene: LedScene,
    ) = LedLinkageRule(
        id = id,
        triggerType = type,
        conditionValue = condition,
        steps = listOf(LedLinkageStep(scene)),
    )
}
