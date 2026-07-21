package com.byd.wjx.statusbar

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.lyrics.MusicPlaybackSnapshot
import com.byd.wjx.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CustomStatusBarItemCategory(val label: String) {
    SYSTEM("系统与媒体"),
    DRIVING("驾驶信息"),
    POWERTRAIN("动力系统"),
    ENERGY("电池与能耗"),
    CLIMATE("座舱与环境"),
    BODY("车身状态"),
}

enum class CustomStatusBarItem(
    val id: String,
    val label: String,
    val category: CustomStatusBarItemCategory,
) {
    CLOCK("clock", "时间", CustomStatusBarItemCategory.SYSTEM),
    DATE("date", "日期", CustomStatusBarItemCategory.SYSTEM),
    WEEKDAY("weekday", "星期", CustomStatusBarItemCategory.SYSTEM),
    WEATHER("weather", "天气", CustomStatusBarItemCategory.SYSTEM),
    MUSIC("music", "当前音乐", CustomStatusBarItemCategory.SYSTEM),
    VEHICLE_CONNECTION("vehicle_connection", "车辆连接", CustomStatusBarItemCategory.SYSTEM),
    WIFI("wifi", "Wi-Fi", CustomStatusBarItemCategory.SYSTEM),
    BLUETOOTH("bluetooth", "蓝牙", CustomStatusBarItemCategory.SYSTEM),
    LOCATION("location", "定位", CustomStatusBarItemCategory.SYSTEM),
    USB("usb", "USB", CustomStatusBarItemCategory.SYSTEM),
    MOBILE_NETWORK("mobile_network", "移动网络", CustomStatusBarItemCategory.SYSTEM),

    SPEED("speed", "车速", CustomStatusBarItemCategory.DRIVING),
    GEAR("gear", "挡位", CustomStatusBarItemCategory.DRIVING),
    WORK_MODE("work_mode", "动力模式", CustomStatusBarItemCategory.DRIVING),
    DRIVE_MODE("drive_mode", "驾驶模式", CustomStatusBarItemCategory.DRIVING),
    ODOMETER("odometer", "总里程", CustomStatusBarItemCategory.DRIVING),
    POWER_STATE("power_state", "车辆电源状态", CustomStatusBarItemCategory.DRIVING),
    CRUISE_SPEED("cruise_speed", "巡航设定车速", CustomStatusBarItemCategory.DRIVING),
    BRAKE_PEDAL("brake_pedal", "制动踏板", CustomStatusBarItemCategory.DRIVING),
    AUTO_PARK_STATE("auto_park_state", "自动泊车状态", CustomStatusBarItemCategory.DRIVING),

    INSTANT_POWER("instant_power", "实时功率", CustomStatusBarItemCategory.POWERTRAIN),
    ENGINE_RPM("engine_rpm", "发动机转速", CustomStatusBarItemCategory.POWERTRAIN),
    FRONT_MOTOR_RPM("front_motor_rpm", "前电机转速", CustomStatusBarItemCategory.POWERTRAIN),
    REAR_MOTOR_RPM("rear_motor_rpm", "后电机转速", CustomStatusBarItemCategory.POWERTRAIN),
    FRONT_MOTOR_TEMPERATURE("front_motor_temperature", "前电机温度", CustomStatusBarItemCategory.POWERTRAIN),
    REAR_MOTOR_TEMPERATURE("rear_motor_temperature", "后电机温度", CustomStatusBarItemCategory.POWERTRAIN),
    FRONT_MOTOR_CURRENT("front_motor_current", "前电机电流", CustomStatusBarItemCategory.POWERTRAIN),
    REAR_MOTOR_CURRENT("rear_motor_current", "后电机电流", CustomStatusBarItemCategory.POWERTRAIN),
    COOLANT_TEMPERATURE("coolant_temperature", "冷却液温度", CustomStatusBarItemCategory.POWERTRAIN),

    SOC("soc", "电量", CustomStatusBarItemCategory.ENERGY),
    SOH("soh", "电池健康", CustomStatusBarItemCategory.ENERGY),
    FUEL_LEVEL("fuel_level", "油量", CustomStatusBarItemCategory.ENERGY),
    ELECTRIC_RANGE("electric_range", "电续航", CustomStatusBarItemCategory.ENERGY),
    FUEL_RANGE("fuel_range", "油续航", CustomStatusBarItemCategory.ENERGY),
    TOTAL_RANGE("total_range", "总续航", CustomStatusBarItemCategory.ENERGY),
    ELECTRIC_CONSUMPTION("electric_consumption", "百公里电耗", CustomStatusBarItemCategory.ENERGY),
    FUEL_CONSUMPTION("fuel_consumption", "百公里油耗", CustomStatusBarItemCategory.ENERGY),
    TRIP_ELECTRICITY("trip_electricity", "本次用电", CustomStatusBarItemCategory.ENERGY),
    TRIP_FUEL("trip_fuel", "本次用油", CustomStatusBarItemCategory.ENERGY),
    VOLTAGE_12V("voltage_12v", "12V电压", CustomStatusBarItemCategory.ENERGY),
    BATTERY_CAPACITY("battery_capacity", "电池容量", CustomStatusBarItemCategory.ENERGY),
    TOTAL_ELECTRICITY("total_electricity", "累计用电", CustomStatusBarItemCategory.ENERGY),
    BATTERY_TEMPERATURE("battery_temperature", "电池温度", CustomStatusBarItemCategory.ENERGY),
    CELL_VOLTAGE("cell_voltage", "单体电压", CustomStatusBarItemCategory.ENERGY),
    CHARGING_STATE("charging_state", "充电状态", CustomStatusBarItemCategory.ENERGY),

    EXTERIOR_TEMPERATURE("exterior_temperature", "车外温度", CustomStatusBarItemCategory.CLIMATE),
    INTERIOR_TEMPERATURE("interior_temperature", "车内温度", CustomStatusBarItemCategory.CLIMATE),
    AC_STATE("ac_state", "空调状态", CustomStatusBarItemCategory.CLIMATE),
    AC_TEMPERATURE("ac_temperature", "空调温度", CustomStatusBarItemCategory.CLIMATE),
    FAN_LEVEL("fan_level", "空调风量", CustomStatusBarItemCategory.CLIMATE),

    TIRE_PRESSURE("tire_pressure", "胎压", CustomStatusBarItemCategory.BODY),
    DOOR_STATE("door_state", "车门状态", CustomStatusBarItemCategory.BODY),
    WINDOW_STATE("window_state", "车窗状态", CustomStatusBarItemCategory.BODY),
    LOCK_STATE("lock_state", "门锁状态", CustomStatusBarItemCategory.BODY),
    TRUNK_STATE("trunk_state", "后备箱状态", CustomStatusBarItemCategory.BODY),
    HOOD_STATE("hood_state", "前舱盖状态", CustomStatusBarItemCategory.BODY),
    SEATBELT_STATE("seatbelt_state", "安全带状态", CustomStatusBarItemCategory.BODY),
    LIGHT_STATE("light_state", "灯光状态", CustomStatusBarItemCategory.BODY),
    ;

    companion object {
        val byId: Map<String, CustomStatusBarItem> = entries.associateBy(CustomStatusBarItem::id)

        val powertrainTelemetryItems: Set<CustomStatusBarItem> = setOf(
            ENGINE_RPM,
            FRONT_MOTOR_RPM,
            REAR_MOTOR_RPM,
            FRONT_MOTOR_TEMPERATURE,
            REAR_MOTOR_TEMPERATURE,
            FRONT_MOTOR_CURRENT,
            REAR_MOTOR_CURRENT,
            COOLANT_TEMPERATURE,
        )
    }
}

