package com.byd.wjx.activation

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActivationEntitlementStoreTest {
    private lateinit var preferences: SharedPreferences
    private var now = 1_800_000_000_000L
    private lateinit var store: ActivationEntitlementStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = ActivationEntitlementStore(preferences) { now }
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `trial snapshot requires stable vehicle and expires after seven days`() {
        val withoutVehicle = store.currentSnapshot("", emptyList())
        val activeTrial = store.currentSnapshot(VIN, listOf(VIN))
        now += ActivationSnapshot.TRIAL_DURATION_MS
        val expiredTrial = store.currentSnapshot(VIN, listOf(VIN))

        assertFalse(withoutVehicle.trialActive)
        assertTrue(withoutVehicle.trialExpired)
        assertTrue(activeTrial.trialActive)
        assertEquals(ActivationSnapshot.TRIAL_DURATION_MS, activeTrial.remainingTrialMillis)
        assertFalse(expiredTrial.trialActive)
        assertTrue(expiredTrial.trialExpired)
    }

    @Test
    fun `server status persists activation token and masked fallback code`() {
        val response = JSONObject()
            .put("activated", true)
            .put("activatedAt", now - 5_000L)
            .put("trialEndsAt", now + 60_000L)
            .put("deviceToken", "device-token")

        val applied = store.applyServerStatus(
            json = response,
            normalizedDevice = VIN,
            requestedActivationCode = "STB-1234-5678-9012",
        )
        val snapshot = store.currentSnapshot(VIN.lowercase(), listOf(VIN))

        assertTrue(applied.activated)
        assertEquals("STB1-****-****-9012", applied.boundActivationCode)
        assertEquals("device-token", store.currentDeviceToken())
        assertTrue(snapshot.activated)
        assertEquals(now - 5_000L, snapshot.activatedAt)
        assertFalse(store.shouldRefresh(ActivationManager.STATUS_REFRESH_INTERVAL_MS))
    }

    @Test
    fun `credential requirement clears stale token without forcing refresh loop`() {
        store.applyServerStatus(
            JSONObject().put("activated", false).put("deviceToken", "stale-token"),
            normalizedDevice = VIN,
        )
        store.applyServerStatus(
            JSONObject().put("activated", false).put("deviceCredentialRequired", true),
            normalizedDevice = VIN,
        )

        assertEquals("", store.currentDeviceToken())
        assertFalse(store.shouldRefresh(ActivationManager.STATUS_REFRESH_INTERVAL_MS))
    }

    companion object {
        private const val PREF_NAME = "activation_entitlement_store_test"
        private const val VIN = "L123456789ABCDEFG"
    }
}
