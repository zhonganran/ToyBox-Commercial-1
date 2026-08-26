package com.byd.wjx.activation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.vehicle.VehicleInfo
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
    fun `status request uses VIN identity without device credentials`() {
        val capturedRequest = AtomicReference<Request>()
        val client = apiClient(capturedRequest) {
            """{"activated":false,"deviceCode":"$VIN_ONE"}"""
        }

        client.requestStatus(VIN_ONE.lowercase())

        val request = capturedRequest.get()
        assertEquals("/api/status", request.url.encodedPath)
        assertEquals(null, request.header("Authorization"))
        val payload = request.bodyJson()
        assertEquals(VIN_ONE, payload.getString("deviceCode"))
        assertEquals(VIN_ONE.lowercase(), payload.getString("rawDeviceCode"))
        assertEquals(false, payload.has("deviceCodes"))
        assertEquals(false, payload.has("rawDeviceCodes"))
        assertEquals(false, payload.has("credentialRecoveryProof"))
        assertEquals(false, payload.has("credentialVersion"))
        assertEquals(false, payload.has("deviceProof"))
        val profile = payload.getJSONObject("vehicleProfile")
        assertEquals(2025, profile.getInt("vinYear"))
        assertEquals("宋 PLUS DM-i", profile.getString("displayName"))
        assertEquals("SA3HEV", profile.getString("modelVariant"))
        assertEquals("SONG_PLUS", profile.getString("carSeries"))
        assertEquals("PHEV", profile.getString("vehicleType"))
        assertEquals("57.1.3.2501010.1", profile.getString("controllerVersion"))
        assertEquals(57, profile.getInt("controllerSeries"))
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
            """{"message":"车架号查询失败"}"""
        }

        val error = assertThrows(IllegalStateException::class.java) {
            client.requestStatus(VIN_ONE)
        }

        assertEquals("车架号查询失败", error.message)
    }

    @Test
    fun `http error hides deployment endpoint details`() {
        val client = apiClient(AtomicReference(), responseCode = 503) {
            """{"message":"https://wjx.689588.xyz failed at 203.0.113.8:443"}"""
        }

        val error = assertThrows(IllegalStateException::class.java) {
            client.requestStatus(VIN_ONE)
        }

        assertEquals(
            "${com.byd.wjx.security.EndpointPrivacy.HIDDEN_ENDPOINT} failed at " +
                com.byd.wjx.security.EndpointPrivacy.HIDDEN_ENDPOINT,
            error.message,
        )
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
            apiBaseUrl = "https://activation.example/api/",
            vehicleInfoProvider = {
                VehicleInfo(
                    vin = VIN_ONE,
                    vinYear = 2025,
                    displayName = "宋 PLUS DM-i",
                    modelVariant = "SA3HEV",
                    carSeries = "SONG_PLUS",
                    vehicleType = "PHEV",
                    controllerVersion = "57.1.3.2501010.1",
                    controllerSeries = 57,
                )
            },
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