enum class CustomStatusBarFontStyle(
    val id: String,
    val label: String,
) {
    DEFAULT("default", "默认"),
    SANS_SERIF("sans_serif", "简洁"),
    SERIF("serif", "衬线"),
    MONOSPACE("monospace", "等宽"),
    ;

    companion object {
        val byId: Map<String, CustomStatusBarFontStyle> = entries.associateBy(CustomStatusBarFontStyle::id)
    }
}

enum class CustomStatusBarIconStyle(
    val id: String,
    val label: String,
) {
    MINIMAL("minimal", "简洁"),
    TONAL("tonal", "柔和底色"),
    OUTLINED("outlined", "描边"),
    ;

    companion object {
        val byId: Map<String, CustomStatusBarIconStyle> = entries.associateBy(CustomStatusBarIconStyle::id)
    }
}

data class CustomStatusBarConfig(
    val enabled: Boolean = false,
    val items: List<CustomStatusBarItem> = listOf(
        CustomStatusBarItem.CLOCK,
        CustomStatusBarItem.DATE,
        CustomStatusBarItem.WEEKDAY,
        CustomStatusBarItem.WEATHER,
        CustomStatusBarItem.WIFI,
        CustomStatusBarItem.BLUETOOTH,
        CustomStatusBarItem.LOCATION,
        CustomStatusBarItem.USB,
        CustomStatusBarItem.MOBILE_NETWORK,
        CustomStatusBarItem.VEHICLE_CONNECTION,
        CustomStatusBarItem.EXTERIOR_TEMPERATURE,
        CustomStatusBarItem.SOC,
        CustomStatusBarItem.TOTAL_RANGE,
    ),
    val backgroundOpacity: Float = 0.78f,
    val heightDp: Float = 48f,
    val textSizeSp: Float = 14f,
    val horizontalOffsetDp: Float = 0f,
    val verticalOffsetDp: Float = 0f,
    val itemSpacingDp: Float = 18f,
    val fontStyle: CustomStatusBarFontStyle = CustomStatusBarFontStyle.DEFAULT,
    val iconStyle: CustomStatusBarIconStyle = CustomStatusBarIconStyle.TONAL,
    val textColorArgb: Int = CUSTOM_STATUS_BAR_DEFAULT_TEXT_COLOR,
)

