package com.byd.wjx.data.tts

import android.content.Context
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class VoxCpmDemoRepository @Inject constructor(
    @ApplicationContext context: Context,
    client: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val generationClient = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun generate(text: String, voiceInstruction: String): File = withContext(Dispatchers.IO) {
        val normalizedText = text.trim()
        val normalizedInstruction = voiceInstruction.trim()
        require(normalizedText.isNotBlank()) { "请输入需要合成的文字" }
        require(normalizedText.length <= MAX_TEXT_LENGTH) { "单次最多输入 $MAX_TEXT_LENGTH 个字符" }
        require(normalizedInstruction.length <= MAX_INSTRUCTION_LENGTH) {
            "音色描述最多输入 $MAX_INSTRUCTION_LENGTH 个字符"
        }

        WjxFileLogger.i(
            TAG,
            "generation requested textLength=${normalizedText.length} instructionLength=${normalizedInstruction.length}",
        )
        try {
            val eventId = submitGeneration(normalizedText, normalizedInstruction)
            val audioUrl = awaitGeneration(eventId)
            val file = downloadAudio(audioUrl)
            cleanupOldFiles(file)
            WjxFileLogger.i(TAG, "generation complete file=${file.name} size=${file.length()}")
            file
        } catch (error: SocketTimeoutException) {
            WjxFileLogger.w(TAG, "generation timeout: ${error.message}", error)
            throw IOException("公开演示服务生成超时，请稍后重试", error)
        } catch (error: IOException) {
            WjxFileLogger.w(TAG, "generation failed: ${error.message}", error)
            throw error
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "generation failed: ${error.message}", error)
            throw IOException(error.message ?: "公开演示服务调用失败", error)
        }
    }

    private fun submitGeneration(text: String, voiceInstruction: String): String {
        val payload = JSONObject().put(
            "data",
            JSONArray()
                .put(text)
                .put(voiceInstruction)
                .put(JSONObject.NULL)
                .put(false)
                .put("")
                .put(DEFAULT_CFG_VALUE)
                .put(true)
                .put(false),
        )
        val request = Request.Builder()
            .url("$BASE_URL/gradio_api/call/generate")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        generationClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw publicDemoHttpError(response.code)
            return parseVoxCpmEventId(body)
        }
    }

    private fun awaitGeneration(eventId: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/gradio_api/call/generate/$eventId")
            .header("Accept", "text/event-stream")
            .build()
        generationClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw publicDemoHttpError(response.code)
            return parseVoxCpmAudioUrl(body)
        }
    }

    private fun downloadAudio(url: String): File {
        validateAudioUrl(url)
        val request = Request.Builder().url(url).build()
        generationClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw publicDemoHttpError(response.code)
            val body = response.body ?: throw IOException("公开演示服务没有返回音频")
            val contentLength = body.contentLength()
            if (contentLength > MAX_AUDIO_BYTES) throw IOException("生成音频文件过大")

            val directory = File(appContext.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
            val target = File(directory, "voxcpm-${System.currentTimeMillis()}.mp3")
            val temporary = File(directory, "${target.name}.download")
            try {
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_AUDIO_BYTES) throw IOException("生成音频文件过大")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (temporary.length() <= 0L) throw IOException("公开演示服务返回了空音频")
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = false)
                    temporary.delete()
                }
                return target
            } catch (error: Exception) {
                runCatching { temporary.delete() }
                runCatching { target.delete() }
                throw error
            }
        }
    }

    private fun validateAudioUrl(url: String) {
        val parsed = url.toHttpUrlOrNull() ?: throw IOException("公开演示服务返回了无效音频地址")
        if (
            parsed.scheme != "https" ||
            parsed.host != DEMO_HOST ||
            !parsed.encodedPath.startsWith("/gradio_api/file=")
        ) {
            throw IOException("公开演示服务返回了不受信任的音频地址")
        }
    }

    private fun cleanupOldFiles(current: File) {
        val files = current.parentFile?.listFiles()?.filter(File::isFile).orEmpty()
        files.sortedByDescending(File::lastModified)
            .drop(MAX_CACHE_FILES)
            .forEach { file -> runCatching { file.delete() } }
    }

    private fun publicDemoHttpError(code: Int): IOException = when (code) {
        429 -> IOException("公开演示服务请求过多，请稍后重试")
        502, 503, 504 -> IOException("公开演示服务正在休眠或繁忙，请稍后重试")
        else -> IOException("公开演示服务请求失败：HTTP $code")
    }

    companion object {
        const val MAX_TEXT_LENGTH = 240
        const val MAX_INSTRUCTION_LENGTH = 120
        private const val BASE_URL = "https://openbmb-voxcpm-demo.hf.space"
        private const val DEMO_HOST = "openbmb-voxcpm-demo.hf.space"
        private const val CACHE_DIRECTORY = "voxcpm-tts"
        private const val MAX_CACHE_FILES = 8
        private const val MAX_AUDIO_BYTES = 20L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val GENERATION_TIMEOUT_SECONDS = 180L
        private const val CALL_TIMEOUT_SECONDS = 210L
        private const val DEFAULT_CFG_VALUE = 2.0
        private const val TAG = "VoxCpmDemo"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun parseVoxCpmEventId(body: String): String {
    val eventId = runCatching { JSONObject(body).optString("event_id").trim() }.getOrDefault("")
    if (eventId.isBlank()) throw IOException("公开演示服务没有返回任务编号")
    if (!eventId.matches(Regex("[A-Za-z0-9_-]{8,128}"))) {
        throw IOException("公开演示服务返回了无效任务编号")
    }
    return eventId
}

internal fun parseVoxCpmAudioUrl(body: String): String {
    var currentEvent = ""
    body.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("event:") -> currentEvent = line.substringAfter(':').trim()
            line.startsWith("data:") && currentEvent == "complete" -> {
                val data = line.substringAfter(':').trim()
                val url = runCatching {
                    JSONArray(data).optJSONObject(0)?.optString("url")?.trim().orEmpty()
                }.getOrDefault("")
                if (url.isNotBlank()) return url
            }
            line.startsWith("data:") && currentEvent == "error" -> {
                val message = line.substringAfter(':').trim().trim('"')
                throw IOException(message.ifBlank { "公开演示服务生成失败" })
            }
        }
    }
    throw IOException("公开演示服务没有返回生成音频")
}
