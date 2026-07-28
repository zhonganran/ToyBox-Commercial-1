package com.byd.wjx.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.audio.AudioOwner
import com.byd.wjx.audio.AudioUsageCoordinator
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private data class RemoteCommand(
    val id: Long,
    val type: String,
    val payload: JSONObject,
)

private data class CommandResult(
    val success: Boolean,
    val message: String,
    val details: JSONObject = JSONObject(),
)

@Singleton
class RemoteCommandSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val okHttpClient: OkHttpClient,
    private val carProfileDetector: CarProfileDetector,
    private val audioUsageCoordinator: AudioUsageCoordinator,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val started = AtomicBoolean(false)
    private var lastFailureLogAtMs = 0L
    private var lastManifestSyncAtMs = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                val succeeded = runCatching { pollOnce() }
                    .onFailure { logPollFailure(it) }
                    .isSuccess
                delay(if (succeeded) POLL_INTERVAL_MS else ERROR_RETRY_INTERVAL_MS)
            }
        }
    }

    internal suspend fun pollOnce() = withContext(Dispatchers.IO) {
        val snapshot = activationManager.currentSnapshot()
        val deviceCode = snapshot.deviceCode.trim()
        val deviceToken = activationManager.currentDeviceToken().trim()
        if (deviceCode.isBlank() || deviceToken.isBlank()) return@withContext
        val command = poll(deviceCode, deviceToken) ?: return@withContext
        val cached = cachedTerminal(command.id)
        if (cached != null) {
            report(
                deviceCode = deviceCode,
                deviceToken = deviceToken,
                commandId = command.id,
                status = cached.optString("status"),
                message = cached.optString("message"),
                result = cached.optJSONObject("result") ?: JSONObject(),
            )
            return@withContext
        }

        WjxFileLogger.i(TAG, "execute command id=${command.id} type=${command.type}")
        val result = when (command.type) {
            "play_outside_sound" -> executePlay(deviceCode, deviceToken, command)
            "stop_outside_sound" -> executeStop()
            else -> CommandResult(false, "不支持的远程指令：${command.type}")
        }
        val status = if (result.success) "completed" else "failed"
        cacheTerminal(command.id, status, result.message, result.details)
        report(deviceCode, deviceToken, command.id, status, result.message, result.details)
    }

    private fun poll(deviceCode: String, deviceToken: String): RemoteCommand? {
        val body = JSONObject().put("deviceCode", deviceCode)
        val now = System.currentTimeMillis()
        val includesManifest = now - lastManifestSyncAtMs >= LOCAL_SOUND_SYNC_INTERVAL_MS
        if (includesManifest) {
            body.put("localSounds", buildLocalSoundManifest())
        }
        val response = executeJsonRequest(
            Request.Builder()
                .url(BuildConfig.REMOTE_COMMAND_POLL_API_URL)
                .header("Authorization", "Bearer $deviceToken")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
        if (includesManifest) lastManifestSyncAtMs = now
        val command = response.optJSONObject("command") ?: return null
        val id = command.optLong("id", 0L)
        val type = command.optString("type").trim()
        if (id <= 0L || type.isBlank()) return null
        return RemoteCommand(id, type, command.optJSONObject("payload") ?: JSONObject())
    }

    private suspend fun executePlay(
        deviceCode: String,
        deviceToken: String,
        command: RemoteCommand,
    ): CommandResult {
        val sound = RemoteLocalSoundCatalog.parseReference(
            soundKey = command.payload.optString("soundKey"),
            name = command.payload.optString("name"),
        )
            ?: return CommandResult(false, "远程音效参数无效")
        runCatching {
            report(
                deviceCode,
                deviceToken,
                command.id,
                "delivered",
                "正在检查车机本地音效",
                JSONObject().put("soundKey", sound.soundKey),
            )
        }.onFailure { WjxFileLogger.w(TAG, "progress report failed id=${command.id}: ${it.message}") }

        val file = resolveLocalSoundFile(sound.soundKey)
            ?: return CommandResult(false, "车机 WJX 音效不存在或文件已变更，请刷新小程序列表")
        runCatching {
            report(
                deviceCode,
                deviceToken,
                command.id,
                "delivered",
                "正在确认车外音频通道",
                JSONObject().put("soundKey", sound.soundKey).put("fileName", file.name),
            )
        }
        val owner = "remote-command:${command.id}"
        return awaitPlaybackStart(file, owner).let { playback ->
            if (playback.success) {
                CommandResult(
                    true,
                    "已开始车外播放",
                    JSONObject()
                        .put("soundKey", sound.soundKey)
                        .put("soundName", sound.name)
                        .put("fileName", file.name)
                        .put("owner", owner),
                )
            } else {
                playback
            }
        }
    }

    private suspend fun executeStop(): CommandResult {
        WjxOutsidePlayService.stop(appContext)
        val released = audioUsageCoordinator.awaitInactive(
            owners = setOf(AudioOwner.ExteriorPlayback),
            timeoutMs = STOP_RELEASE_TIMEOUT_MS,
        )
        return if (released) {
            CommandResult(true, "车外播放已停止")
        } else {
            CommandResult(false, "停止指令已发送，但车外音频通道未及时释放")
        }
    }

    private fun buildLocalSoundManifest(): JSONArray = JSONArray().apply {
        localSoundFiles().forEach { file ->
            put(
                JSONObject()
                    .put("soundKey", RemoteLocalSoundCatalog.soundKey(file))
                    .put("fileName", file.name)
                    .put("displayName", file.nameWithoutExtension)
                    .put("fileSize", file.length())
                    .put("modifiedAt", file.lastModified())
            )
        }
    }

    private fun resolveLocalSoundFile(soundKey: String): File? = localSoundFiles()
        .firstOrNull { RemoteLocalSoundCatalog.soundKey(it) == soundKey }

    private fun localSoundFiles(): List<File> {
        WjxDirectories.ensureBaseDirs()
        return RemoteLocalSoundCatalog.listFiles(
            rootDirectory = WjxDirectories.soundDir,
            supportedExtensions = SoundLibrary.SUPPORTED_EXT,
            maxItems = MAX_LOCAL_SOUNDS,
        )
    }

    private suspend fun awaitPlaybackStart(file: File, owner: String): CommandResult {
        val completion = CompletableDeferred<CommandResult>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
                if (intent.getStringExtra(WjxOutsidePlayService.EXTRA_OWNER) != owner) return
                when (intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE)) {
                    WjxOutsidePlayService.STATE_STARTED -> completion.complete(CommandResult(true, "已开始车外播放"))
                    WjxOutsidePlayService.STATE_ERROR -> completion.complete(CommandResult(false, "车外路由或播放启动失败"))
                    WjxOutsidePlayService.STATE_STOPPED -> completion.complete(CommandResult(false, "播放启动前已停止"))
                    WjxOutsidePlayService.STATE_COMPLETED -> completion.complete(CommandResult(false, "音效未确认启动即已结束"))
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WjxOutsidePlayService.ACTION_PLAYBACK_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return try {
            val channel = ExteriorHailingSettings(appContext)
                .effectiveChannel(carProfileDetector.detect())
            WjxOutsidePlayService.play(appContext, file, channel, owner = owner)
            withTimeoutOrNull(PLAYBACK_START_TIMEOUT_MS) { completion.await() }
                ?: CommandResult(false, "等待车外播放启动超时")
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    private fun report(
        deviceCode: String,
        deviceToken: String,
        commandId: Long,
        status: String,
        message: String,
        result: JSONObject,
    ) {
        val body = JSONObject()
            .put("deviceCode", deviceCode)
            .put("status", status)
            .put("message", message)
            .put("result", result)
        val base = BuildConfig.REMOTE_COMMAND_RESULT_API_BASE_URL.trimEnd('/')
        executeJsonRequest(
            Request.Builder()
                .url("$base/$commandId/complete")
                .header("Authorization", "Bearer $deviceToken")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
    }

    private fun executeJsonRequest(request: Request): JSONObject {
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("message", "HTTP ${response.code}"))
            }
            return json
        }
    }

    private fun cachedTerminal(commandId: Long): JSONObject? {
        if (prefs.getLong(KEY_LAST_COMMAND_ID, 0L) != commandId) return null
        val raw = prefs.getString(KEY_LAST_COMMAND_RESULT, "").orEmpty()
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun cacheTerminal(commandId: Long, status: String, message: String, result: JSONObject) {
        val value = JSONObject()
            .put("status", status)
            .put("message", message)
            .put("result", result)
        prefs.edit()
            .putLong(KEY_LAST_COMMAND_ID, commandId)
            .putString(KEY_LAST_COMMAND_RESULT, value.toString())
            .apply()
    }

    private fun logPollFailure(error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastFailureLogAtMs < FAILURE_LOG_INTERVAL_MS) return
        lastFailureLogAtMs = now
        WjxFileLogger.w(TAG, "poll failed: ${error.message}", error)
    }

    private companion object {
        const val TAG = "RemoteCommandSync"
        const val PREFS_NAME = "wjx_remote_commands"
        const val KEY_LAST_COMMAND_ID = "last_command_id"
        const val KEY_LAST_COMMAND_RESULT = "last_command_result"
        const val POLL_INTERVAL_MS = 5_000L
        const val ERROR_RETRY_INTERVAL_MS = 15_000L
        const val FAILURE_LOG_INTERVAL_MS = 60_000L
        const val LOCAL_SOUND_SYNC_INTERVAL_MS = 30_000L
        const val MAX_LOCAL_SOUNDS = 200
        const val PLAYBACK_START_TIMEOUT_MS = 20_000L
        const val STOP_RELEASE_TIMEOUT_MS = 4_000L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
