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
    val adasApaParkingStatus: Int? = null,
    val adasAccMode: Int? = null,
    val adasLccNcaStatus: Int? = null,
    val accCruiseState: Int? = null,
    val adasMnoaStatus: Int? = null,
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
    AUTO_PARKING("自动泊车"),
    ACC("ACC"),
    PILOT_ASSIST("领航辅助"),
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
        LedLinkageTriggerType.AUTO_PARKING to listOf(
            LedLinkageConditionOption("active", "自动泊车进行中"),
        ),
        LedLinkageTriggerType.ACC to listOf(
            LedLinkageConditionOption("active", "ACC 已激活"),
        ),
        LedLinkageTriggerType.PILOT_ASSIST to listOf(
            LedLinkageConditionOption("active", "LCC/NCA/NOA 已激活"),
        ),
    )

    fun options(type: LedLinkageTriggerType): List<LedLinkageConditionOption> =
        checkNotNull(optionsByType[type])
}

internal object LedLinkageMatcher {
    fun matches(
        type: LedLinkageTriggerType,
        condition: String,
        signals: LedVehicleSignals,
    ): Boolean = when (type) {
        LedLinkageTriggerType.SPEED -> when (condition) {
            "gt_30" -> signals.speed?.let { it > 30 } == true
            "gt_60" -> signals.speed?.let { it > 60 } == true
            "gt_80" -> signals.speed?.let { it > 80 } == true
            "gt_100" -> signals.speed?.let { it > 100 } == true
            "lt_20" -> signals.speed?.let { it < 20 } == true
            else -> false
        }
        LedLinkageTriggerType.GEAR -> signals.gear == when (condition) {
            "P" -> 1
            "R" -> 2
            "N" -> 3
            "D" -> 4
            "S" -> 5
            "M" -> 6
            else -> null
        }
        LedLinkageTriggerType.ACCELERATOR ->
            signals.accelPedalDepth.exceedsEncodedThreshold(condition)
        LedLinkageTriggerType.BRAKE ->
            signals.brakePedalDepth.exceedsEncodedThreshold(condition)
        LedLinkageTriggerType.DOOR -> when (condition) {
            "front_left" -> signals.doorFL == 1
            "front_right" -> signals.doorFR == 1
            "rear_left" -> signals.doorRL == 1
            "rear_right" -> signals.doorRR == 1
            "trunk" -> signals.trunk == 1
            else -> false
        }
        LedLinkageTriggerType.LIGHT -> when (condition) {
            "left_turn" -> signals.turnSignalFlash == 2
            "right_turn" -> signals.turnSignalFlash == 4
            "hazard" -> signals.turnSignalFlash == 6
            "brake" -> signals.lightBrake == 1
            "reverse" -> signals.lightReversing == 1
            else -> false
        }
        LedLinkageTriggerType.AUTO_PARKING ->
            condition == "active" && signals.adasApaParkingStatus == 4
        LedLinkageTriggerType.ACC -> condition == "active" && (
            signals.accCruiseState == 2 ||
                signals.adasAccMode?.let { it in ACC_ACTIVE_STATES } == true
        )
        LedLinkageTriggerType.PILOT_ASSIST -> condition == "active" && (
            signals.adasLccNcaStatus == 2 || signals.adasMnoaStatus == 2
        )
    }

    private fun Int?.exceedsEncodedThreshold(condition: String): Boolean {
        val threshold = condition.removePrefix("gt_").toIntOrNull() ?: return false
        return this?.let { it > threshold } == true
    }

    private val ACC_ACTIVE_STATES = setOf(3, 4, 6, 7)
}
