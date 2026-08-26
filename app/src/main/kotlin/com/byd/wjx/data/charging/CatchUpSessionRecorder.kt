package com.byd.wjx.data.charging

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.local.entity.BatterySnapshotEntity
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.BatteryHealthRepository
import com.byd.wjx.data.repository.ChargeRepository

internal data class CatchUpSessionRequest(
    val now: Long,
    val socStart: Int,
    val socEnd: Int,
    val energy: CatchUpEnergyResolution,
    val type: String,
    val cost: Double,
    val battery: BatteryReading,
    val charging: ChargingReading?,
    val latestSample: DiParsData?,
    val sourceSessionKey: String,
)

/** Owns the database writes and final baseline update for one reconstructed charge. */
internal class CatchUpSessionRecorder(
    private val chargeRepository: ChargeRepository,
    private val batteryHealthRepository: BatteryHealthRepository,
    private val stateStore: ChargingStateStore,
    private val parsReader: ParsReader,
) {
    suspend fun record(request: CatchUpSessionRequest): Long {
        val batterySnapshot = if (
            request.socEnd - request.socStart >= CATCH_UP_MIN_SOC_DELTA_FOR_SNAPSHOT
        ) {
            buildBatteryHealthSnapshot(request)
        } else {
            null
        }
        val chargeId = chargeRepository.insertCatchUpCharge(
            ChargeEntity(
                startTs = request.now,
                endTs = request.now,
                socStart = request.socStart,
                socEnd = request.socEnd,
                kwhCharged = request.energy.kwh,
                kwhChargedSoc = (request.socEnd - request.socStart) / 100.0 *
                    request.energy.effectiveCapacityKwh,
                type = request.type,
                cost = request.cost,
                status = "COMPLETED",
                gunState = request.charging?.gunConnectState?.takeIf(ChargeGunState::isConnected),
                detectionSource = request.energy.source,
                sourceSessionKey = request.sourceSessionKey,
            ),
            batterySnapshot,
        )
        stateStore.save(
            socPercent = request.socEnd,
            mileageKm = request.battery.lifetimeMileageKm,
            capacityKwh = request.charging?.chargingCapacityKwh,
            ts = request.now,
        )
        stateStore.setChargePending(false)
        return chargeId
    }

    private suspend fun buildBatteryHealthSnapshot(
        request: CatchUpSessionRequest,
    ): BatterySnapshotEntity {
        val sample = request.latestSample ?: runCatching {
            parsReader.fetchVehicleSnapshot()?.data
        }.getOrNull()
        val cellDelta = if (sample?.maxCellVoltage != null && sample.minCellVoltage != null) {
            sample.maxCellVoltage - sample.minCellVoltage
        } else {
            null
        }
        return BatterySnapshotEntity(
            timestamp = request.now,
            odometerKm = request.battery.lifetimeMileageKm?.toDouble(),
            socStart = request.socStart,
            socEnd = request.socEnd,
            kwhCharged = request.energy.kwh,
            calculatedCapacityKwh = batteryHealthRepository.calculateCapacity(
                request.energy.kwh,
                request.socStart,
                request.socEnd,
            ),
            sohPercent = request.battery.sohPercent?.toDouble(),
            cellDeltaV = cellDelta,
            batTempAvg = sample?.avgBatTemp?.toDouble(),
            chargeId = null,
        )
    }
}