internal fun sanitizeCustomStatusBarConfig(raw: CustomStatusBarConfig): CustomStatusBarConfig {
    return raw.copy(
        items = raw.items.distinct(),
        backgroundOpacity = raw.backgroundOpacity.coerceIn(0f, 1f),
        heightDp = raw.heightDp.coerceIn(CUSTOM_STATUS_BAR_MIN_HEIGHT_DP, CUSTOM_STATUS_BAR_MAX_HEIGHT_DP),
        textSizeSp = raw.textSizeSp.coerceIn(CUSTOM_STATUS_BAR_MIN_TEXT_SP, CUSTOM_STATUS_BAR_MAX_TEXT_SP),
        horizontalOffsetDp = raw.horizontalOffsetDp.coerceIn(
            CUSTOM_STATUS_BAR_MIN_HORIZONTAL_OFFSET_DP,
            CUSTOM_STATUS_BAR_MAX_HORIZONTAL_OFFSET_DP,
        ),
        verticalOffsetDp = raw.verticalOffsetDp.coerceIn(
            CUSTOM_STATUS_BAR_MIN_VERTICAL_OFFSET_DP,
            CUSTOM_STATUS_BAR_MAX_VERTICAL_OFFSET_DP,
        ),
        itemSpacingDp = raw.itemSpacingDp.coerceIn(
            CUSTOM_STATUS_BAR_MIN_ITEM_SPACING_DP,
            CUSTOM_STATUS_BAR_MAX_ITEM_SPACING_DP,
        ),
        textColorArgb = raw.textColorArgb.takeIf { it ushr 24 != 0 } ?: CUSTOM_STATUS_BAR_DEFAULT_TEXT_COLOR,
    )
}

internal fun CustomStatusBarConfig.addItem(item: CustomStatusBarItem): CustomStatusBarConfig =
    if (item in items) this else copy(items = items + item)

internal fun CustomStatusBarConfig.removeItem(item: CustomStatusBarItem): CustomStatusBarConfig = copy(
    items = items.filterNot { it == item },
)

internal fun CustomStatusBarConfig.moveItem(
    item: CustomStatusBarItem,
    offset: Int,
): CustomStatusBarConfig {
    val from = items.indexOf(item)
    if (from < 0) return this
    val to = (from + offset).coerceIn(items.indices)
    if (from == to) return this
    return copy(items = items.toMutableList().apply { add(to, removeAt(from)) })
}

internal fun CustomStatusBarConfig.needsPowertrainTelemetry(): Boolean =
    enabled && items.any(CustomStatusBarItem.powertrainTelemetryItems::contains)

internal fun CustomStatusBarConfig.needsWeather(): Boolean = enabled && CustomStatusBarItem.WEATHER in items

data class CustomStatusBarDisplayItem(
    val type: CustomStatusBarItem,
    val text: String,
    val available: Boolean = true,
    val iconOnly: Boolean = false,
    val indicatorState: SystemIndicatorState? = null,
    val level: Int? = null,
    val weatherCode: Int? = null,
)

enum class SystemIndicatorState {
    ACTIVE,
    ENABLED,
    DISABLED,
    UNAVAILABLE,
}

data class CustomStatusBarRenderState(
    val config: CustomStatusBarConfig = CustomStatusBarConfig(),
    val items: List<CustomStatusBarDisplayItem> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cameraActive: Boolean = false,
)

enum class CustomStatusBarVisibilityReason {
    VISIBLE,
    DISABLED,
    CAMERA_ACTIVE,
    OVERLAY_PERMISSION_MISSING,
}

