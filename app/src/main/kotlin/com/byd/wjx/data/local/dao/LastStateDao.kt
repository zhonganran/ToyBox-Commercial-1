package com.byd.wjx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.byd.wjx.data.local.entity.LastStateEntity

@Dao
interface LastStateDao {
    @Query("SELECT * FROM last_state WHERE id = 1")
    suspend fun getCurrent(): LastStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: LastStateEntity)

    /** Updates live snapshot columns without touching the independently owned open-trip anchor. */
    @Query(
        """
        UPDATE last_state
        SET ts = :ts,
            soc = :soc,
            mileage = :mileage,
            total_elec = :totalElec,
            ignition = :ignition,
            energydata_available = :energydataAvailable
        WHERE id = 1
        """
    )
    suspend fun updateSnapshot(
        ts: Long,
        soc: Int?,
        mileage: Double?,
        totalElec: Double?,
        ignition: Int?,
        energydataAvailable: Int,
    ): Int

    /**
     * Mark a new open trip in last_state. Writes to row id=1 (creating it if absent
     * via UPSERT semantics on the caller's side — most callers will read getCurrent()
     * first and merge). Called by TripRecorder on trip open.
     *
     * Uses COALESCE to preserve an existing openTripId if one is already set.
     * Returns the number of rows affected (0 if row id=1 does not exist yet).
     */
    @Query(
        """
        UPDATE last_state
        SET open_trip_id = COALESCE(open_trip_id, :startTs),
            trip_start_ts = :startTs,
            trip_start_soc = :startSoc,
            trip_start_mileage = :startMileage,
            trip_start_total_elec = :startTotalElec,
            ts = :now
        WHERE id = 1
        """
    )
    suspend fun openTrip(startTs: Long, startSoc: Int?, startMileage: Double?, startTotalElec: Double?, now: Long): Int

    @Query(
        """
        UPDATE last_state
        SET open_trip_id = NULL,
            trip_start_ts = NULL,
            trip_start_soc = NULL,
            trip_start_mileage = NULL,
            trip_start_total_elec = NULL
        WHERE id = 1
        """
    )
    suspend fun clearOpenTrip()
}
