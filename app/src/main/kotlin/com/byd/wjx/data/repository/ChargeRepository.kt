package com.byd.wjx.data.repository

import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.dao.ChargePointDao
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.ChargePointEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class LifetimeChargingStats(
    val totalKwhAdded: Double,
    val acKwh: Double,
    val dcKwh: Double,
    val sessionCount: Int
)

@Singleton
class ChargeRepository @Inject constructor(
    private val chargeDao: ChargeDao,
    private val chargePointDao: ChargePointDao
) {
    suspend fun insertCharge(charge: ChargeEntity): Long = chargeDao.insert(charge)

    suspend fun updateCharge(charge: ChargeEntity) = chargeDao.update(charge)

    suspend fun getChargeById(id: Long): ChargeEntity? = chargeDao.getById(id)

    fun getAllCharges(): Flow<List<ChargeEntity>> = chargeDao.getAll()

    fun getChargesByDateRange(from: Long, to: Long): Flow<List<ChargeEntity>> =
        chargeDao.getByDateRange(from, to)

    suspend fun getPeriodSummary(from: Long, to: Long): ChargeSummary =
        chargeDao.getPeriodSummary(from, to)

    fun getLastCharge(): Flow<ChargeEntity?> = chargeDao.getLastCharge()

    suspend fun insertChargePoints(points: List<ChargePointEntity>) =
        chargePointDao.insertAll(points)

    suspend fun getChargePoints(chargeId: Long): List<ChargePointEntity> =
        chargePointDao.getByChargeId(chargeId)

    suspend fun getLastSuspendedCharge(): ChargeEntity? = chargeDao.getLastSuspendedCharge()

    suspend fun getStaleSessions(cutoffTs: Long): List<ChargeEntity> =
        chargeDao.getStaleSessions(cutoffTs)

    suspend fun getRecentChargesWithBatteryData(): List<ChargeEntity> =
        chargeDao.getRecentChargesWithBatteryData()

    suspend fun getMaxLifetimeKwhAtFinish(): Double? =
        chargeDao.getMaxLifetimeKwhAtFinish()

    suspend fun getLifetimeStats(): LifetimeChargingStats {
        val all = chargeDao.getAllAutoserviceCharges()
        val ac = all.filter { it.type == "AC" }.sumOf { it.kwhCharged ?: 0.0 }
        val dc = all.filter { it.type == "DC" }.sumOf { it.kwhCharged ?: 0.0 }
        return LifetimeChargingStats(
            totalKwhAdded = ac + dc,
            acKwh = ac,
            dcKwh = dc,
            sessionCount = all.size
        )
    }

    suspend fun hasLegacyCharges(): Boolean = chargeDao.hasLegacyCharges()

    suspend fun deleteEmpty(): Int = chargeDao.deleteEmpty()

    suspend fun deleteCharge(charge: ChargeEntity) = chargeDao.delete(charge)
}
