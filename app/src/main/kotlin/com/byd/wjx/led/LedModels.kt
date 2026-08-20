package com.byd.wjx.led

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
