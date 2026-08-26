package com.byd.wjx.data.vehicle

import android.os.SystemClock
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AutoDoorHardwareGateway @Inject constructor(
    private val helperBootstrap: HelperBootstrap,
    private val helper: HelperClient,
) {
    suspend fun ensureReady(operation: String): Boolean =
        runCatching { helperBootstrap.ensureCurrent() }
            .onFailure { WjxFileLogger.w(TAG, "$operation failed: ${it.message}", it) }
            .getOrDefault(false)

    suspend fun readCommandReadings(target: AutoDoorTarget): AutoDoorCommandReadings {
        val speedRaw = readRaw(AutoDoorCatalog.DEV_SPEED, AutoDoorCatalog.FID_SPEED, 7)
        val slideReadyRaw = readRaw(
            AutoDoorCatalog.DEV_BODYWORK,
            AutoDoorCatalog.FID_ELEC_SLIDE_STATE,
            5,
        )
        val coverRaw = readRaw(AutoDoorCatalog.DEV_CHARGING, target.coverFid, 5)
        val state = readState(target)
        return AutoDoorCommandReadings(
            speedRaw = speedRaw,
            speedKmh = speedRaw?.toInt()?.let(SentinelDecoder::parseFloatFromShellInt),
            slideReadyRaw = slideReadyRaw,
            slideReady = slideReadyRaw?.toInt()?.let(SentinelDecoder::decodeInt),
            coverRaw = coverRaw,
            coverState = coverRaw?.toInt()?.let(SentinelDecoder::decodeInt),
            beforeState = state.action,
            beforePosition = state.percent,
        )
    }

    suspend fun readState(target: AutoDoorTarget): AutoDoorStateReadings = AutoDoorStateReadings(
        action = readDecoded(AutoDoorCatalog.DEV_BODYWORK, target.stateFid, 5),
        percent = readDecoded(AutoDoorCatalog.DEV_BODYWORK, target.positionFid, 5),
    )

    suspend fun writeCommand(target: AutoDoorTarget, commandValue: Int): AutoDoorWriteResult {
        val startedAt = SystemClock.elapsedRealtime()
        val status = runCatching {
            helper.writeStatus(AutoDoorCatalog.DEV_BODYWORK, target.writeFid, commandValue)
        }.onFailure {
            WjxFileLogger.w(TAG, "write failed ${target.key}/$commandValue: ${it.message}", it)
        }.getOrNull()
        return AutoDoorWriteResult(
            status = status,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private suspend fun readRaw(dev: Int, fid: Int, tx: Int): Long? =
        runCatching { helper.read(dev, fid, tx) }
            .onFailure {
                WjxFileLogger.w(
                    TAG,
                    "read failed dev=$dev fid=${AutoDoorCatalog.fidHex(fid)} tx=$tx: ${it.message}",
                    it,
                )
            }
            .getOrNull()

    private suspend fun readDecoded(dev: Int, fid: Int, tx: Int): Int? =
        readRaw(dev, fid, tx)?.toInt()?.let(SentinelDecoder::decodeInt)

    private companion object {
        const val TAG = "AutoDoorHardwareGateway"
    }
}
