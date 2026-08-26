package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.VehicleBatchRead
import com.byd.wjx.data.vehicle.VehicleGateway
import com.byd.wjx.data.vehicle.VehicleRawRead
import com.byd.wjx.data.vehicle.VehicleReadSpec
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns native read selection, tiered full-snapshot caching, and gateway batching.
 * Field decoding and domain guards belong to [NativeVehicleSnapshotParser].
 */
@Singleton
class NativeParsReader @Inject constructor(
    private val gateway: VehicleGateway,
    private val settings: SettingsRepository,
    private val actionSnapshotFieldProvider: ActionSnapshotFieldProvider,
) : ParsReader, OnDemandVehicleTelemetryReader {
    @Volatile private var cachedActionSelection =
        NativeActionSelection(emptySet(), NativeReadPlan(emptyList()))
    @Volatile private var cachedInstrumentSelection =
        NativeActionSelection(emptySet(), NativeReadPlan(emptyList()))
    private val fullReadMutex = Mutex()
    private val fullRawCache = HashMap<VehicleReadSpec, VehicleRawRead>()
    private val parser = NativeVehicleSnapshotParser()
    private var fullCacheInitialized = false
    private var lastDetailAttemptAtNanos = 0L
    internal var monotonicNanos: () -> Long = System::nanoTime

    override suspend fun fetchVehicleSnapshot(): VehicleSnapshot? = fullReadMutex.withLock {
        fetchTieredFullSnapshot()
    }

    override suspend fun fetchActionVehicleSnapshot(): VehicleSnapshot? {
        val requestedFields = VehicleReadProfiles.actionCoreFields +
            actionSnapshotFieldProvider.additionalFields()
        val selectedFields = buildSet {
            addAll(requestedFields)
            if ("driveMode" in requestedFields) add("driveModeIndicator")
            if ("workMode" in requestedFields) add("workModeAlt")
        }
        return fetchPlan(actionPlan(selectedFields), VehicleSnapshotKind.ACTION)
    }

    override suspend fun fetchLedMotionVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(NativeReadPlans.ledMotion, VehicleSnapshotKind.ACTION)

    override suspend fun fetchLedBrakeVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(NativeReadPlans.ledBrake, VehicleSnapshotKind.ACTION)

    override suspend fun fetchLedBodyVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(NativeReadPlans.ledBody, VehicleSnapshotKind.ACTION)

    override suspend fun fetchLedAdasVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(NativeReadPlans.ledAdas, VehicleSnapshotKind.ACTION)

    override suspend fun readTireTelemetry(): DiParsData? =
        fetchPlan(NativeReadPlans.tireTelemetry, VehicleSnapshotKind.ACTION)?.data

    override suspend fun readPowertrainTelemetry(): DiParsData? =
        fetchPlan(NativeReadPlans.powertrainTelemetry, VehicleSnapshotKind.ACTION)?.data

    override suspend fun readPowertrainTelemetry(fields: Set<String>): DiParsData? {
        val selectedFields = fields.intersect(VehicleReadProfiles.powertrainTelemetryFields)
        if (selectedFields.isEmpty()) return null
        val normalizedFields = buildSet {
            addAll(selectedFields)
            if ("frontMotorSpeed" in selectedFields || "rearMotorSpeed" in selectedFields) {
                add("gear")
            }
        }
        return fetchPlan(instrumentPlan(normalizedFields), VehicleSnapshotKind.ACTION)?.data
    }

    private suspend fun fetchTieredFullSnapshot(): VehicleSnapshot? {
        val nowNanos = monotonicNanos()
        if (!fullCacheInitialized) {
            val batch = gateway.readBatch(NativeReadPlans.full.specs) ?: return null
            cacheBatch(batch)
            val snapshot = parsePlan(NativeReadPlans.full, VehicleSnapshotKind.FULL, batch)
            if (snapshot != null) {
                fullCacheInitialized = true
                lastDetailAttemptAtNanos = nowNanos
            }
            return snapshot
        }

        val coreBatch = gateway.readBatch(NativeReadPlans.coreFull.specs) ?: return null
        cacheBatch(coreBatch)
        var requestedCount = coreBatch.specs.size
        var acceptedCount = coreBatch.acceptedCount
        var readStartedAtMs = coreBatch.startedAtMs
        var readCompletedAtMs = coreBatch.completedAtMs

        if (detailRefreshDue(nowNanos)) {
            lastDetailAttemptAtNanos = nowNanos
            val detailBatch = gateway.readBatch(NativeReadPlans.detailFull.specs)
            requestedCount += NativeReadPlans.detailFull.specs.size
            if (detailBatch != null) {
                cacheBatch(detailBatch)
                acceptedCount += detailBatch.acceptedCount
                readStartedAtMs = minOf(readStartedAtMs, detailBatch.startedAtMs)
                readCompletedAtMs = maxOf(readCompletedAtMs, detailBatch.completedAtMs)
            } else {
                NativeReadPlans.detailFull.specs.forEach { spec ->
                    fullRawCache[spec] = UNAVAILABLE_RAW_READ
                }
            }
        }

        val mergedBatch = VehicleBatchRead(
            specs = NativeReadPlans.full.specs,
            values = NativeReadPlans.full.specs.map { spec ->
                fullRawCache[spec] ?: UNAVAILABLE_RAW_READ
            },
            source = coreBatch.source,
            startedAtMs = readStartedAtMs,
            completedAtMs = readCompletedAtMs,
        )
        return parsePlan(
            plan = NativeReadPlans.full,
            kind = VehicleSnapshotKind.FULL,
            batch = mergedBatch,
            requestedFieldCount = requestedCount,
            transportSuccessCount = acceptedCount,
        )
    }

    private fun detailRefreshDue(nowNanos: Long): Boolean {
        val elapsed = nowNanos - lastDetailAttemptAtNanos
        return elapsed < 0L || elapsed >= DETAIL_REFRESH_INTERVAL_NANOS
    }

    private fun cacheBatch(batch: VehicleBatchRead) {
        batch.specs.forEachIndexed { index, spec ->
            fullRawCache[spec] = batch.values[index]
        }
    }

    private fun actionPlan(fields: Set<String>): NativeReadPlan {
        cachedActionSelection.takeIf { it.fields == fields }?.let { return it.plan }
        return createSelection(fields, "action").also { cachedActionSelection = it }.plan
    }

    private fun instrumentPlan(fields: Set<String>): NativeReadPlan {
        cachedInstrumentSelection.takeIf { it.fields == fields }?.let { return it.plan }
        return createSelection(fields, "instrument").also { cachedInstrumentSelection = it }.plan
    }

    private fun createSelection(fields: Set<String>, label: String): NativeActionSelection {
        val entries = FidMap.entries.filter { it.field in fields }
        WjxFileLogger.i(
            TAG,
            "$label batch selected count=${entries.size} " +
                "fields=${entries.joinToString(",") { it.field }}",
        )
        return NativeActionSelection(fields.toSet(), NativeReadPlan(entries))
    }

    private suspend fun fetchPlan(
        plan: NativeReadPlan,
        kind: VehicleSnapshotKind,
    ): VehicleSnapshot? {
        val batch = gateway.readBatch(plan.specs) ?: return null
        return parsePlan(plan, kind, batch)
    }

    private suspend fun parsePlan(
        plan: NativeReadPlan,
        kind: VehicleSnapshotKind,
        batch: VehicleBatchRead,
        requestedFieldCount: Int = batch.specs.size,
        transportSuccessCount: Int = batch.acceptedCount,
    ): VehicleSnapshot? = parser.parse(
        plan = plan,
        kind = kind,
        batch = batch,
        batteryCapacityKwh = if (kind == VehicleSnapshotKind.FULL) {
            settings.getBatteryCapacity()
        } else {
            null
        },
        requestedFieldCount = requestedFieldCount,
        transportSuccessCount = transportSuccessCount,
    )

    private companion object {
        const val TAG = "NativeParsReader"
        const val DETAIL_REFRESH_INTERVAL_NANOS = 3_000_000_000L
        val UNAVAILABLE_RAW_READ = VehicleRawRead(status = -1, value = 0)
    }
}
