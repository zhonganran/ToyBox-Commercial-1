package com.byd.wjx.data.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class SentryModeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    suspend fun setEnabled(enabled: Boolean): DispatchResult {
        val officialCode = callOfficial(enabled)
        if (officialCode != null && officialCodeAccepted(enabled, officialCode)) {
            WjxFileLogger.i(TAG, "official sentry mode enabled=$enabled code=$officialCode")
            return DispatchResult(true)
        }

        val fallback = runFallback(enabled)
        WjxFileLogger.i(
            TAG,
            "fallback sentry mode enabled=$enabled success=$fallback officialCode=${officialCode ?: "unavailable"}",
        )
        return if (fallback) {
            DispatchResult(true, "官方哨兵服务不可用，已使用系统设置兜底")
        } else {
            DispatchResult(false, sentryFailureReason(enabled, officialCode))
        }
    }

    private suspend fun callOfficial(enabled: Boolean): Int? = withTimeoutOrNull(OFFICIAL_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var connection: ServiceConnection

            fun finish(value: Int?) {
                if (!completed.compareAndSet(false, true)) return
                runCatching { context.unbindService(connection) }
                if (continuation.isActive) continuation.resume(value)
            }

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val code = service?.let { binder -> transactOfficial(binder, enabled) }
                    finish(code)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    finish(null)
                }

                override fun onBindingDied(name: ComponentName?) {
                    finish(null)
                }

                override fun onNullBinding(name: ComponentName?) {
                    finish(null)
                }
            }

            val bound = runCatching {
                context.bindService(
                    Intent(OFFICIAL_SERVICE_ACTION).setPackage(OFFICIAL_PACKAGE),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            }.getOrElse {
                WjxFileLogger.w(TAG, "bind official sentry service failed: ${it.message}", it)
                false
            }
            if (!bound) finish(null)
            continuation.invokeOnCancellation { finish(null) }
        }
    }

    private fun transactOfficial(binder: IBinder, enabled: Boolean): Int? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(OFFICIAL_INTERFACE)
            val transaction = if (enabled) TX_ENABLE else TX_DISABLE
            if (!binder.transact(transaction, data, reply, 0)) return null
            reply.readException()
            reply.readInt()
        } catch (error: Throwable) {
            WjxFileLogger.w(TAG, "official sentry transact failed: ${error.message}", error)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private suspend fun runFallback(enabled: Boolean): Boolean {
        if (!helperBootstrap.ensureCurrent()) return false
        return if (enabled) {
            helperClient.sentryShell("put_global", "sentrymode_enabled_switch=1") != null
        } else {
            val switchOff = helperClient.sentryShell("put_global", "sentrymode_enabled_switch=0") != null
            val enabledOff = helperClient.sentryShell("put_global", "sentrymode_enabled=0") != null
            val stateOff = helperClient.sentryShell("set_prop", "sys.byd.sentrymode_state=disable") != null
            switchOff && enabledOff && stateOff
        }
    }

    internal companion object {
        private const val TAG = "SentryModeController"
        private const val OFFICIAL_PACKAGE = "com.byd.sentrymode"
        private const val OFFICIAL_SERVICE_ACTION = "com.byd.sentrymode.main.SentryModeService"
        private const val OFFICIAL_INTERFACE = "com.byd.sentrymodelibrary.ISentryModeRequest"
        private const val TX_ENABLE = 5
        private const val TX_DISABLE = 6
        private const val OFFICIAL_TIMEOUT_MS = 3_000L

        fun officialCodeAccepted(enabled: Boolean, code: Int): Boolean =
            code == 1000 || (enabled && code == 1017)

        fun sentryFailureReason(enabled: Boolean, code: Int?): String = when (code) {
            1002 -> "当前车型不支持哨兵模式"
            1003 -> "请先在原车哨兵应用中同意隐私协议"
            1004 -> "哨兵模式未检测到可用存储"
            1005, 1006, 1007, 1008 -> "存储空间不足，无法${if (enabled) "开启" else "关闭"}哨兵模式"
            1012 -> "车辆电量过低，无法开启哨兵模式"
            else -> "哨兵模式控制失败${code?.let { "（code=$it）" }.orEmpty()}"
        }
    }
}
