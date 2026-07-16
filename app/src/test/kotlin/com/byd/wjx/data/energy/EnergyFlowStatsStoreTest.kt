package com.byd.wjx.data.energy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.remote.diParsData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class EnergyFlowStatsStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(EnergyFlowStatsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(EnergyFlowStatsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `flush restores session and lifetime totals after process restart`() {
        val sample = diParsData(power = -36.0, energyState = 5, chargeGunState = 1)
        val store = EnergyFlowStatsStore(context)
        store.onSample(100_000L, sample, sessionStartedAt = 100_000L)
        store.onSample(101_000L, sample, sessionStartedAt = 100_000L)
        store.flush(101_000L)

        val restored = EnergyFlowStatsStore(context).state.value

        assertEquals(100_000L, restored.sessionStartedAt)
        assertEquals(0.01, restored.session.evFwdRegenKwh, 0.000001)
        assertEquals(0.01, restored.lifetime.evFwdRegenKwh, 0.000001)
    }
}
