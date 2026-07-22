package com.byd.wjx.lyrics

import android.os.SystemClock

object MediaSessionDumpParser {
    private val descriptionRegex = Regex("""description=([^,\n]+),\s*([^,\n]+)""")
    private val sessionStartRegex = Regex("""(?m)^\s*SessionRecord\{""")
    private val stateCodeRegex = Regex("""state=PlaybackState\s*\{state=(\d+)""")
    private val positionRegex = Regex("""\bposition=(-?\d+)""")
    private val speedRegex = Regex("""\bspeed=([-\d.]+)""")
    private val updatedRegex = Regex("""\bupdated=(\d+)""")
    private val durationRegex = Regex("""(?:android\.media\.metadata\.DURATION|duration)=(\d+)""")
    private val packageRegex = Regex("""(?:package|packageName)=([A-Za-z0-9_.]+)""")

    fun parse(raw: String): MusicPlaybackSnapshot? {
        if (raw.isBlank()) return null
        val snapshots = sessionBlocks(raw)
            .mapNotNull(::parseBlock)
            .filter { it.hasSong }
        val preferred = snapshots.filter {
            it.packageName.isBlank() || SupportedMusicPackages.isSupported(it.packageName)
        }.ifEmpty { snapshots }
        return preferred
            .sortedWith(
                compareByDescending<MusicPlaybackSnapshot> { it.playing }
                    .thenByDescending { it.sessionUpdatedElapsedMs }
                    .thenByDescending { SupportedMusicPackages.isSupported(it.packageName) }
                    .thenBy { it.title.isBlank() },
            )
            .firstOrNull()
    }

    private fun sessionBlocks(raw: String): List<String> {
        val starts = sessionStartRegex.findAll(raw).map { it.range.first }.toList()
        if (starts.isEmpty()) return listOf(raw)
        return starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: raw.length
            raw.substring(start, end)
        }
    }

    private fun parseBlock(block: String): MusicPlaybackSnapshot? {
        val desc = descriptionRegex.find(block)
        val title = firstValue(block, TITLE_KEYS)
            ?: desc?.groupValues?.getOrNull(1)?.trim()
            ?: return null
        val artist = firstValue(block, ARTIST_KEYS)
            ?: desc?.groupValues?.getOrNull(2)?.trim()
            ?: ""
        if (title.isBlank()) return null
        val stateCode = stateCodeRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val position = positionRegex.find(block)?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val speed = speedRegex.find(block)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        val updated = updatedRegex.find(block)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
        val duration = durationRegex.find(block)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val pkg = packageRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
        val mediaId = firstValue(block, MEDIA_ID_KEYS)
        val uri = firstValue(block, MEDIA_URI_KEYS)
        val metadataLyrics = lineValue(block, BYD_LYRICS)
        val timedLyrics = LyricRepository.parseBydMetadataLyrics(metadataLyrics, "media_session_dump")?.lines
        val realtimeLyrics = REALTIME_LYRIC_KEYS.mapNotNull { firstValue(block, listOf(it)) }
        return MusicPlaybackSnapshot(
            title = title,
            artist = artist,
            durationMs = duration,
            positionMs = position,
            playing = stateCode == 3,
            packageName = pkg,
            mediaId = mediaId,
            mediaUri = uri,
            metadataLyrics = metadataLyrics,
            timedLyrics = timedLyrics,
            realtimeLyrics = realtimeLyrics,
            anchorElapsedMs = updated ?: SystemClock.elapsedRealtime(),
            sessionUpdatedElapsedMs = updated ?: SystemClock.elapsedRealtime(),
            playbackSpeed = speed,
            source = MusicPlaybackSource.AdbFallback,
        )
    }

    private fun firstValue(block: String, keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key ->
            Regex("""(?:^|[\s,{])${Regex.escape(key)}=([^\n,}]+)""")
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

    private fun lineValue(block: String, key: String): String? =
        Regex("""${Regex.escape(key)}=([^\n]+)""")
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.removeSuffix(",")
            ?.takeIf { it.isNotBlank() }

    private val TITLE_KEYS = listOf(
        "android.media.metadata.TITLE",
        "title",
    )
    private val ARTIST_KEYS = listOf(
        "android.media.metadata.ARTIST",
        "artist",
    )
    private const val BYD_LYRICS = "com.byd.mediacenter.metadata.LYRICS"
    private val REALTIME_LYRIC_KEYS = listOf("lrc_line_1", "lrc_line_2", "lrc_line_3")
    private val MEDIA_ID_KEYS = listOf(
        "android.media.metadata.MEDIA_ID",
        "mediaId",
        "media_id",
    )
    private val MEDIA_URI_KEYS = listOf(
        "android.media.metadata.MEDIA_URI",
        "com.byd.mediacenter.metadata.MEDIA_URI",
        "com.byd.mediacenter.metadata.FILE_PATH",
        "com.byd.mediacenter.metadata.PATH",
    )
}

class MusicPlaybackPoller(
    private val adb: com.byd.wjx.data.autoservice.AdbOnDeviceClient,
) {
    suspend fun pollOnce(): MusicPlaybackSnapshot? =
        adb.readMediaSessionDump()?.let(MediaSessionDumpParser::parse)
}
