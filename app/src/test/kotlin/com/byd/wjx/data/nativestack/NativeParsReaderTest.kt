package com.byd.wjx.data.nativestack

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
        val data = reader.fetch()
        assertNotNull("fetch() returned null", data)
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

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields).fetch()
        assertNotNull("fetch() returned null", data)
        checkNotNull(data)

        assertEquals("maxBatTemp", 11, data.maxBatTemp)
        assertEquals("minBatTemp", 10, data.minBatTemp)
        assertEquals("avgBatTemp restored as mean of max/min", 11, data.avgBatTemp)
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
        val result = reader.fetch()
        assertNull("fetch() must return null when soc + mileage + voltage12v are all null", result)
    }

    @Test
    fun `action snapshot does not query battery capacity settings`() = runTest {
        val gateway = FakeVehicleGateway().apply {
            floatValues[1014 to 1246777400] = 50f
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val data = NativeParsReader(gateway, settings, noAdditionalActionFields).fetchAction()

        assertNotNull(data)
        assertNull(data?.batteryCapacityKwh)
        assertEquals(6, gateway.lastBatchSpecs.size)
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

        val data = NativeParsReader(gateway, settings, provider).fetchAction()

        assertNotNull(data)
        assertEquals(18, data?.insideTemp)
        assertEquals(7, gateway.lastBatchSpecs.size)
    }

    @Test
    fun `LED fast snapshot reads independent turn hazard gear and brake fields`() = runTest {
        val turnLeft = FidMap.entries.single { it.field == "turnSignalLeft" }
        val turnRight = FidMap.entries.single { it.field == "turnSignalRight" }
        val hazard = FidMap.entries.single { it.field == "hazardLights" }
        val gear = FidMap.entries.single { it.field == "gear" }
        val brake = FidMap.entries.single { it.field == "brakePedalDepth" }
        val gateway = FakeVehicleGateway().apply {
            intValues[turnLeft.device to turnLeft.fid] = 1
            intValues[turnRight.device to turnRight.fid] = 0
            intValues[hazard.device to hazard.fid] = 0
            intValues[gear.device to gear.fid] = 4
            intValues[brake.device to brake.fid] = 28
        }
        val settings = mockk<SettingsRepository>(relaxed = true)

        val snapshot = NativeParsReader(gateway, settings, noAdditionalActionFields)
            .fetchLedVehicleSnapshot()

        assertNotNull(snapshot)
        assertEquals(1, snapshot?.data?.turnSignalLeft)
        assertEquals(0, snapshot?.data?.turnSignalRight)
        assertEquals(0, snapshot?.data?.hazardLights)
        assertEquals(4, snapshot?.data?.gear)
        assertEquals(28, snapshot?.data?.brakePedalDepth)
        assertEquals(5, gateway.lastBatchSpecs.size)
        coVerify(exactly = 0) { settings.getBatteryCapacity() }
    }
}