data class CustomStatusBarVisibilityDecision(
    val show: Boolean,
    val reason: CustomStatusBarVisibilityReason,
)

object CustomStatusBarVisibility {
    fun evaluate(
        config: CustomStatusBarConfig,
        overlayPermissionGranted: Boolean,
        cameraActive: Boolean = false,
    ): CustomStatusBarVisibilityDecision = when {
        !config.enabled -> CustomStatusBarVisibilityDecision(false, CustomStatusBarVisibilityReason.DISABLED)
        cameraActive -> CustomStatusBarVisibilityDecision(false, CustomStatusBarVisibilityReason.CAMERA_ACTIVE)
        !overlayPermissionGranted -> CustomStatusBarVisibilityDecision(
            false,
            CustomStatusBarVisibilityReason.OVERLAY_PERMISSION_MISSING,
        )
        else -> CustomStatusBarVisibilityDecision(true, CustomStatusBarVisibilityReason.VISIBLE)
    }
}

internal object CustomStatusBarFormatter {
    fun build(
        config: CustomStatusBarConfig,
        vehicle: VehicleState,
        music: MusicPlaybackSnapshot,
        nowMs: Long,
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        powertrain: DiParsData? = null,
        systemStatus: SystemStatusSnapshot = SystemStatusSnapshot(),
        weather: StatusBarWeatherSnapshot = StatusBarWeatherSnapshot(),
        cameraActive: Boolean = false,
    ): CustomStatusBarRenderState = CustomStatusBarRenderState(
        config = config,
        items = config.items.map { format(it, vehicle, music, nowMs, powertrain, systemStatus, weather) },
        themeMode = themeMode,
        cameraActive = cameraActive,
    )

    fun formatGear(value: Int?): String? = when (value) {
        1 -> "P"
        2 -> "R"
        3 -> "N"
        4 -> "D"
        else -> null
    }

    fun formatWorkMode(value: Int?): String? = when (value) {
        0 -> "停车"
        1 -> "EV"
        2 -> "强制EV"
        3 -> "HEV"
        4 -> "燃油"
        5 -> "保电"
        else -> null
    }

    fun formatDriveMode(value: Int?): String? = when (value) {
        1 -> "ECO"
        2 -> "SPORT"
        3 -> "COMFORT"
        4 -> "雪地"
        5 -> "泥地"
        6 -> "沙地"
        7 -> "山地"
        11 -> "赛道"
        else -> null
    }

    private fun format(
        type: CustomStatusBarItem,
        vehicle: VehicleState,
        music: MusicPlaybackSnapshot,
        nowMs: Long,
        powertrain: DiParsData?,
        systemStatus: SystemStatusSnapshot,
        weather: StatusBarWeatherSnapshot,
    ): CustomStatusBarDisplayItem {
        formatSystemIndicator(type, systemStatus)?.let { return it }
        val data = vehicle.data
        val value = when (type) {
            CustomStatusBarItem.CLOCK -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
            CustomStatusBarItem.DATE -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(nowMs))
            CustomStatusBarItem.WEEKDAY -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(nowMs))
            CustomStatusBarItem.WEATHER -> weather.displayText
            CustomStatusBarItem.MUSIC -> formatMusic(music)
            CustomStatusBarItem.VEHICLE_CONNECTION -> when (vehicle.freshness) {
                VehicleDataFreshness.FRESH -> "车辆已连接"
                VehicleDataFreshness.STALE -> "车辆数据延迟"
                VehicleDataFreshness.DISCONNECTED -> "车辆未连接"
                VehicleDataFreshness.UNAVAILABLE -> "暂无车辆数据"
            }
            CustomStatusBarItem.WIFI,
            CustomStatusBarItem.BLUETOOTH,
            CustomStatusBarItem.LOCATION,
            CustomStatusBarItem.USB,
            CustomStatusBarItem.MOBILE_NETWORK,
            -> null

            CustomStatusBarItem.SPEED -> data?.speed?.takeIf { it in 0..400 }?.let { "${it}km/h" }
            CustomStatusBarItem.GEAR -> formatGear(data?.gear)
            CustomStatusBarItem.WORK_MODE -> formatWorkMode(data?.workMode)
            CustomStatusBarItem.DRIVE_MODE -> formatDriveMode(data?.driveMode)
            CustomStatusBarItem.ODOMETER -> data?.mileage.validDouble(0.0, 5_000_000.0)?.let { "里程 ${it.format(0)}km" }
            CustomStatusBarItem.POWER_STATE -> formatPowerState(data?.powerState)
            CustomStatusBarItem.CRUISE_SPEED -> data?.accCruisingSpeed?.takeIf { it in 0..260 }?.let { "巡航 ${it}km/h" }
            CustomStatusBarItem.BRAKE_PEDAL -> data?.brakePedalDepth.validPercent()?.let { "制动 $it%" }
            CustomStatusBarItem.AUTO_PARK_STATE -> formatAutoParkState(data)

