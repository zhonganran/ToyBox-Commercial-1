package com.byd.wjx.statusbar

import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.lyrics.MusicPlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomStatusBarModelsTest {
    @Test
    fun sanitize_removesDuplicatesAndClampsStyleValues() {
        val result = sanitizeCustomStatusBarConfig(
            CustomStatusBarConfig(
                items = listOf(
                    CustomStatusBarItem.CLOCK,
                    CustomStatusBarItem.CLOCK,
                    CustomStatusBarItem.SOC,
                ),
                backgroundOpacity = 1.5f,
                heightDp = 10f,
                textSizeSp = 50f,
                horizontalOffsetDp = 900f,
                verticalOffsetDp = -80f,
                itemSpacingDp = 100f,
                textColorArgb = 0,
            ),
        )

        assertEquals(listOf(CustomStatusBarItem.CLOCK, CustomStatusBarItem.SOC), result.items)
        assertEquals(1f, result.backgroundOpacity)
        assertEquals(CUSTOM_STATUS_BAR_MIN_HEIGHT_DP, result.heightDp)
        assertEquals(CUSTOM_STATUS_BAR_MAX_TEXT_SP, result.textSizeSp)
        assertEquals(CUSTOM_STATUS_BAR_MAX_HORIZONTAL_OFFSET_DP, result.horizontalOffsetDp)
        assertEquals(CUSTOM_STATUS_BAR_MIN_VERTICAL_OFFSET_DP, result.verticalOffsetDp)
        assertEquals(CUSTOM_STATUS_BAR_MAX_ITEM_SPACING_DP, result.itemSpacingDp)
        assertEquals(CUSTOM_STATUS_BAR_DEFAULT_TEXT_COLOR, result.textColorArgb)
    }

    @Test
    fun addItem_appendsNewContentFromLeftToRight() {
        val config = CustomStatusBarConfig(items = listOf(CustomStatusBarItem.CLOCK))

        val result = config.addItem(CustomStatusBarItem.INTERIOR_TEMPERATURE)

        assertEquals(
            listOf(CustomStatusBarItem.CLOCK, CustomStatusBarItem.INTERIOR_TEMPERATURE),
            result.items,
        )
        assertEquals(result, result.addItem(CustomStatusBarItem.CLOCK))
    }

    @Test
    fun moveItem_reordersSingleDisplaySequence() {
        val config = CustomStatusBarConfig(
            items = listOf(CustomStatusBarItem.CLOCK, CustomStatusBarItem.MUSIC, CustomStatusBarItem.SPEED),
        )

        val result = config.moveItem(CustomStatusBarItem.SPEED, -1)

        assertEquals(
            listOf(CustomStatusBarItem.CLOCK, CustomStatusBarItem.SPEED, CustomStatusBarItem.MUSIC),
            result.items,
        )
    }

    @Test
    fun visibility_hidesOverlayDuringCameraAndRequiresPermissionOtherwise() {
        val config = CustomStatusBarConfig(
            enabled = true,
            items = listOf(
                CustomStatusBarItem.INTERIOR_TEMPERATURE,
                CustomStatusBarItem.EXTERIOR_TEMPERATURE,
            ),
        )
        val camera = CustomStatusBarVisibility.evaluate(config, overlayPermissionGranted = true, cameraActive = true)
        val missingPermission = CustomStatusBarVisibility.evaluate(config, overlayPermissionGranted = false)
        val visible = CustomStatusBarVisibility.evaluate(config, overlayPermissionGranted = true)

        assertFalse(camera.show)
        assertEquals(CustomStatusBarVisibilityReason.CAMERA_ACTIVE, camera.reason)
        assertFalse(missingPermission.show)
        assertEquals(CustomStatusBarVisibilityReason.OVERLAY_PERMISSION_MISSING, missingPermission.reason)
        assertTrue(visible.show)
    }

    @Test
    fun formatter_usesValidatedBydGearAndModeMappings() {
        assertEquals("P", CustomStatusBarFormatter.formatGear(1))
        assertEquals("R", CustomStatusBarFormatter.formatGear(2))
        assertEquals("D", CustomStatusBarFormatter.formatGear(4))
        assertEquals(null, CustomStatusBarFormatter.formatGear(8191))
        assertEquals("EV", CustomStatusBarFormatter.formatWorkMode(1))
        assertEquals("HEV", CustomStatusBarFormatter.formatWorkMode(3))
        assertEquals(null, CustomStatusBarFormatter.formatWorkMode(8191))
        assertEquals("SPORT", CustomStatusBarFormatter.formatDriveMode(2))
    }

    @Test
    fun powertrainTelemetry_onlyRunsWhenEnabledAndARelevantItemIsSelected() {
        val selected = CustomStatusBarConfig(
            enabled = true,
            items = listOf(CustomStatusBarItem.ENGINE_RPM),
        )

        assertTrue(selected.needsPowertrainTelemetry())
        assertFalse(selected.copy(enabled = false).needsPowertrainTelemetry())
        assertFalse(selected.copy(items = listOf(CustomStatusBarItem.SPEED)).needsPowertrainTelemetry())
    }

    @Test
    fun formatter_usesOnDemandPowertrainReadingForEngineRpm() {
        val state = CustomStatusBarFormatter.build(
            config = CustomStatusBarConfig(items = listOf(CustomStatusBarItem.ENGINE_RPM)),
            vehicle = VehicleState(),
            music = MusicPlaybackSnapshot.EMPTY,
            nowMs = 0L,
            powertrain = diParsData(engineRpm = 2350),
        )

        assertEquals("发动机 2350rpm", state.items.single().text)
        assertTrue(state.items.single().available)
    }

    @Test
    fun defaults_includeDateWeekdayWeatherAndCoreSystemIndicators() {
        val config = CustomStatusBarConfig()
        val items = config.items

        assertEquals(CustomStatusBarItem.CLOCK, items[0])
        assertEquals(CustomStatusBarItem.DATE, items[1])
        assertEquals(CustomStatusBarItem.WEEKDAY, items[2])
        assertEquals(CustomStatusBarItem.WEATHER, items[3])
        assertTrue(CustomStatusBarItem.WIFI in items)
        assertTrue(CustomStatusBarItem.BLUETOOTH in items)
        assertTrue(CustomStatusBarItem.LOCATION in items)
        assertEquals(CustomStatusBarIconStyle.TONAL, config.iconStyle)
        assertEquals(18f, config.itemSpacingDp)
    }

    @Test
    fun storedIconStyleIds_remainStable() {
        assertEquals(CustomStatusBarIconStyle.MINIMAL, CustomStatusBarIconStyle.byId["minimal"])
        assertEquals(CustomStatusBarIconStyle.TONAL, CustomStatusBarIconStyle.byId["tonal"])
        assertEquals(CustomStatusBarIconStyle.OUTLINED, CustomStatusBarIconStyle.byId["outlined"])
    }

    @Test
    fun formatter_buildsSystemAndWeatherItemsInConfiguredOrder() {
        val state = CustomStatusBarFormatter.build(
            config = CustomStatusBarConfig(
                items = listOf(
                    CustomStatusBarItem.WIFI,
                    CustomStatusBarItem.BLUETOOTH,
                    CustomStatusBarItem.LOCATION,
                    CustomStatusBarItem.WEATHER,
                ),
            ),
            vehicle = VehicleState(),
            music = MusicPlaybackSnapshot.EMPTY,
            nowMs = 0L,
            systemStatus = SystemStatusSnapshot(
                wifiEnabled = true,
                wifiConnected = true,
                wifiLevel = 2,
                bluetoothEnabled = true,
                bluetoothConnected = false,
                locationEnabled = false,
            ),
            weather = StatusBarWeatherSnapshot(temperatureC = 26, weatherCode = 0, updatedAtMs = 1L),
        )

        assertEquals(SystemIndicatorState.ACTIVE, state.items[0].indicatorState)
        assertEquals(2, state.items[0].level)
        assertEquals(SystemIndicatorState.ENABLED, state.items[1].indicatorState)
        assertEquals(SystemIndicatorState.DISABLED, state.items[2].indicatorState)
        assertEquals("晴 26°C", state.items[3].text)
    }

    @Test
    fun interiorAndExteriorTemperaturesRemainIndependentSelectableItems() {
        assertEquals("车内温度", CustomStatusBarItem.INTERIOR_TEMPERATURE.label)
        assertEquals("车外温度", CustomStatusBarItem.EXTERIOR_TEMPERATURE.label)
    }
}
