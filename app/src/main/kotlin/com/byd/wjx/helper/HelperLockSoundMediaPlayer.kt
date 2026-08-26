package com.byd.wjx.helper

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import com.byd.wjx.audio.ExteriorChannel
import com.byd.wjx.audio.WjxAudioAttributes
import java.io.File

internal class LockSoundMediaPlayer {
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var enhancer: LoudnessEnhancer? = null

    fun start(
        file: File,
        event: LockEvent,
        channel: ExteriorChannel,
        reason: String,
        onCompletion: () -> Unit,
        onError: (what: Int, extra: Int) -> Unit,
    ): Boolean {
        var createdPlayer: MediaPlayer? = null
        return runCatching {
            val mediaPlayer = MediaPlayer().apply {
                createdPlayer = this
                setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                setDataSource(file.absolutePath)
                setVolume(1.0f, 1.0f)
                isLooping = event.looping
                setOnCompletionListener { onCompletion() }
                setOnErrorListener { _, what, extra ->
                    onError(what, extra)
                    true
                }
                prepare()
            }
            player = mediaPlayer
            enableEnhancer(mediaPlayer, channel)
            mediaPlayer.start()
            if (!mediaPlayer.isPlaying) throw IllegalStateException("MediaPlayer did not enter playing state")
            helperLog(TAG, "MediaPlayer started event=${event.key} reason=$reason file=${file.name}")
            true
        }.getOrElse {
            helperLog(TAG, "MediaPlayer failed event=${event.key} reason=$reason: ${throwableSummary(it)}")
            if (player == null) releaseCreatedPlayer(createdPlayer) else release()
            false
        }
    }

    fun release() {
        val mediaPlayer = player ?: return
        val currentEnhancer = enhancer
        runCatching { mediaPlayer.setOnCompletionListener(null) }
        runCatching { mediaPlayer.setOnErrorListener(null) }
        runCatching { mediaPlayer.setVolume(0f, 0f) }
        runCatching { currentEnhancer?.enabled = false }
        runCatching { currentEnhancer?.setTargetGain(0) }
        sleepQuietly(220L)
        runCatching { mediaPlayer.pause() }
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.release() }
        runCatching { currentEnhancer?.release() }
        player = null
        enhancer = null
    }

    private fun enableEnhancer(mediaPlayer: MediaPlayer, channel: ExteriorChannel) {
        runCatching {
            val gain = LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB
            enhancer = LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            helperLog(TAG, "enhancer enabled gain=$gain playerVolume=1.0 channel=$channel mode=player")
        }.onFailure {
            helperLog(TAG, "enhancer failed: ${throwableSummary(it)}")
        }
    }

    private fun releaseCreatedPlayer(mediaPlayer: MediaPlayer?) {
        if (mediaPlayer == null) return
        runCatching { mediaPlayer.setOnCompletionListener(null) }
        runCatching { mediaPlayer.setOnErrorListener(null) }
        runCatching { mediaPlayer.release() }
    }

    private fun sleepQuietly(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }

    private companion object {
        private const val TAG = "LockSound"
    }
}
