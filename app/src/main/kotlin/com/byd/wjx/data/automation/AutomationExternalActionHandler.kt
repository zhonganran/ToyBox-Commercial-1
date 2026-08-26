package com.byd.wjx.data.automation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.delay
import org.json.JSONObject

internal class AutomationExternalActionHandler(
    private val context: Context,
) {
    suspend fun launchApp(action: ActionDef): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
        val packageName = payload?.optString("packageName")?.takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置应用包名")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return DispatchResult(false, "应用未安装：$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = tryStartActivity(intent, "app_launch:$packageName")
        if (result.success) maybeMinimize(payload)
        return result
    }

    suspend fun dial(action: ActionDef): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
        val phone = payload?.optString("phone")?.takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置电话号码")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = tryStartActivity(intent, "dial:$phone")
        if (result.success && payload.optBoolean("autoDial", false)) {
            delay(AUTO_DIAL_DELAY_MS)
            val press = Intent(BT_CALL_ACTION_DIAL_HANGUP).apply {
                setPackage(BT_CALL_PACKAGE)
                putExtra("keycode", BT_CALL_KEYCODE_DIAL)
            }
            try {
                context.sendBroadcast(press)
            } catch (error: Exception) {
                Log.w(TAG, "autoDial broadcast failed: ${error.message}")
                WjxFileLogger.w(TAG, "autoDial broadcast failed: ${error.message}")
            }
        }
        return result
    }

    fun navigate(action: ActionDef): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置参数")
        val latitude = payload.optDouble("lat", Double.NaN)
        val longitude = payload.optDouble("lon", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) {
            return DispatchResult(false, "未设置经纬度")
        }
        val uri = "androidamap://route/plan/?dlat=$latitude&dlon=$longitude&dev=0&t=0"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStartActivity(intent, "navigate:$latitude,$longitude")
    }

    suspend fun openUrl(action: ActionDef): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
        val url = payload?.optString("url")?.takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置链接")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = tryStartActivity(intent, "url:$url")
        if (result.success) maybeMinimize(payload)
        return result
    }

    fun goHome(): DispatchResult {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStartActivity(home, "go_home")
    }

    private suspend fun maybeMinimize(payload: JSONObject?) {
        if (payload?.optBoolean("minimize", false) != true) return
        delay(MINIMIZE_DELAY_MS)
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(home)
        } catch (error: Exception) {
            Log.w(TAG, "home failed: ${error.message}")
            WjxFileLogger.w(TAG, "home failed: ${error.message}")
        }
    }

    private fun tryStartActivity(intent: Intent, label: String): DispatchResult = try {
        context.startActivity(intent)
        DispatchResult(true)
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "$label: ${error.message}")
        WjxFileLogger.w(TAG, "$label: ${error.message}")
        DispatchResult(false, "没有可处理的应用：${error.message}")
    } catch (error: SecurityException) {
        Log.w(TAG, "$label (security): ${error.message}")
        WjxFileLogger.w(TAG, "$label (security): ${error.message}")
        DispatchResult(false, "没有权限：${error.message}")
    }

    companion object {
        private const val TAG = "ActionDispatcher"
        private const val MINIMIZE_DELAY_MS = 3_000L
        private const val AUTO_DIAL_DELAY_MS = 300L
        private const val BT_CALL_PACKAGE = "com.byd.bluetoothcall"
        private const val BT_CALL_ACTION_DIAL_HANGUP = "com.byd.btcall.action.DIAL_HANGUP"
        private const val BT_CALL_KEYCODE_DIAL = 313
    }
}
