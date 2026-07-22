package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.FakeVehicleGateway
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-driven test for NativeParsReader.
 *
 * Loads S2 (parked, IGN ON, AC off) snapshot, stubs VehicleGateway with
 * raw_int values from the fixture, and asserts that fetch() decodes each
 * FidMap field to a value consistent with the DiPlus side.
 *
 * Tolerances:
 *   floats (soc, speed, totalElecConsumption): ± 0.5
 *   kwh (totalElecConsumption):               ± 0.05
 *   volts (voltage12v, cell voltages):        ± 0.01
 */
class NativeParsReaderTest {

    private val noAdditionalActionFields = ActionSnapshotFieldProvider { emptySet() }

    @Test
    fun `LED profiles keep the per-second read budget bounded`() {
        assertEquals(setOf("turnSignalFlash", "gear"), VehicleReadProfiles.ledMotionFields)
        assertEquals(
            setOf("brakePedalDepth", "lightBrake", "lightReversing", "accelPedalDepth"),
            VehicleReadProfiles.ledBrakeFields,
        )
        assertEquals(
            setOf("doorFL", "doorFR", "doorRL", "doorRR", "trunk"),
            VehicleReadProfiles.ledBodyFields,
        )

        val readsPerSecond = VehicleReadProfiles.ledMotion.size * 10 +
            VehicleReadProfiles.ledBrake.size * 5 +
            VehicleReadProfiles.ledBody.size * 2
        assertEquals(50, readsPerSecond)
    }

    private fun loadFixture(): JSONObject {
        val stream = checkNotNull(
            NativeParsReaderTest::class.java.classLoader
                ?.getResourceAsStream("native-stack-fixtures/s2-parked-on.json")
        ) { "s2-parked-on.json fixture not found in test resources" }
        return JSONObject(stream.bufferedReader().readText())
    }

    /**
     * Build a map (device, fid) → raw_int from the fixture's autoservice block,
     * skipping entries marked skipped=true.
     */
    private fun buildFixtureAutoserviceMap(fixture: JSONObject): Map<Pair<Int, Int>, Int> {
        val result = mutableMapOf<Pair<Int, Int>, Int>()
        val autoservice = fixture.getJSONObject("autoservice")
        for (param in autoservice.keys()) {
            val entry = autoservice.getJSONObject(param)
            if (entry.optBoolean("skipped", false)) continue
            val dev = entry.getInt("device")
            val fid = entry.getInt("fid")
            val raw = entry.getInt("raw_int")
            result[dev to fid] = raw
        }
        return result
    }

