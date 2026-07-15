package com.byd.wjx.data.charging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingTelemetrySemanticsTest {
    @Test
    fun `AC DC connector and official charging states activate telemetry`() {
        assertTrue(ChargingTelemetrySemantics.isChargingSession(2, 0))
        assertTrue(ChargingTelemetrySemantics.isChargingSession(3, 0))
        assertTrue(ChargingTelemetrySemantics.isChargingSession(null, 1))
        assertTrue(ChargingTelemetrySemantics.isChargingSession(null, 13))
    }

    @Test
    fun `disconnected and VTOL states do not activate charging telemetry`() {
        assertFalse(ChargingTelemetrySemantics.isChargingSession(1, 0))
        assertFalse(ChargingTelemetrySemantics.isChargingSession(5, 3))
        assertFalse(ChargingTelemetrySemantics.isChargingSession(null, 2))
    }
}
