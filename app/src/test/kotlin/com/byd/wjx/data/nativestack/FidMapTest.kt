package com.byd.wjx.data.nativestack

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

class FidMapTest {

    @Test fun `CAN-FD turn signal fields match BYD framework ids`() {
        assertEquals(339738672, FidMap.entries.single { it.field == "turnSignalLeft" }.fid)
        assertEquals(339738678, FidMap.entries.single { it.field == "turnSignalRight" }.fid)
    }

    @Test fun `unmapped DiParsData fields are explicit derived or unsupported values`() {
        val dataFields = com.byd.wjx.data.remote.DiParsData::class
            .memberProperties.map { it.name }.toSet()
        val mapped = FidMap.entries.map { it.field }.toSet()
        val missing = dataFields - mapped
        assertEquals(
            setOf("avgBatTemp", "batteryCapacityKwh", "autoPark", "rain"),
            missing,
        )
    }

    @Test fun `FidMap coverage matches the selectable production catalog`() {
        val dataFields = com.byd.wjx.data.remote.DiParsData::class
            .memberProperties.map { it.name }.toSet()
        val mappedFields = FidMap.entries.map { it.field }.toSet()
        val covered = mappedFields.intersect(dataFields)
        assertEquals(115, covered.size)
    }

    @Test fun `official Settings corrections replace misleading AutoTark mappings`() {
        fun entry(field: String) = FidMap.entries.single { it.field == field }

        assertEquals(1001, entry("powerState").device)
        assertEquals(315621418, entry("powerState").fid)
        assertEquals(876609560, entry("chargingStatus").fid)
        assertEquals(1267728408, entry("windowRR").fid)
        assertEquals(1101004808, entry("sunroof").fid)
        assertEquals(1009, entry("fuelCap").device)
        assertEquals(1081081896, entry("fuelCap").fid)
        assertEquals(1145045032, entry("soh").fid)
        assertEquals(824180744, entry("seatbeltFR").fid)
        assertEquals(824180746, entry("seatbeltRL").fid)
        assertEquals(824180748, entry("seatbeltRR").fid)
        assertEquals(1006, entry("energyState").device)
        assertEquals(873463832, entry("energyState").fid)
    }

    @Test fun `core and detail profiles partition the full catalog`() {
        assertEquals(
            VehicleReadProfiles.regularFull.toSet(),
            (VehicleReadProfiles.coreFull + VehicleReadProfiles.detailFull).toSet(),
        )
        assertTrue(
            VehicleReadProfiles.coreFull.map { it.field }.toSet()
                .intersect(VehicleReadProfiles.detailFull.map { it.field }.toSet())
                .isEmpty(),
        )
    }

    @Test fun `on demand reads are selectable but do not expand regular full polling`() {
        assertEquals(71, VehicleReadProfiles.regularFull.size)
        assertEquals(44, VehicleReadProfiles.onDemandFields.size)
        assertEquals(8, VehicleReadProfiles.tireTelemetry.size)
        assertEquals(8, VehicleReadProfiles.adasTelemetry.size)
        assertEquals(8, VehicleReadProfiles.powertrainTelemetry.size)
        assertTrue(
            VehicleReadProfiles.regularFull.none {
                it.field in VehicleReadProfiles.onDemandFields
            },
        )
        assertEquals(
            FidMap.entries.map { it.field }.toSet(),
            VehicleReadProfiles.regularFull.map { it.field }.toSet() +
                VehicleReadProfiles.onDemandFields,
        )
    }

    @Test fun `fast action core contains only lifecycle wake fields`() {
        assertEquals(
            setOf("speed", "gear", "chargeGunState", "powerState"),
            VehicleReadProfiles.actionCoreFields,
        )
        assertTrue("soc must be selected only by active rules", "soc" !in VehicleReadProfiles.actionCoreFields)
        assertTrue(
            "auto parking status must be selected only by an enabled consumer",
            "adasApaParkingStatus" !in VehicleReadProfiles.actionCoreFields,
        )
    }

