package com.byd.wjx.data.vehicle

import android.os.DeadObjectException
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import com.byd.wjx.helper.HelperBinderProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HelperClientBinderTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Minimal IBinder implementation that does NOT extend android.os.Binder
     * (transact is final there). All abstract methods stubbed; transact()
     * is implemented directly by subclasses / lambdas.
     */
    private abstract class FakeIBinder : IBinder {
        override fun isBinderAlive(): Boolean = true
        override fun pingBinder(): Boolean = true
        override fun getInterfaceDescriptor(): String = HelperBinderProtocol.DESCRIPTOR
        override fun queryLocalInterface(descriptor: String): IInterface? = null
        @Suppress("OVERRIDE_DEPRECATION")
        override fun dump(fd: java.io.FileDescriptor, args: Array<String>?) {}
        override fun dumpAsync(fd: java.io.FileDescriptor, args: Array<String>?) {}
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {}
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true
    }

    /**
     * Builds a live fake binder that writes [status] and optionally [value] to reply,
     * then resets the reply position to 0 (so the client reads from the start).
     */
    private fun liveFake(status: Int, value: Int = 0): IBinder = object : FakeIBinder() {
        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            reply!!.writeInt(status)
            reply.writeInt(value)
            reply.setDataPosition(0)
            return true
        }
    }

    /**
     * Binder whose transact() returns false (rejected / not handled), without
     * writing anything to reply.
     */
    private val rejectingFake: IBinder = object : FakeIBinder() {
        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = false
    }

    /**
     * Binder whose transact() throws DeadObjectException (simulates a stale binder).
     */
    private val deadFake: IBinder = object : FakeIBinder() {
        override fun isBinderAlive(): Boolean = false
        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
            throw DeadObjectException("test dead binder")
    }

    /** Creates a HelperClientImpl with [resolveBinder] overridden to return [binder]. */
    private fun clientWith(binder: IBinder?): HelperClientImpl = object : HelperClientImpl() {
        override fun resolveBinder(): IBinder? = binder
    }

    // ---------------------------------------------------------------------------
    // Test cases
    // ---------------------------------------------------------------------------

    /** TC-1: read returns value when status == 0 */
    @Test
    fun `read returns value when status is 0`() = runBlocking {
        val client = clientWith(liveFake(status = 0, value = 42))
        val result = client.read(dev = 1014, fid = 1246777400, tx = 5)
        assertEquals(42L, result)
    }

    /** TC-2: read returns null when status != 0 (readAccepted only on 0) */
    @Test
    fun `read returns null when status is non-zero`() = runBlocking {
        val clientStatus1 = clientWith(liveFake(status = 1, value = 42))
        assertNull(clientStatus1.read(dev = 1014, fid = 1246777400))

        val clientStatusNeg = clientWith(liveFake(status = -1, value = 42))
        assertNull(clientStatusNeg.read(dev = 1014, fid = 1246777400))
    }

    /** TC-3: write returns true when status >= 0 */
    @Test
    fun `write returns true when status is non-negative`() = runBlocking {
        val clientStatus0 = clientWith(liveFake(status = 0))
        assertTrue(clientStatus0.write(dev = 1000, fid = 501219357, value = 1))

        val clientStatus1 = clientWith(liveFake(status = 1))
        assertTrue(clientStatus1.write(dev = 1000, fid = 501219357, value = 1))
    }

    /** TC-4: write returns false when status < 0 */
    @Test
    fun `write returns false when status is negative`() = runBlocking {
        val client = clientWith(liveFake(status = -1))
        assertFalse(client.write(dev = 1000, fid = 501219357, value = 1))
    }

    /** TC-5: isAlive true when ping status == 0, false otherwise */
    @Test
    fun `isAlive reflects ping status`() = runBlocking {
        val aliveClient = clientWith(liveFake(status = 0))
        assertTrue(aliveClient.isAlive())

        val deadClient = clientWith(liveFake(status = -999))
        assertFalse(deadClient.isAlive())
    }

    @Test
    fun `protocolVersion requires status and exact version fields`() = runBlocking {
        assertEquals(
            HelperBinderProtocol.CURRENT_VERSION,
            clientWith(liveFake(0, HelperBinderProtocol.CURRENT_VERSION)).protocolVersion(),
        )
        val legacyBinder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                reply!!.writeInt(0)
                reply.setDataPosition(0)
                return true
            }
        }
        assertNull(clientWith(legacyBinder).protocolVersion())
    }

    @Test
    fun `sentry write operation preserves restricted op and assignment`() = runBlocking {
        val binder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(HelperBinderProtocol.TX_SENTRY_SHELL, code)
                data.setDataPosition(0)
                data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
                assertEquals("put_global", data.readString())
                assertEquals("sentrymode_enabled_switch=1", data.readString())
                reply!!.writeInt(0)
                reply.writeString("")
                reply.setDataPosition(0)
                return true
            }
        }

        assertEquals(
            "",
            clientWith(binder).sentryShell("put_global", "sentrymode_enabled_switch=1"),
        )
    }

    @Test
    fun `real sentry event uses guarded binder transaction and source`() = runBlocking {
        val binder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(HelperBinderProtocol.TX_PLAY_SENTRY_EVENT, code)
                data.setDataPosition(0)
                data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
                assertEquals("broadcast warn=2", data.readString())
                reply!!.writeInt(0)
                reply.writeInt(1)
                reply.setDataPosition(0)
                return true
            }
        }

        assertTrue(clientWith(binder).playSentryEvent("broadcast warn=2"))
    }

    @Test
    fun `system rotation operations use dedicated binder transactions`() = runBlocking {
        val calls = mutableListOf<Int>()
        val binder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                calls += code
                reply!!.writeInt(0)
                reply.writeInt(if (code == HelperBinderProtocol.TX_GET_SYSTEM_ROTATION_STATE) 2 else 0)
                reply.setDataPosition(0)
                return true
            }
        }
        val client = clientWith(binder)

        assertTrue(client.forceSystemLandscape())
        assertEquals(2, client.systemRotationState())
        assertTrue(client.restoreSystemRotation())
        assertEquals(
            listOf(
                HelperBinderProtocol.TX_FORCE_SYSTEM_LANDSCAPE,
                HelperBinderProtocol.TX_GET_SYSTEM_ROTATION_STATE,
                HelperBinderProtocol.TX_RESTORE_SYSTEM_ROTATION,
            ),
            calls,
        )
    }

    @Test
    fun `native status bar replacement uses dedicated boolean transaction`() = runBlocking {
        val values = mutableListOf<Int>()
        val binder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(HelperBinderProtocol.TX_SET_NATIVE_STATUS_BAR_REPLACED, code)
                data.setDataPosition(0)
                data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
                values += data.readInt()
                reply!!.writeInt(0)
                reply.writeInt(0)
                reply.setDataPosition(0)
                return true
            }
        }
        val client = clientWith(binder)

        assertTrue(client.setNativeStatusBarReplaced(true))
        assertTrue(client.setNativeStatusBarReplaced(false))
        assertEquals(listOf(1, 0), values)
    }

    @Test
    fun `lock sound playback state uses privileged helper transaction`() = runBlocking {
        val values = mutableListOf<Int>()
        val binder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(HelperBinderProtocol.TX_SET_LOCK_SOUND_PLAYBACK_STATE, code)
                data.setDataPosition(0)
                data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
                values += data.readInt()
                reply!!.writeInt(0)
                reply.writeInt(0)
                reply.setDataPosition(0)
                return true
            }
        }
        val client = clientWith(binder)

        assertTrue(client.setLockSoundPlaybackState(true))
        assertTrue(client.setLockSoundPlaybackState(false))
        assertEquals(listOf(1, 0), values)
    }

    /** TC-6: transact returning false yields null for read and false for write */
    @Test
    fun `transact returning false yields null for read and false for write`() = runBlocking {
        val client = clientWith(rejectingFake)
        assertNull(client.read(dev = 1014, fid = 1246777400))
        assertFalse(client.write(dev = 1000, fid = 501219357, value = 1))
    }

    /**
     * TC-7: DeadObjectException retry — resolveBinder() is called exactly twice:
     * once returns the dead binder (throws DeadObjectException), once returns a
     * live binder (status=0, value=7). The single read() call returns 7L.
     */
    @Test
    fun `DeadObjectException triggers retry and resolveBinder called twice`() = runBlocking {
        var resolveCount = 0
        val client = object : HelperClientImpl() {
            override fun resolveBinder(): IBinder? {
                resolveCount++
                return if (resolveCount == 1) deadFake else liveFake(status = 0, value = 7)
            }
        }

        val result = client.read(dev = 1014, fid = 1246777400, tx = 5)
        assertEquals(7L, result)
        assertEquals("resolveBinder should be called exactly twice", 2, resolveCount)
    }

    @Test
    fun `readBatch preserves request order and raw statuses`() = runBlocking {
        val captured = mutableListOf<VehicleReadSpec>()
        val binder = object : FakeIBinder() {
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(HelperBinderProtocol.TX_READ_BATCH, code)
                data.setDataPosition(0)
                data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
                val count = data.readInt()
                repeat(count) {
                    captured += VehicleReadSpec(
                        transaction = data.readInt(),
                        device = data.readInt(),
                        fid = data.readInt(),
                    )
                }
                reply!!.writeInt(0)
                reply.writeInt(count)
                reply.writeInt(0); reply.writeInt(42)
                reply.writeInt(-10011); reply.writeInt(0)
                reply.setDataPosition(0)
                return true
            }
        }
        val specs = listOf(
            VehicleReadSpec(5, 1014, 1145045032),
            VehicleReadSpec(7, 1014, 1246777400),
        )

        val result = clientWith(binder).readBatch(specs)

        assertEquals(specs, captured)
        assertNotNull(result)
        assertEquals(listOf(VehicleRawRead(0, 42), VehicleRawRead(-10011, 0)), result)
    }

    @Test
    fun `readBatch rejects oversized request before binder lookup`() = runBlocking {
        var resolved = false
        val client = object : HelperClientImpl() {
            override fun resolveBinder(): IBinder? {
                resolved = true
                return liveFake(0)
            }
        }
        val specs = List(HelperBinderProtocol.MAX_BATCH_READS + 1) {
            VehicleReadSpec(5, 1014, it)
        }

        assertNull(client.readBatch(specs))
        assertFalse(resolved)
    }
}
