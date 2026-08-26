package com.byd.wjx.helper

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/** Returns the live channel and exclusive lock, or null when this daemon already has an owner. */
internal fun acquireSingleOwnerLock(path: String): Pair<FileChannel, FileLock>? = try {
    val channel = RandomAccessFile(path, "rw").channel
    val lock = channel.tryLock()
    if (lock == null) {
        runCatching { channel.close() }
        null
    } else {
        channel to lock
    }
} catch (_: OverlappingFileLockException) {
    null
} catch (_: Exception) {
    null
}
