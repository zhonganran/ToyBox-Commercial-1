package com.byd.wjx.data.automation

import android.content.Context
import android.content.SharedPreferences
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.MirrorPosition
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val MIRROR_TRANSIT_TOLERANCE = 1f

internal data class MirrorTiltRequest(
    val leftEnabled: Boolean,
    val rightEnabled: Boolean,
    val leftHorizontalOffset: Float,
    val leftVerticalOffset: Float,
    val rightHorizontalOffset: Float,
    val rightVerticalOffset: Float,
) {
    fun isValid(): Boolean =
        (leftEnabled || rightEnabled) &&
            allOffsets().all { it.isFinite() && it in OFFSET_MIN..OFFSET_MAX } &&
            (!leftEnabled || leftHasEffectiveOffset()) &&
            (!rightEnabled || rightHasEffectiveOffset())

    private fun allOffsets(): List<Float> = listOf(
        leftHorizontalOffset,
        leftVerticalOffset,
        rightHorizontalOffset,
        rightVerticalOffset,
    )

    private fun leftHasEffectiveOffset(): Boolean =
        abs(leftHorizontalOffset) >= MIN_EFFECTIVE_OFFSET ||
            abs(leftVerticalOffset) >= MIN_EFFECTIVE_OFFSET

    private fun rightHasEffectiveOffset(): Boolean =
        abs(rightHorizontalOffset) >= MIN_EFFECTIVE_OFFSET ||
            abs(rightVerticalOffset) >= MIN_EFFECTIVE_OFFSET

    companion object {
        const val OFFSET_MIN = -30f
        const val OFFSET_MAX = 30f
        const val DEFAULT_HORIZONTAL_OFFSET = 0f
        const val DEFAULT_VERTICAL_OFFSET = 10f
        private const val MIN_EFFECTIVE_OFFSET = 0.5f

        fun parse(payload: String?): MirrorTiltRequest? {
            val json = parseAutomationActionPayload(payload) ?: return null
            return MirrorTiltRequest(
                leftEnabled = json.optBoolean(KEY_LEFT_ENABLED, true),
                rightEnabled = json.optBoolean(KEY_RIGHT_ENABLED, true),
                leftHorizontalOffset = json.optDouble(
                    KEY_LEFT_HORIZONTAL_OFFSET,
                    DEFAULT_HORIZONTAL_OFFSET.toDouble(),
                ).toFloat(),
                leftVerticalOffset = json.optDouble(
                    KEY_LEFT_VERTICAL_OFFSET,
                    json.optDouble(LEGACY_KEY_LEFT_OFFSET, DEFAULT_VERTICAL_OFFSET.toDouble()),
                ).toFloat(),
                rightHorizontalOffset = json.optDouble(
                    KEY_RIGHT_HORIZONTAL_OFFSET,
                    DEFAULT_HORIZONTAL_OFFSET.toDouble(),
                ).toFloat(),
                rightVerticalOffset = json.optDouble(
                    KEY_RIGHT_VERTICAL_OFFSET,
                    json.optDouble(LEGACY_KEY_RIGHT_OFFSET, DEFAULT_VERTICAL_OFFSET.toDouble()),
                ).toFloat(),
            ).takeIf(MirrorTiltRequest::isValid)
        }

        fun payload(request: MirrorTiltRequest): String = JSONObject().apply {
            put(KEY_LEFT_ENABLED, request.leftEnabled)
            put(KEY_RIGHT_ENABLED, request.rightEnabled)
            put(KEY_LEFT_HORIZONTAL_OFFSET, request.leftHorizontalOffset.toDouble())
            put(KEY_LEFT_VERTICAL_OFFSET, request.leftVerticalOffset.toDouble())
            put(KEY_RIGHT_HORIZONTAL_OFFSET, request.rightHorizontalOffset.toDouble())
            put(KEY_RIGHT_VERTICAL_OFFSET, request.rightVerticalOffset.toDouble())
        }.toString()

        private const val KEY_LEFT_ENABLED = "leftEnabled"
        private const val KEY_RIGHT_ENABLED = "rightEnabled"
        private const val KEY_LEFT_HORIZONTAL_OFFSET = "leftHorizontalOffset"
        private const val KEY_LEFT_VERTICAL_OFFSET = "leftVerticalOffset"
        private const val KEY_RIGHT_HORIZONTAL_OFFSET = "rightHorizontalOffset"
        private const val KEY_RIGHT_VERTICAL_OFFSET = "rightVerticalOffset"
        private const val LEGACY_KEY_LEFT_OFFSET = "leftOffset"
        private const val LEGACY_KEY_RIGHT_OFFSET = "rightOffset"
    }
}

