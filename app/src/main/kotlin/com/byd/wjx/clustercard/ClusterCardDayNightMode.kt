package com.byd.wjx.clustercard

import android.content.ContentResolver
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri

internal object ClusterCardDayNightMode {
    const val VEHICLE_MODE_KEY = "sys_day_or_night"

    val globalUri: Uri
        get() = Uri.parse(GLOBAL_URI_STRING)

    val vehicleModeUri: Uri
        get() = Uri.withAppendedPath(globalUri, VEHICLE_MODE_KEY)

    fun resolve(contentResolver: ContentResolver, configuration: Configuration): Boolean =
        resolveState(contentResolver, configuration).isNight

    fun resolveState(contentResolver: ContentResolver, configuration: Configuration): Resolution {
        val rawValue = readVehicleNightModeRaw(contentResolver)
        val vehicleMode = parseVehicleNightMode(rawValue)
        return if (vehicleMode != null) {
            Resolution(vehicleMode, Source.VEHICLE, rawValue)
        } else {
            Resolution(isAndroidNightMode(configuration), Source.ANDROID, rawValue)
        }
    }

    enum class Source {
        VEHICLE,
        ANDROID,
    }

    data class Resolution(
        val isNight: Boolean,
        val source: Source,
        val rawValue: String?,
    )

    fun parseVehicleNightMode(value: String?): Boolean? = when (value?.trim()?.toIntOrNull()) {
        VEHICLE_DAY_VALUE -> false
        VEHICLE_NIGHT_VALUE -> true
        else -> when (value?.trim()?.lowercase()) {
            "day", "daytime", "light", "false" -> false
            "night", "nighttime", "dark", "true" -> true
            else -> null
        }
    }

    private fun readVehicleNightModeRaw(contentResolver: ContentResolver): String? {
        // CarSettings.Global follows the Android name/value table schema. Some older
        // images expose the row through the key column or the key-specific URI instead.
        val queries = listOf(
            Query(
                uri = globalUri,
                selection = "$NAME_COLUMN = ?",
                selectionArgs = arrayOf(VEHICLE_MODE_KEY),
            ),
            Query(uri = vehicleModeUri),
            Query(
                uri = globalUri,
                selection = "$LEGACY_KEY_COLUMN = ?",
                selectionArgs = arrayOf(VEHICLE_MODE_KEY),
            ),
        )
        queries.forEach { query ->
            val value = runCatching { queryValue(contentResolver, query) }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun queryValue(contentResolver: ContentResolver, query: Query): String? =
        contentResolver.query(
            query.uri,
            arrayOf(VALUE_COLUMN),
            query.selection,
            query.selectionArgs,
            null,
        )?.use { cursor -> readCursorValue(cursor) }

    private fun readCursorValue(cursor: Cursor): String? {
        if (!cursor.moveToFirst()) return null
        val valueColumn = cursor.getColumnIndex(VALUE_COLUMN)
        return if (valueColumn < 0 || cursor.isNull(valueColumn)) null else cursor.getString(valueColumn)
    }

    private data class Query(
        val uri: Uri,
        val selection: String? = null,
        val selectionArgs: Array<String>? = null,
    )

    private fun isAndroidNightMode(configuration: Configuration): Boolean =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private const val NAME_COLUMN = "name"
    private const val LEGACY_KEY_COLUMN = "key"
    private const val VALUE_COLUMN = "value"
    private const val GLOBAL_URI_STRING = "content://carsettings/global"
    // BYD CarSettings.Global stores sys_day_or_night as a boolean:
    // 0=false uses daytime brightness, 1=true uses nighttime brightness.
    private const val VEHICLE_NIGHT_VALUE = 1
    private const val VEHICLE_DAY_VALUE = 0
}