    @Test
    fun `s2 fixture decodes to values that match D+ side after equivalent unit transform`() = runTest {
        val fixture = loadFixture()
        val fixtureMap = buildFixtureAutoserviceMap(fixture)
        val diplus = fixture.getJSONObject("diplus")

        val gateway = FakeVehicleGateway()

        // Override per FidMap entry where the fixture has a real raw_int.
        // NOTE: The fixture was captured with some older fids (Gear=562036736,
        // DRL=985661476). FidMap has since been updated to validated fids.
        // Those entries will get null from the mock — that's correct behaviour.
        for (entry in FidMap.entries) {
            val raw = fixtureMap[entry.device to entry.fid] ?: continue
            when (entry.transact) {
                5 -> gateway.intValues[entry.device to entry.fid] = raw
                7 -> gateway.floatValues[entry.device to entry.fid] =
                    java.lang.Float.intBitsToFloat(raw)
            }
        }

        // Mock SettingsRepository
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        // ── Run ───────────────────────────────────────────────────────────────
        val reader = NativeParsReader(gateway, settings, noAdditionalActionFields)
        val data = reader.fetchVehicleSnapshot()?.data
        assertNotNull("fetchVehicleSnapshot() returned null", data)
        checkNotNull(data)

        // ── Assert each covered FidMap field ─────────────────────────────────

        // soc: float 100.0 → Int 100; D+ SOC="100"
        val diplusSoc = diplus.getString("SOC").toIntOrNull()
        assertEquals("soc", diplusSoc, data.soc)

        // speed: float 0.0 → Int 0; D+ Speed="0"
        val diplusSpeed = diplus.getString("Speed").toIntOrNull()
        assertEquals("speed", diplusSpeed, data.speed)

        // mileage: raw 37998 × 0.1 = 3799.8; D+ Mileage=37998 → 37998/10=3799.8
        val diplusMileage = diplus.getString("Mileage").toDoubleOrNull()?.let { it / 10.0 }
        if (diplusMileage != null && data.mileage != null) {
            assertEquals("mileage", diplusMileage, data.mileage!!, 0.1)
        }

        // power: raw 0 → Int 0; D+ Power="0"
        val diplusPower = diplus.getString("Power").toDoubleOrNull()
        if (diplusPower != null) {
            assertEquals("power", diplusPower, data.power ?: 0.0, 0.5)
        }

        // totalElecConsumption: float from bits 1149052519 ≈ 1012.6; D+ TotalElecCon="1012.6"
        val diplusTec = diplus.getString("TotalElecCon").toDoubleOrNull()
        if (diplusTec != null && data.totalElecConsumption != null) {
            assertEquals("totalElecConsumption", diplusTec, data.totalElecConsumption!!, 0.05)
        }

        // voltage12v: float 14.0; D+ Voltage12V="14" (already volts)
        val diplusV12 = diplus.getString("Voltage12V").toDoubleOrNull()
        if (diplusV12 != null && diplusV12 > 0.0 && data.voltage12v != null) {
            // D+ may return millivolts (>100) or volts; 14 is already volts
            val expected = if (diplusV12 > 100.0) diplusV12 / 1000.0 else diplusV12
            assertEquals("voltage12v", expected, data.voltage12v!!, 0.01)
        }

        // maxCellVoltage: 3426 × 0.001 = 3.426; D+ MaxCellV="3.427" (±0.01 tolerance)
        val diplusMaxCell = diplus.getString("MaxCellV").toDoubleOrNull()
        if (diplusMaxCell != null && diplusMaxCell > 0.5 && data.maxCellVoltage != null) {
            assertEquals("maxCellVoltage", diplusMaxCell, data.maxCellVoltage!!, 0.01)
        }

        // minCellVoltage: 3352 × 0.001 = 3.352; D+ MinCellV="3.353" (±0.01 tolerance)
        val diplusMinCell = diplus.getString("MinCellV").toDoubleOrNull()
        if (diplusMinCell != null && diplusMinCell > 0.5 && data.minCellVoltage != null) {
            assertEquals("minCellVoltage", diplusMinCell, data.minCellVoltage!!, 0.01)
        }

        // chargeGunState: raw 1; D+ ChargeGun="1"
        val diplusGun = diplus.getString("ChargeGun").toIntOrNull()
        assertEquals("chargeGunState", diplusGun, data.chargeGunState)

        // acStatus: raw 0; D+ ACStatus="0"
        val diplusAcStatus = diplus.getString("ACStatus").toIntOrNull()
        assertEquals("acStatus", diplusAcStatus, data.acStatus)

        // insideTemp: raw 18; D+ InsideTemp="18"
        val diplusInside = diplus.getString("InsideTemp").toIntOrNull()
        if (diplusInside != null && diplusInside in -50..80) {
            assertEquals("insideTemp", diplusInside, data.insideTemp)
        }

        // exteriorTemp: raw 15; D+ ExtTemp="15"
        val diplusExt = diplus.getString("ExtTemp").toIntOrNull()
        if (diplusExt != null && diplusExt in -50..80) {
            assertEquals("exteriorTemp", diplusExt, data.exteriorTemp)
        }

        // hood: raw 0; D+ Hood="0"
        val diplusHood = diplus.getString("Hood").toIntOrNull()
        assertEquals("hood", diplusHood, data.hood)

        // seatbeltFL: raw 0; D+ SeatbeltFL="0"
        val diplusBelt = diplus.getString("SeatbeltFL").toIntOrNull()
        assertEquals("seatbeltFL", diplusBelt, data.seatbeltFL)

        // tire pressures: direct int comparison
        assertEquals("tirePressFL", diplus.getString("TirePressFL").toIntOrNull(), data.tirePressFL)
        assertEquals("tirePressFR", diplus.getString("TirePressFR").toIntOrNull(), data.tirePressFR)
        assertEquals("tirePressRL", diplus.getString("TirePressRL").toIntOrNull(), data.tirePressRL)
        assertEquals("tirePressRR", diplus.getString("TirePressRR").toIntOrNull(), data.tirePressRR)

        // batteryCapacityKwh: from settings mock = 72.9
        assertEquals("batteryCapacityKwh", 72.9, data.batteryCapacityKwh!!, 0.01)

        // ── Fields not in FidMap (or no fixture raw for validated fid) ────────
        // Gear: FidMap uses validated fid=555745336; fixture used old fid=562036736 — no match → null
        assertNull("gear should be null (no fixture data for validated fid)", data.gear)
        // DRL: FidMap uses validated fid=1231040528; fixture used old fid=985661476 — no match → null
        assertNull("drl should be null (no fixture data for validated fid)", data.drl)
        // lightLow: no fixture data for fid=950009866 → null
        assertNull("lightLow should be null (no fixture data)", data.lightLow)
    }

