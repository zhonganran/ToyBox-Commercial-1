package com.byd.wjx.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class HailingAudioProcessor(
    tuning: RealtimeHailingTuning,
) {
    @Volatile private var params = ProcessorParams.from(tuning)
    private var feedbackDuckFrames = 0

    fun update(tuning: RealtimeHailingTuning) {
        params = ProcessorParams.from(tuning)
    }

    fun process(input: ShortArray, output: ShortArray): Int {
        val current = params
        if (current.micGain <= 0f) {
            java.util.Arrays.fill(output, 0, input.size, 0.toShort())
            return input.size
        }
        val gain = current.micGain * if (feedbackDuckFrames > 0) FEEDBACK_DUCK_GAIN else 1f
        if (feedbackDuckFrames > 0) feedbackDuckFrames -= 1
        var peak = 0
        var outIndex = 0
        input.forEach { sample ->
            val amplified = (sample.toFloat() * gain)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val peakValue = if (amplified == Short.MIN_VALUE.toInt()) Short.MAX_VALUE.toInt() else abs(amplified)
            if (peakValue > peak) peak = peakValue
            var y = amplified / SHORT_MAX
            val magnitude = abs(y)
            if (magnitude > COMPRESS_THRESHOLD) {
                val compressed = COMPRESS_THRESHOLD + (magnitude - COMPRESS_THRESHOLD) / COMPRESS_RATIO
                y = if (y < 0f) -compressed else compressed
            }
            y = y.coerceIn(-LIMIT_THRESHOLD, LIMIT_THRESHOLD)
            val pcm = (y * SHORT_PEAK).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            output[outIndex++] = pcm
        }
        if (peak < current.noiseGateThresholdPcm) {
            java.util.Arrays.fill(output, 0, outIndex, 0.toShort())
        } else if (peak >= FEEDBACK_PEAK_PCM) {
            feedbackDuckFrames = FEEDBACK_DUCK_FRAMES
        }
        return outIndex
    }

    private data class ProcessorParams(
        val micGain: Float,
        val noiseGateThresholdPcm: Int,
    ) {
        companion object {
            fun from(tuning: RealtimeHailingTuning): ProcessorParams =
                ProcessorParams(
                    micGain = tuning.micGainMultiplier.coerceIn(0f, MAX_MIC_GAIN),
                    noiseGateThresholdPcm = (BASE_NOISE_GATE_PCM + tuning.noiseSuppression * NOISE_GATE_RANGE_PCM)
                        .roundToInt()
                        .coerceIn(0, Short.MAX_VALUE.toInt()),
                )
        }
    }

    companion object {
        private const val BASE_NOISE_GATE_PCM = 80f
        private const val NOISE_GATE_RANGE_PCM = 120f
        private const val MAX_MIC_GAIN = 5.0f
        private const val COMPRESS_THRESHOLD = 0.78f
        private const val COMPRESS_RATIO = 3.0f
        private const val LIMIT_THRESHOLD = 0.96f
        private const val FEEDBACK_PEAK_PCM = 28_500
        private const val FEEDBACK_DUCK_GAIN = 0.62f
        private const val FEEDBACK_DUCK_FRAMES = 15
        private const val SHORT_MAX = 32768f
        private const val SHORT_PEAK = 32767f
    }
}