            CustomStatusBarItem.INSTANT_POWER -> data?.power.validDouble(-1_000.0, 1_000.0)?.let { "功率 ${it.format(1)}kW" }
            CustomStatusBarItem.ENGINE_RPM -> powertrain?.engineRpm?.takeIf { it in 0..10_000 }?.let { "发动机 ${it}rpm" }
            CustomStatusBarItem.FRONT_MOTOR_RPM -> powertrain?.frontMotorSpeed?.takeIf { it in 0..30_000 }?.let { "前电机 ${it}rpm" }
            CustomStatusBarItem.REAR_MOTOR_RPM -> powertrain?.rearMotorSpeed?.takeIf { it in 0..30_000 }?.let { "后电机 ${it}rpm" }
            CustomStatusBarItem.FRONT_MOTOR_TEMPERATURE -> powertrain?.frontMotorTemp.validTemperature(200)?.let { "前电机 ${it}°C" }
            CustomStatusBarItem.REAR_MOTOR_TEMPERATURE -> powertrain?.rearMotorTemp.validTemperature(200)?.let { "后电机 ${it}°C" }
            CustomStatusBarItem.FRONT_MOTOR_CURRENT -> powertrain?.frontMotorCurrent.validDouble(-2_000.0, 2_000.0)?.let { "前电流 ${it.format(1)}A" }
            CustomStatusBarItem.REAR_MOTOR_CURRENT -> powertrain?.rearMotorCurrent.validDouble(-2_000.0, 2_000.0)?.let { "后电流 ${it.format(1)}A" }
            CustomStatusBarItem.COOLANT_TEMPERATURE -> powertrain?.waterTemp.validTemperature(200)?.let { "水温 ${it}°C" }

            CustomStatusBarItem.SOC -> data?.soc.validPercent()?.let { "$it%" }
            CustomStatusBarItem.SOH -> data?.soh.validPercent()?.let { "SOH $it%" }
            CustomStatusBarItem.FUEL_LEVEL -> data?.fuelLevel.validPercent()?.let { "油量 $it%" }
            CustomStatusBarItem.ELECTRIC_RANGE -> data?.elecDrivingRangeKm.validRange()?.let { "电 ${it}km" }
            CustomStatusBarItem.FUEL_RANGE -> data?.fuelDrivingRangeKm.validRange()?.let { "油 ${it}km" }
            CustomStatusBarItem.TOTAL_RANGE -> formatTotalRange(data)
            CustomStatusBarItem.ELECTRIC_CONSUMPTION -> data?.elecConPer100km.validDouble(0.0, 100.0)?.let { "电耗 ${it.format(1)}kWh/100km" }
            CustomStatusBarItem.FUEL_CONSUMPTION -> data?.fuelConPer100km.validDouble(0.0, 100.0)?.let { "油耗 ${it.format(1)}L/100km" }
            CustomStatusBarItem.TRIP_ELECTRICITY -> data?.currentTripElecKwh.validDouble(0.0, 500.0)?.let { "本次 ${it.format(1)}kWh" }
            CustomStatusBarItem.TRIP_FUEL -> data?.currentTripFuelL.validDouble(0.0, 500.0)?.let { "本次 ${it.format(1)}L" }
            CustomStatusBarItem.VOLTAGE_12V -> data?.voltage12v.validDouble(8.0, 16.5)?.let { "12V ${it.format(1)}V" }
            CustomStatusBarItem.BATTERY_CAPACITY -> data?.batteryCapacityKwh.validDouble(1.0, 500.0)?.let { "容量 ${it.format(1)}kWh" }
            CustomStatusBarItem.TOTAL_ELECTRICITY -> data?.totalElecConsumption.validDouble(0.0, 10_000_000.0)?.let { "累计 ${it.format(1)}kWh" }
            CustomStatusBarItem.BATTERY_TEMPERATURE -> formatBatteryTemperature(data)
            CustomStatusBarItem.CELL_VOLTAGE -> formatCellVoltage(data)
            CustomStatusBarItem.CHARGING_STATE -> formatChargingState(data)

