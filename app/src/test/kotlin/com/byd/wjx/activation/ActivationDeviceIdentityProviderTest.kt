package com.byd.wjx.activation

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActivationDeviceIdentityProviderTest {
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `reads deduplicates and persists VIN candidates`() = runBlocking {
        val helperBootstrap = readyBootstrap()
        val helperClient = mockk<HelperClient> {
            coEvery { readVehicleVinRaw() } returns
                "primary=$VIN_ONE backup=$VIN_TWO duplicate=${VIN_ONE.lowercase()}"
        }
        val provider = ActivationDeviceIdentityProvider(preferences, helperBootstrap, helperClient)

        val candidates = provider.refreshCandidates()

        assertEquals(listOf(VIN_ONE, VIN_TWO), candidates)
        assertEquals(VIN_ONE, provider.storedVinCode())
        assertEquals(listOf(VIN_ONE, VIN_TWO), provider.storedAliases())
    }

    @Test
    fun `reuses successful short cache unless hardware refresh is forced`() = runBlocking {
        var now = 100_000L
        val helperBootstrap = readyBootstrap()
        val helperClient = mockk<HelperClient> {
            coEvery { readVehicleVinRaw() } returnsMany listOf(VIN_ONE, VIN_TWO)
        }
        val provider = ActivationDeviceIdentityProvider(
            preferences = preferences,
            helperBootstrap = helperBootstrap,
            helperClient = helperClient,
            currentTimeMillis = { now },
        )

        assertEquals(listOf(VIN_ONE), provider.refreshCandidates())
        now += 59_000L
        assertEquals(listOf(VIN_ONE), provider.refreshCandidates())
        assertEquals(listOf(VIN_TWO), provider.refreshCandidates(forceHardware = true))

        coVerify(exactly = 2) { helperClient.readVehicleVinRaw() }
    }

    private fun readyBootstrap() = mockk<HelperBootstrap> {
        coEvery { ensureCurrent() } returns true
    }

    companion object {
        private const val PREF_NAME = "activation_device_identity_test"
        private const val VIN_ONE = "L123456789ABCDEFG"
        private const val VIN_TWO = "L987654321HJKLMNP"
    }
}
