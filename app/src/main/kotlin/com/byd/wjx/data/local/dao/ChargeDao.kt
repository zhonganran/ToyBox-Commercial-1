package com.byd.wjx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.byd.wjx.data.local.entity.BatterySnapshotEntity
import com.byd.wjx.data.local.entity.ChargeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeDao {
    @Insert
    suspend fun insert(charge: ChargeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(charge: ChargeEntity): Long

    @Query("SELECT id FROM charges WHERE source_session_key = :sourceSessionKey LIMIT 1")
    suspend fun findIdBySourceSessionKey(sourceSessionKey: String): Long?

    @Insert
    suspend fun insertBatterySnapshot(snapshot: BatterySnapshotEntity): Long

    @Transaction
    suspend fun insertCatchUpSession(
        charge: ChargeEntity,
        batterySnapshot: BatterySnapshotEntity?,
    ): Long {
        require(!charge.sourceSessionKey.isNullOrBlank()) { "补录充电记录缺少来源会话键" }
        val insertedId = insertIgnoringDuplicate(charge)
        if (insertedId != -1L) {
            batterySnapshot?.let { insertBatterySnapshot(it.copy(chargeId = insertedId)) }
            return insertedId
        }
        return requireNotNull(findIdBySourceSessionKey(charge.sourceSessionKey)) {
            "补录充电记录幂等冲突后未找到原记录"
        }
    }

    @Update
    suspend fun update(charge: ChargeEntity)

    @Query("SELECT * FROM charges ORDER BY start_ts DESC")
    fun getAll(): Flow<List<ChargeEntity>>

    @Query("SELECT * FROM charges WHERE start_ts >= :from AND start_ts <= :to ORDER BY start_ts DESC")
    fun getByDateRange(from: Long, to: Long): Flow<List<ChargeEntity>>

    @Query("SELECT * FROM charges ORDER BY start_ts DESC LIMIT 1")
    fun getLastCharge(): Flow<ChargeEntity?>

    /** All charges detected by the autoservice path, ascending by start time. */
    @Query("SELECT * FROM charges WHERE detection_source LIKE 'autoservice_%' ORDER BY start_ts ASC")
    suspend fun getAllAutoserviceCharges(): List<ChargeEntity>

    @androidx.room.Delete
    suspend fun delete(charge: ChargeEntity)
}

data class ChargeSummary(
    val sessionCount: Int,
    val totalKwh: Double,
    val totalCost: Double
)