    @Test fun `on demand fids match the candidate registry`() {
        fun entry(field: String) = FidMap.entries.single { it.field == field }

        assertEquals(1077936146, entry("acCtrlMode").fid)
        assertEquals(1077936150, entry("acDefrostFront").fid)
        assertEquals(1077936152, entry("acWindMode").fid)
        assertEquals(702545948, entry("seatHeatD").fid)
        assertEquals(702545944, entry("seatVentD").fid)
        assertEquals(711983132, entry("seatHeatP").fid)
        assertEquals(711983128, entry("seatVentP").fid)
        assertEquals(950009864, entry("lightSide").fid)
        assertEquals(1246797848, entry("tireTempFL").fid)
        assertEquals(1246797884, entry("tireTempRR").fid)
        assertEquals(1011875870, entry("lightFrontFog").fid)
        assertEquals(950009872, entry("lightRearFog").fid)
        assertEquals(950009874, entry("lightBrake").fid)
        assertEquals(950009876, entry("lightReversing").fid)
        assertEquals(874512392, entry("accelPedalDepth").fid)
        assertEquals(562036753, entry("epbState").fid)
        assertEquals(692060194, entry("brakeState").fid)
        assertEquals(888143930, entry("ePedalMode").fid)
        assertEquals(1128267825, entry("acDefrostRear").fid)
        assertEquals(1116733454, entry("steeringHeat").fid)
        assertEquals(1101004816, entry("sunshadePos").fid)
        assertEquals(1011875887, entry("mirrorFoldState").fid)
        assertEquals(-1728052957, entry("tireStateFL").fid)
        assertEquals(-1728052945, entry("tireStateRR").fid)
        assertEquals(852492332, entry("adasAebState").fid)
        assertEquals(304087110, entry("adasAvhState").fid)
        assertEquals(1098907656, entry("adasBsdState").fid)
        assertEquals(305135676, entry("adasEspState").fid)
        assertEquals(535826448, entry("adasAccGrayState").fid)
        assertEquals(535826458, entry("adasNoaGrayState").fid)
        assertEquals(790643093, entry("adasMnoaStatus").fid)
        assertEquals(513807420, entry("adasExecSpeed").fid)
        assertEquals(1141899272, entry("frontMotorSpeed").fid)
        assertEquals(621805576, entry("rearMotorSpeed").fid)
        assertEquals(1154482192, entry("frontMotorTemp").fid)
        assertEquals(1155530768, entry("rearMotorTemp").fid)
        assertEquals(1186988040, entry("frontMotorCurrent").fid)
        assertEquals(1186988056, entry("rearMotorCurrent").fid)
        assertEquals(339738642, entry("engineRpm").fid)
        assertEquals(839263, entry("waterTemp").fid)
    }

    @Test fun `no duplicate device-fid pairs`() {
        val pairs = FidMap.entries.map { it.device to it.fid }
        assertTrue("Duplicate (device, fid) pairs found", pairs.size == pairs.toSet().size)
    }

    @Test fun `no duplicate field names`() {
        val fields = FidMap.entries.map { it.field }
        assertEquals(fields.size, fields.toSet().size)
    }

    @Test fun `every entry has decoder and transact in set 5 7`() {
        FidMap.entries.forEach { e ->
            assertNotNull("Decoder null for ${e.field}", e.decoder)
            assertTrue("Bad transact ${e.transact} for ${e.field}", e.transact in listOf(5, 7))
            if (e.decoder.name.startsWith("FLOAT_")) {
                assertEquals("FLOAT decoder must use transact 7 for ${e.field}", 7, e.transact)
            } else {
                assertEquals("INT decoder must use transact 5 for ${e.field}", 5, e.transact)
            }
            if (e.decoder == Decoder.INT_SCALED) {
                assertTrue(
                    "INT_SCALED entry '${e.field}' must have scale != 1.0 (got ${e.scale})",
                    e.scale != 1.0
                )
            }
        }
    }
}
