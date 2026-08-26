package com.byd.wjx.activation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationPaymentCheckoutPolicyTest {
    @Test
    fun `hosted checkout requires credential-free https url`() {
        assertTrue(
            ActivationPaymentCheckoutPolicy.resolve(
                order(mode = "hosted", payUrl = "https://pay.example/checkout"),
            ) is ActivationPaymentCheckoutTarget.HostedPage,
        )
        assertTrue(
            ActivationPaymentCheckoutPolicy.resolve(
                order(mode = "hosted", payUrl = "http://pay.example/checkout"),
            ) is ActivationPaymentCheckoutTarget.Rejected,
        )
        assertTrue(
            ActivationPaymentCheckoutPolicy.resolve(
                order(mode = "hosted", payUrl = "https://user@pay.example/checkout"),
            ) is ActivationPaymentCheckoutTarget.Rejected,
        )
    }

    @Test
    fun `payment schemes are rendered as qr only for matching provider`() {
        assertTrue(
            ActivationPaymentCheckoutPolicy.resolve(
                order(
                    paymentType = "wxpay",
                    mode = "qrcode",
                    payInfo = "weixin://wxpay/bizpayurl?pr=test",
                ),
            ) is ActivationPaymentCheckoutTarget.QrCode,
        )
        assertTrue(
            ActivationPaymentCheckoutPolicy.resolve(
                order(
                    paymentType = "alipay",
                    mode = "qrcode",
                    payInfo = "weixin://wxpay/bizpayurl?pr=test",
                ),
            ) is ActivationPaymentCheckoutTarget.Rejected,
        )
        assertTrue(
            ActivationPaymentCheckoutPolicy.resolve(
                order(mode = "qrcode", payInfo = "javascript:alert(1)"),
            ) is ActivationPaymentCheckoutTarget.Rejected,
        )
    }

    @Test
    fun `webview navigation blocks non https schemes`() {
        assertTrue(ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation("https://pay.example/next"))
        assertFalse(ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation("http://pay.example/next"))
        assertFalse(ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation("weixin://wxpay/order"))
        assertFalse(ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation("file:///sdcard/order.html"))
        assertTrue(
            ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation(
                "https://pay.example/start",
                "https://pay.example/next",
            ),
        )
        assertFalse(
            ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation(
                "https://pay.example/start",
                "https://other.example/next",
            ),
        )
    }

    private fun order(
        paymentType: String = "wxpay",
        mode: String,
        payInfo: String = "",
        payUrl: String = payInfo,
    ) = ActivationPaymentOrder(
        orderNo = "order-1",
        paymentType = paymentType,
        paymentName = "支付",
        payType = "qrcode",
        checkoutMode = mode,
        payInfo = payInfo,
        payUrl = payUrl,
        amount = "15.90",
        pollToken = "poll-1",
        expiresAt = 1L,
    )
}