    /**
     * Battery temps (dev=1014) carry a -40 CAN offset. Raw 51/50 must decode to
     * 11°C/10°C, and avgBatTemp (dropped in native v2.9.0) must be restored as the
     * mean of the two live extremes — (11+10)/2 → 11. Verified against D+ 10/11/11.
     */
    @Test
    fun `battery temps decode with -40 offset and avg is mean of max-min`() = runTest {
        val gateway = FakeVehicleGateway()
        // Liveness gate: provide mileage (raw 37998 × 0.1 = 3799.8 km).
        gateway.intValues[1014 to 1246765072] = 37998
        // Battery temp raws (need -40 offset).
        gateway.intValues[1014 to 1148190752] = 51 // max → 11°C
        gateway.intValues[1014 to 1148190736] = 50 // min → 10°C

        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchVehicleSnapshot()?.data
        assertNotNull("fetchVehicleSnapshot() returned null", data)
        checkNotNull(data)

        assertEquals("maxBatTemp", 11, data.maxBatTemp)
        assertEquals("minBatTemp", 10, data.minBatTemp)
        assertEquals("avgBatTemp restored as mean of max/min", 11, data.avgBatTemp)
    }

    @Test
    fun `legacy disconnected charge gun state zero remains available`() = runTest {
        val mileageEntry = FidMap.entries.single { it.field == "mileage" }
        val chargeGunEntry = FidMap.entries.single { it.field == "chargeGunState" }
        val gateway = FakeVehicleGateway().apply {
            intValues[mileageEntry.device to mileageEntry.fid] = 10_000
            intValues[chargeGunEntry.device to chargeGunEntry.fid] = 0
        }
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchVehicleSnapshot()?.data

        assertEquals(0, data?.chargeGunState)
    }

    @Test
    fun `official BYD enum values pass through without legacy remapping`() = runTest {
        fun entry(field: String) = FidMap.entries.single { it.field == field }
        val gateway = FakeVehicleGateway().apply {
            // Liveness gate.
            intValues[1014 to 1246765072] = 10_000
            listOf(
                "powerState" to 3,
                "chargingStatus" to 13,
                "chargeGunState" to 2,
                "trunk" to 3,
                "chargingCapCover" to 2,
                "fuelCap" to 1,
                "lockFL" to 2,
                "lockFR" to 1,
                "gear" to 4,
                "driveMode" to 1,
                "driveModeIndicator" to 7,
                "workMode" to 3,
                "workModeAlt" to 1,
                "sunroof" to 44,
            ).forEach { (field, value) ->
                val fid = entry(field)
                intValues[fid.device to fid.fid] = value
            }
        }
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchVehicleSnapshot()?.data

        assertEquals(3, data?.powerState)
        assertEquals(13, data?.chargingStatus)
        assertEquals(2, data?.chargeGunState)
        assertEquals(3, data?.trunk)
        assertEquals(2, data?.chargingCapCover)
        assertEquals(1, data?.fuelCap)
        assertEquals(2, data?.lockFL)
        assertEquals(1, data?.lockFR)
        assertEquals(7, data?.driveMode)
        assertEquals(3, data?.workMode)
        assertEquals(44, data?.sunroof)
    }