internal data class ReverseMirrorSnapshot(
    val original: MirrorPosition,
    val target: MirrorPosition,
    val leftEnabled: Boolean,
    val rightEnabled: Boolean,
    val savedAtMs: Long,
)

@Singleton
internal class ReverseMirrorSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): ReverseMirrorSnapshot? {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        return runCatching {
            ReverseMirrorSnapshot(
                original = readPosition(ORIGINAL_PREFIX),
                target = readPosition(TARGET_PREFIX),
                leftEnabled = prefs.getBoolean(KEY_LEFT_ENABLED, false),
                rightEnabled = prefs.getBoolean(KEY_RIGHT_ENABLED, false),
                savedAtMs = prefs.getLong(KEY_SAVED_AT, 0L),
            )
        }.getOrNull()?.takeIf {
            it.original.isSane() && it.target.isSane() && (it.leftEnabled || it.rightEnabled)
        }
    }

    fun save(snapshot: ReverseMirrorSnapshot): Boolean =
        prefs.edit()
            .putPosition(ORIGINAL_PREFIX, snapshot.original)
            .putPosition(TARGET_PREFIX, snapshot.target)
            .putBoolean(KEY_LEFT_ENABLED, snapshot.leftEnabled)
            .putBoolean(KEY_RIGHT_ENABLED, snapshot.rightEnabled)
            .putLong(KEY_SAVED_AT, snapshot.savedAtMs)
            .putBoolean(KEY_ACTIVE, true)
            .commit()

    fun clear(): Boolean = prefs.edit().clear().commit()

    private fun readPosition(prefix: String): MirrorPosition = MirrorPosition(
        leftHorizontal = prefs.getFloat("${prefix}_lh", Float.NaN),
        leftVertical = prefs.getFloat("${prefix}_lv", Float.NaN),
        rightHorizontal = prefs.getFloat("${prefix}_rh", Float.NaN),
        rightVertical = prefs.getFloat("${prefix}_rv", Float.NaN),
    )

    private fun SharedPreferences.Editor.putPosition(
        prefix: String,
        position: MirrorPosition,
    ): SharedPreferences.Editor =
        putFloat("${prefix}_lh", position.leftHorizontal)
            .putFloat("${prefix}_lv", position.leftVertical)
            .putFloat("${prefix}_rh", position.rightHorizontal)
            .putFloat("${prefix}_rv", position.rightVertical)

    companion object {
        const val PREFS_NAME = "wjx_reverse_mirror_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_LEFT_ENABLED = "left_enabled"
        private const val KEY_RIGHT_ENABLED = "right_enabled"
        private const val KEY_SAVED_AT = "saved_at"
        private const val ORIGINAL_PREFIX = "original"
        private const val TARGET_PREFIX = "target"
    }
}

