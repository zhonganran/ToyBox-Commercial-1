package com.byd.wjx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.byd.wjx.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY start_ts DESC")
    fun getAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE start_ts >= :from AND start_ts <= :to ORDER BY start_ts DESC")
    fun getByDateRange(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY start_ts DESC LIMIT 1")
    fun getLastTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY start_ts DESC LIMIT :limit")
    fun getRecent(limit: Int = 5): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE byd_id = :bydId LIMIT 1")
    suspend fun getByBydId(bydId: Long): TripEntity?

    @Query(
        "SELECT * FROM trips WHERE " +
            "(cost IS NULL AND kwh_consumed IS NOT NULL) OR " +
            "(fuel_cost IS NULL AND fuel_consumed_l IS NOT NULL)"
    )
    suspend fun getTripsWithoutCost(): List<TripEntity>

    @Query("""
        SELECT COALESCE(SUM(distance_km), 0.0) as totalKm,
               COALESCE(SUM(kwh_consumed), 0.0) as totalKwh,
               COUNT(*) as tripCount,
               COALESCE(SUM(cost), 0.0) as totalCost
        FROM trips
        WHERE start_ts >= :from AND start_ts <= :to
    """)
    suspend fun getPeriodSummary(from: Long, to: Long): TripSummary

    @Query("SELECT * FROM trips WHERE start_ts >= :minTs AND start_ts <= :maxTs LIMIT 1")
    suspend fun getByStartTsRange(minTs: Long, maxTs: Long): TripEntity?

    @Query("SELECT * FROM trips ORDER BY start_ts")
    suspend fun getAllSnapshot(): List<TripEntity>

    @Query("""
        SELECT * FROM trips
        WHERE distance_km >= 1
          AND kwh_consumed > 0
          AND (kwh_consumed * 100.0 / distance_km) <= 50
        ORDER BY start_ts DESC LIMIT :limit
    """)
    suspend fun getRecentForEma(limit: Int = 10): List<TripEntity>

    @Query("""
        SELECT * FROM trips
        WHERE distance_km >= :minKm AND kwh_consumed > 0
        ORDER BY start_ts DESC LIMIT :limit
    """)
    suspend fun getRecentForEmaFiltered(minKm: Double, limit: Int): List<TripEntity>
}

data class TripSummary(
    val totalKm: Double,
    val totalKwh: Double,
    val tripCount: Int = 0,
    val totalCost: Double = 0.0
)