    @Test
    fun `terrain drive mode indicator is ignored while parked and used while driving`() = runTest {
        fun entry(field: String) = FidMap.entries.single { it.field == field }
        val mileage = entry("mileage")
        val gear = entry("gear")
        val base = entry("driveMode")
        val indicator = entry("driveModeIndicator")
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        suspend fun read(gearValue: Int, indicatorValue: Int): Int? {
            val gateway = FakeVehicleGateway().apply {
                intValues[mileage.device to mileage.fid] = 10_000
                intValues[gear.device to gear.fid] = gearValue
                intValues[base.device to base.fid] = 3
                intValues[indicator.device to indicator.fid] = indicatorValue
            }
            return NativeParsReader(gateway, settings, noAdditionalActionFields)
                .fetchVehicleSnapshot()?.data?.driveMode
        }

        assertEquals(3, read(gearValue = 1, indicatorValue = 7))
        assertEquals(7, read(gearValue = 4, indicatorValue = 7))
        assertEquals(11, read(gearValue = 2, indicatorValue = 11))
    }

    @Test
    fun `drive mode automation reads base and terrain indicator`() = runTest {
        val gear = FidMap.entries.single { it.field == "gear" }
        val base = FidMap.entries.single { it.field == "driveMode" }
        val indicator = FidMap.entries.single { it.field == "driveModeIndicator" }
        val gateway = FakeVehicleGateway().apply {
            intValues[gear.device to gear.fid] = 4
            intValues[base.device to base.fid] = 3
            intValues[indicator.device to indicator.fid] = 7
        }
        val settings = mockk<SettingsRepository>(relaxed = true)
        val provider = ActionSnapshotFieldProvider { setOf("driveMode") }

        val data = NativeParsReader(gateway, settings, provider)
            .fetchActionVehicleSnapshot()?.data

        assertEquals(7, data?.driveMode)
        assertEquals(VehicleReadProfiles.actionCoreFields.size + 2, gateway.lastBatchSpecs.size)
    }

    @Test
    fun `work mode prefers the direct fid with platform fallback and rejects invalid values`() = runTest {
        fun entry(field: String) = FidMap.entries.single { it.field == field }
        val mileage = entry("mileage")
        val direct = entry("workMode")
        val instrument50 = entry("workModeAlt")
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        suspend fun read(newValue: Int?, directValue: Int?): Int? {
            val gateway = FakeVehicleGateway().apply {
                intValues[mileage.device to mileage.fid] = 10_000
                newValue?.let { intValues[instrument50.device to instrument50.fid] = it }
                directValue?.let { intValues[direct.device to direct.fid] = it }
            }
            return NativeParsReader(gateway, settings, noAdditionalActionFields)
                .fetchVehicleSnapshot()?.data?.workMode
        }

        assertEquals(3, read(newValue = 1, directValue = 3))
        assertEquals(1, read(newValue = 3, directValue = 1))
        assertEquals(3, read(newValue = 0, directValue = 3))
        assertEquals(1, read(newValue = null, directValue = 1))
        assertNull(read(newValue = 9, directValue = -1))
    }

    @Test
    fun `work mode automation reads both platform fids`() = runTest {
        val workMode50 = FidMap.entries.single { it.field == "workModeAlt" }
        val gateway = FakeVehicleGateway().apply {
            intValues[workMode50.device to workMode50.fid] = 3
        }
        val settings = mockk<SettingsRepository>(relaxed = true)
        val provider = ActionSnapshotFieldProvider { setOf("workMode") }

        val data = NativeParsReader(gateway, settings, provider)
            .fetchActionVehicleSnapshot()?.data

        assertEquals(3, data?.workMode)
        assertEquals(VehicleReadProfiles.actionCoreFields.size + 2, gateway.lastBatchSpecs.size)
    }

    /**
     * Liveness gate: when autoservice is available but all primary signals (soc,
     * mileage, voltage12v) return null, fetch() must return null so the caller
     * treats it as an unreachable / sentinel-spamming autoservice and retries.
     */
    @Test
    fun `fetch returns null when all primary signals are null`() = runTest {
        val gateway = FakeVehicleGateway()

        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9

        val reader = NativeParsReader(gateway, settings, noAdditionalActionFields)
        val result = reader.fetchVehicleSnapshot()
        assertNull("fetchVehicleSnapshot() must return null when primary fields are null", result)
    }