            CustomStatusBarItem.EXTERIOR_TEMPERATURE -> data?.exteriorTemp.validTemperature()?.let { "外 ${it}°C" }
            CustomStatusBarItem.INTERIOR_TEMPERATURE -> data?.insideTemp.validTemperature()?.let { "内 ${it}°C" }
            CustomStatusBarItem.AC_STATE -> when (data?.acStatus) {
                0 -> "空调关闭"
                1 -> "空调开启"
                else -> null
            }
            CustomStatusBarItem.AC_TEMPERATURE -> data?.acTemp.validTemperature(40)?.let { "空调 ${it}°C" }
            CustomStatusBarItem.FAN_LEVEL -> data?.fanLevel?.takeIf { it in 0..15 }?.let { "风量 ${it}挡" }

            CustomStatusBarItem.TIRE_PRESSURE -> formatTirePressure(data)
            CustomStatusBarItem.DOOR_STATE -> formatDoorState(data)
            CustomStatusBarItem.WINDOW_STATE -> formatWindowState(data)
            CustomStatusBarItem.LOCK_STATE -> formatLockState(data)
            CustomStatusBarItem.TRUNK_STATE -> formatTrunkState(data?.trunk)
            CustomStatusBarItem.HOOD_STATE -> when (data?.hood) {
                0 -> "前舱盖已关"
                1 -> "前舱盖开启"
                else -> null
            }
            CustomStatusBarItem.SEATBELT_STATE -> formatSeatbeltState(data)
            CustomStatusBarItem.LIGHT_STATE -> formatLightState(data)
        }
        return CustomStatusBarDisplayItem(
            type = type,
            text = value ?: unavailableText(type),
            available = value != null && when (type) {
                CustomStatusBarItem.VEHICLE_CONNECTION -> vehicle.freshness == VehicleDataFreshness.FRESH
                CustomStatusBarItem.MUSIC -> music.hasSong
                CustomStatusBarItem.WEATHER -> weather.available
                else -> true
            },
            weatherCode = weather.weatherCode.takeIf { type == CustomStatusBarItem.WEATHER },
        )
    }

    private fun formatSystemIndicator(
        type: CustomStatusBarItem,
        status: SystemStatusSnapshot,
    ): CustomStatusBarDisplayItem? {
        val state = when (type) {
            CustomStatusBarItem.WIFI -> when {
                status.wifiEnabled == null -> SystemIndicatorState.UNAVAILABLE
                !status.wifiEnabled -> SystemIndicatorState.DISABLED
                status.wifiConnected -> SystemIndicatorState.ACTIVE
                else -> SystemIndicatorState.ENABLED
            }
            CustomStatusBarItem.BLUETOOTH -> when {
                status.bluetoothEnabled == null -> SystemIndicatorState.UNAVAILABLE
                !status.bluetoothEnabled -> SystemIndicatorState.DISABLED
                status.bluetoothConnected -> SystemIndicatorState.ACTIVE
                else -> SystemIndicatorState.ENABLED
            }
            CustomStatusBarItem.LOCATION -> when (status.locationEnabled) {
                true -> SystemIndicatorState.ACTIVE
                false -> SystemIndicatorState.DISABLED
                null -> SystemIndicatorState.UNAVAILABLE
            }
            CustomStatusBarItem.USB -> if (status.usbConnected) {
                SystemIndicatorState.ACTIVE
            } else {
                SystemIndicatorState.DISABLED
            }
            CustomStatusBarItem.MOBILE_NETWORK -> if (status.mobileConnected) {
                SystemIndicatorState.ACTIVE
            } else {
                SystemIndicatorState.DISABLED
            }
            else -> return null
        }
        val description = when (type) {
            CustomStatusBarItem.WIFI -> when (state) {
                SystemIndicatorState.ACTIVE -> "Wi-Fi已连接"
                SystemIndicatorState.ENABLED -> "Wi-Fi已开启"
                SystemIndicatorState.DISABLED -> "Wi-Fi已关闭"
                SystemIndicatorState.UNAVAILABLE -> "Wi-Fi状态不可用"
            }
            CustomStatusBarItem.BLUETOOTH -> when (state) {
                SystemIndicatorState.ACTIVE -> "蓝牙已连接"
                SystemIndicatorState.ENABLED -> "蓝牙已开启"
                SystemIndicatorState.DISABLED -> "蓝牙已关闭"
                SystemIndicatorState.UNAVAILABLE -> "蓝牙状态不可用"
            }
            CustomStatusBarItem.LOCATION -> if (state == SystemIndicatorState.ACTIVE) "定位已开启" else "定位已关闭"
            CustomStatusBarItem.USB -> if (state == SystemIndicatorState.ACTIVE) "USB已连接" else "USB未连接"
            CustomStatusBarItem.MOBILE_NETWORK -> if (state == SystemIndicatorState.ACTIVE) "移动网络已连接" else "移动网络未连接"
            else -> type.label
        }
        return CustomStatusBarDisplayItem(
            type = type,
            text = description,
            available = state == SystemIndicatorState.ACTIVE || state == SystemIndicatorState.ENABLED,
            iconOnly = true,
            indicatorState = state,
            level = status.wifiLevel.takeIf { type == CustomStatusBarItem.WIFI },
        )
    }

    private fun formatMusic(music: MusicPlaybackSnapshot): String? {
        if (!music.hasSong) return null
        return buildString {
            append(music.title.trim())
            music.artist.trim().takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
        }
    }

    private fun formatPowerState(value: Int?): String? = when (value) {
        0 -> "车辆OFF"
        1 -> "车辆ACC"
        2 -> "车辆ON"
        3 -> "车辆OK"
        4 -> "车辆READY"
        else -> null
    }

    private fun formatAutoParkState(data: DiParsData?): String? {
        data?.adasApaParkingStatus?.let { status ->
            return when (status) {
                0 -> "自动泊车关闭"
                1 -> "自动泊车搜索中"
                2 -> "自动泊车待命"
                3 -> "自动泊车暂停"
                4 -> "自动泊车进行中"
                5, 6 -> "自动泊车完成"
                7 -> "自动泊车选择车位"
                else -> null
            }
        }
        return when (data?.autoPark) {
            0 -> "自动泊车关闭"
            1 -> "自动泊车待命"
            2 -> "自动泊车进行中"
            else -> null
        }
    }

    private fun formatTotalRange(data: DiParsData?): String? {
        val electric = data?.elecDrivingRangeKm.validRange()
        val fuel = data?.fuelDrivingRangeKm.validRange()
        val total = when {
            electric != null && fuel != null -> electric + fuel
            electric != null -> electric
            fuel != null -> fuel
            else -> null
        }
        return total?.let { "总 ${it}km" }
    }

    private fun formatTirePressure(data: DiParsData?): String? {
        val values = listOf(
            data?.tirePressFL,
            data?.tirePressFR,
            data?.tirePressRL,
            data?.tirePressRR,
        ).mapNotNull { it?.takeIf { pressure -> pressure in 100..500 } }
        return values.takeIf { it.isNotEmpty() }?.joinToString(prefix = "胎压 ", separator = "/", postfix = "kPa")
    }

    private fun formatBatteryTemperature(data: DiParsData?): String? {
        val average = data?.avgBatTemp.validTemperature(100)
        val minimum = data?.minBatTemp.validTemperature(100)
        val maximum = data?.maxBatTemp.validTemperature(100)
        return when {
            average != null -> "电池 ${average}°C"
            minimum != null && maximum != null -> "电池 ${minimum}-${maximum}°C"
            maximum != null -> "电池最高 ${maximum}°C"
            minimum != null -> "电池最低 ${minimum}°C"
            else -> null
        }
    }

    private fun formatCellVoltage(data: DiParsData?): String? {
        val minimum = data?.minCellVoltage.validDouble(0.5, 6.0)
        val maximum = data?.maxCellVoltage.validDouble(0.5, 6.0)
        return when {
            minimum != null && maximum != null -> "单体 ${minimum.format(3)}-${maximum.format(3)}V"
            maximum != null -> "单体最高 ${maximum.format(3)}V"
            minimum != null -> "单体最低 ${minimum.format(3)}V"
            else -> null
        }
    }

    private fun formatChargingState(data: DiParsData?): String? = when (data?.chargingStatus) {
        1 -> "正在充电"
        2 -> "充电完成"
        3 -> "对外放电"
        13 -> "充电暂停"
        0 -> if (data?.chargeGunState == 1) "已插枪" else "未充电"
        else -> when (data?.chargeGunState) {
            1 -> "已插枪"
            0 -> "未插枪"
            else -> null
        }
    }

    private fun formatDoorState(data: DiParsData?): String? {
        val openDoors = listOf(
            "左前" to data?.doorFL,
            "右前" to data?.doorFR,
            "左后" to data?.doorRL,
            "右后" to data?.doorRR,
        ).filter { it.second == 1 }.map { it.first }
        val hasReading = listOf(data?.doorFL, data?.doorFR, data?.doorRL, data?.doorRR).any { it != null }
        return when {
            openDoors.isNotEmpty() -> "${openDoors.joinToString("/")}门开启"
            hasReading -> "车门已关"
            else -> null
        }
    }

    private fun formatWindowState(data: DiParsData?): String? {
        val windows = listOf(
            "左前" to data?.windowFL,
            "右前" to data?.windowFR,
            "左后" to data?.windowRL,
            "右后" to data?.windowRR,
        )
        val openWindows = windows.filter { (_, value) -> value != null && value > 0 }
            .map { (label, value) -> "$label${value}%" }
        return when {
            openWindows.isNotEmpty() -> openWindows.joinToString(prefix = "车窗 ", separator = "/")
            windows.any { it.second != null } -> "车窗已关"
            else -> null
        }
    }

    private fun formatLockState(data: DiParsData?): String? {
        val values = listOf(data?.lockFL, data?.lockFR, data?.lockRL, data?.lockRR).filterNotNull()
        return when {
            values.isEmpty() -> null
            values.all { it == 2 } -> "车辆已锁"
            values.any { it == 1 } -> "车辆未锁"
            else -> null
        }
    }

    private fun formatTrunkState(value: Int?): String? = when (value) {
        1 -> "后备箱开启"
        2 -> "后备箱已关"
        3 -> "后备箱暂停"
        else -> null
    }

    private fun formatSeatbeltState(data: DiParsData?): String? {
        val unbuckled = listOf(
            "主驾" to data?.seatbeltFL,
            "副驾" to data?.seatbeltFR,
            "左后" to data?.seatbeltRL,
            "右后" to data?.seatbeltRR,
        ).filter { it.second == 0 }.map { it.first }
        val hasReading = listOf(data?.seatbeltFL, data?.seatbeltFR, data?.seatbeltRL, data?.seatbeltRR)
            .any { it == 0 || it == 1 }
        return when {
            unbuckled.isNotEmpty() -> "${unbuckled.joinToString("/")}未系"
            hasReading -> "安全带正常"
            else -> null
        }
    }

    private fun formatLightState(data: DiParsData?): String? = when {
        data?.hazardLights == 1 || data?.turnSignalFlash == 6 -> "双闪开启"
        data?.lightHigh == 1 -> "远光开启"
        data?.lightLow == 1 -> "近光开启"
        data?.turnSignalLeft == 1 || data?.turnSignalFlash == 2 -> "左转向"
        data?.turnSignalRight == 1 || data?.turnSignalFlash == 4 -> "右转向"
        listOf(data?.hazardLights, data?.lightHigh, data?.lightLow, data?.turnSignalLeft, data?.turnSignalRight)
            .any { it != null } -> "灯光关闭"
        else -> null
    }

    private fun unavailableText(type: CustomStatusBarItem): String = when (type) {
        CustomStatusBarItem.MUSIC -> "暂无音乐"
        CustomStatusBarItem.WEATHER -> "天气 --"
        CustomStatusBarItem.VEHICLE_CONNECTION -> "暂无车辆数据"
        else -> "${type.label} --"
    }

    private fun Int?.validPercent(): Int? = this?.takeIf { it in 0..100 }
    private fun Int?.validRange(): Int? = this?.takeIf { it in 0..3000 }
    private fun Int?.validTemperature(maximum: Int = 120): Int? = this?.takeIf { it in -60..maximum }
    private fun Double?.validDouble(minimum: Double, maximum: Double): Double? =
        this?.takeIf { it.isFinite() && it in minimum..maximum }
    private fun Double.format(decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", this)
}

internal const val CUSTOM_STATUS_BAR_MIN_HEIGHT_DP = 34f
internal const val CUSTOM_STATUS_BAR_MAX_HEIGHT_DP = 96f
internal const val CUSTOM_STATUS_BAR_MIN_TEXT_SP = 10f
internal const val CUSTOM_STATUS_BAR_MAX_TEXT_SP = 32f
internal const val CUSTOM_STATUS_BAR_MIN_HORIZONTAL_OFFSET_DP = -600f
internal const val CUSTOM_STATUS_BAR_MAX_HORIZONTAL_OFFSET_DP = 600f
internal const val CUSTOM_STATUS_BAR_MIN_VERTICAL_OFFSET_DP = -32f
internal const val CUSTOM_STATUS_BAR_MAX_VERTICAL_OFFSET_DP = 32f
internal const val CUSTOM_STATUS_BAR_MIN_ITEM_SPACING_DP = 4f
internal const val CUSTOM_STATUS_BAR_MAX_ITEM_SPACING_DP = 40f
internal const val CUSTOM_STATUS_BAR_DEFAULT_TEXT_COLOR = -0x1
