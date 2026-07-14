package com.byd.wjx.data.automation

import com.byd.wjx.data.remote.diParsData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardVehicleDetectorTest {
    @Test
    fun `parked closed door then opening emits boarding event`() {
        val detector = BoardVehicleDetector()
        assertFalse(detector.onSample(diParsData(powerState = 0, gear = 1, doorFL = 0)))
        assertTrue(detector.onSample(diParsData(powerState = 0, gear = 1, doorFL = 1)))
        assertFalse(detector.onSample(diParsData(powerState = 0, gear = 1, doorFL = 1)))
    }

    @Test
    fun `opening door to exit after driving is not boarding`() {
        val detector = BoardVehicleDetector()
        detector.onSample(diParsData(powerState = 0, gear = 1, doorFL = 0))
        detector.onSample(diParsData(powerState = 2, gear = 4, speed = 30, doorFL = 0))
        assertFalse(detector.onSample(diParsData(powerState = 0, gear = 1, speed = 0, doorFL = 1)))
        assertFalse(detector.onSample(diParsData(powerState = 0, gear = 1, speed = 0, doorFL = 0)))
        assertTrue(detector.onSample(diParsData(powerState = 0, gear = 1, speed = 0, doorFL = 1)))
    }
}
