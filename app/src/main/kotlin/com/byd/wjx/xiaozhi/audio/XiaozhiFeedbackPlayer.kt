package com.byd.wjx.xiaozhi.audio

import android.media.AudioManager
import android.media.ToneGenerator
import com.byd.wjx.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Singleton
class XiaozhiFeedbackPlayer @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun playWakeTone() = play(
        toneType = ToneGenerator.TONE_PROP_BEEP,
        durationMs = 120,
        volume = 28,
    )

    fun playFailureTone() = play(
        toneType = ToneGenerator.TONE_PROP_NACK,
        durationMs = 260,
        volume = 32,
    )

    private fun play(toneType: Int, durationMs: Int, volume: Int) {
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, volume).also { tone ->
                tone.startTone(toneType, durationMs)
                applicationScope.launch {
                    delay(durationMs + RELEASE_PADDING_MS)
                    runCatching { tone.release() }
                }
            }
        }
    }

    private companion object {
        const val RELEASE_PADDING_MS = 120L
    }
}
