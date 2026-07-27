package com.byd.wjx.data.vehicle

import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HeadlightAvailability {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
    UNAVAILABLE,
}

data class HeadlightControlSnapshot(
    val availability: HeadlightAvailability = HeadlightAvailability.UNKNOWN,
    val mode: HeadlightMode? = null,
    val message: String = "正在检测车辆灯光服务",
)

data class HeadlightControlResult(
    val success: Boolean,
    val snapshot: HeadlightControlSnapshot,
)

/** Safe app-side facade over BYD's official headlight-mode API. */
@Singleton
class HeadlightController @Inject constructor(
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    suspend fun refresh(): HeadlightControlSnapshot = withContext(Dispatchers.IO) {
        if (!ensureHelper()) return@withContext helperUnavailable()

        val support = helperClient.queryHeadlightSupport()
            ?: return@withContext transportUnavailable("能力检测无返回")
        logReply("support", support)
        support.failureSnapshot("能力检测失败")?.let { return@withContext it }
        if (support.value != 1) {
            return@withContext HeadlightControlSnapshot(
                availability = HeadlightAvailability.UNSUPPORTED,
                message = "当前车型未开放前照灯模式控制",
            )
        }

        val current = helperClient.getHeadlightMode()
            ?: return@withContext transportUnavailable("模式读取无返回")
        logReply("get", current)
        current.failureSnapshot("模式读取失败")?.let { return@withContext it }
        val mode = HeadlightMode.fromServiceValue(current.value)
            ?: return@withContext HeadlightControlSnapshot(
                availability = HeadlightAvailability.UNAVAILABLE,
                message = "车辆返回了未知灯光模式：${current.value}",
            )
        HeadlightControlSnapshot(
            availability = HeadlightAvailability.SUPPORTED,
            mode = mode,
            message = "当前模式：${mode.label}",
        )
    }

    suspend fun setMode(mode: HeadlightMode): HeadlightControlResult = withContext(Dispatchers.IO) {
        if (!ensureHelper()) {
            return@withContext HeadlightControlResult(false, helperUnavailable())
        }

        val support = helperClient.queryHeadlightSupport()
            ?: return@withContext HeadlightControlResult(false, transportUnavailable("能力检测无返回"))
        logReply("support_before_set", support)
        support.failureSnapshot("能力检测失败")?.let {
            return@withContext HeadlightControlResult(false, it)
        }
        if (support.value != 1) {
            return@withContext HeadlightControlResult(
                false,
                HeadlightControlSnapshot(
                    availability = HeadlightAvailability.UNSUPPORTED,
                    message = "当前车型未开放前照灯模式控制",
                ),
            )
        }

        val reply = helperClient.setHeadlightMode(mode)
            ?: return@withContext HeadlightControlResult(false, transportUnavailable("设置无返回"))
        logReply("set_${mode.name.lowercase()}", reply)
        reply.failureSnapshot("设置失败")?.let {
            return@withContext HeadlightControlResult(false, it)
        }

        val actual = HeadlightMode.fromServiceValue(reply.value)
        val success = actual == mode
        val message = when {
            success -> "已切换为${mode.label}，车辆回读确认成功"
            actual != null -> "车辆未切换到${mode.label}，当前仍为${actual.label}"
            else -> "设置已发送，但车辆回读模式异常：${reply.value}"
        }
        HeadlightControlResult(
            success = success,
            snapshot = HeadlightControlSnapshot(
                availability = HeadlightAvailability.SUPPORTED,
                mode = actual,
                message = message,
            ),
        )
    }

    private suspend fun ensureHelper(): Boolean = runCatching {
        helperBootstrap.ensureCurrent()
    }.onFailure {
        WjxFileLogger.w(TAG, "helper bootstrap failed: ${it.message}", it)
    }.getOrDefault(false)

    private fun HelperHeadlightReply.failureSnapshot(prefix: String): HeadlightControlSnapshot? {
        if (!transportOk) {
            return transportUnavailable("$prefix：灯光服务链路不可用（transport=$transportStatus）")
        }
        if (!serviceOk) {
            val availability = if (serviceCode in UNSUPPORTED_SERVICE_CODES) {
                HeadlightAvailability.UNSUPPORTED
            } else {
                HeadlightAvailability.UNAVAILABLE
            }
            return HeadlightControlSnapshot(
                availability = availability,
                message = "$prefix：${serviceCodeMessage(serviceCode)}（code=$serviceCode）",
            )
        }
        return null
    }

    private fun helperUnavailable() = HeadlightControlSnapshot(
        availability = HeadlightAvailability.UNAVAILABLE,
        message = "Helper 未连接，暂时无法控制前照灯",
    )

    private fun transportUnavailable(message: String) = HeadlightControlSnapshot(
        availability = HeadlightAvailability.UNAVAILABLE,
        message = message,
    )

    private fun logReply(operation: String, reply: HelperHeadlightReply) {
        WjxFileLogger.i(
            TAG,
            "$operation transport=${reply.transportStatus} service=${reply.serviceCode} value=${reply.value}",
        )
    }

    companion object {
        private const val TAG = "HeadlightController"
        private val UNSUPPORTED_SERVICE_CODES = setOf(10_004, 11_000, 20_005)

        internal fun serviceCodeMessage(code: Int): String = when (code) {
            10_004, 20_005 -> "当前车型或车机版本不支持"
            11_000 -> "车辆未部署对应灯光服务"
            11_001 -> "灯光服务连接失败"
            11_002 -> "灯光服务版本不兼容"
            11_003, 20_003, 20_004 -> "车辆拒绝访问或权限不足"
            11_004 -> "灯光服务通信异常"
            20_001 -> "车辆拒绝了无效模式"
            20_002 -> "车辆未启用此功能"
            else -> "车辆灯光服务返回错误"
        }
    }
}
