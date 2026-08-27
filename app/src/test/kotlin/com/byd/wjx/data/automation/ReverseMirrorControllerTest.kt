package com.byd.wjx.data.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.MirrorPosition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ReverseMirrorControllerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ReverseMirrorSnapshotStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `tilt captures current position and exit restores it`() = runTest {
        val helper = mockk<HelperClient>()
        val original = MirrorPosition(20f, 40f, 80f, 60f)
        val target = MirrorPosition(15f, 50f, 86f, 70f)
        coEvery { helper.readMirrorPosition() } returnsMany listOf(
            original,
            target,
            target,
            original,
        )
        coEvery { helper.applyMirrorPosition(any()) } returns true
        val store = ReverseMirrorSnapshotStore(context)
        val controller = ReverseMirrorController(
            helperClient = helper,
            snapshotStore = store,
            vehicleStateRepository = mockk<VehicleStateRepository>(relaxed = true),
        )
        val payload = MirrorTiltRequest.payload(
            MirrorTiltRequest(true, true, -5f, 10f, 6f, 10f),
        )

        val tilt = controller.tilt(payload, diParsData(gear = 2, speed = 0), preview = false)
        val restore = controller.restore(diParsData(gear = 4, speed = 0))

        assertTrue(tilt.success)
        assertTrue(restore.success)
        assertNull(store.read())
        coVerify(exactly = 1) { helper.applyMirrorPosition(target) }
        coVerify(exactly = 1) { helper.applyMirrorPosition(original) }
    }

    @Test
    fun `manual adjustment after tilt is not overwritten during restore`() = runTest {
        val helper = mockk<HelperClient>()
        val original = MirrorPosition(20f, 40f, 80f, 60f)
        val target = MirrorPosition(15f, 50f, 86f, 70f)
        val manuallyAdjusted = MirrorPosition(12f, 50f, 86f, 70f)
        coEvery { helper.readMirrorPosition() } returnsMany listOf(
            original,
            target,
            manuallyAdjusted,
        )
        coEvery { helper.applyMirrorPosition(any()) } returns true
        val store = ReverseMirrorSnapshotStore(context)
        val controller = ReverseMirrorController(
            helperClient = helper,
            snapshotStore = store,
            vehicleStateRepository = mockk<VehicleStateRepository>(relaxed = true),
        )
        val payload = MirrorTiltRequest.payload(
            MirrorTiltRequest(true, true, -5f, 10f, 6f, 10f),
        )

        assertTrue(controller.tilt(payload, diParsData(gear = 2, speed = 0), false).success)
        val restore = controller.restore(diParsData(gear = 4, speed = 0))

        assertTrue(restore.success)
        assertTrue(restore.reason?.contains("手动调节") == true)
        assertNull(store.read())
        coVerify(exactly = 1) { helper.applyMirrorPosition(any()) }
    }

    @Test
    fun `restore accepts a recent in-flight position and returns to the saved original`() = runTest {
        val helper = mockk<HelperClient>()
        val original = MirrorPosition(20f, 40f, 80f, 60f)
        val target = MirrorPosition(15f, 50f, 86f, 70f)
        val moving = MirrorPosition(18f, 44f, 82f, 64f)
        coEvery { helper.readMirrorPosition() } returnsMany listOf(moving, original)
        coEvery { helper.applyMirrorPosition(any()) } returns true
        val store = ReverseMirrorSnapshotStore(context)
        assertTrue(
            store.save(
                ReverseMirrorSnapshot(
                    original = original,
                    target = target,
                    leftEnabled = true,
                    rightEnabled = true,
                    savedAtMs = System.currentTimeMillis(),
                ),
            ),
        )
        val controller = ReverseMirrorController(
            helperClient = helper,
            snapshotStore = store,
            vehicleStateRepository = mockk<VehicleStateRepository>(relaxed = true),
        )

        val restore = controller.restore(diParsData(gear = 4, speed = 0))

        assertTrue(restore.success)
        assertNull(store.read())
        coVerify(exactly = 1) { helper.applyMirrorPosition(original) }
    }

    @Test
    fun `tilt never moves mirror when durable snapshot cannot be established`() = runTest {
        val helper = mockk<HelperClient>()
        coEvery { helper.readMirrorPosition() } returns MirrorPosition(20f, 40f, 80f, 60f)
        val store = mockk<ReverseMirrorSnapshotStore>()
        coEvery { helper.applyMirrorPosition(any()) } returns true
        io.mockk.every { store.read() } returns null
        io.mockk.every { store.save(any()) } returns false
        val controller = ReverseMirrorController(
            helperClient = helper,
            snapshotStore = store,
            vehicleStateRepository = mockk<VehicleStateRepository>(relaxed = true),
        )

        val result = controller.tilt(
            MirrorTiltRequest.payload(MirrorTiltRequest(true, false, 0f, 10f, 0f, 10f)),
            diParsData(gear = 2, speed = 0),
            preview = false,
        )

        assertFalse(result.success)
        coVerify(exactly = 0) { helper.applyMirrorPosition(any()) }
    }
}
