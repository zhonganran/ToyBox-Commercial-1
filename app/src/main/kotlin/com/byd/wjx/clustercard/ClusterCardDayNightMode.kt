package com.byd.wjx.clustercard

import android.content.ContentResolver
import android.content.res.Configuration
import android.net.Uri

internal object ClusterCardDayNightMode {
    const val VEHICLE_MODE_KEY = "sys_day_or_night"

    val vehicleModeUri: Uri
        get() = Uri.parse("$GLOBAL_URI_STRING/$VEHICLE_MODE_KEY")

    fun resolve(contentResolver: ContentResolver, configuration: Configuration): Boolean =
        readVehicleNightMode(contentResolver) ?: isAndroidNightMode(configuration)

    fun parseVehicleNightMode(value: String?): Boolean? = when (value?.trim()?.toIntOrNull()) {
        VEHICLE_DAY_VALUE -> false
        VEHICLE_NIGHT_VALUE -> true
        else -> null
    }

    private fun readVehicleNightMode(contentResolver: ContentResolver): Boolean? = runCatching {
        contentResolver.query(
            Uri.parse(GLOBAL_URI_STRING),
            arrayOf(VALUE_COLUMN),
            "key LIKE ?",
            arrayOf(VEHICLE_MODE_KEY),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val valueColumn = cursor.getColumnIndex(VALUE_COLUMN)
            if (valueColumn < 0) null else parseVehicleNightMode(cursor.getString(valueColumn))
        }
    }.getOrNull()

    private fun isAndroidNightMode(configuration: Configuration): Boolean =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private const val VALUE_COLUMN = "value"
    private const val GLOBAL_URI_STRING = "content://carsettings/global"
    private const val VEHICLE_NIGHT_VALUE = 0
    private const val VEHICLE_DAY_VALUE = 1
}
