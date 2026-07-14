package com.byd.wjx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.byd.wjx.data.local.entity.TripPointEntity

@Dao
interface TripPointDao {
    @Insert
    suspend fun insertAll(points: List<TripPointEntity>)

    @Query("SELECT * FROM trip_points WHERE trip_id = :tripId ORDER BY timestamp ASC")
    suspend fun getByTripId(tripId: Long): List<TripPointEntity>

    @Query("SELECT * FROM trip_points WHERE trip_id IN (:tripIds) ORDER BY trip_id, timestamp ASC")
    suspend fun getByTripIds(tripIds: List<Long>): List<TripPointEntity>

    @Query("SELECT COUNT(*) FROM trip_points")
    suspend fun getCount(): Int

    @Query("SELECT * FROM trip_points ORDER BY trip_id, timestamp ASC")
    suspend fun getAll(): List<TripPointEntity>

    /**
     * Delete old points keeping every Nth one per trip.
     * Points older than [cutoff] are thinned to ~1 per [intervalMs].
     */
    @Query("""
        DELETE FROM trip_points
        WHERE timestamp < :cutoff
        AND id NOT IN (
            SELECT MIN(id) FROM trip_points
            WHERE timestamp < :cutoff
            GROUP BY trip_id, (timestamp / :intervalMs)
        )
    """)
    suspend fun thinOldPoints(cutoff: Long, intervalMs: Long): Int

    @Query("SELECT * FROM trip_points WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getByTimeRange(from: Long, to: Long): List<TripPointEntity>

    @Query("UPDATE trip_points SET trip_id = :tripId WHERE timestamp BETWEEN :from AND :to AND trip_id = 0")
    suspend fun attachToTrip(tripId: Long, from: Long, to: Long): Int

    @Query("""
        UPDATE trip_points
        SET trip_id = :tripId, byd_id = :bydId, byd_date = :bydDate
        WHERE timestamp BETWEEN :from AND :to
        AND trip_id = 0
    """)
    suspend fun attachToTripWithBydKey(
        tripId: Long,
        bydId: Long?,
        bydDate: String?,
        from: Long,
        to: Long
    ): Int

    @Query("""
        UPDATE trip_points
        SET trip_id = :tripId
        WHERE byd_id = :bydId
        AND ((byd_date = :bydDate) OR (byd_date IS NULL AND :bydDate IS NULL))
        AND trip_id != :tripId
    """)
    suspend fun relinkByBydKey(tripId: Long, bydId: Long, bydDate: String?): Int

    @Query("""
        UPDATE trip_points
        SET byd_id = :bydId, byd_date = :bydDate
        WHERE trip_id = :tripId
        AND byd_id IS NULL
        AND timestamp BETWEEN :from AND :to
    """)
    suspend fun tagTripPointsWithBydKey(
        tripId: Long,
        bydId: Long,
        bydDate: String?,
        from: Long,
        to: Long
    ): Int

    @Query("""
        UPDATE trip_points
        SET trip_id = 0
        WHERE trip_id != 0
        AND trip_id NOT IN (SELECT id FROM trips)
    """)
    suspend fun detachOrphanPoints(): Int

    @Insert
    suspend fun insert(point: TripPointEntity): Long
}
