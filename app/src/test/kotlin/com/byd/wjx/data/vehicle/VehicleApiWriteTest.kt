package com.byd.wjx.data.vehicle

import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.local.entity.VehicleWriteLogEntity
import com.byd.wjx.data.nativestack.ParsReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for VehicleApi structured write methods (Task C.2 / C.5 / C.6).
 *
 * Allowlist built from LIVE_VALIDATED so tests don't depend on vehicle action JSON asset.
 * All write methods now return Result<Unit>; assertions use isSuccess / isFailure.
 */
class VehicleApiWriteTest {

    private val parsReader: ParsReader = mockk(relaxed = true)
    private val gateway: VehicleGateway = mockk()
    private val writeLogDao: VehicleWriteLogDao = mockk(relaxed = true)

    // Build allowlist from LIVE_VALIDATED — same data VehicleApiImpl uses at runtime.
    private val allowlist = WriteAllowlist(
        WriteAllowlist.LIVE_VALIDATED.associateBy { it.actionName.lowercase() }
    )

    private val api: VehicleApi = VehicleApiImpl(parsReader, gateway, allowlist, writeLogDao)

    // ── Test 1: writeAcOn happy path ──────────────────────────────────────────

    @Test fun `writeAcOn calls helper write with dev=1000 fid=501219352 val=0 and returns success`() = runTest {
        val entry = allowlist.find("ac_on")!!
        // ac_on = vehicle ac_ctrl_mode (501219352) set to 0 (AUTO); no readbackFid
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 0) } returns 0

        assertTrue(api.writeAcOn().isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 0) }
        // No readback for ac_on
        coVerify(exactly = 0) { gateway.readRaw(any()) }
    }

    // ── Test 2: writeSetDriverTemp out of range rejected ──────────────────────

    @Test fun `writeSetDriverTemp with celsius=99 is rejected before calling helper`() = runTest {
        val result = api.writeSetDriverTemp(99)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.OutOfRange)
        coVerify(exactly = 0) { gateway.writeStatus(any(), any(), any()) }
    }

    @Test fun `writeSetDriverTemp with celsius=5 is rejected before calling helper`() = runTest {
        val result = api.writeSetDriverTemp(5)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.OutOfRange)
        coVerify(exactly = 0) { gateway.writeStatus(any(), any(), any()) }
    }

    // ── Test 3: writeUnlockDoors calls helper with val=1 ─────────────────────

    @Test fun `writeUnlockDoors calls helper with val=1 and returns success`() = runTest {
        val entry = allowlist.find("doors_unlock")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 1) } returns 0
        coEvery { gateway.readRaw(readbackSpec(entry)) } returns VehicleRawRead(0, 1)

        assertTrue(api.writeUnlockDoors().isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 1) }
    }

    // ── Test 4: writeLockDoors calls helper with val=2 ────────────────────────

    @Test fun `writeLockDoors calls helper with val=2 and returns success`() = runTest {
        val entry = allowlist.find("doors_lock")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 2) } returns 0
        coEvery { gateway.readRaw(readbackSpec(entry)) } returns VehicleRawRead(0, 2)

        assertTrue(api.writeLockDoors().isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 2) }
    }

    // ── Test 5: allowlist miss returns failure without crash ──────────────────

    @Test fun `write method with EMPTY allowlist returns failure AllowlistMiss without throwing`() = runTest {
        val emptyApi: VehicleApi = VehicleApiImpl(parsReader, gateway, WriteAllowlist.EMPTY, writeLogDao)
        val result = emptyApi.writeAcOn()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.AllowlistMiss)
        coVerify(exactly = 0) { gateway.writeStatus(any(), any(), any()) }
    }

    // ── Test 6: readback mismatch returns failure ─────────────────────────────

    @Test fun `writeLockDoors returns failure ReadbackMismatch when readback value does not match`() = runTest {
        val entry = allowlist.find("doors_lock")!!
        // Write succeeds but readback returns wrong value (1 instead of 2)
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 2) } returns 0
        coEvery { gateway.readRaw(readbackSpec(entry)) } returns VehicleRawRead(0, 1)

        val result = api.writeLockDoors()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.ReadbackMismatch)
    }

    // ── Bonus: readback sentinel -10011 returns failure Sentinel ─────────────

    @Test fun `writeLockDoors returns failure Sentinel when readback returns -10011`() = runTest {
        val entry = allowlist.find("doors_lock")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 2) } returns 0
        coEvery { gateway.readRaw(readbackSpec(entry)) } returns VehicleRawRead(0, -10011)

        val result = api.writeLockDoors()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.Sentinel)
    }

    // ── Window pos: happy path + helper failure ───────────────────────────────

    @Test fun `writeWindowDriver with 50 percent calls helper write with correct fid and returns success`() = runTest {
        val entry = allowlist.find("window_driver_pos")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 50) } returns 0
        // window_driver_pos has no readbackFid
        assertTrue(api.writeWindowDriver(50).isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 50) }
    }

    @Test fun `writeWindowDriver returns failure HelperUnreachable when helper write fails (validated entry)`() = runTest {
        val entry = allowlist.find("window_driver_pos")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 50) } returns null
        val result = api.writeWindowDriver(50)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.HelperUnreachable)
    }

    // ── writeSunroof: enum maps to correct action name ────────────────────────

    @Test fun `writeSunroof TILT calls helper with sunroof_tilt entry fid and val=3`() = runTest {
        val entry = allowlist.find("sunroof_tilt")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 3) } returns 0
        assertTrue(api.writeSunroof(SunroofMode.TILT).isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 3) }
    }

    // ── writeSunshade: open vs close ──────────────────────────────────────────

    @Test fun `writeSunshade open=true calls sunshade_open entry with val=1`() = runTest {
        val entry = allowlist.find("sunshade_open")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 1) } returns 0
        assertTrue(api.writeSunshade(open = true).isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 1) }
    }

    @Test fun `writeSunshade open=false calls sunshade_close entry with val=2`() = runTest {
        val entry = allowlist.find("sunshade_close")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 2) } returns 0
        assertTrue(api.writeSunshade(open = false).isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 2) }
    }

    // ── Composite dispatch: rear windows fan out to both rear % fids ──────────

    @Test fun `dispatch rear windows open writes both rear pos fids and returns success`() = runTest {
        val rl = allowlist.find("window_rear_left_pos")!!
        val rr = allowlist.find("window_rear_right_pos")!!
        coEvery { gateway.writeStatus(rl.dev, rl.writeFid, 100) } returns 0
        coEvery { gateway.writeStatus(rr.dev, rr.writeFid, 100) } returns 0

        val result = api.dispatch("后排车窗全开")
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(rl.dev, rl.writeFid, 100) }
        coVerify(exactly = 1) { gateway.writeStatus(rr.dev, rr.writeFid, 100) }
    }

    @Test fun `dispatch rear windows open returns failure but still attempts both when one fid fails`() = runTest {
        val rl = allowlist.find("window_rear_left_pos")!!
        val rr = allowlist.find("window_rear_right_pos")!!
        coEvery { gateway.writeStatus(rl.dev, rl.writeFid, 100) } returns 0
        coEvery { gateway.writeStatus(rr.dev, rr.writeFid, 100) } returns null // rear-right fails

        val result = api.dispatch("后排车窗全开")
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { gateway.writeStatus(rl.dev, rl.writeFid, 100) }
        coVerify(exactly = 1) { gateway.writeStatus(rr.dev, rr.writeFid, 100) }
    }

    @Test fun `dispatch unknown command returns failure AllowlistMiss without helper call`() = runTest {
        val result = api.dispatch("不存在的命令")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.AllowlistMiss)
        coVerify(exactly = 0) { gateway.writeStatus(any(), any(), any()) }
    }

    @Test fun `dispatch hazard on writes the dedicated switch without readback`() = runTest {
        coEvery { gateway.writeStatus(1004, 871366669, 1) } returns 0

        assertTrue(api.dispatch("双闪打开").isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(1004, 871366669, 1) }
        coVerify(exactly = 0) { gateway.readRaw(any()) }
    }

    @Test fun `headlight height accepts successful write when optional readback is sentinel`() = runTest {
        val productionAllowlist = WriteAllowlist.loadProduction { "{}" }
        val headlightApi: VehicleApi = VehicleApiImpl(
            parsReader,
            gateway,
            productionAllowlist,
            writeLogDao,
        )
        val entry = requireNotNull(productionAllowlist.find("headlight_height_level"))
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 5) } returns 0
        coEvery { gateway.readRaw(readbackSpec(entry)) } returns VehicleRawRead(0, -10011)

        assertTrue(headlightApi.dispatch("前照灯高度3档").isSuccess)
        coVerify(exactly = 1) { gateway.writeStatus(entry.dev, entry.writeFid, 5) }
    }

    // ── C.5: DAO receives audit entry on write ────────────────────────────────

    @Test fun `writeAcOn updates one durable audit row with outcome`() = runTest {
        val insertions = mutableListOf<VehicleWriteLogEntity>()
        coEvery { gateway.writeStatus(1000, 501219352, 0) } returns 0
        coEvery { writeLogDao.insert(capture(insertions)) } returns 42L
        coEvery { writeLogDao.complete(42L, any(), any(), any()) } returns 1
        api.writeAcOn()
        coVerify(exactly = 1) { writeLogDao.insert(any()) }
        val attempt = insertions.single()
        assertEquals("ac_on", attempt.actionName)
        assertEquals(1000, attempt.dev)
        assertEquals(0, attempt.requested)
        assertEquals("attempt", attempt.error)
        coVerify(exactly = 1) { writeLogDao.complete(42L, null, 0, null) }
    }

    private fun readbackSpec(entry: WriteEntry): VehicleReadSpec =
        VehicleReadSpec(
            transaction = 5,
            device = entry.dev,
            fid = requireNotNull(entry.readbackFid),
        )
}
