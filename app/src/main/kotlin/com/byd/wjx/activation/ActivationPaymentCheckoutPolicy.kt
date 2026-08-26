package com.byd.wjx.activation

import java.net.URI
import java.util.Locale

internal sealed interface ActivationPaymentCheckoutTarget {
    data class HostedPage(val url: String) : ActivationPaymentCheckoutTarget
    data class QrCode(val payload: String) : ActivationPaymentCheckoutTarget
    data class Rejected(val reason: String) : ActivationPaymentCheckoutTarget
}

internal object ActivationPaymentCheckoutPolicy {
    const val MODE_HOSTED = "hosted"
    const val MODE_QR_CODE = "qrcode"

    fun resolve(order: ActivationPaymentOrder): ActivationPaymentCheckoutTarget {
        val mode = order.checkoutMode.trim().lowercase(Locale.US).ifBlank {
            if (isAllowedHostedNavigation(order.payUrl)) MODE_HOSTED else MODE_QR_CODE
        }
        return when (mode) {
            MODE_HOSTED -> resolveHostedPage(order.payUrl)
            MODE_QR_CODE -> resolveQrCode(order.paymentType, order.payInfo.ifBlank { order.payUrl })
            else -> ActivationPaymentCheckoutTarget.Rejected("支付服务器返回了未知的收银台类型")
        }
    }

    fun isAllowedHostedNavigation(rawUrl: String): Boolean {
        val uri = parseUri(rawUrl) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }

    fun isAllowedHostedNavigation(initialUrl: String, candidateUrl: String): Boolean {
        if (!isAllowedHostedNavigation(initialUrl) || !isAllowedHostedNavigation(candidateUrl)) return false
        val initial = parseUri(initialUrl) ?: return false
        val candidate = parseUri(candidateUrl) ?: return false
        return initial.host.equals(candidate.host, ignoreCase = true) &&
            effectivePort(initial) == effectivePort(candidate)
    }

    private fun resolveHostedPage(rawUrl: String): ActivationPaymentCheckoutTarget =
        if (isAllowedHostedNavigation(rawUrl)) {
            ActivationPaymentCheckoutTarget.HostedPage(rawUrl.trim())
        } else {
            ActivationPaymentCheckoutTarget.Rejected("支付页面不是安全的 HTTPS 地址")
        }

    private fun resolveQrCode(paymentType: String, rawPayload: String): ActivationPaymentCheckoutTarget {
        val payload = rawPayload.trim()
        if (payload.isBlank() || payload.length > MAX_QR_PAYLOAD_LENGTH || payload.any(Char::isISOControl)) {
            return ActivationPaymentCheckoutTarget.Rejected("支付二维码内容无效")
        }
        val scheme = parseUri(payload)?.scheme?.lowercase(Locale.US)
        if (scheme == null) return ActivationPaymentCheckoutTarget.QrCode(payload)
        if (scheme == "https") return ActivationPaymentCheckoutTarget.QrCode(payload)
        val allowedSchemes = when (paymentType.trim().lowercase(Locale.US)) {
            "alipay" -> setOf("alipay", "alipays")
            "wxpay" -> setOf("weixin")
            else -> emptySet()
        }
        return if (scheme in allowedSchemes) {
            ActivationPaymentCheckoutTarget.QrCode(payload)
        } else {
            ActivationPaymentCheckoutTarget.Rejected("支付二维码协议不受支持")
        }
    }

    private fun parseUri(value: String): URI? =
        runCatching { URI(value.trim()) }.getOrNull()

    private fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else 443

    private const val MAX_QR_PAYLOAD_LENGTH = 4_096
}
