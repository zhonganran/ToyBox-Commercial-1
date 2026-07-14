package com.byd.wjx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.byd.wjx.data.local.entity.VehicleWriteLogEntity

@Dao
interface VehicleWriteLogDao {
    @Insert suspend fun insert(entity: VehicleWriteLogEntity): Long

    @Query(
        """UPDATE vehicle_write_log
           SET readback = :readback, status = :status, error = :error
           WHERE id = :id"""
    )
    suspend fun complete(id: Long, readback: Int?, status: Int, error: String?): Int

    @Query("SELECT * FROM vehicle_write_log ORDER BY ts DESC LIMIT 200")
    suspend fun getLast200(): List<VehicleWriteLogEntity>

    @Query("DELETE FROM vehicle_write_log WHERE ts < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query(
        """DELETE FROM vehicle_write_log
           WHERE id NOT IN (
               SELECT id FROM vehicle_write_log ORDER BY ts DESC, id DESC LIMIT :keepCount
           )"""
    )
    suspend fun trimToLatest(keepCount: Int): Int
}
