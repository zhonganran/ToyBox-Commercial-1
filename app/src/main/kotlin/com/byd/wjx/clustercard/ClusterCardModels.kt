package com.byd.wjx.clustercard

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.domain.vehicle.totalVehicleRangeKm
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

const val CLUSTER_CARD_MAX_METRICS = 8
const val CLUSTER_CARD_MIN_FONT_SCALE = 0.7f
const val CLUSTER_CARD_MAX_FONT_SCALE = 1.5f
const val CLUSTER_CARD_MIN_HEIGHT_SCALE = 0.55f
const val CLUSTER_CARD_MAX_HEIGHT_SCALE = 1.4f

enum class ClusterCardSlot(
    val label: String,
    internal val preferenceSuffix: String,
    internal val defaultXPct: Float,
    internal val defaultYPct: Float,
) {
    PRIMARY("仪表卡片", "", 50f, 50f),
    SECONDARY("仪表卡片2", "_2", 100f, 50f),
}

interface ClusterCardColorOption {
    val id: String
    val label: String
    val argb: Int
}

enum class ClusterCardTextColor(
    override val id: String,
    override val label: String,
    override val argb: Int,
) : ClusterCardColorOption {
    DEEP_BLACK("deep_black", "深黑", 0xFF05070A.toInt()),
    CHARCOAL("charcoal", "炭灰", 0xFF252A30.toInt()),
    MIDNIGHT_BLUE("midnight_blue", "深蓝", 0xFF102A43.toInt()),
    FOREST_GREEN("forest_green", "深绿", 0xFF123D2F.toInt()),
    BURGUNDY("burgundy", "深红", 0xFF5A1722.toInt()),
    DEEP_VIOLET("deep_violet", "深紫", 0xFF3E2A5C.toInt()),
    BRIGHT_WHITE("bright_white", "亮白", 0xFFF5F7FA.toInt()),
    ;

    companion object {
        val DEFAULT: ClusterCardTextColor = DEEP_BLACK

        fun fromId(id: String?): ClusterCardTextColor =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class ClusterCardBackgroundColor(
    override val id: String,
    override val label: String,
    override val argb: Int,
) : ClusterCardColorOption {
    BLACK("black", "黑底", 0xB8000000.toInt()),
    CHARCOAL("charcoal", "灰底", 0xB82A2F35.toInt()),
    MIDNIGHT_BLUE("midnight_blue", "蓝底", 0xB8102A43.toInt()),
    FOREST_GREEN("forest_green", "绿底", 0xB8123D2F.toInt()),
    BURGUNDY("burgundy", "红底", 0xB85A1722.toInt()),
    LIGHT_GRAY("light_gray", "浅色底", 0xC0EDF1F5.toInt()),
    ;

    companion object {
        val DEFAULT: ClusterCardBackgroundColor = BLACK

        fun fromId(id: String?): ClusterCardBackgroundColor =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class ClusterCardConfig(
    val enabled: Boolean = false,
    val metricIds: List<String> = emptyList(),
    val scale: Float = 0.65f,
    val widthScale: Float = 1f,
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    val fontScale: Float = 1f,
    val heightScale: Float = 1f,
    val adaptiveTextColorEnabled: Boolean = true,
    val manualTextColorId: String = ClusterCardTextColor.DEFAULT.id,
    val textOutlineEnabled: Boolean = false,
    val backgroundEnabled: Boolean = false,
    val backgroundColorId: String = ClusterCardBackgroundColor.DEFAULT.id,
)

internal fun sanitizeClusterCardConfig(raw: ClusterCardConfig): ClusterCardConfig = raw.copy(
    metricIds = raw.metricIds
        .distinct()
        .filter(ClusterCardMetricCatalog.byId::containsKey)
        .take(CLUSTER_CARD_MAX_METRICS),
    scale = raw.scale.coerceIn(0.45f, 1f),
    widthScale = raw.widthScale.coerceIn(0.7f, 1.4f),
    xPct = raw.xPct.coerceIn(0f, 100f),
    yPct = raw.yPct.coerceIn(0f, 100f),
    fontScale = raw.fontScale.coerceIn(CLUSTER_CARD_MIN_FONT_SCALE, CLUSTER_CARD_MAX_FONT_SCALE),
    heightScale = raw.heightScale.coerceIn(
        CLUSTER_CARD_MIN_HEIGHT_SCALE,
        CLUSTER_CARD_MAX_HEIGHT_SCALE,
    ),
    manualTextColorId = ClusterCardTextColor.fromId(raw.manualTextColorId).id,
    backgroundColorId = ClusterCardBackgroundColor.fromId(raw.backgroundColorId).id,
)

internal object ClusterCardTextAppearance {
    fun textColor(config: ClusterCardConfig, isNightMode: Boolean): Int =
        if (config.adaptiveTextColorEnabled) {
            if (isNightMode) AUTOMATIC_NIGHT_COLOR else AUTOMATIC_DAY_COLOR
        } else {
            ClusterCardTextColor.fromId(config.manualTextColorId).argb
        }

    fun outlineColor(textColor: Int): Int {
        val red = textColor ushr 16 and 0xFF
        val green = textColor ushr 8 and 0xFF
        val blue = textColor and 0xFF
        val perceivedBrightness = (red * 299 + green * 587 + blue * 114) / 1000
        return if (perceivedBrightness < DARK_COLOR_THRESHOLD) LIGHT_OUTLINE else DARK_OUTLINE
    }

    fun outlineEnabled(
        config: ClusterCardConfig,
        textColor: Int,
        backgroundColor: Int?,
    ): Boolean = config.textOutlineEnabled || needsContrastProtection(textColor, backgroundColor)

    fun needsContrastProtection(textColor: Int, backgroundColor: Int?): Boolean {
        if (backgroundColor == null) return false
        val textRgb = rgb(textColor)
        return EXTREME_INSTRUMENT_BACKGROUNDS.any { instrumentBackground ->
            val composedBackground = composite(backgroundColor, instrumentBackground)
            contrastRatio(textRgb, composedBackground) < MINIMUM_TEXT_CONTRAST_RATIO
        }
    }

    private fun composite(foreground: Int, background: Rgb): Rgb {
        val alpha = foreground ushr 24 and 0xFF
        val inverseAlpha = 0xFF - alpha
        val foregroundRgb = rgb(foreground)
        return Rgb(
            red = blendChannel(foregroundRgb.red, background.red, alpha, inverseAlpha),
            green = blendChannel(foregroundRgb.green, background.green, alpha, inverseAlpha),
            blue = blendChannel(foregroundRgb.blue, background.blue, alpha, inverseAlpha),
        )
    }

    private fun blendChannel(
        foreground: Int,
        background: Int,
        alpha: Int,
        inverseAlpha: Int,
    ): Int = (foreground * alpha + background * inverseAlpha + 127) / 255

    private fun contrastRatio(first: Rgb, second: Rgb): Double {
        val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
        val darker = minOf(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Rgb): Double =
        0.2126 * linearChannel(color.red) +
            0.7152 * linearChannel(color.green) +
            0.0722 * linearChannel(color.blue)

    private fun linearChannel(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun rgb(color: Int): Rgb = Rgb(
        red = color ushr 16 and 0xFF,
        green = color ushr 8 and 0xFF,
        blue = color and 0xFF,
    )

    private data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int,
    )

    private const val AUTOMATIC_DAY_COLOR = 0xFF000000.toInt()
    private const val AUTOMATIC_NIGHT_COLOR = 0xFFFFFFFF.toInt()
    private const val LIGHT_OUTLINE = 0xFFFFFFFF.toInt()
    private const val DARK_OUTLINE = 0xFF000000.toInt()
    private const val DARK_COLOR_THRESHOLD = 150
    private const val MINIMUM_TEXT_CONTRAST_RATIO = 4.5
    private val EXTREME_INSTRUMENT_BACKGROUNDS = listOf(
        Rgb(0, 0, 0),
        Rgb(255, 255, 255),
    )
}

internal object ClusterCardBackgroundAppearance {
    fun color(config: ClusterCardConfig): Int? =
        if (config.backgroundEnabled) {
            ClusterCardBackgroundColor.fromId(config.backgroundColorId).argb
        } else {
            null
        }
}

internal object ClusterCardVisibility {
    fun shouldShow(
        config: ClusterCardConfig,
        vehicleConnected: Boolean,
        overlayPermissionGranted: Boolean,
    ): Boolean = config.enabled &&
        vehicleConnected &&
        overlayPermissionGranted &&
        ClusterCardMetricCatalog.selected(config).isNotEmpty()
}

enum class ClusterCardMetric(
    val id: String,
    val label: String,
    val category: String,
    val telemetryField: String? = null,
) {
    SPEED("speed", "车速", "行驶"),
    GEAR("gear", "挡位", "行驶"),
    DRIVE_MODE("drive_mode", "驾驶模式", "行驶"),
    WORK_MODE("work_mode", "动力模式", "行驶"),
    POWER("power", "实时功率", "行驶"),
    ENGINE_RPM("engine_rpm", "发动机转速", "行驶", "engineRpm"),
    FRONT_MOTOR_RPM("front_motor_rpm", "前电机转速", "行驶", "frontMotorSpeed"),
    REAR_MOTOR_RPM("rear_motor_rpm", "后电机转速", "行驶", "rearMotorSpeed"),

    SOC("soc", "剩余电量", "能耗"),
    SOH("soh", "电池健康度", "能耗"),
    FUEL_LEVEL("fuel_level", "剩余油量", "能耗"),
    ELECTRIC_RANGE("electric_range", "纯电续航", "能耗"),
    FUEL_RANGE("fuel_range", "燃油续航", "能耗"),
    TOTAL_RANGE("total_range", "总续航", "能耗"),
    ELECTRIC_CONSUMPTION("electric_consumption", "百公里电耗", "能耗"),
    FUEL_CONSUMPTION("fuel_consumption", "百公里油耗", "能耗"),
    TRIP_ELECTRICITY("trip_electricity", "本次用电", "能耗"),
    TRIP_FUEL("trip_fuel", "本次用油", "能耗"),
    VOLTAGE_12V("voltage_12v", "小电瓶电压", "能耗"),
    CELL_VOLTAGE_DELTA("cell_voltage_delta", "电芯压差", "能耗"),

    CABIN_TEMP("cabin_temp", "车内温度", "温度"),
    EXTERIOR_TEMP("exterior_temp", "车外温度", "温度"),
    COOLANT_TEMP("coolant_temp", "发动机水温", "温度", "waterTemp"),
    BATTERY_MAX_TEMP("battery_max_temp", "电池最高温度", "温度"),
    BATTERY_AVG_TEMP("battery_avg_temp", "电池平均温度", "温度"),
    BATTERY_MIN_TEMP("battery_min_temp", "电池最低温度", "温度"),
    FRONT_MOTOR_TEMP("front_motor_temp", "前电机温度", "温度", "frontMotorTemp"),
    REAR_MOTOR_TEMP("rear_motor_temp", "后电机温度", "温度", "rearMotorTemp"),
    AC_TEMP("ac_temp", "空调设定温度", "温度"),

    TIRE_PRESSURE_FL("tire_pressure_fl", "左前胎压", "轮胎"),
    TIRE_PRESSURE_FR("tire_pressure_fr", "右前胎压", "轮胎"),
    TIRE_PRESSURE_RL("tire_pressure_rl", "左后胎压", "轮胎"),
    TIRE_PRESSURE_RR("tire_pressure_rr", "右后胎压", "轮胎"),
    ;

    fun displayValue(base: DiParsData?, telemetry: DiParsData?): String = when (this) {
        SPEED -> base?.speed?.let { "$it km/h" }
        GEAR -> base?.gear?.let(::gearText)
        DRIVE_MODE -> base?.driveMode?.let(::driveModeText)
        WORK_MODE -> base?.workMode?.let(::workModeText)
        POWER -> base?.power?.let { decimal(it, "kW") }
        ENGINE_RPM -> (telemetry?.engineRpm ?: base?.engineRpm)?.let { "$it rpm" }
        FRONT_MOTOR_RPM -> (telemetry?.frontMotorSpeed ?: base?.frontMotorSpeed)?.let { "$it rpm" }
        REAR_MOTOR_RPM -> (telemetry?.rearMotorSpeed ?: base?.rearMotorSpeed)?.let { "$it rpm" }

        SOC -> base?.soc?.let { "$it%" }
        SOH -> base?.soh?.let { "$it%" }
        FUEL_LEVEL -> base?.fuelLevel?.let { "$it%" }
        ELECTRIC_RANGE -> base?.elecDrivingRangeKm?.let { "$it km" }
        FUEL_RANGE -> base?.fuelDrivingRangeKm?.let { "$it km" }
        TOTAL_RANGE -> totalVehicleRangeKm(
            base?.elecDrivingRangeKm,
            base?.fuelDrivingRangeKm,
        )?.let { "$it km" }
        ELECTRIC_CONSUMPTION -> base?.elecConPer100km?.let { decimal(it, "kWh/100km") }
        FUEL_CONSUMPTION -> base?.fuelConPer100km?.let { decimal(it, "L/100km") }
        TRIP_ELECTRICITY -> base?.currentTripElecKwh?.let { decimal(it, "kWh") }
        TRIP_FUEL -> base?.currentTripFuelL?.let { decimal(it, "L") }
        VOLTAGE_12V -> base?.voltage12v?.let { decimal(it, "V") }
        CELL_VOLTAGE_DELTA -> cellVoltageDelta(base)?.let { decimal(it, "V", 3) }

        CABIN_TEMP -> base?.insideTemp?.let(::temperature)
        EXTERIOR_TEMP -> base?.exteriorTemp?.let(::temperature)
        COOLANT_TEMP -> (telemetry?.waterTemp ?: base?.waterTemp)?.let(::temperature)
        BATTERY_MAX_TEMP -> base?.maxBatTemp?.let(::temperature)
        BATTERY_AVG_TEMP -> base?.avgBatTemp?.let(::temperature)
        BATTERY_MIN_TEMP -> base?.minBatTemp?.let(::temperature)
        FRONT_MOTOR_TEMP -> (telemetry?.frontMotorTemp ?: base?.frontMotorTemp)?.let(::temperature)
        REAR_MOTOR_TEMP -> (telemetry?.rearMotorTemp ?: base?.rearMotorTemp)?.let(::temperature)
        AC_TEMP -> base?.acTemp?.let(::temperature)

        TIRE_PRESSURE_FL -> base?.tirePressFL?.let(::pressure)
        TIRE_PRESSURE_FR -> base?.tirePressFR?.let(::pressure)
        TIRE_PRESSURE_RL -> base?.tirePressRL?.let(::pressure)
        TIRE_PRESSURE_RR -> base?.tirePressRR?.let(::pressure)
    } ?: "—"

    private companion object {
        fun temperature(value: Int): String = "$value°C"

        fun pressure(value: Int): String = "$value kPa"

        fun decimal(value: Double, unit: String, digits: Int = 1): String =
            String.format(Locale.US, "%.${digits}f %s", value, unit)

        fun cellVoltageDelta(data: DiParsData?): Double? {
            val max = data?.maxCellVoltage ?: return null
            val min = data.minCellVoltage ?: return null
            return abs(max - min)
        }

        fun gearText(value: Int): String = when (value) {
            1 -> "P"
            2 -> "R"
            3 -> "N"
            4 -> "D"
            else -> value.toString()
        }

        fun driveModeText(value: Int): String = when (value) {
            1 -> "经济"
            2 -> "运动"
            3 -> "舒适"
            4 -> "雪地"
            5 -> "泥地"
            6 -> "沙地"
            7 -> "山地"
            11 -> "赛道"
            else -> value.toString()
        }

        fun workModeText(value: Int): String = when (value) {
            0 -> "停止"
            1 -> "EV"
            2 -> "强制EV"
            3 -> "HEV"
            4 -> "燃油"
            5 -> "保电"
            else -> value.toString()
        }
    }
}

object ClusterCardMetricCatalog {
    val all: List<ClusterCardMetric> = ClusterCardMetric.entries
    val byId: Map<String, ClusterCardMetric> = all.associateBy(ClusterCardMetric::id)
    val categories: List<String> = all.map(ClusterCardMetric::category).distinct()

    fun selected(config: ClusterCardConfig): List<ClusterCardMetric> =
        config.metricIds.mapNotNull(byId::get)

    fun telemetryFields(config: ClusterCardConfig): Set<String> =
        selected(config).mapNotNullTo(linkedSetOf(), ClusterCardMetric::telemetryField)
}

object ClusterCardLayout {
    fun rowCount(metricCount: Int): Int = (metricCount.coerceAtLeast(0) + 1) / 2

    fun widthDp(config: ClusterCardConfig): Float = 300f * config.scale * config.widthScale

    fun heightDp(config: ClusterCardConfig): Float {
        val effectiveScale = config.scale.coerceAtLeast(MIN_TEXT_LAYOUT_SCALE)
        val rowFontScale = config.fontScale.coerceIn(
            CLUSTER_CARD_MIN_FONT_SCALE,
            CLUSTER_CARD_MAX_FONT_SCALE,
        )
        val rowHeight = 66f * rowFontScale * config.heightScale
        return (18f + rowCount(config.metricIds.size) * rowHeight) * effectiveScale
    }

    private const val MIN_TEXT_LAYOUT_SCALE = 0.62f
}
