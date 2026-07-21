package com.byd.wjx.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WjxRuntimeWatchdogTest {

    @Test
    fun `script monitors helper app service and applies scoped keepalive policy`() {
        val script = WjxRuntimeWatchdog.buildScript("/sdcard/WJX")

        assertTrue(script.contains("pidof \"\$HELPER_NAME\""))
        assertTrue(script.contains("com[.]byd[.]wjx/[.]service[.]KeepAliveService"))
        assertTrue(script.contains("com.byd.wjx.service.ShellKeepAliveReceiver"))
        assertTrue(script.contains("RUN_ANY_IN_BACKGROUND allow"))
        assertTrue(script.contains("deviceidle whitelist +\"\$PACKAGE\""))
        assertTrue(script.contains("ssc_whitelist"))
        assertTrue(script.contains("sleep 5"))
    }

    @Test
    fun `script avoids global doze disable and self matching process kills`() {
        val script = WjxRuntimeWatchdog.buildScript("/sdcard/WJX")

        assertFalse(script.contains("deviceidle disable"))
        assertFalse(script.contains("pkill -9 -f wjx_helper"))
        assertTrue(script.contains("for PID in \$PIDS"))
    }
}
