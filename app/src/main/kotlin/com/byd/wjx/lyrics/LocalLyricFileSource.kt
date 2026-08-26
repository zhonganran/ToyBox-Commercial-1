package com.byd.wjx.lyrics

import android.net.Uri
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import java.net.URLDecoder

internal class LocalLyricFileSource {
    fun load(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val songFile = resolveMediaFile(snapshot.mediaUri) ?: return null
        val lyricFile = findSiblingLrc(songFile) ?: return null
        val bytes = runCatching { lyricFile.readBytes() }.getOrNull() ?: return null
        val utf8 = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        val raw = if (utf8?.contains("[") == true) {
            utf8
        } else {
            runCatching { bytes.toString(charset("GBK")) }.getOrNull()
        } ?: return null
        val lines = LyricParsers.parseLrcText(raw)
        if (lines.isNotEmpty()) {
            WjxFileLogger.i(TAG, "loaded ${lines.size} lines from ${lyricFile.absolutePath}")
        }
        return lines.takeIf(List<LyricLine>::isNotEmpty)?.let { LyricsResult(key, it) }
    }

    private fun resolveMediaFile(mediaUri: String?): File? {
        val raw = mediaUri?.trim()?.takeIf(String::isNotBlank) ?: return null
        val path = when {
            raw.startsWith("file://", ignoreCase = true) -> runCatching { Uri.parse(raw).path }.getOrNull()
            raw.startsWith("/") -> raw
            else -> null
        } ?: return null
        val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
        return File(decoded).takeIf { it.isFile && it.canRead() }
    }

    private fun findSiblingLrc(songFile: File): File? {
        val parent = songFile.parentFile ?: return null
        val expectedName = "${songFile.nameWithoutExtension}.lrc"
        val direct = File(parent, expectedName)
        if (direct.isFile && direct.canRead()) return direct
        return parent.listFiles()?.firstOrNull {
            it.isFile && it.canRead() && it.name.equals(expectedName, ignoreCase = true)
        }
    }

    private companion object {
        const val TAG = "LocalLyricFileSource"
    }
}
