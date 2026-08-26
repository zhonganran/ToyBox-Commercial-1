package com.byd.wjx.helper

import java.io.File
import org.json.JSONObject

internal const val LOCK_STATE_CACHE_PATH = "/data/local/tmp/wjx_lock_state.txt"
internal const val SENTRY_RESERVATION_PATH = "/data/local/tmp/wjx_sentry_playback_reservation.json"

internal data class PersistedLockState(
    val state: Int,
    val changedAtMs: Long,
    val sampledAtMs: Long,
    val flashValue: Int?,
    val flashAtMs: Long,
    val pendingState: Int?,
    val pendingSinceMs: Long,
)

internal class LockSoundStateStore(
    private val file: File = File(LOCK_STATE_CACHE_PATH),
) {
    fun read(): PersistedLockState? {
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val state = json.optInt("state", Int.MIN_VALUE)
            if (state != LOCK_SOUND_DOOR_LOCKED && state != LOCK_SOUND_DOOR_UNLOCKED) {
                return@runCatching null
            }
            PersistedLockState(
                state = state,
                changedAtMs = json.optLong("changedAtMs", 0L),
                sampledAtMs = json.optLong("sampledAtMs", 0L),
                flashValue = json.optInt("flashValue", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                flashAtMs = json.optLong("flashAtMs", 0L),
                pendingState = json.optInt("pendingState", Int.MIN_VALUE)
                    .takeIf { it == LOCK_SOUND_DOOR_LOCKED || it == LOCK_SOUND_DOOR_UNLOCKED },
                pendingSinceMs = json.optLong("pendingSinceMs", 0L),
            )
        }.getOrNull()
    }

    fun write(state: PersistedLockState) {
        writeTextAtomically(
            file,
            JSONObject()
                .put("version", 2)
                .put("state", state.state)
                .put("changedAtMs", state.changedAtMs)
                .put("sampledAtMs", state.sampledAtMs)
                .put("flashValue", state.flashValue ?: JSONObject.NULL)
                .put("flashAtMs", state.flashAtMs)
                .put("pendingState", state.pendingState ?: JSONObject.NULL)
                .put("pendingSinceMs", state.pendingSinceMs)
                .toString(),
        )
    }
}

internal class SentryPlaybackReservationStore(
    private val file: File = File(SENTRY_RESERVATION_PATH),
) {
    fun readReservedAt(): Long {
        if (!file.isFile) return 0L
        return runCatching { JSONObject(file.readText()).optLong("reservedAtMs", 0L) }.getOrDefault(0L)
    }

    fun write(nowMs: Long, reason: String) {
        writeTextAtomically(
            file,
            JSONObject()
                .put("reservedAtMs", nowMs)
                .put("reason", reason.take(200))
                .toString(),
        )
    }
}

private fun writeTextAtomically(target: File, value: String) {
    val temporary = File("${target.path}.tmp")
    temporary.writeText(value)
    if (!temporary.renameTo(target)) {
        target.writeText(value)
        temporary.delete()
    }
}
