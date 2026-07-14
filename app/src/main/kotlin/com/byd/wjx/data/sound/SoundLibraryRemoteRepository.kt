package com.byd.wjx.data.sound

import com.byd.wjx.BuildConfig
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundDestination
import com.byd.wjx.storage.SoundLibrary
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class SoundLibraryRemoteRepository @Inject constructor(
    private val client: OkHttpClient,
    private val soundLibrary: SoundLibrary,
) {
    fun loadCategories(): List<RemoteSoundCategory> {
        val url = "${baseUrl()}/categories"
        WjxFileLogger.i(TAG, "load categories url=$url")
        val json = getJson(url)
        val items = json.optJSONArray("items") ?: return emptyList()
        val categories = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            RemoteSoundCategory(
                name = item.optString("name"),
                total = item.optInt("total"),
            )
        }.filter { it.name.isNotBlank() }
        WjxFileLogger.i(TAG, "load categories count=${categories.size}")
        return categories
    }

    fun searchSounds(
        query: String,
        category: String?,
        page: Int,
        pageSize: Int = 30,
    ): RemoteSoundPage {
        val params = buildList {
            add("page=${page.coerceAtLeast(1)}")
            add("pageSize=${pageSize.coerceIn(1, 50)}")
            if (query.isNotBlank()) add("q=${query.urlEncode()}")
            if (!category.isNullOrBlank()) add("category=${category.urlEncode()}")
        }.joinToString("&")
        val url = "${baseUrl()}?$params"
        WjxFileLogger.i(TAG, "search sounds page=$page pageSize=$pageSize category=${category.orEmpty()} queryLength=${query.length}")
        val json = getJson(url)
        val items = json.optJSONArray("items")
        val sounds = if (items == null) {
            emptyList()
        } else {
            (0 until items.length()).mapNotNull { index ->
                items.optJSONObject(index)?.toRemoteSound()
            }
        }
        val pageResult = RemoteSoundPage(
            items = sounds,
            page = json.optInt("page", page),
            total = json.optInt("total", sounds.size),
            hasMore = json.optBoolean("hasMore", false),
        )
        WjxFileLogger.i(TAG, "search sounds result items=${pageResult.items.size} total=${pageResult.total} hasMore=${pageResult.hasMore}")
        return pageResult
    }

    fun download(sound: RemoteSound, destination: SoundDestination): DownloadedSound {
        WjxFileLogger.i(TAG, "download sound id=${sound.id} destination=$destination name=${sound.name} url=${sound.downloadUrl}")
        val request = Request.Builder().url(sound.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("下载失败：空响应")
            val ext = extensionFromHeaders(
                contentDisposition = response.header("Content-Disposition"),
                url = sound.downloadUrl,
                contentType = response.header("Content-Type"),
            )
            val file = soundLibrary.saveSound(destination, sound.name, ext, body.byteStream())
            val result = DownloadedSound(
                fileName = file.name,
                sizeBytes = file.length(),
                destination = destination,
            )
            WjxFileLogger.i(TAG, "download sound success id=${sound.id} destination=$destination file=${result.fileName} size=${result.sizeBytes}")
            return result
        }
    }

    fun previewToCache(sound: RemoteSound, cacheDir: File): File {
        val url = sound.previewUrl.ifBlank { sound.downloadUrl }
        WjxFileLogger.i(TAG, "preview sound id=${sound.id} name=${sound.name} url=$url")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("试听失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("试听失败：空响应")
            val ext = extensionFromHeaders(
                contentDisposition = response.header("Content-Disposition"),
                url = sound.previewUrl.ifBlank { sound.downloadUrl },
                contentType = response.header("Content-Type"),
            )
            cacheDir.mkdirs()
            val target = File(cacheDir, "preview-${sound.id}.$ext")
            target.outputStream().use { output -> body.byteStream().copyTo(output) }
            WjxFileLogger.i(TAG, "preview sound cached id=${sound.id} file=${target.absolutePath} size=${target.length()}")
            return target
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                WjxFileLogger.w(TAG, "json request failed code=${response.code} url=$url")
                throw IOException("接口请求失败：HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) WjxFileLogger.w(TAG, "json request empty url=$url")
            return JSONObject(body)
        }
    }

    private fun JSONObject.toRemoteSound(): RemoteSound {
        val tagsJson = optJSONArray("tags")
        val tags = if (tagsJson == null) {
            emptyList()
        } else {
            (0 until tagsJson.length()).mapNotNull { tagsJson.optString(it).takeIf(String::isNotBlank) }
        }
        return RemoteSound(
            id = optLong("id"),
            name = optString("name").ifBlank { "未命名音效" },
            category = optString("category"),
            tags = tags,
            fileSize = optLong("fileSize"),
            durationMs = optLong("durationMs"),
            downloadCount = optLong("downloadCount"),
            downloadUrl = optString("downloadUrl"),
            previewUrl = optString("previewUrl").ifBlank { optString("downloadUrl") },
        )
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun extensionFromHeaders(contentDisposition: String?, url: String, contentType: String?): String {
        val filenameStar = contentDisposition
            ?.substringAfter("filename*=UTF-8''", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore(";")
        val filename = filenameStar
            ?: contentDisposition
                ?.substringAfter("filename=", missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
                ?.trim('"')
        val fromName = filename
            ?.substringBefore("?")
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in SoundLibrary.SUPPORTED_EXT }
        if (fromName != null) return fromName

        val fromUrl = url
            .substringBefore("?")
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it in SoundLibrary.SUPPORTED_EXT }
        if (fromUrl != null) return fromUrl

        return when {
            contentType?.contains("mpeg", ignoreCase = true) == true -> "mp3"
            contentType?.contains("mp4", ignoreCase = true) == true -> "m4a"
            contentType?.contains("ogg", ignoreCase = true) == true -> "ogg"
            contentType?.contains("flac", ignoreCase = true) == true -> "flac"
            else -> "wav"
        }
    }

    companion object {
        private const val TAG = "SoundRemoteRepo"
        private fun baseUrl(): String = BuildConfig.SOUND_API_BASE_URL.trim().trimEnd('/')
    }
}

data class RemoteSoundCategory(
    val name: String,
    val total: Int,
)

data class RemoteSound(
    val id: Long,
    val name: String,
    val category: String,
    val tags: List<String>,
    val fileSize: Long,
    val durationMs: Long,
    val downloadCount: Long,
    val downloadUrl: String,
    val previewUrl: String,
)

data class RemoteSoundPage(
    val items: List<RemoteSound>,
    val page: Int,
    val total: Int,
    val hasMore: Boolean,
)

data class DownloadedSound(
    val fileName: String,
    val sizeBytes: Long,
    val destination: SoundDestination,
)