    @Test
    fun `action snapshot does not query battery capacity settings`() = runTest {
        val powerState = FidMap.entries.single { it.field == "powerState" }
        val gateway = FakeVehicleGateway().apply {
            intValues[powerState.device to powerState.fid] = 3
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchActionVehicleSnapshot()?.data

        assertNotNull(data)
        assertNull(data?.batteryCapacityKwh)
        assertEquals(VehicleReadProfiles.actionCoreFields.size, gateway.lastBatchSpecs.size)
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }

    @Test
    fun `action snapshot adds fields required by enabled automations`() = runTest {
        val insideTemp = FidMap.entries.single { it.field == "insideTemp" }
        val gateway = FakeVehicleGateway().apply {
            intValues[insideTemp.device to insideTemp.fid] = 18
        }
        val settings = mockk<SettingsRepository>(relaxed = true)
        val provider = ActionSnapshotFieldProvider { setOf("insideTemp") }

        val data = NativeParsReader(gateway, settings, provider)
            .fetchActionVehicleSnapshot()?.data

        assertNotNull(data)
        assertEquals(18, data?.insideTemp)
        assertEquals(VehicleReadProfiles.actionCoreFields.size + 1, gateway.lastBatchSpecs.size)
    }

    @Test
    fun `validated comfort fid is read only when an automation requests it`() = runTest {
        val seatHeat = FidMap.entries.single { it.field == "seatHeatD" }
        val gateway = FakeVehicleGateway().apply {
            intValues[seatHeat.device to seatHeat.fid] = 3
        }
        val settings = mockk<SettingsRepository>(relaxed = true)
        val provider = ActionSnapshotFieldProvider { setOf("seatHeatD") }

        val data = NativeParsReader(gateway, settings, provider)
            .fetchActionVehicleSnapshot()?.data

        assertNotNull(data)
        assertEquals(3, data?.seatHeatD)
        assertEquals(
            VehicleReadProfiles.actionCoreFields.size + 1,
            gateway.lastBatchSpecs.size,
        )
        assertTrue(
            gateway.lastBatchSpecs.any {
                it.device == seatHeat.device && it.fid == seatHeat.fid
            },
        )
        assertTrue(VehicleReadProfiles.regularFull.none { it.field == "seatHeatD" })
    }

    @Test
    fun `full snapshot refreshes core every tick and reuses detail for three seconds`() = runTest {
        val soc = FidMap.entries.single { it.field == "soc" }
        val soh = FidMap.entries.single { it.field == "soh" }
        val insideTemp = FidMap.entries.single { it.field == "insideTemp" }
        val gateway = FakeVehicleGateway().apply {
            floatValues[soc.device to soc.fid] = 50f
            intValues[soh.device to soh.fid] = 96
            intValues[insideTemp.device to insideTemp.fid] = 18
        }
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getBatteryCapacity() } returns 72.9
        var nowNanos = 0L
        val reader = NativeParsReader(gateway, settings, noAdditionalActionFields).apply {
            monotonicNanos = { nowNanos }
        }

        val initial = reader.fetchVehicleSnapshot()
        assertNotNull(initial)
        assertEquals(50, initial?.data?.soc)
        assertEquals(96, initial?.data?.soh)
        assertEquals(18, initial?.data?.insideTemp)
        assertEquals(1, gateway.batchHistory.size)
        assertEquals(VehicleReadProfiles.regularFull.size, gateway.batchHistory.single().size)
        assertEquals(71, gateway.batchHistory.single().size)
        assertTrue(
            gateway.batchHistory.single().none { spec ->
                FidMap.entries.any {
                    it.field in VehicleReadProfiles.onDemandFields &&
                        it.device == spec.device && it.fid == spec.fid
                }
            },
        )

        gateway.floatValues[soc.device to soc.fid] = 51f
        gateway.intValues[soh.device to soh.fid] = 95
        gateway.intValues[insideTemp.device to insideTemp.fid] = 19
        val coreOnly = reader.fetchVehicleSnapshot()
        assertEquals(51, coreOnly?.data?.soc)
        assertEquals(96, coreOnly?.data?.soh)
        assertEquals(18, coreOnly?.data?.insideTemp)
        assertEquals(VehicleReadProfiles.coreFull.size, gateway.lastBatchSpecs.size)
        assertEquals(2, gateway.batchHistory.size)

        nowNanos = 3_000_000_000L
        val detailRefreshed = reader.fetchVehicleSnapshot()
        assertEquals(51, detailRefreshed?.data?.soc)
        assertEquals(95, detailRefreshed?.data?.soh)
        assertEquals(19, detailRefreshed?.data?.insideTemp)
        assertEquals(4, gateway.batchHistory.size)
        assertEquals(VehicleReadProfiles.detailFull.size, gateway.lastBatchSpecs.size)
    }

