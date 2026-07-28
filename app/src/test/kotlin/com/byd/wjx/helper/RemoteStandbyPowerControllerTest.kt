package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteStandbyPowerControllerTest {
    @Test
    fun `empty or zero acc animation status means vehicle powered`() {
        assertEquals(RemoteAccState.ON, parseRemoteAccState(""))
        assertEquals(RemoteAccState.ON, parseRemoteAccState(" 0 "))
    }

    @Test
    fun `nonzero acc animation status means vehicle parked`() {
        assertEquals(RemoteAccState.OFF, parseRemoteAccState("1"))
        assertEquals(RemoteAccState.OFF, parseRemoteAccState("shutdown"))
    }

    @Test
    fun `missing acc animation read remains unknown`() {
        assertEquals(RemoteAccState.UNKNOWN, parseRemoteAccState(null))
    }
}
