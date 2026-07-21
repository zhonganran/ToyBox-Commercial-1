package com.byd.wjx.statusbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Wifi1Bar
import androidx.compose.material.icons.rounded.Wifi2Bar
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.CardSurface

@Composable
fun CustomStatusBarContent(state: CustomStatusBarRenderState) {
    val config = state.config
    val itemSpacing by animateDpAsState(
        targetValue = config.itemSpacingDp.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusBarItemSpacing",
    )
    val horizontalOffset by animateDpAsState(
        targetValue = config.horizontalOffsetDp.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusBarHorizontalOffset",
    )
    val verticalOffset by animateDpAsState(
        targetValue = config.verticalOffsetDp.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusBarVerticalOffset",
    )
    val textSizeSp by animateFloatAsState(
        targetValue = config.textSizeSp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusBarTextSize",
    )
    val backgroundOpacity by animateFloatAsState(
        targetValue = config.backgroundOpacity,
        animationSpec = tween(180),
        label = "statusBarBackgroundOpacity",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardSurface.copy(alpha = backgroundOpacity)),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .offset(x = horizontalOffset, y = verticalOffset)
                .padding(horizontal = 16.dp)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.items.forEach { item ->
                StatusBarItem(
                    item = item,
                    textSizeSp = textSizeSp,
                    textColor = Color(config.textColorArgb),
                    fontFamily = config.fontStyle.fontFamily,
                    iconStyle = config.iconStyle,
                )
            }
        }
    }
}

