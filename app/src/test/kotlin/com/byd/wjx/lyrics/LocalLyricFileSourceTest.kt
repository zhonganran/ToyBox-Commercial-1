package com.byd.wjx.lyrics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalLyricFileSourceTest {
    @Test
    fun `local lyric reader accepts normal files and rejects oversized siblings`() {
        val directory = Files.createTempDirectory("wjx-local-lyric").toFile()
        try {
            val song = File(directory, "song.mp3").apply { writeBytes(byteArrayOf(0)) }
            val lyric = File(directory, "song.lrc")
            val snapshot = MusicPlaybackSnapshot(mediaUri = song.absolutePath)

            lyric.writeText("[00:01.00]第一句\n[00:02.00]第二句")
            val loaded = LocalLyricFileSource().load(snapshot, "key")
            assertEquals(listOf("第一句", "第二句"), loaded?.lines?.map(LyricLine::text))

            lyric.writeBytes(ByteArray(2 * 1024 * 1024 + 1))
            assertNull(LocalLyricFileSource().load(snapshot, "key"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
