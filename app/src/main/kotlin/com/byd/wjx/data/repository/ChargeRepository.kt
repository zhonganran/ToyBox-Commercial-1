package com.byd.wjx.data.repository

import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.entity.ChargeEntity
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
) {
    suspend fun insertCharge(charge: ChargeEntity): Long = chargeDao.insert(charge)

    suspend fun updateCharge(charge: ChargeEntity) = chargeDao.update(charge)

    fun getAllCharges(): Flow<List<ChargeEntity>> = chargeDao.getAll()

    fun getChargesByDateRange(from: Long, to: Long): Flow<List<ChargeEntity>> =
        chargeDao.getByDateRange(from, to)

    fun getLastCharge(): Flow<ChargeEntity?> = chargeDao.getLastCharge()

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

    suspend fun deleteCharge(charge: ChargeEntity) = chargeDao.delete(charge)
}
