package com.byd.wjx.data.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.byd.wjx.audio.AutoParkingStatus
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.AutoDoorController
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class DispatchResult(val success: Boolean, val reason: String? = null)

@Singleton
class ActionDispatcher @Inject constructor(
    private val vehicleApi: VehicleApi,
    private val autoDoorController: AutoDoorController,
    private val httpClient: OkHttpClient,
    private val sentryModeController: SentryModeController,
    private val notificationPresenter: AutomationNotificationPresenter,
    @ApplicationContext private val context: Context,
    private val profileDetector: CarProfileDetector = CarProfileDetector(),
) {
    companion object {
        private const val TAG = "ActionDispatcher"
        private const val CHANNEL_SILENT_ID = "wjx_automation_silent"
        private const val CHANNEL_SOUND_ID = "wjx_automation_sound"
        private const val USER_NOTIF_BASE_ID = 10000
        private const val MINIMIZE_DELAY_MS = 3000L
        private const val AUTO_DIAL_DELAY_MS = 300L
        private const val MEDIA_REPLACEMENT_GUARD_MS = 150L
        private const val REVERSE_GEAR = 2
        private const val BT_CALL_PACKAGE = "com.byd.bluetoothcall"
        private const val BT_CALL_ACTION_DIAL_HANGUP = "com.byd.btcall.action.DIAL_HANGUP"
        private const val BT_CALL_KEYCODE_DIAL = 313
        // Hard cap on user-set delay action; protects against typos like "60000000".
        private const val MAX_DELAY_MS = 30_000L
        private val BLOCKED_PATTERNS = listOf("发送CAN", "执行SHELL", "下电")

        /**
         * True if [command] would OPEN a window/sunroof/sunshade (apertures that
         * are blocked above 80 km/h). Pure predicate — kept in the companion so it
         * is unit-testable without Android deps.
         *
         * "打开N" sets the aperture to N%; N==0 is a CLOSE (safe at speed) and must
         * not be treated as an open. A bare "打开" with no percentage is treated
         * conservatively as an open. 全开/半开/通风 are always opens.
         */
        internal fun isWindowOpenCommand(command: String): Boolean {
            val subjects = listOf("车窗", "天窗", "主驾", "副驾", "后左", "后右", "遮阳帘")
            if (subjects.none { command.contains(it) }) return false
            if (command.contains("关")) return false
            val opensViaPosition = POSITION_OPEN.find(command)
                ?.let { it.groupValues[1].toInt() > 0 }
                ?: command.contains("打开")        // bare "打开" (no %) — treat as open
            val opensViaWord = listOf("全开", "半开", "通风").any { command.contains(it) }
            return opensViaPosition || opensViaWord
        }

        private val POSITION_OPEN = Regex("打开(\\d+)")

        /** Clamp a requested media volume level to the device's valid [0, max] range. Pure — unit-testable. */
        internal fun clampVolume(level: Int, max: Int): Int = level.coerceIn(0, max.coerceAtLeast(0))
    }

    private val notifCounter = AtomicInteger(USER_NOTIF_BASE_ID)
    init {
        createUserChannels()
    }

    suspend fun dispatch(action: ActionDef, data: DiParsData?, owner: String? = null): DispatchResult = try {
        WjxFileLogger.i(TAG, "dispatch kind=${action.kind} command=${action.command} owner=${owner.orEmpty()}")
        when (action.kind) {
            "param" -> dispatchParam(action, data)
            "notification_silent" -> showNotification(action, silent = true)
            "notification_sound" -> showNotification(action, silent = false)
            "app_launch" -> launchApp(action)
            "call" -> dial(action)
            "navigate" -> navigate(action)
            "url" -> openUrl(action)
            "delay" -> dispatchDelay(action)
            "media_volume" -> setMediaVolume(action)
            "outside_sound" -> playOutsideSound(action, data, owner)
            "stop_outside_sound" -> stopAllOutsideSound()
            "auto_door" -> controlAutoDoor(action)
            "go_home" -> goHome()
            "http" -> sendHttp(action)
            "play_music" -> playMusic(action, owner)
            "stop_music" -> stopMusic()
            "play_video" -> playVideo(action, owner)
            "stop_video" -> stopVideo()
            "sentry_mode" -> setSentryMode(action)
            else -> DispatchResult(false, "未知动作类型：${action.kind}")
        }
    } catch (e: Exception) {
        WjxFileLogger.e(TAG, "dispatch failed for kind=${action.kind}: ${e.message}", e)
        DispatchResult(false, e.message ?: "未知错误")
    }

    private suspend fun dispatchDelay(action: ActionDef): DispatchResult {
        val ms = action.payload?.toLongOrNull()
            ?: return DispatchResult(false, "未设置延迟时长")
        if (ms < 0 || ms > MAX_DELAY_MS) {
            return DispatchResult(false, "延迟时长超出范围（0..${MAX_DELAY_MS} 毫秒）")
        }
        kotlinx.coroutines.delay(ms)
        return DispatchResult(true)
    }

    // --- media volume (standard AudioManager, no autoservice) ---

    private fun setMediaVolume(action: ActionDef): DispatchResult {
        val level = action.payload?.toIntOrNull()
            ?: return DispatchResult(false, "未设置媒体音量")
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return DispatchResult(false, "AudioManager 不可用")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, clampVolume(level, max), 0)
        return DispatchResult(true)
    }

    // --- outside sound (same route as the Sound page) ---

    fun stopOutsideSound(owner: String) {
        WjxFileLogger.i(TAG, "stop outside sound owner=$owner")
        WjxOutsidePlayService.stop(context, owner = owner)
    }

    private fun stopAllOutsideSound(): DispatchResult {
        WjxFileLogger.i(TAG, "stop all outside sound from automation")
        WjxOutsidePlayService.stop(context)
        return DispatchResult(true)
    }

    private suspend fun playMusic(action: ActionDef, owner: String?): DispatchResult {
        val payload = parsePayload(action.payload) ?: return DispatchResult(false, "未设置音乐")
        val source = payload.optString("path").trim()
        if (source.isBlank()) return DispatchResult(false, "未选择音乐")
        if (!canReadMediaSource(source)) return DispatchResult(false, "音乐文件不存在或无法读取")
        val volume = payload.optInt("volume", 30).coerceIn(0, 100)
        val loop = payload.optBoolean("loop", false)
        AutomationVideoActivity.stop(context)
        kotlinx.coroutines.delay(MEDIA_REPLACEMENT_GUARD_MS)
        AutomationMusicService.play(context, source, volume, loop, owner)
        return DispatchResult(true)
    }

    private fun stopMusic(): DispatchResult {
        AutomationMusicService.stop(context)
        return DispatchResult(true)
    }

    private suspend fun playVideo(action: ActionDef, owner: String?): DispatchResult {
        val payload = parsePayload(action.payload) ?: return DispatchResult(false, "未设置视频")
        val source = payload.optString("path").trim()
        if (source.isBlank()) return DispatchResult(false, "未选择视频")
        if (!canReadMediaSource(source)) return DispatchResult(false, "视频文件不存在或无法读取")
        val volume = payload.optInt("volume", 30).coerceIn(0, 100)
        AutomationMusicService.stop(context)
        kotlinx.coroutines.delay(MEDIA_REPLACEMENT_GUARD_MS)
        AutomationVideoActivity.play(context, source, volume, owner)
        return DispatchResult(true)
    }

    private fun stopVideo(): DispatchResult {
        AutomationVideoActivity.stop(context)
        return DispatchResult(true)
    }

    fun stopTransientMedia(kind: String, owner: String) {
        when (kind) {
            "play_music" -> AutomationMusicService.stop(context, owner)
            "play_video" -> AutomationVideoActivity.stop(context, owner)
        }
    }

    fun stopMediaOwnedByInactiveRules(activeRuleIds: Set<Long>) {
        val musicOwner = AutomationMediaRegistry.musicOwner
        val musicRule = AutomationMediaRegistry.ruleId(musicOwner)
        if (musicRule != null && musicRule !in activeRuleIds) {
            WjxFileLogger.i(TAG, "stop music for inactive automation rule=$musicRule")
            AutomationMediaRegistry.musicOwner = null
            AutomationMusicService.stop(context, musicOwner)
        }
        val videoOwner = AutomationMediaRegistry.videoOwner
        val videoRule = AutomationMediaRegistry.ruleId(videoOwner)
        if (videoRule != null && videoRule !in activeRuleIds) {
            WjxFileLogger.i(TAG, "stop video for inactive automation rule=$videoRule")
            AutomationMediaRegistry.videoOwner = null
            AutomationVideoActivity.stop(context, videoOwner)
        }
    }

    private suspend fun setSentryMode(action: ActionDef): DispatchResult {
        val enabled = when (action.payload?.trim()?.lowercase()) {
            "on" -> true
            "off" -> false
            else -> return DispatchResult(false, "哨兵模式动作无效")
        }
        return sentryModeController.setEnabled(enabled)
    }

    private fun canReadMediaSource(source: String): Boolean {
        val uri = Uri.parse(source)
        return if (uri.scheme.equals("content", ignoreCase = true)) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
        } else {
            File(source).isFile
        }
    }

    private fun playOutsideSound(action: ActionDef, data: DiParsData?, owner: String?): DispatchResult {
        val payload = parsePayload(action.payload) ?: return DispatchResult(false, "未设置音效")
        val path = payload.optString("path").takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置音效文件")
        val loop = payload.optBoolean("loop", false)
        val file = File(path)
        if (!file.isFile) return DispatchResult(false, "音效文件不存在：${file.name}")
        val parkingStatus = data?.adasApaParkingStatus
        if (owner?.startsWith("automation:") == true && parkingStatus != null && AutoParkingStatus.isSoundActive(parkingStatus)) {
            WjxFileLogger.i(
                TAG,
                "skip outside sound during auto parking file=${file.name} owner=$owner adasApaParkingStatus=$parkingStatus"
            )
            return DispatchResult(true, "自动泊车执行中，已跳过普通自动化车外音效")
        }

        val profile = profileDetector.detect()
        val channel = ExteriorHailingSettings(context).effectiveChannel(profile)
        val reverseActive = data?.gear == REVERSE_GEAR
        WjxFileLogger.i(
            TAG,
            "play outside sound file=${file.name} loop=$loop owner=${owner.orEmpty()} " +
                "controller=${profile.seriesLabel} channel=$channel reverse=$reverseActive"
        )
        WjxOutsidePlayService.play(
            context,
            file,
            channel,
            loop = loop,
            owner = owner,
            reverseActive = reverseActive,
        )
        return DispatchResult(true)
    }

    // --- electric sliding doors ---

    private suspend fun controlAutoDoor(action: ActionDef): DispatchResult {
        val payload = parsePayload(action.payload) ?: return DispatchResult(false, "未设置自动滑门动作")
        val side = payload.optString("side").takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置自动滑门方向")
        val commandValue = payload.optInt("commandValue", -1)
        if (commandValue !in 1..3) return DispatchResult(false, "自动滑门命令无效：$commandValue")
        val sides = when (side) {
            "slide_l", "slide_r" -> listOf(side)
            "both" -> listOf("slide_l", "slide_r")
            else -> return DispatchResult(false, "自动滑门方向无效：$side")
        }
        val result = autoDoorController.runDoors(
            slotKeys = sides,
            commandValue = commandValue,
            groupLabel = action.displayName.ifBlank { "自动滑门" },
        )
        WjxFileLogger.i(TAG, "auto door action side=$side command=$commandValue success=${result.success} reason=${result.message}")
        return DispatchResult(result.success, if (result.success) null else result.message)
    }

    // --- param (native autoservice via VehicleApi) ---

    private suspend fun dispatchParam(action: ActionDef, data: DiParsData?): DispatchResult {
        val blockReason = getBlockReason(action.command, data)
        if (blockReason != null) {
            WjxFileLogger.w(TAG, "Blocked '${action.command}': $blockReason")
            return DispatchResult(false, blockReason)
        }
        val result = vehicleApi.dispatch(action.command)
        val success = result.isSuccess
        val reason = if (!success) {
            result.exceptionOrNull()?.message ?: "执行失败"
        } else null
        WjxFileLogger.i(TAG, "param dispatch command=${action.command} success=$success reason=${reason.orEmpty()}")
        return DispatchResult(success, reason)
    }

    private fun getBlockReason(command: String, data: DiParsData?): String? {
        if (BLOCKED_PATTERNS.any { command.contains(it) }) return "禁止执行的命令"
        if (data == null) return null
        if (isWindowOpenCommand(command)) {
            val speed = data.speed ?: return "当前车速未知"
            if (speed > 80) return "车速 ${speed} km/h，已阻止开窗（>80）"
        }
        return null
    }

    // --- notifications (user-visible) ---

    private suspend fun showNotification(action: ActionDef, silent: Boolean): DispatchResult {
        val payload = parsePayload(action.payload)
        val title = payload?.optString("title")?.takeIf(String::isNotBlank) ?: action.displayName
        val text = payload?.optString("text") ?: ""

        if (!silent && notificationPresenter.show(title, text)) {
            return DispatchResult(true)
        }

        val channelId = if (silent) CHANNEL_SILENT_ID else CHANNEL_SOUND_ID
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .build()
        val id = notifCounter.incrementAndGet()
        nm().notify(id, notif)
        return DispatchResult(true)
    }

    private fun createUserChannels() {
        val silent = NotificationChannel(
            CHANNEL_SILENT_ID,
            "Automation Silent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            description = "Silent automation notifications"
        }
        val sound = NotificationChannel(
            CHANNEL_SOUND_ID,
            "Automation Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Audible automation notifications"
        }
        val manager = nm()
        manager.createNotificationChannel(silent)
        manager.createNotificationChannel(sound)
    }

    // --- external activities ---

    private suspend fun launchApp(action: ActionDef): DispatchResult {
        val payload = parsePayload(action.payload)
        val pkg = payload?.optString("packageName")?.takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置应用包名")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return DispatchResult(false, "应用未安装：$pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = tryStartActivity(intent, "app_launch:$pkg")
        if (result.success) maybeMinimize(payload)
        return result
    }

    private suspend fun dial(action: ActionDef): DispatchResult {
        val payload = parsePayload(action.payload)
        val phone = payload?.optString("phone")?.takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置电话号码")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = tryStartActivity(intent, "dial:$phone")
        if (result.success && payload.optBoolean("autoDial", false)) {
            kotlinx.coroutines.delay(AUTO_DIAL_DELAY_MS)
            val press = Intent(BT_CALL_ACTION_DIAL_HANGUP).apply {
                setPackage(BT_CALL_PACKAGE)
                putExtra("keycode", BT_CALL_KEYCODE_DIAL)
            }
            try {
                context.sendBroadcast(press)
            } catch (e: Exception) {
                Log.w(TAG, "autoDial broadcast failed: ${e.message}")
                WjxFileLogger.w(TAG, "autoDial broadcast failed: ${e.message}")
            }
        }
        return result
    }

    private fun navigate(action: ActionDef): DispatchResult {
        val payload = parsePayload(action.payload) ?: return DispatchResult(false, "未设置参数")
        val lat = payload.optDouble("lat", Double.NaN)
        val lon = payload.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return DispatchResult(false, "未设置经纬度")
        val uri = "androidamap://route/plan/?dlat=$lat&dlon=$lon&dev=0&t=0"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStartActivity(intent, "navigate:$lat,$lon")
    }

    private suspend fun openUrl(action: ActionDef): DispatchResult {
        val payload = parsePayload(action.payload)
        val url = payload?.optString("url")?.takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置链接")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = tryStartActivity(intent, "url:$url")
        if (result.success) maybeMinimize(payload)
        return result
    }

    private suspend fun maybeMinimize(payload: JSONObject?) {
        if (payload?.optBoolean("minimize", false) != true) return
        kotlinx.coroutines.delay(MINIMIZE_DELAY_MS)
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(home)
        } catch (e: Exception) {
            Log.w(TAG, "home failed: ${e.message}")
            WjxFileLogger.w(TAG, "home failed: ${e.message}")
        }
    }

    private fun goHome(): DispatchResult {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStartActivity(home, "go_home")
    }

    private suspend fun sendHttp(action: ActionDef): DispatchResult {
        val payload = parsePayload(action.payload) ?: return DispatchResult(false, "未设置 HTTP 请求")
        val url = payload.optString("url").trim()
        if (url.isBlank()) return DispatchResult(false, "HTTP 地址为空")
        val method = payload.optString("method", "GET").uppercase()
        val bodyText = payload.optString("body", "")
        val contentType = payload.optString("contentType", "application/json")
        val builder = Request.Builder().url(url)
        payload.optJSONObject("headers")?.let { headers ->
            headers.keys().forEach { name -> builder.header(name, headers.optString(name)) }
        }
        val requestBody = bodyText.toRequestBody(contentType.toMediaTypeOrNull())
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (bodyText.isBlank()) builder.delete() else builder.delete(requestBody)
            "POST" -> builder.post(requestBody)
            "PUT" -> builder.put(requestBody)
            "PATCH" -> builder.patch(requestBody)
            else -> return DispatchResult(false, "不支持的 HTTP 方法：$method")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) DispatchResult(true)
                    else DispatchResult(false, "HTTP ${response.code}")
                }
            }.getOrElse { error ->
                WjxFileLogger.w(TAG, "http action failed: ${error.message}", error)
                DispatchResult(false, error.message ?: "HTTP 请求失败")
            }
        }
    }

    private fun tryStartActivity(intent: Intent, label: String): DispatchResult = try {
        context.startActivity(intent)
        DispatchResult(true)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "$label: ${e.message}")
        WjxFileLogger.w(TAG, "$label: ${e.message}")
        DispatchResult(false, "没有可处理的应用：${e.message}")
    } catch (e: SecurityException) {
        Log.w(TAG, "$label (security): ${e.message}")
        WjxFileLogger.w(TAG, "$label (security): ${e.message}")
        DispatchResult(false, "没有权限：${e.message}")
    }

    // --- helpers ---

    private fun parsePayload(payload: String?): JSONObject? {
        if (payload.isNullOrBlank()) return null
        return try { JSONObject(payload) } catch (e: Exception) { null }
    }

    private fun nm(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
