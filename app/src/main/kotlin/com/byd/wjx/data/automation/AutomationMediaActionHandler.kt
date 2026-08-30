package com.byd.wjx.data.automation

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.CarProfileDetector
import java.io.File
import kotlinx.coroutines.delay

internal class AutomationMediaActionHandler(
    private val context: Context,
    private val profileDetector: CarProfileDetector,
) {
    fun setMediaVolume(action: ActionDef): DispatchResult {
        val level = action.payload?.toIntOrNull()
            ?: return DispatchResult(false, "未设置媒体音量")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return DispatchResult(false, "AudioManager 不可用")
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            clampAutomationVolume(level, maximum),
            0,
        )
        return DispatchResult(true)
    }

    suspend fun playInsideSound(action: ActionDef, owner: String?): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置车内音效")
        val source = payload.optString("path").trim()
        if (source.isBlank()) return DispatchResult(false, "未选择车内音效")
        if (!canReadMediaSource(source)) {
            return DispatchResult(false, "车内音效文件不存在或无法读取")
        }
        val loop = payload.optBoolean("loop", false)
        val volume = payload.optInt("volume", DEFAULT_INSIDE_SOUND_VOLUME).coerceIn(0, 100)
        val file = File(source)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val streamVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
        val streamMaximum = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        WjxFileLogger.i(
            TAG,
            "inside sound request source=${source.takeLast(100)} " +
                "size=${file.takeIf(File::isFile)?.length()} loop=$loop volume=$volume " +
                "owner=${owner.orEmpty()} streamVolume=$streamVolume/$streamMaximum",
        )
        AutomationVideoActivity.stop(context)
        delay(MEDIA_REPLACEMENT_GUARD_MS)
        val started = AutomationMusicService.play(
            context = context,
            source = source,
            volume = volume,
            loop = loop,
            owner = owner,
            adjustStreamVolume = true,
        )
        return if (started) {
            DispatchResult(true, "车内音效播放请求已提交")
        } else {
            DispatchResult(false, "车内音效播放服务启动失败")
        }
    }

    fun stopAllInsideSound(): DispatchResult {
        WjxFileLogger.i(TAG, "stop all inside sound from automation")
        AutomationMediaRegistry.musicOwner = null
        AutomationMusicService.stop(context)
        return DispatchResult(true)
    }

    suspend fun playMusic(action: ActionDef, owner: String?): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置音乐")
        val source = payload.optString("path").trim()
        if (source.isBlank()) return DispatchResult(false, "未选择音乐")
        if (!canReadMediaSource(source)) return DispatchResult(false, "音乐文件不存在或无法读取")
        val volume = payload.optInt("volume", 30).coerceIn(0, 100)
        val loop = payload.optBoolean("loop", false)
        AutomationVideoActivity.stop(context)
        delay(MEDIA_REPLACEMENT_GUARD_MS)
        AutomationMusicService.play(context, source, volume, loop, owner)
        return DispatchResult(true)
    }

    fun stopMusic(): DispatchResult {
        AutomationMusicService.stop(context)
        return DispatchResult(true)
    }

    suspend fun playVideo(action: ActionDef, owner: String?): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置视频")
        val source = payload.optString("path").trim()
        if (source.isBlank()) return DispatchResult(false, "未选择视频")
        if (!canReadMediaSource(source)) return DispatchResult(false, "视频文件不存在或无法读取")
        val volume = payload.optInt("volume", 30).coerceIn(0, 100)
        AutomationMusicService.stop(context)
        delay(MEDIA_REPLACEMENT_GUARD_MS)
        AutomationVideoActivity.play(context, source, volume, owner)
        return DispatchResult(true)
    }

    fun stopVideo(): DispatchResult {
        AutomationVideoActivity.stop(context)
        return DispatchResult(true)
    }

    fun playOutsideSound(
        action: ActionDef,
        data: DiParsData?,
        owner: String?,
    ): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置音效")
        val path = payload.optString("path").takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置音效文件")
        val loop = payload.optBoolean("loop", false)
        val file = File(path)
        if (!file.isFile) return DispatchResult(false, "音效文件不存在：${file.name}")
        val parkingStatus = data?.adasApaParkingStatus
        val allowDuringAutoParking = payload.optBoolean("allowDuringAutoParking", false)
        if (shouldSkipAutomationOutsideSound(owner, parkingStatus, allowDuringAutoParking)) {
            WjxFileLogger.i(
                TAG,
                "skip outside sound during auto parking file=${file.name} " +
                    "owner=$owner adasApaParkingStatus=$parkingStatus",
            )
            return DispatchResult(true, "自动泊车执行中，已跳过普通自动化车外音效")
        }

        val profile = profileDetector.detect()
        val channel = ExteriorHailingSettings(context).effectiveChannel(profile)
        val reverseActive = data?.gear == REVERSE_GEAR
        WjxFileLogger.i(
            TAG,
            "play outside sound file=${file.name} loop=$loop owner=${owner.orEmpty()} " +
                "controller=${profile.seriesLabel} channel=$channel reverse=$reverseActive",
        )
        if (owner?.startsWith("automation:") == true) {
            AutomationMediaRegistry.outsideOwner = owner
        }
        WjxOutsidePlayService.play(
            context = context,
            file = file,
            channel = channel,
            loop = loop,
            owner = owner,
            reverseActive = reverseActive,
        )
        return DispatchResult(true)
    }

    fun stopOutsideSound(owner: String) {
        WjxFileLogger.i(TAG, "stop outside sound owner=$owner")
        if (AutomationMediaRegistry.outsideOwner == owner) {
            AutomationMediaRegistry.outsideOwner = null
        }
        WjxOutsidePlayService.stop(context, owner = owner)
    }

    fun stopAllOutsideSound(): DispatchResult {
        WjxFileLogger.i(TAG, "stop all outside sound from automation")
        AutomationMediaRegistry.outsideOwner = null
        WjxOutsidePlayService.stop(context)
        return DispatchResult(true)
    }

    fun stopTransientMedia(kind: String, owner: String) {
        when (kind) {
            "inside_sound", "play_music" -> AutomationMusicService.stop(context, owner)
            "play_video" -> AutomationVideoActivity.stop(context, owner)
        }
    }

    fun stopMediaOwnedByInactiveRules(activeRuleIds: Set<Long>) {
        val outsideOwner = AutomationMediaRegistry.outsideOwner
        val outsideRuleId = AutomationMediaRegistry.ruleId(outsideOwner)
        if (outsideRuleId != null && outsideRuleId !in activeRuleIds) {
            WjxFileLogger.i(TAG, "stop outside sound for inactive automation rule=$outsideRuleId")
            AutomationMediaRegistry.outsideOwner = null
            WjxOutsidePlayService.stop(context, outsideOwner)
        }
        val musicOwner = AutomationMediaRegistry.musicOwner
        val musicRuleId = AutomationMediaRegistry.ruleId(musicOwner)
        if (musicRuleId != null && musicRuleId !in activeRuleIds) {
            WjxFileLogger.i(TAG, "stop music for inactive automation rule=$musicRuleId")
            AutomationMediaRegistry.musicOwner = null
            AutomationMusicService.stop(context, musicOwner)
        }
        val videoOwner = AutomationMediaRegistry.videoOwner
        val videoRuleId = AutomationMediaRegistry.ruleId(videoOwner)
        if (videoRuleId != null && videoRuleId !in activeRuleIds) {
            WjxFileLogger.i(TAG, "stop video for inactive automation rule=$videoRuleId")
            AutomationMediaRegistry.videoOwner = null
            AutomationVideoActivity.stop(context, videoOwner)
        }
    }

    fun stopMediaOwnedByRule(ruleId: Long) {
        val outsideOwner = AutomationMediaRegistry.outsideOwner
        if (AutomationMediaRegistry.ruleId(outsideOwner) == ruleId) {
            WjxFileLogger.i(TAG, "stop outside sound for edited automation rule=$ruleId")
            AutomationMediaRegistry.outsideOwner = null
            WjxOutsidePlayService.stop(context, outsideOwner)
        }
        val musicOwner = AutomationMediaRegistry.musicOwner
        if (AutomationMediaRegistry.ruleId(musicOwner) == ruleId) {
            WjxFileLogger.i(TAG, "stop music for edited automation rule=$ruleId")
            AutomationMediaRegistry.musicOwner = null
            AutomationMusicService.stop(context, musicOwner)
        }
        val videoOwner = AutomationMediaRegistry.videoOwner
        if (AutomationMediaRegistry.ruleId(videoOwner) == ruleId) {
            WjxFileLogger.i(TAG, "stop video for edited automation rule=$ruleId")
            AutomationMediaRegistry.videoOwner = null
            AutomationVideoActivity.stop(context, videoOwner)
        }
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

    companion object {
        private const val TAG = "ActionDispatcher"
        private const val DEFAULT_INSIDE_SOUND_VOLUME = 30
        private const val MEDIA_REPLACEMENT_GUARD_MS = 150L
        private const val REVERSE_GEAR = 2
    }
}
