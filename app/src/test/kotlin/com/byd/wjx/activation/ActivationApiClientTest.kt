package com.byd.wjx.activation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActivationApiClientTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `status request includes only the primary identity and signed device proof`() {
        val capturedRequest = AtomicReference<Request>()
        val client = apiClient(capturedRequest) {
            """{"activated":false,"deviceCode":"$VIN_ONE"}"""
        }

        client.requestStatus(VIN_ONE.lowercase())

        val request = capturedRequest.get()
        assertEquals("/api/status", request.url.encodedPath)
        assertEquals("Bearer device-token", request.header("Authorization"))
        val payload = request.bodyJson()
        assertEquals(VIN_ONE, payload.getString("deviceCode"))
        assertEquals(VIN_ONE.lowercase(), payload.getString("rawDeviceCode"))
        assertEquals(false, payload.has("deviceCodes"))
        assertEquals(false, payload.has("rawDeviceCodes"))
        assertEquals(false, payload.has("credentialRecoveryProof"))
        assertEquals("signed-proof", payload.getJSONObject("deviceProof").getString("signature"))
    }

    @Test
    fun `payment responses are parsed behind the api boundary`() {
        val client = apiClient(AtomicReference()) { request ->
            when (request.url.encodedPath) {
                "/api/payment/create" ->
                    """{"orderNo":"order-1","paymentType":"alipay","payType":"url","checkoutMode":"hosted","payInfo":"https://pay.example/order-1","pollToken":"poll-1","expiresAt":"2027-01-02T03:04:05Z"}"""
                else -> """{"status":"expired","paid":false}"""
            }
        }

        val order = client.createPayment(VIN_ONE, "alipay")
        val status = client.checkPayment(VIN_ONE, order)

        assertEquals("order-1", order.orderNo)
        assertEquals("hosted", order.checkoutMode)
        assertEquals("https://pay.example/order-1", order.payUrl)
        assertEquals("15.90", order.amount)
        assertEquals(1_798_859_045_000L, order.expiresAt)
        assertEquals("expired", status.status)
        assertEquals("支付二维码已过期，请重新创建订单", status.message)
    }

    @Test
    fun `http error exposes server message`() {
        val client = apiClient(AtomicReference(), responseCode = 403) {
            """{"message":"设备凭证无效"}"""
        }

        val error = assertThrows(IllegalStateException::class.java) {
            client.requestStatus(VIN_ONE)
        }

        assertEquals("设备凭证无效", error.message)
    }

    private fun apiClient(
        capturedRequest: AtomicReference<Request>,
        responseCode: Int = 200,
        responseBody: (Request) -> String,
    ): ActivationApiClient {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedRequest.set(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message(if (responseCode in 200..299) "OK" else "Error")
                    .body(responseBody(request).toResponseBody(JSON))
                    .build()
            }
            .build()
        return ActivationApiClient(
            context = context,
            okHttpClient = okHttpClient,
            deviceTokenProvider = { "device-token" },
            deviceProofProvider = { JSONObject().put("signature", "signed-proof") },
            apiBaseUrl = "https://activation.example/api/",
        )
    }

    private fun Request.bodyJson(): JSONObject {
        val buffer = Buffer()
        assertNotNull(body)
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    companion object {
        private const val VIN_ONE = "L123456789ABCDEFG"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
