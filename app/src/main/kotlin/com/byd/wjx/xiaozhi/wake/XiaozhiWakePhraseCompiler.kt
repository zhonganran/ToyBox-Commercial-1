package com.byd.wjx.xiaozhi.wake

import android.content.Context
import android.icu.text.Transliterator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CompiledWakePhrase(
    val displayPhrase: String,
    val romanizedPhrase: String,
    val tokens: List<String>,
    val sherpaKeyword: String,
)

/** Converts a short Chinese wake phrase into the pinyin-token format expected by the KWS model. */
@Singleton
class XiaozhiWakePhraseCompiler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val assets = context.applicationContext.assets
    private val lock = Any()
    private val transliterator by lazy { Transliterator.getInstance("Han-Latin") }
    private val tokenSet by lazy {
        assets.open("$MODEL_DIR/tokens.txt").bufferedReader().useLines { lines ->
            lines.mapNotNull(::parseTokenLine).toSet()
        }
    }

    fun compile(phrase: String): Result<CompiledWakePhrase> = runCatching {
        val displayPhrase = normalizeWakeText(phrase)
        validateLocalWakePhrase(displayPhrase)?.let(::error)
        val romanized = synchronized(lock) {
            Normalizer.normalize(
                transliterator.transliterate(displayPhrase),
                Normalizer.Form.NFC,
            ).lowercase(Locale.ROOT)
        }
        val syllables = PINYIN_SYLLABLE.findAll(romanized).map { it.value }.toList()
        check(syllables.size == displayPhrase.codePointCount(0, displayPhrase.length)) {
            "唤醒词“$displayPhrase”的拼音转换不完整，请换一个常用中文名称"
        }
        val tokens = syllables.flatMap { syllable ->
            segmentPinyinSyllable(syllable, tokenSet)
                ?: error("唤醒词“$displayPhrase”包含当前模型不支持的读音：$syllable")
        }
        val shortPhrase = displayPhrase.codePointCount(0, displayPhrase.length) <= 2
        val boost = if (shortPhrase) "2.0" else "1.7"
        val threshold = if (shortPhrase) "0.28" else "0.25"
        CompiledWakePhrase(
            displayPhrase = displayPhrase,
            romanizedPhrase = syllables.joinToString(" "),
            tokens = tokens,
            sherpaKeyword = "${tokens.joinToString(" ")} :$boost #$threshold @$displayPhrase",
        )
    }

    fun validationError(phrase: String): String? = compile(phrase).exceptionOrNull()?.message

    private companion object {
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
        val PINYIN_SYLLABLE = Regex("[a-züāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜńňǹḿ]+")
    }
}

internal fun validateLocalWakePhrase(value: String): String? {
    val codePoints = value.codePointCount(0, value.length)
    return when {
        codePoints < 2 -> "唤醒词至少需要 2 个汉字"
        codePoints > 6 -> "为了保证唤醒速度，唤醒词不能超过 6 个汉字"
        value.codePoints().anyMatch { Character.UnicodeScript.of(it) != Character.UnicodeScript.HAN } ->
            "本地唤醒词目前只支持中文汉字"
        else -> null
    }
}

internal fun parseTokenLine(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null
    val fields = trimmed.split(WHITESPACE)
    if (fields.size != 2 || fields[1].toIntOrNull() == null) return null
    return fields[0].takeUnless { it.startsWith('<') }
}

internal fun segmentPinyinSyllable(
    syllable: String,
    availableTokens: Set<String>,
): List<String>? {
    val normalized = Normalizer.normalize(syllable, Normalizer.Form.NFC)
    val candidates = availableTokens
        .asSequence()
        .filter { token -> token.isNotBlank() && normalized.contains(token) }
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        .toList()
    val memo = mutableMapOf<Int, List<String>?>()

    fun solve(offset: Int): List<String>? {
        if (offset == normalized.length) return emptyList()
        if (offset in memo) return memo[offset]
        for (token in candidates) {
            if (!normalized.startsWith(token, offset)) continue
            val suffix = solve(offset + token.length) ?: continue
            return (listOf(token) + suffix).also { memo[offset] = it }
        }
        memo[offset] = null
        return null
    }

    return solve(0)
}

private val WHITESPACE = Regex("\\s+")