@Singleton
class ReverseMirrorController @Inject internal constructor(
    private val helperClient: HelperClient,
    private val snapshotStore: ReverseMirrorSnapshotStore,
    private val vehicleStateRepository: VehicleStateRepository,
) {
    private val operationMutex = Mutex()

    fun hasPendingRestore(): Boolean = snapshotStore.read() != null

    suspend fun tilt(
        payload: String?,
        data: DiParsData?,
        preview: Boolean,
    ): DispatchResult = operationMutex.withLock {
        val request = MirrorTiltRequest.parse(payload)
            ?: return@withLock DispatchResult(false, "后视镜下翻参数无效")
        val liveData = resolveLiveData(data)
        mirrorTiltBlockReason(liveData, preview)?.let {
            return@withLock DispatchResult(false, it)
        }

        val current = helperClient.readMirrorPosition()
            ?: return@withLock DispatchResult(false, "当前车型未读取到后视镜位置")
        snapshotStore.read()?.let { previous ->
            if (current.matchesSelected(previous.target, previous)) {
                return@withLock DispatchResult(true, "后视镜已处于下翻位置")
            }
            if (
                current.isBetweenSelected(previous.original, previous.target, previous) &&
                !current.matchesSelected(previous.original, previous)
            ) {
                return@withLock DispatchResult(false, "后视镜仍在移动，已保留原位置快照")
            }
            snapshotStore.clear()
            WjxFileLogger.i(TAG, "discard stale snapshot because mirror position changed")
        }

        val target = current.withOffsets(
            leftEnabled = request.leftEnabled,
            rightEnabled = request.rightEnabled,
            leftHorizontalOffset = request.leftHorizontalOffset,
            leftVerticalOffset = request.leftVerticalOffset,
            rightHorizontalOffset = request.rightHorizontalOffset,
            rightVerticalOffset = request.rightVerticalOffset,
        )
        if (current.matchesTarget(target, request.leftEnabled, request.rightEnabled, POSITION_TOLERANCE)) {
            return@withLock DispatchResult(false, "后视镜已到调节边界，请减小或反向调整偏移")
        }

        val snapshot = ReverseMirrorSnapshot(
            original = current,
            target = target,
            leftEnabled = request.leftEnabled,
            rightEnabled = request.rightEnabled,
            savedAtMs = System.currentTimeMillis(),
        )
        if (!snapshotStore.save(snapshot)) {
            return@withLock DispatchResult(false, "无法保存原位置，已阻止后视镜下翻")
        }
        if (!helperClient.applyMirrorPosition(target)) {
            return@withLock DispatchResult(false, "后视镜位置命令发送失败，原位置已保留")
        }
        val verified = awaitPosition(target, request.leftEnabled, request.rightEnabled)
        WjxFileLogger.i(
            TAG,
            "tilt accepted verified=$verified left=${request.leftEnabled} right=${request.rightEnabled}",
        )
        if (verified) DispatchResult(true) else DispatchResult(false, "命令已发送，但未确认后视镜移动")
    }

    suspend fun restore(data: DiParsData?): DispatchResult = operationMutex.withLock {
        val snapshot = snapshotStore.read()
            ?: return@withLock DispatchResult(true, "没有待恢复的后视镜位置")
        mirrorRestoreBlockReason(resolveLiveData(data))?.let {
            return@withLock DispatchResult(false, it)
        }
        val current = helperClient.readMirrorPosition()
            ?: return@withLock DispatchResult(false, "无法读取当前位置，已保留恢复快照")

        if (current.matchesSelected(snapshot.original, snapshot)) {
            snapshotStore.clear()
            return@withLock DispatchResult(true, "后视镜已经恢复")
        }
        val snapshotAgeMs = (System.currentTimeMillis() - snapshot.savedAtMs).coerceAtLeast(0L)
        val movingTowardTarget = snapshotAgeMs <= MOVEMENT_GRACE_MS &&
            current.isBetweenSelected(snapshot.original, snapshot.target, snapshot)
        if (!current.matchesSelected(snapshot.target, snapshot) && !movingTowardTarget) {
            snapshotStore.clear()
            WjxFileLogger.i(TAG, "skip restore because driver adjusted mirror after tilt")
            return@withLock DispatchResult(true, "检测到手动调节，已保留当前位置")
        }
        val restoreTarget = current.restoreSelectedFrom(
            original = snapshot.original,
            leftEnabled = snapshot.leftEnabled,
            rightEnabled = snapshot.rightEnabled,
        )
        if (!helperClient.applyMirrorPosition(restoreTarget)) {
            return@withLock DispatchResult(false, "恢复命令发送失败，恢复快照已保留")
        }
        val verified = awaitPosition(
            restoreTarget,
            snapshot.leftEnabled,
            snapshot.rightEnabled,
        )
        if (verified) snapshotStore.clear()
        WjxFileLogger.i(TAG, "restore accepted verified=$verified")
        if (verified) DispatchResult(true) else DispatchResult(false, "恢复命令已发送，但未确认恢复完成")
    }

    private fun resolveLiveData(data: DiParsData?): DiParsData? =
        data ?: vehicleStateRepository.actionSnapshot.value?.data
        ?: vehicleStateRepository.fullSnapshot.value?.data

    private suspend fun awaitPosition(
        target: MirrorPosition,
        leftEnabled: Boolean,
        rightEnabled: Boolean,
    ): Boolean {
        repeat(VERIFY_ATTEMPTS) {
            delay(VERIFY_INTERVAL_MS)
            val position = helperClient.readMirrorPosition() ?: return@repeat
            if (position.matchesTarget(target, leftEnabled, rightEnabled, VERIFY_TOLERANCE)) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "ReverseMirror"
        private const val REVERSE_GEAR = 2
        private const val PARK_GEAR = 1
        private const val MAX_TILT_SPEED_KMH = 5
        private const val MAX_RESTORE_SPEED_KMH = 15
        private const val POSITION_TOLERANCE = 0.5f
        private const val VERIFY_TOLERANCE = 1f
        private const val VERIFY_ATTEMPTS = 20
        private const val VERIFY_INTERVAL_MS = 300L
        private const val MOVEMENT_GRACE_MS = 15_000L

        internal fun mirrorTiltBlockReason(data: DiParsData?, preview: Boolean): String? {
            val gear = data?.gear ?: return "当前档位未知，已阻止后视镜动作"
            val speed = data.speed ?: return "当前车速未知，已阻止后视镜动作"
            if (speed > MAX_TILT_SPEED_KMH) return "当前车速 $speed km/h，已阻止后视镜下翻"
            return when {
                preview && gear != PARK_GEAR -> "测试下翻只能在P挡执行"
                !preview && gear != REVERSE_GEAR -> "后视镜下翻只能在倒车挡执行"
                else -> null
            }
        }

        internal fun mirrorRestoreBlockReason(data: DiParsData?): String? {
            val speed = data?.speed ?: return "当前车速未知，已延后恢复后视镜"
            return if (speed > MAX_RESTORE_SPEED_KMH) {
                "当前车速 $speed km/h，已延后恢复后视镜"
            } else {
                null
            }
        }
    }
}

private fun MirrorPosition.matchesSelected(
    expected: MirrorPosition,
    snapshot: ReverseMirrorSnapshot,
): Boolean = matchesTarget(
    expected = expected,
    leftEnabled = snapshot.leftEnabled,
    rightEnabled = snapshot.rightEnabled,
    tolerance = 0.5f,
)

private fun MirrorPosition.matchesTarget(
    expected: MirrorPosition,
    leftEnabled: Boolean,
    rightEnabled: Boolean,
    tolerance: Float,
): Boolean {
    val leftMatches = !leftEnabled || (
        abs(leftHorizontal - expected.leftHorizontal) <= tolerance &&
            abs(leftVertical - expected.leftVertical) <= tolerance
        )
    val rightMatches = !rightEnabled || (
        abs(rightHorizontal - expected.rightHorizontal) <= tolerance &&
            abs(rightVertical - expected.rightVertical) <= tolerance
        )
    return leftMatches && rightMatches
}

private fun MirrorPosition.isBetweenSelected(
    start: MirrorPosition,
    end: MirrorPosition,
    snapshot: ReverseMirrorSnapshot,
): Boolean {
    val leftBetween = !snapshot.leftEnabled || (
        leftHorizontal.isBetween(start.leftHorizontal, end.leftHorizontal) &&
            leftVertical.isBetween(start.leftVertical, end.leftVertical)
        )
    val rightBetween = !snapshot.rightEnabled || (
        rightHorizontal.isBetween(start.rightHorizontal, end.rightHorizontal) &&
            rightVertical.isBetween(start.rightVertical, end.rightVertical)
        )
    return leftBetween && rightBetween
}

private fun Float.isBetween(start: Float, end: Float): Boolean {
    val lower = minOf(start, end) - MIRROR_TRANSIT_TOLERANCE
    val upper = maxOf(start, end) + MIRROR_TRANSIT_TOLERANCE
    return this in lower..upper
}
