package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationActionSnapshotFieldProviderTest {
    @Test
    fun `auto parking status is read only while its sound feature is enabled`() {
        assertTrue(requiredRuntimeFeatureFields(false).isEmpty())
        assertEquals(setOf("adasApaParkingStatus"), requiredRuntimeFeatureFields(true))
    }

    @Test
    fun `electronic mirror fast path reads stable turn and auto parking ownership`() {
        assertEquals(
            setOf("turnSignalFlash", "adasApaParkingStatus"),
            requiredRuntimeFeatureFields(
                autoParkingSoundEnabled = false,
                electronicMirrorLinkageEnabled = true,
            ),
        )
    }

    @Test
    fun `streaming rear reads camera ownership without turn signal`() {
        assertEquals(
            setOf("adasApaParkingStatus"),
            requiredRuntimeFeatureFields(
                autoParkingSoundEnabled = false,
                streamingRearEnabled = true,
            ),
        )
    }

    @Test
    fun `enabled param triggers select only their required vehicle fields`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("InsideTemp", "车内温度", ">", "26", "车内温度"),
                    TriggerDef("AvgBatTemp", "平均电池温度", ">", "40", "平均电池温度"),
                ),
            ),
        )

        assertEquals(setOf("insideTemp", "maxBatTemp", "minBatTemp"), fields)
    }

    @Test
    fun `event and location triggers do not expand the vehicle batch`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("", "启动", "==", "1", "服务启动", kind = "service_start"),
                    TriggerDef("", "到家", "==", "1", "到家", kind = "place_enter", placeId = 1),
                ),
            ),
        )

        assertTrue(fields.isEmpty())
        assertFalse("speed" in fields)
    }

    @Test
    fun `comfort triggers select their on demand fields`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("ACCtrlMode", "空调控制模式", "==", "0", "空调控制模式"),
                    TriggerDef("ACDefrostFront", "前挡除霜", "==", "1", "前挡除霜"),
                    TriggerDef("ACWindMode", "空调出风模式", "==", "5", "空调出风模式"),
                    TriggerDef("SeatHeatD", "主驾座椅加热", ">", "0", "主驾座椅加热"),
                    TriggerDef("SeatVentD", "主驾座椅通风", ">", "0", "主驾座椅通风"),
                    TriggerDef("SeatHeatP", "副驾座椅加热", ">", "0", "副驾座椅加热"),
                    TriggerDef("SeatVentP", "副驾座椅通风", ">", "0", "副驾座椅通风"),
                    TriggerDef("LightSide", "位置灯", "==", "1", "位置灯"),
                ),
            ),
        )

        assertEquals(
            setOf(
                "acCtrlMode",
                "acDefrostFront",
                "acWindMode",
                "seatHeatD",
                "seatVentD",
                "seatHeatP",
                "seatVentP",
                "lightSide",
            ),
            fields,
        )
    }

    @Test
    fun `extended vehicle triggers select only their requested fields`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("TireTempFL", "左前轮胎温度", ">", "50", "左前轮胎温度"),
                    TriggerDef("LightBrake", "刹车灯", "==", "1", "刹车灯"),
                    TriggerDef("LightReversing", "倒车灯", "==", "1", "倒车灯"),
                    TriggerDef("AccelPedal", "加速踏板", ">", "20", "加速踏板"),
                    TriggerDef("EpbState", "电子手刹", "==", "1", "电子手刹"),
                    TriggerDef("EpedalMode", "单踏板", "==", "1", "单踏板"),
                    TriggerDef("ACDefrostRear", "后挡除霜", "==", "1", "后挡除霜"),
                    TriggerDef("SunshadePos", "遮阳帘位置", ">", "50", "遮阳帘位置"),
                    TriggerDef("MirrorFoldState", "后视镜折叠", "==", "1", "后视镜折叠"),
                ),
            ),
        )

        assertEquals(
            setOf(
                "tireTempFL",
                "lightBrake",
                "lightReversing",
                "accelPedalDepth",
                "epbState",
                "ePedalMode",
                "acDefrostRear",
                "sunshadePos",
                "mirrorFoldState",
            ),
            fields,
        )
    }

    @Test
    fun `ADAS powertrain and tire state triggers stay field scoped`() {
        val fields = requiredAutomationVehicleFields(
            listOf(
                rule(
                    TriggerDef("AdasAebState", "AEB", "!=", "0", "AEB"),
                    TriggerDef("AdasExecSpeed", "智驾执行速度", ">", "80", "智驾执行速度"),
                    TriggerDef("FrontMotorCurrent", "前电机电流", ">", "100", "前电机电流"),
                    TriggerDef("WaterTemp", "冷却液温度", ">", "100", "冷却液温度"),
                    TriggerDef("TireStateRR", "右后轮胎状态", "!=", "0", "右后轮胎状态"),
                    TriggerDef("EnergyState", "能量流状态", "==", "5", "能量流状态"),
                ),
            ),
        )

        assertEquals(
            setOf(
                "adasAebState",
                "adasExecSpeed",
                "frontMotorCurrent",
                "waterTemp",
                "tireStateRR",
                "energyState",
            ),
            fields,
        )
    }

    private fun rule(vararg triggers: TriggerDef) = RuleEntity(
        id = 1,
        name = "test",
        triggers = TriggerDef.listToJson(triggers.toList()),
        actions = "[]",
    )
}
