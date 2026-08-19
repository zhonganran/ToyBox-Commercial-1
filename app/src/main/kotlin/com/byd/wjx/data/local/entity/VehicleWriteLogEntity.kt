package com.byd.wjx.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_write_log")
data class VehicleWriteLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val actionName: String,
    val dev: Int,
    val fid: Int,
    val requested: Int,
    val readback: Int?,
    val status: Int,
    val error: String?,
    // Keep the generated Room schema explicit for fresh installs.
    @ColumnInfo(defaultValue = "0") val validated: Boolean,
)
