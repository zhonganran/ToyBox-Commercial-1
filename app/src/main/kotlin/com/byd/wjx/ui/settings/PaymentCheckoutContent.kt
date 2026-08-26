package com.byd.wjx.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.byd.wjx.activation.ActivationPaymentCheckoutPolicy
import com.byd.wjx.activation.ActivationPaymentCheckoutTarget
import com.byd.wjx.activation.ActivationPaymentOrder
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
internal fun PaymentCheckoutContent(
    order: ActivationPaymentOrder,
    modifier: Modifier = Modifier,
) {
    when (val target = ActivationPaymentCheckoutPolicy.resolve(order)) {
        is ActivationPaymentCheckoutTarget.HostedPage ->
            PaymentCheckoutWebView(target.url, modifier)
        is ActivationPaymentCheckoutTarget.QrCode ->
            PaymentQrCode(target.payload, modifier)
        is ActivationPaymentCheckoutTarget.Rejected ->
            PaymentCheckoutError(target.reason, modifier)
    }
}

@Composable
private fun PaymentQrCode(payload: String, modifier: Modifier) {
    val bitmap = remember(payload) { createPaymentQrBitmap(payload) }
    Box(
        modifier = modifier.background(Color.White).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "支付二维码",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    "请使用手机扫码支付",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Text("支付二维码生成失败", color = SocRed, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PaymentCheckoutError(message: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(Color.White).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = SocRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaymentCheckoutWebView(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val webView = remember(url) {
        WebView(context).apply {
            configurePaymentWebView(this)
            webViewClient = PaymentWebViewClient(url)
            setBackgroundColor(android.graphics.Color.WHITE)
            loadUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            webView.destroy()
            CookieManager.getInstance().removeSessionCookies(null)
        }
    }
    AndroidView(
        factory = { webView },
        modifier = modifier,
    )
}

@SuppressLint("SetJavaScriptEnabled")
internal fun configurePaymentWebView(webView: WebView) {
    webView.settings.apply {
        // The hosted provider page uses JavaScript and DOM storage to render and refresh its QR code.
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = true
        safeBrowsingEnabled = true
        cacheMode = WebSettings.LOAD_NO_CACHE
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = false
        displayZoomControls = false
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    webView.setDownloadListener { _, _, _, _, _ -> Unit }
}

@Suppress("OVERRIDE_DEPRECATION")
internal class PaymentWebViewClient(
    private val initialUrl: String,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation(initialUrl, request.url.toString())

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        !ActivationPaymentCheckoutPolicy.isAllowedHostedNavigation(initialUrl, url)

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
    }
}

private fun createPaymentQrBitmap(payload: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        QR_SIZE,
        QR_SIZE,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
        ),
    )
    val pixels = IntArray(QR_SIZE * QR_SIZE)
    for (y in 0 until QR_SIZE) {
        for (x in 0 until QR_SIZE) {
            pixels[y * QR_SIZE + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    Bitmap.createBitmap(pixels, QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
}.getOrNull()

private const val QR_SIZE = 720
