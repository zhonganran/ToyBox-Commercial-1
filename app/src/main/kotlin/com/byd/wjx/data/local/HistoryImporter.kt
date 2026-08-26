package com.byd.wjx.data.local

import android.content.Context
import android.util.Log
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/** Serializes history imports, selects the best vehicle source, and commits source watermarks. */
@Singleton
class HistoryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val energyDataReader: EnergyDataReader,
    tripRepository: TripRepository,
    tripDao: TripDao,
    tripPointDao: TripPointDao,
    idleDrainDao: IdleDrainDao,
    private val settingsRepository: SettingsRepository,
    lastSessionRepository: LastSessionRepository,
    private val diPlusDbReader: DiPlusDbReader = DiPlusDbReader(context),
) {
    private val syncMutex = Mutex()
    private val energyImporter = EnergyHistoryImporter(
        reader = energyDataReader,
        tripRepository = tripRepository,
        tripDao = tripDao,
        idleDrainDao = idleDrainDao,
        settingsRepository = settingsRepository,
        lastSessionRepository = lastSessionRepository,
    )
    private val diPlusImporter = DiPlusHistoryImporter(diPlusDbReader, tripRepository, tripDao)
    private val maintenance = HistoryMaintenanceCoordinator(tripRepository, tripDao, tripPointDao)

    suspend fun syncFromEnergyData(): HistoryImportResult = runExclusive("syncFromEnergyData") {
        if (!energyDataReader.peekSourceChanged(context)) {
            Log.d(TAG, "syncFromEnergyData: source unchanged, skipping")
            return@runExclusive HistoryImportResult(trips = 0, details = "没有变化")
        }
        val result = energyImporter.import()
        if (!result.isError) energyDataReader.markSourceProcessed(context)
        WjxFileLogger.i(
            TAG,
            "syncFromEnergyData result trips=${result.trips} idle=${result.idleDrains} " +
                "error=${result.error.orEmpty()}",
        )
        result
    }

    suspend fun syncFromDiPlus(): HistoryImportResult = runExclusive("syncFromDiPlus") {
        if (!diPlusDbReader.isAvailable()) {
            return@runExclusive HistoryImportResult(trips = 0, details = "未找到可读取的系统行程库")
        }
        if (!diPlusDbReader.peekSourceChanged()) {
            return@runExclusive HistoryImportResult(trips = 0, details = "没有变化")
        }
        val result = diPlusImporter.import()
        if (!result.isError) diPlusDbReader.markSourceProcessed()
        WjxFileLogger.i(TAG, "syncFromDiPlus result trips=${result.trips} error=${result.error.orEmpty()}")
        result
    }

    suspend fun syncFromVehicleHistory(): HistoryImportResult = when {
        energyDataReader.isAvailable() -> syncFromEnergyData()
        diPlusDbReader.isAvailable() -> syncFromDiPlus()
        else -> HistoryImportResult(trips = 0, details = "未找到车机系统行程数据库")
    }

    suspend fun calculateMissingCosts(
        tariff: Double,
        fuelPricePerLiter: Double = SettingsRepository.DEFAULT_FUEL_PRICE_PER_LITER.toDouble(),
    ) = maintenance.calculateMissingCosts(tariff, fuelPricePerLiter)

    suspend fun attachGpsPoints() = maintenance.attachGpsPoints()

    suspend fun runSync(): HistoryImportResult {
        val result = syncFromVehicleHistory()
        if (!result.isError && result.count > 0) {
            calculateMissingCosts(
                tariff = settingsRepository.getTripCostTariff(),
                fuelPricePerLiter = settingsRepository.getFuelPricePerLiter(),
            )
            attachGpsPoints()
        }
        return result
    }

    private suspend fun runExclusive(
        operation: String,
        block: suspend () -> HistoryImportResult,
    ): HistoryImportResult {
        if (!syncMutex.tryLock()) {
            WjxFileLogger.i(TAG, "$operation skipped: already running")
            return HistoryImportResult(trips = 0, details = "同步已在进行中")
        }
        return try {
            block()
        } catch (error: EnergyDataException) {
            Log.w(TAG, "$operation failed: ${error.message}", error)
            WjxFileLogger.w(TAG, "$operation failed: ${error.message}", error)
            HistoryImportResult(trips = 0, error = error.message)
        } catch (error: Exception) {
            Log.e(TAG, "$operation unexpected error", error)
            WjxFileLogger.e(TAG, "$operation unexpected error: ${error.message}", error)
            HistoryImportResult(trips = 0, error = error.message ?: error.toString())
        } finally {
            syncMutex.unlock()
        }
    }

    private companion object {
        const val TAG = "HistoryImporter"
    }
}
