package com.byd.wjx.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_points",
    indices = [Index("trip_id"), Index("timestamp"), Index("byd_id"), Index("byd_date")]
)
data class TripPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    @ColumnInfo(name = "byd_id") val bydId: Long? = null,
    @ColumnInfo(name = "byd_date") val bydDate: String? = null,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    @ColumnInfo(name = "speed_kmh") val speedKmh: Double? = null
)
