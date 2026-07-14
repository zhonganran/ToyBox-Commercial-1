package com.byd.wjx.data.vehicle

import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.nativestack.ParsReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the fail-soft Result<Unit> contract introduced in Task C.6.
 * Covers AllowlistMiss, HelperUnreachable (IOException), Sentinel, and
 * Unsupported (non-validated helper-false) paths.
 */
class VehicleApiFailSoftTest {

    private val parsReader: ParsReader = mockk(relaxed = true)
    private val gateway: VehicleGateway = mockk()
    private val dao: VehicleWriteLogDao = mockk(relaxed = true)

    private val allowlist = WriteAllowlist(
        WriteAllowlist.LIVE_VALIDATED.associateBy { it.actionName.lowercase() }
    )

    private val api: VehicleApiImpl = VehicleApiImpl(parsReader, gateway, allowlist, dao)

    init {
        coEvery { dao.insert(any()) } returns 1L
        coEvery { dao.complete(any(), any(), any(), any()) } returns 1
    }

    // ── Test 1: allowlist miss returns Result failure AllowlistMiss ───────────

    @Test fun `allowlist miss returns Result failure AllowlistMiss`() = runTest {
        val result = api.dispatch("nonsense_action_42")
        assertTrue(result.isFailure)
        // CommandTranslator has no "nonsense_action_42" → no_translator_mapping path
        assertTrue(result.exceptionOrNull() is VehicleWriteError.AllowlistMiss)
        coVerify { dao.insert(any()) }
    }

    // ── Test 2: helper IOException wraps into HelperUnreachable ──────────────

    @Test fun `helper IOException wraps into HelperUnreachable`() = runTest {
        coEvery { gateway.writeStatus(any(), any(), any()) } throws java.io.IOException("daemon dead")

        val result = api.writeAcOn()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.HelperUnreachable)
    }

    // ── Test 3: readback sentinel -10011 returns Result failure Sentinel ──────

    @Test fun `readback sentinel returns Result failure Sentinel`() = runTest {
        // doors_lock has readbackFid; -10011 triggers Sentinel error
        val entry = allowlist.find("doors_lock")!!
        coEvery { gateway.writeStatus(entry.dev, entry.writeFid, 2) } returns 0
        coEvery { gateway.readRaw(readbackSpec(entry)) } returns VehicleRawRead(0, -10011)

        val result = api.writeLockDoors()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.Sentinel)
    }

    // ── Test 4: non-validated entry helper-false returns Unsupported ──────────
    //
    // dispatch() goes through CommandTranslator which has no Chinese mapping for
    // "nonvalidated_action_test". Using doWrite() directly (internal visibility)
    // to bypass the translator and exercise the Unsupported path cleanly.

    @Test fun `non-validated entry helper-false returns Unsupported`() = runTest {
        val customAllowlist = WriteAllowlist(mapOf(
            "nonvalidated_action_test" to WriteEntry(
                actionName = "nonvalidated_action_test",
                dev = 1001,
                writeFid = 999999999,
                readbackFid = null,
                valueMin = 1,
                valueMax = 1,
                category = "other",
                validated = false,
                source = "wjx-vehicle-actions",
            )
        ))
        val customApi = VehicleApiImpl(parsReader, gateway, customAllowlist, dao)

        coEvery { gateway.writeStatus(1001, 999999999, 1) } returns null

        val result = customApi.doWrite("nonvalidated_action_test", 1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.Unsupported)
    }

    // ── Test 5: helper RuntimeException also wraps into HelperUnreachable ─────

    @Test fun `helper RuntimeException wraps into HelperUnreachable`() = runTest {
        coEvery { gateway.writeStatus(any(), any(), any()) } throws RuntimeException("unexpected")

        val result = api.writeAcOff()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VehicleWriteError.HelperUnreachable)
    }

    // ── Test 6: non-validated sentinel returns Unsupported, DAO row uses "readback_sentinel" ──

    @Test fun `non-validated sentinel returns Unsupported not Sentinel`() = runTest {
        val customAllowlist = WriteAllowlist(mapOf(
            "trunk_open" to WriteEntry(
                actionName = "trunk_open",
                dev = 1001,
                writeFid = 999000001,
                readbackFid = 999000002,
                valueMin = 1,
                valueMax = 1,
                category = "trunk",
                validated = false,
                source = "wjx-vehicle-actions",
            )
        ))
        val customApi = VehicleApiImpl(parsReader, gateway, customAllowlist, dao)
        coEvery { gateway.writeStatus(any(), any(), any()) } returns 0
        coEvery { gateway.readRaw(any()) } returns VehicleRawRead(0, -10011)

        val result = customApi.doWrite("trunk_open", 1)
        assertTrue(result.isFailure)
        assertTrue(
            "Expected Unsupported for non-validated sentinel, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is VehicleWriteError.Unsupported
        )
        // The durable attempt row is finalized with the precise diagnostic reason.
        coVerify { dao.complete(1L, -10011, -1, "readback_sentinel") }
    }

    // ── Test 7: attempt row inserted before helper.write for durability ─────────

    @Test fun `attempt row inserted before helper write for durability`() = runTest {
        coEvery { gateway.writeStatus(any(), any(), any()) } returns 0
        api.writeAcOn()
        // Pending attempt row (status=-2) must exist
        coVerify { dao.insert(match { it.status == -2 && it.error == "attempt" }) }
        // The same row is finalized instead of inserting a duplicate outcome row.
        coVerify { dao.complete(1L, null, 0, null) }
    }

    private fun readbackSpec(entry: WriteEntry): VehicleReadSpec =
        VehicleReadSpec(
            transaction = 5,
            device = entry.dev,
            fid = requireNotNull(entry.readbackFid),
        )
}
