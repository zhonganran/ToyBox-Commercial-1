package com.byd.wjx.xiaozhi.wake

import android.content.Context
import android.os.Build
import com.byd.wjx.log.WjxFileLogger
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small, fully offline keyword spotter. It never connects to the Xiaozhi server. */
@Singleton
class SherpaOnnxWakeWordEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val phraseCompiler: XiaozhiWakePhraseCompiler,
) {
    private val assetManager = context.applicationContext.assets
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var activePhrase: String = ""
    private val floatSamples = FloatArray(FRAME_SAMPLES)

    fun availabilityError(): String? = when {
        Build.SUPPORTED_ABIS.none { it == SUPPORTED_ABI } ->
            "当前车机不是 ARM64 架构，无法加载本地唤醒引擎"
        REQUIRED_ASSETS.any { path ->
            runCatching { assetManager.open("$MODEL_DIR/$path").close() }.isFailure
        } -> "本地唤醒模型文件不完整，请重新安装正式包"
        else -> null
    }

    @Synchronized
    fun prepare(phrase: String): Result<CompiledWakePhrase> = runCatching {
        availabilityError()?.let(::error)
        val compiled = phraseCompiler.compile(phrase).getOrThrow()
        val activeSpotter = spotter ?: createSpotter().also { spotter = it }
        stream?.release()
        activePhrase = compiled.displayPhrase
        stream = activeSpotter.createStream(compiled.sherpaKeyword).also { created ->
            check(created.ptr != 0L) { "本地唤醒词加载失败" }
        }
        compiled
    }

    @Synchronized
    fun accept(samples: ShortArray, count: Int): String? {
        val activeSpotter = spotter ?: return null
        val activeStream = stream ?: return null
        val safeCount = count.coerceIn(0, minOf(samples.size, floatSamples.size))
        if (safeCount == 0) return null
        for (index in 0 until safeCount) {
            floatSamples[index] = samples[index] / PCM_SCALE
        }
        val waveform = if (safeCount == floatSamples.size) floatSamples else floatSamples.copyOf(safeCount)
        activeStream.acceptWaveform(waveform, SAMPLE_RATE)
        while (activeSpotter.isReady(activeStream)) {
            activeSpotter.decode(activeStream)
            val result = activeSpotter.getResult(activeStream).keyword
            if (result.isNotBlank()) {
                activeSpotter.reset(activeStream)
                if (normalizeWakeText(result) == normalizeWakeText(activePhrase)) {
                    return activePhrase
                }
                WjxFileLogger.i(TAG, "ignored bootstrap keyword result=$result")
            }
        }
        return null
    }

    @Synchronized
    fun stopStream() {
        stream?.release()
        stream = null
        activePhrase = ""
    }

    @Synchronized
    fun release() {
        stopStream()
        spotter?.release()
        spotter = null
    }

    private fun createSpotter(): KeywordSpotter = KeywordSpotter(
        assetManager = assetManager,
        config = KeywordSpotterConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = FEATURE_DIM,
                dither = 0.0f,
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    joiner = "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                ),
                tokens = "$MODEL_DIR/tokens.txt",
                numThreads = 1,
                debug = false,
                provider = "cpu",
                modelType = "zipformer2",
            ),
            maxActivePaths = 4,
            keywordsFile = "$MODEL_DIR/keywords.txt",
            keywordsScore = 1.7f,
            keywordsThreshold = 0.25f,
            numTrailingBlanks = 2,
        ),
    )

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1_600
        private const val FEATURE_DIM = 80
        private const val PCM_SCALE = 32_768.0f
        private const val SUPPORTED_ABI = "arm64-v8a"
        private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
        private const val TAG = "SherpaWakeEngine"
        private val REQUIRED_ASSETS = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "tokens.txt",
            "keywords.txt",
        )
    }
}
