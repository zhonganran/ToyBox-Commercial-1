package com.byd.wjx.data.autoservice

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbOnDeviceClientTest {

    private fun newClient(fake: AdbProtocol): AdbOnDeviceClientImpl {
        return newClient { fake }
    }

    private fun newClient(factory: () -> AdbProtocol): AdbOnDeviceClientImpl {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = AdbKeyStore(ctx)
        val client = AdbOnDeviceClientImpl(ctx, keyStore)
        client.protocolFactory = factory
        return client
    }

    private class FakeProtocol(
        var connectResult: Boolean = true,
        var connectThrows: Throwable? = null,
        var connectDelayMs: Long = 0,
        var execResponses: Map<String, String?> = emptyMap(),
        var disconnectOnNullExec: Boolean = false,
        var execResponder: ((String) -> String?)? = null,
    ) : AdbProtocol {
        var connectCalls = 0
        var disconnectCalls = 0
        var execCalls = mutableListOf<String>()
        private var connected = false

        override fun connect(): Boolean {
            connectCalls++
            if (connectDelayMs > 0) Thread.sleep(connectDelayMs)
            connectThrows?.let { throw it }
            connected = connectResult
            return connectResult
        }
        override fun exec(cmd: String): String? {
            execCalls += cmd
            val result = if (execResponder != null) {
                execResponder?.invoke(cmd)
            } else if (execResponses.containsKey(cmd)) {
                execResponses[cmd]
            } else {
                ""
            }
            if (result == null && disconnectOnNullExec) connected = false
            return result
        }
        override fun isConnected(): Boolean = connected
        override fun disconnect() {
            disconnectCalls++
            connected = false
        }

        fun forceDisconnect() {
            connected = false
        }
    }

    @Test
    fun `connect protocol returns true returns success`() = runTest {
        val fake = FakeProtocol(connectResult = true)
        val client = newClient(fake)

        val result = client.connect()

        assertTrue(result.isSuccess)
        assertEquals(1, fake.connectCalls)
    }

    @Test
    fun `concurrent connect calls share one adb handshake`() = runTest {
        val fake = FakeProtocol(connectResult = true, connectDelayMs = 100)
        val client = newClient(fake)

        val results = List(5) {
            async { client.connect() }
        }.awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, fake.connectCalls)
    }

    @Test
    fun `connect protocol returns false returns failure`() = runTest {
        val fake = FakeProtocol(connectResult = false)
        val client = newClient(fake)

        val result = client.connect()

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `connect protocol throws returns failure does not crash`() = runTest {
        val fake = FakeProtocol(connectThrows = RuntimeException("kaboom"))
        val client = newClient(fake)

        val result = client.connect()

        assertTrue(result.isFailure)
        assertEquals("kaboom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `shutdown idempotent does not crash when never connected`() = runTest {
        val client = newClient(FakeProtocol())
        // Never connected.
        client.shutdown()
        client.shutdown()
        // No exception → ok.
    }

    @Test
    fun `shutdown after connect disconnects protocol`() = runTest {
        val fake = FakeProtocol(connectResult = true)
        val client = newClient(fake)

        client.connect()
        client.shutdown()

        assertEquals(1, fake.disconnectCalls)
    }

    @Test
    fun `spawnHelper builds app_process classpath command without dex push`() = runTest {
        val fake = FakeProtocol(connectResult = true)
        val client = newClient(fake)

        client.connect()
        fake.execCalls.clear()
        val result = client.spawnHelper()

        assertTrue("spawnHelper should return true on successful dispatch", result)
        assertEquals("pkill plus spawn exec calls should be made", 2, fake.execCalls.size)

        val killCmd = fake.execCalls.first()
        assertTrue("must kill stale helper before spawn", killCmd.contains("pkill") && killCmd.contains("wjx_helper"))

        val cmd = fake.execCalls.last()
        assertTrue("must contain setsid", cmd.contains("setsid"))
        assertTrue("must contain CLASSPATH=", cmd.contains("CLASSPATH="))
        assertTrue("must contain app_process", cmd.contains("app_process"))
        assertTrue("must contain --nice-name=wjx_helper", cmd.contains("--nice-name=wjx_helper"))
        assertTrue("must contain HelperDaemon class", cmd.contains("com.byd.wjx.helper.HelperDaemon"))
        assertTrue("must contain caller uid", cmd.contains(android.os.Process.myUid().toString()))
        assertTrue("must redirect helper output to unified WJX log", cmd.contains("wjx.log"))
        assertFalse("must not write separate helper log", cmd.contains("wjx_helper.log"))

        // SIGHUP-race fix: the spawning shell must stay alive (poll-loop on the
        // service registry) until the detached app_process has booted and called
        // addService. Otherwise the on-device ADB closes the shell stream the
        // instant the `&` backgrounds the job, adbd SIGHUPs the still-booting JVM,
        // and the daemon dies before its first println (empty log, no registration).
        assertTrue(
            "must keep spawning shell alive until daemon registers (poll-loop)",
            cmd.contains("service list") && cmd.contains("sleep")
        )

        // Prove no dex push artifacts
        assertFalse("must not contain base64", cmd.contains("base64"))
        assertFalse("must not contain echo", cmd.contains("echo"))
        assertFalse("must not contain helper.dex", cmd.contains("helper.dex"))
    }

    @Test
    fun `spawnHelper reconnects a retained disconnected protocol`() = runTest {
        val fake = FakeProtocol(connectResult = true)
        val client = newClient(fake)

        assertTrue(client.connect().isSuccess)
        fake.forceDisconnect()
        fake.execCalls.clear()

        assertTrue(client.spawnHelper())
        assertEquals(2, fake.connectCalls)
        assertTrue(fake.execCalls.any { it.contains("pkill") })
        assertTrue(fake.execCalls.any { it.contains("app_process") })
    }

    @Test
    fun `spawnHelper discards a broken protocol and retries on a fresh connection`() = runTest {
        val first = FakeProtocol(connectResult = true)
        val second = FakeProtocol(connectResult = true)
        val protocols = ArrayDeque<AdbProtocol>().apply {
            add(first)
            add(second)
        }
        val client = newClient { protocols.removeFirst() }

        assertTrue(client.connect().isSuccess)
        first.execResponses = mapOf(
            "pkill -9 -f wjx_helper 2>/dev/null; sleep 0.5" to null,
        )
        first.disconnectOnNullExec = true

        assertTrue(client.spawnHelper())
        assertEquals(1, first.disconnectCalls)
        assertEquals(1, second.connectCalls)
        assertTrue(second.execCalls.any { it.contains("pkill") })
        assertTrue(second.execCalls.any { it.contains("app_process") })
    }

    @Test
    fun `spawnHelper does not report success when spawn dispatch returns null`() = runTest {
        val fake = FakeProtocol(connectResult = true, disconnectOnNullExec = true)
        val client = newClient(fake)

        assertTrue(client.connect().isSuccess)
        val spawnCommandMarker = "sh /data/local/tmp/wjx_helper_start.sh"
        fake.execResponder = { command ->
            if (command.contains(spawnCommandMarker)) null else ""
        }

        assertFalse(client.spawnHelper())
    }
}
