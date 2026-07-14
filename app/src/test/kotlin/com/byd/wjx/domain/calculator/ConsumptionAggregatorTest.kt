package com.byd.wjx.domain.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * v2.5.2: Aggregator no longer derives display from recentAvg. Caller passes
 * displayValue explicitly (computed by BigNumberCalculator). Trend logic
 * unchanged: ratio = shortAvg / recentAvg with +-10% bands and 30-sec debounce.
 */
class ConsumptionAggregatorTest {
    private lateinit var aggregator: ConsumptionAggregator

    @Before fun reset() {
        aggregator = ConsumptionAggregator()
    }

    @After fun teardown() {
        aggregator.reset()
    }

    @Test fun `null short, display passes through, trend NONE`() {
        aggregator.onSample(now = 0L, displayValue = 22.5, recentAvg = 18.0, shortAvg = null)
        val s = aggregator.state.value
        assertEquals(22.5, s.displayValue!!, 0.001)
        assertEquals(Trend.NONE, s.trend)
    }

    @Test fun `null displayValue renders prochirk`() {
        aggregator.onSample(now = 0L, displayValue = null, recentAvg = 18.0, shortAvg = 18.0)
        val s = aggregator.state.value
        assertNull(s.displayValue)
        assertEquals(Trend.NONE, s.trend)
    }

    @Test fun `recent zero, display passes, trend NONE`() {
        aggregator.onSample(now = 0L, displayValue = 22.5, recentAvg = 0.0, shortAvg = 18.0)
        val s = aggregator.state.value
        assertEquals(22.5, s.displayValue!!, 0.001)
        assertEquals(Trend.NONE, s.trend)
    }

    @Test fun `ratio inside band, trend FLAT after debounce`() {
        var now = 0L
        aggregator.onSample(now, displayValue = 22.5, recentAvg = 18.0, shortAvg = 19.0)
        now += 35_000
        aggregator.onSample(now, displayValue = 22.5, recentAvg = 18.0, shortAvg = 19.0)
        assertEquals(Trend.FLAT, aggregator.state.value.trend)
    }

    @Test fun `short below 0_90, DOWN after debounce`() {
        var now = 0L
        aggregator.onSample(now, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        now += 35_000
        aggregator.onSample(now, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        assertEquals(Trend.DOWN, aggregator.state.value.trend)
    }

    @Test fun `short above 1_10, UP after debounce`() {
        var now = 0L
        aggregator.onSample(now, displayValue = 22.5, recentAvg = 20.0, shortAvg = 23.0)
        now += 35_000
        aggregator.onSample(now, displayValue = 22.5, recentAvg = 20.0, shortAvg = 23.0)
        assertEquals(Trend.UP, aggregator.state.value.trend)
    }

    @Test fun `trend stays NONE before debounce expires`() {
        aggregator.onSample(now = 0, displayValue = 22.5, recentAvg = 20.0, shortAvg = 23.0)
        aggregator.onSample(now = 5_000, displayValue = 22.5, recentAvg = 20.0, shortAvg = 23.0)
        assertEquals(Trend.NONE, aggregator.state.value.trend)
    }

    @Test fun `flapping candidate resets debounce timer`() {
        aggregator.onSample(now = 0, displayValue = 22.5, recentAvg = 20.0, shortAvg = 23.0)
        aggregator.onSample(now = 20_000, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        aggregator.onSample(now = 35_000, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        assertEquals(Trend.NONE, aggregator.state.value.trend)
        aggregator.onSample(now = 70_000, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        assertEquals(Trend.DOWN, aggregator.state.value.trend)
    }

    @Test fun `displayValue independent of recentAvg`() {
        aggregator.onSample(now = 0, displayValue = 99.9, recentAvg = 18.0, shortAvg = 18.0)
        assertEquals(99.9, aggregator.state.value.displayValue!!, 0.001)
    }

    @Test fun `reset wipes everything`() {
        aggregator.onSample(now = 0, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        aggregator.onSample(now = 35_000, displayValue = 22.5, recentAvg = 20.0, shortAvg = 17.0)
        aggregator.reset()
        val s = aggregator.state.value
        assertNull(s.displayValue)
        assertEquals(Trend.NONE, s.trend)
    }
}