    @Test
    fun `LED motion snapshot reads only stable turn enum and gear`() = runTest {
        val turnFlash = FidMap.entries.single { it.field == "turnSignalFlash" }
        val gear = FidMap.entries.single { it.field == "gear" }
        val gateway = FakeVehicleGateway().apply {
            intValues[turnFlash.device to turnFlash.fid] = 2
            intValues[gear.device to gear.fid] = 4
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val snapshot = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchLedMotionVehicleSnapshot()

        assertNotNull(snapshot)
        assertEquals(2, snapshot?.data?.turnSignalFlash)
        assertEquals(4, snapshot?.data?.gear)
        assertEquals(2, gateway.lastBatchSpecs.size)
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }

    @Test
    fun `LED pedal and lamp snapshot reads linkage inputs`() = runTest {
        val brake = FidMap.entries.single { it.field == "brakePedalDepth" }
        val brakeLight = FidMap.entries.single { it.field == "lightBrake" }
        val reverseLight = FidMap.entries.single { it.field == "lightReversing" }
        val accelerator = FidMap.entries.single { it.field == "accelPedalDepth" }
        val gateway = FakeVehicleGateway().apply {
            intValues[brake.device to brake.fid] = 28
            intValues[brakeLight.device to brakeLight.fid] = 1
            intValues[reverseLight.device to reverseLight.fid] = 1
            intValues[accelerator.device to accelerator.fid] = 35
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val snapshot = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchLedBrakeVehicleSnapshot()

        assertNotNull(snapshot)
        assertEquals(28, snapshot?.data?.brakePedalDepth)
        assertEquals(1, snapshot?.data?.lightBrake)
        assertEquals(1, snapshot?.data?.lightReversing)
        assertEquals(35, snapshot?.data?.accelPedalDepth)
        assertEquals(4, gateway.lastBatchSpecs.size)
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }

    @Test
    fun `LED body snapshot reads four doors and trunk`() = runTest {
        val gateway = FakeVehicleGateway().apply {
            VehicleReadProfiles.ledBody.forEach { entry ->
                intValues[entry.device to entry.fid] = when (entry.field) {
                    "doorFL", "doorFR", "doorRL", "doorRR" -> 0
                    "trunk" -> 1
                    else -> error("Unexpected LED context field ${entry.field}")
                }
            }
        }
        val settings = mockk<SettingsRepository>(relaxed = true)
        val reader = NativeParsReader(gateway, settings, noAdditionalActionFields)

        val body = reader.fetchLedBodyVehicleSnapshot()
        assertEquals(0, body?.data?.doorFL)
        assertEquals(1, body?.data?.trunk)
        assertEquals(VehicleReadProfiles.ledBody.size, gateway.lastBatchSpecs.size)
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }

    @Test
    fun `tire telemetry profile reads temperatures and raw states only`() = runTest {
        val fields = VehicleReadProfiles.tireTelemetry
        val gateway = FakeVehicleGateway().apply {
            fields.forEach { entry ->
                intValues[entry.device to entry.fid] = when (entry.field) {
                    "tireTempFL" -> 31
                    "tireTempFR" -> 32
                    "tireTempRL" -> 33
                    "tireTempRR" -> 34
                    "tireStateFL" -> 0
                    "tireStateFR" -> 1
                    "tireStateRL" -> 2
                    "tireStateRR" -> 3
                    else -> error("Unexpected tire field ${entry.field}")
                }
            }
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .readTireTelemetry()

        assertEquals(31, data?.tireTempFL)
        assertEquals(34, data?.tireTempRR)
        assertEquals(0, data?.tireStateFL)
        assertEquals(3, data?.tireStateRR)
        assertEquals(8, gateway.lastBatchSpecs.size)
        assertEquals(
            fields.map { it.device to it.fid }.toSet(),
            gateway.lastBatchSpecs.map { it.device to it.fid }.toSet(),
        )
    }

    @Test
    fun `automation action snapshot decodes ADAS enums and execution speed`() = runTest {
        val fields = FidMap.entries.filter { it.field in VehicleReadProfiles.adasTelemetryFields }
        val values = mapOf(
            "adasAebState" to 1,
            "adasAvhState" to 2,
            "adasBsdState" to 3,
            "adasEspState" to 4,
            "adasAccGrayState" to 5,
            "adasNoaGrayState" to 6,
            "adasMnoaStatus" to 7,
            "adasExecSpeed" to 88,
        )
        val gateway = FakeVehicleGateway().apply {
            fields.forEach { entry ->
                intValues[entry.device to entry.fid] = checkNotNull(values[entry.field])
            }
        }
        val settings = mockk<SettingsRepository>(relaxed = true)
        val provider = ActionSnapshotFieldProvider { VehicleReadProfiles.adasTelemetryFields }

        val data = NativeParsReader(gateway, settings, provider)
            .fetchActionVehicleSnapshot()?.data

        assertEquals(1, data?.adasAebState)
        assertEquals(4, data?.adasEspState)
        assertEquals(7, data?.adasMnoaStatus)
        assertEquals(88, data?.adasExecSpeed)
        assertEquals(12, gateway.lastBatchSpecs.size)
        assertTrue(
            fields.map { it.device to it.fid }.toSet()
                .all { it in gateway.lastBatchSpecs.map { spec -> spec.device to spec.fid }.toSet() },
        )
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }

    @Test
    fun `powertrain telemetry scales signed currents and accepts diagnostic temperatures`() = runTest {
        val fields = VehicleReadProfiles.powertrainTelemetry
        val gear = FidMap.entries.single { it.field == "gear" }
        val values = mapOf(
            "frontMotorSpeed" to 12_345,
            "rearMotorSpeed" to -8_765,
            "frontMotorTemp" to 96,
            "rearMotorTemp" to 104,
            "frontMotorCurrent" to 123,
            "rearMotorCurrent" to -456,
            "engineRpm" to 1_850,
            "waterTemp" to 92,
        )
        val gateway = FakeVehicleGateway().apply {
            intValues[gear.device to gear.fid] = 4
            fields.forEach { entry ->
                intValues[entry.device to entry.fid] = checkNotNull(values[entry.field])
            }
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .readPowertrainTelemetry()

        assertEquals(12_345, data?.frontMotorSpeed)
        assertEquals(8_765, data?.rearMotorSpeed)
        assertEquals(96, data?.frontMotorTemp)
        assertEquals(104, data?.rearMotorTemp)
        assertEquals(12.3, data?.frontMotorCurrent ?: 0.0, 0.001)
        assertEquals(-45.6, data?.rearMotorCurrent ?: 0.0, 0.001)
        assertEquals(1_850, data?.engineRpm)
        assertEquals(92, data?.waterTemp)
        assertEquals(9, gateway.lastBatchSpecs.size)
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }

    @Test
    fun `powertrain telemetry normalizes unavailable and stopped rpm values`() = runTest {
        fun entry(field: String) = FidMap.entries.single { it.field == field }
        val gear = entry("gear")
        val engine = entry("engineRpm")
        val frontMotor = entry("frontMotorSpeed")
        val rearMotor = entry("rearMotorSpeed")
        val settings = mockk<SettingsRepository>(relaxed = true)

        suspend fun read(gearValue: Int, engineValue: Int, frontValue: Int, rearValue: Int): DiParsData? {
            val gateway = FakeVehicleGateway().apply {
                intValues[gear.device to gear.fid] = gearValue
                intValues[engine.device to engine.fid] = engineValue
                intValues[frontMotor.device to frontMotor.fid] = frontValue
                intValues[rearMotor.device to rearMotor.fid] = rearValue
            }
            return NativeParsReader(gateway, settings, noAdditionalActionFields)
                .readPowertrainTelemetry()
        }

        val driving = read(gearValue = 4, engineValue = 8_191, frontValue = -1, rearValue = 1)
        assertEquals(0, driving?.engineRpm)
        assertEquals(0, driving?.frontMotorSpeed)
        assertEquals(0, driving?.rearMotorSpeed)

        val parked = read(gearValue = 1, engineValue = 900, frontValue = 1_200, rearValue = -1_300)
        assertEquals(900, parked?.engineRpm)
        assertEquals(0, parked?.frontMotorSpeed)
        assertEquals(0, parked?.rearMotorSpeed)
    }
}