@Composable
private fun StatusBarItem(
    item: CustomStatusBarDisplayItem,
    textSizeSp: Float,
    textColor: Color,
    fontFamily: FontFamily,
    iconStyle: CustomStatusBarIconStyle,
) {
    val tint = when (item.indicatorState) {
        SystemIndicatorState.DISABLED,
        SystemIndicatorState.UNAVAILABLE,
        -> textColor.copy(alpha = 0.42f)
        else -> if (item.available) textColor else textColor.copy(alpha = 0.42f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((textSizeSp * 0.32f).coerceIn(4f, 7f).dp),
    ) {
        StatusBarIcon(
            icon = item.icon,
            contentDescription = item.text,
            tint = tint,
            size = (textSizeSp + 2f).dp,
            style = iconStyle,
        )
        if (!item.iconOnly) {
            AnimatedContent(
                targetState = item.text,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(140)) { -it / 3 })
                },
                label = "statusBarValue_${item.type.id}",
            ) { text ->
                Text(
                    text = text,
                    color = tint,
                    fontSize = textSizeSp.sp,
                    fontFamily = fontFamily,
                    fontWeight = if (item.type == CustomStatusBarItem.CLOCK) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = if (item.type == CustomStatusBarItem.MUSIC) 240.dp else 190.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBarIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    size: Dp,
    style: CustomStatusBarIconStyle,
) {
    val iconContent: @Composable () -> Unit = {
        Crossfade(targetState = icon, animationSpec = tween(180), label = "statusBarIcon") { image ->
            Icon(
                imageVector = image,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size),
            )
        }
    }
    when (style) {
        CustomStatusBarIconStyle.MINIMAL -> iconContent()
        CustomStatusBarIconStyle.TONAL -> Box(
            modifier = Modifier
                .size(size + 9.dp)
                .background(tint.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { iconContent() }
        CustomStatusBarIconStyle.OUTLINED -> Box(
            modifier = Modifier
                .size(size + 9.dp)
                .border(1.dp, tint.copy(alpha = 0.38f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { iconContent() }
    }
}

private val CustomStatusBarFontStyle.fontFamily: FontFamily
    get() = when (this) {
        CustomStatusBarFontStyle.DEFAULT -> FontFamily.Default
        CustomStatusBarFontStyle.SANS_SERIF -> FontFamily.SansSerif
        CustomStatusBarFontStyle.SERIF -> FontFamily.Serif
        CustomStatusBarFontStyle.MONOSPACE -> FontFamily.Monospace
    }

private val CustomStatusBarDisplayItem.icon: ImageVector
    get() = when (type) {
        CustomStatusBarItem.WIFI -> when {
            indicatorState == SystemIndicatorState.DISABLED -> Icons.Rounded.WifiOff
            level == 0 -> Icons.Rounded.Wifi1Bar
            level == 1 -> Icons.Rounded.Wifi2Bar
            else -> Icons.Rounded.Wifi
        }
        CustomStatusBarItem.BLUETOOTH -> when (indicatorState) {
            SystemIndicatorState.ACTIVE -> Icons.Rounded.BluetoothConnected
            SystemIndicatorState.DISABLED -> Icons.Rounded.BluetoothDisabled
            else -> Icons.Rounded.Bluetooth
        }
        CustomStatusBarItem.LOCATION -> if (indicatorState == SystemIndicatorState.ACTIVE) {
            Icons.Rounded.GpsFixed
        } else {
            Icons.Rounded.GpsOff
        }
        CustomStatusBarItem.USB -> if (indicatorState == SystemIndicatorState.ACTIVE) {
            Icons.Rounded.Usb
        } else {
            Icons.Rounded.UsbOff
        }
        CustomStatusBarItem.MOBILE_NETWORK -> if (indicatorState == SystemIndicatorState.ACTIVE) {
            Icons.Rounded.SignalCellularAlt
        } else {
            Icons.Rounded.SignalCellularOff
        }
        CustomStatusBarItem.WEATHER -> when (weatherCode) {
            0, 1 -> Icons.Rounded.WbSunny
            in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
            in 71..77, in 85..86 -> Icons.Rounded.AcUnit
            in 95..99 -> Icons.Rounded.Thunderstorm
            else -> Icons.Rounded.Cloud
        }
        else -> type.icon
    }

private val CustomStatusBarItem.icon: ImageVector
    get() = when (this) {
        CustomStatusBarItem.CLOCK -> Icons.Rounded.AccessTime
        CustomStatusBarItem.DATE -> Icons.Rounded.CalendarMonth
        CustomStatusBarItem.WEEKDAY -> Icons.Rounded.Today
        CustomStatusBarItem.WEATHER -> Icons.Rounded.Cloud
        CustomStatusBarItem.MUSIC -> Icons.Rounded.MusicNote
        CustomStatusBarItem.VEHICLE_CONNECTION -> Icons.Rounded.Link
        CustomStatusBarItem.WIFI,
        CustomStatusBarItem.BLUETOOTH,
        CustomStatusBarItem.LOCATION,
        CustomStatusBarItem.USB,
        CustomStatusBarItem.MOBILE_NETWORK,
        -> Icons.Rounded.Settings

        CustomStatusBarItem.SPEED,
        CustomStatusBarItem.CRUISE_SPEED,
        CustomStatusBarItem.BRAKE_PEDAL,
        -> Icons.Rounded.Speed
        CustomStatusBarItem.GEAR,
        CustomStatusBarItem.AUTO_PARK_STATE,
        -> Icons.Rounded.DirectionsCar
        CustomStatusBarItem.WORK_MODE,
        CustomStatusBarItem.POWER_STATE,
        -> Icons.Rounded.Bolt
        CustomStatusBarItem.DRIVE_MODE -> Icons.Rounded.SportsScore
        CustomStatusBarItem.ODOMETER -> Icons.Rounded.Route

        CustomStatusBarItem.INSTANT_POWER,
        CustomStatusBarItem.FRONT_MOTOR_CURRENT,
        CustomStatusBarItem.REAR_MOTOR_CURRENT,
        -> Icons.Rounded.Bolt
        CustomStatusBarItem.ENGINE_RPM,
        CustomStatusBarItem.FRONT_MOTOR_RPM,
        CustomStatusBarItem.REAR_MOTOR_RPM,
        -> Icons.Rounded.Sync
        CustomStatusBarItem.FRONT_MOTOR_TEMPERATURE,
        CustomStatusBarItem.REAR_MOTOR_TEMPERATURE,
        CustomStatusBarItem.COOLANT_TEMPERATURE,
        -> Icons.Rounded.DeviceThermostat

        CustomStatusBarItem.SOC,
        CustomStatusBarItem.SOH,
        CustomStatusBarItem.VOLTAGE_12V,
        CustomStatusBarItem.BATTERY_CAPACITY,
        CustomStatusBarItem.TOTAL_ELECTRICITY,
        CustomStatusBarItem.BATTERY_TEMPERATURE,
        CustomStatusBarItem.CELL_VOLTAGE,
        -> Icons.Rounded.BatteryChargingFull
        CustomStatusBarItem.FUEL_LEVEL,
        CustomStatusBarItem.FUEL_CONSUMPTION,
        CustomStatusBarItem.TRIP_FUEL,
        -> Icons.Rounded.LocalGasStation
        CustomStatusBarItem.ELECTRIC_RANGE -> Icons.Rounded.ElectricCar
        CustomStatusBarItem.FUEL_RANGE -> Icons.Rounded.LocalGasStation
        CustomStatusBarItem.TOTAL_RANGE -> Icons.Rounded.Route
        CustomStatusBarItem.ELECTRIC_CONSUMPTION,
        CustomStatusBarItem.TRIP_ELECTRICITY,
        -> Icons.Rounded.ElectricCar
        CustomStatusBarItem.CHARGING_STATE -> Icons.Rounded.EvStation

        CustomStatusBarItem.EXTERIOR_TEMPERATURE,
        CustomStatusBarItem.INTERIOR_TEMPERATURE,
        CustomStatusBarItem.AC_TEMPERATURE,
        -> Icons.Rounded.DeviceThermostat
        CustomStatusBarItem.AC_STATE -> Icons.Rounded.AcUnit
        CustomStatusBarItem.FAN_LEVEL -> Icons.Rounded.Air

        CustomStatusBarItem.TIRE_PRESSURE -> Icons.Rounded.TireRepair
        CustomStatusBarItem.LOCK_STATE -> Icons.Rounded.Lock
        CustomStatusBarItem.LIGHT_STATE -> Icons.Rounded.LightMode
        CustomStatusBarItem.WINDOW_STATE -> Icons.Rounded.Air
        CustomStatusBarItem.DOOR_STATE,
        CustomStatusBarItem.TRUNK_STATE,
        CustomStatusBarItem.HOOD_STATE,
        CustomStatusBarItem.SEATBELT_STATE,
        -> Icons.Rounded.DirectionsCar
    }
