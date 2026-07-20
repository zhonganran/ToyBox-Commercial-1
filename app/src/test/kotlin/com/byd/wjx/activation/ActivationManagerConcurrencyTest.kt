package com.byd.wjx.activation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActivationManagerConcurrencyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activationPreferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        activationPreferences().edit().clear().commit()
    }

    @Test
    fun `concurrent forced refreshes share one server request`() = runBlocking {
        val vin = "L123456789ABCDEFG"
        val requestCount = AtomicInteger(0)
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount.incrementAndGet()
                requestStarted.countDown()
                check(releaseRequest.await(5L, TimeUnit.SECONDS))
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"activated":false,"deviceCode":"$vin"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            }
            .build()
        val helperBootstrap = mockk<HelperBootstrap> {
            coEvery { ensureCurrent() } returns true
        }
        val helperClient = mockk<HelperClient> {
            coEvery { readVehicleVinRaw() } returns vin
        }
        val manager = ActivationManager(context, client, helperBootstrap, helperClient)

        val refreshes = List(8) {
            async(Dispatchers.Default) { manager.refreshIfNeeded(forceAfterMs = 0L) }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { requestStarted.await(5L, TimeUnit.SECONDS) })
            delay(100L)
        } finally {
            releaseRequest.countDown()
        }
        refreshes.awaitAll()

        assertEquals(1, requestCount.get())
    }

    private fun activationPreferences() =
        context.getSharedPreferences("wjx_activation", Context.MODE_PRIVATE)
}
