package com.byd.wjx.data.repository

import com.byd.wjx.data.local.dao.SettingsDao
import com.byd.wjx.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Some numeric keyboards emit "71,8" — bare toDoubleOrNull() returns null
// on the comma and we fall back to the default. Normalize it before parsing so
// battery capacity, tariffs, and thresholds use the value the user typed.
internal fun String.parseNumericSetting(): Double? =
    replace(',', '.').trim().toDoubleOrNull()

@Singleton
open class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
) {
    @Volatile private var cachedBatteryCapacity: Double? = null
    @Volatile private var cachedFuelTankCapacity: Double? = null
    @Volatile private var hasCachedFuelTankCapacity: Boolean = false
    @Volatile private var cachedTripGpsRecordingEnabled: Boolean? = null

    companion object {
        const val KEY_BATTERY_CAPACITY = "battery_capacity_kwh"
        const val KEY_FUEL_TANK_CAPACITY = "fuel_tank_capacity_liters"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_APP_WALLPAPER = "app_wallpaper"
        const val KEY_HOME_TARIFF = "home_tariff"
        const val KEY_DC_TARIFF = "dc_tariff"
        const val KEY_TRIP_COST_TARIFF = "trip_cost_tariff" // "home", "dc", or numeric
        const val KEY_CONSUMPTION_GOOD = "consumption_good_threshold"
        const val KEY_CONSUMPTION_BAD = "consumption_bad_threshold"
        const val KEY_LAST_ENERGYDATA_IMPORT_TS = "last_energydata_import_ts"
        const val KEY_RECORD_TRIP_GPS = "record_trip_gps"
        const val KEY_LAST_MILEAGE_KM = "last_mileage_km"
        const val KEY_LAST_CAPACITY_KWH = "last_capacity_kwh"
        const val KEY_LAST_STATE_TS = "last_state_ts"
        // ChargingStateStore baseline used to reconstruct an interrupted charging session.
        const val KEY_CHARGING_BASELINE_SOC = "charging_baseline_soc"
        // Set when runCatchUp saw the gun connected (a charge session is in
        // progress around the stored baseline); cleared once the session is
        // reconstructed or dismissed. Lets a later catch-up create the row
        // even if the odometer moved before the first successful run.
        const val KEY_CHARGE_PENDING = "charge_pending"
        // Persistent ring buffer of recent runCatchUp decisions (CatchUpJournal).
        // Included in the diagnostic dump — logcat rotates out the startup
        // window within minutes on DiLink, so field reports need this.
        const val KEY_CATCHUP_JOURNAL = "catchup_journal"

        const val DEFAULT_BATTERY_CAPACITY = "72.9"
        const val DEFAULT_FUEL_TANK_CAPACITY = ""
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_APP_WALLPAPER = "sakura_lake"
        const val DEFAULT_HOME_TARIFF = "0.60"
        const val DEFAULT_DC_TARIFF = "1.20"
        const val DEFAULT_CONSUMPTION_GOOD = "20"
        const val DEFAULT_CONSUMPTION_BAD = "30"
        const val DEFAULT_RECORD_TRIP_GPS = "true"

    }

    suspend fun getString(key: String, default: String): String =
        settingsDao.get(key) ?: default

    fun observeString(key: String): Flow<String?> = settingsDao.observe(key)

    suspend fun setString(key: String, value: String) {
        settingsDao.set(SettingEntity(key, value))
        updateHotCache(key, value)
    }

    /** Writes all key/value pairs in one Room transaction (all or nothing). */
    suspend fun setStrings(values: Map<String, String>) {
        settingsDao.setAll(values.map { (k, v) -> SettingEntity(k, v) })
        values.forEach(::updateHotCache)
    }

    suspend fun getBatteryCapacity(): Double {
        cachedBatteryCapacity?.let { return it }
        return (getString(KEY_BATTERY_CAPACITY, DEFAULT_BATTERY_CAPACITY)
            .parseNumericSetting() ?: 72.9).also { cachedBatteryCapacity = it }
    }

    fun observeBatteryCapacity(): Flow<Double?> =
        observeString(KEY_BATTERY_CAPACITY).map { raw ->
            (raw ?: DEFAULT_BATTERY_CAPACITY).parsePositiveCapacity()
        }

    suspend fun getFuelTankCapacity(): Double? {
        if (hasCachedFuelTankCapacity) return cachedFuelTankCapacity
        return getString(KEY_FUEL_TANK_CAPACITY, DEFAULT_FUEL_TANK_CAPACITY)
            .parsePositiveCapacity()
            .also {
                cachedFuelTankCapacity = it
                hasCachedFuelTankCapacity = true
            }
    }

    fun observeFuelTankCapacity(): Flow<Double?> =
        observeString(KEY_FUEL_TANK_CAPACITY).map { raw ->
            (raw ?: DEFAULT_FUEL_TANK_CAPACITY).parsePositiveCapacity()
        }

    suspend fun getThemeMode(): String =
        normalizeThemeMode(getString(KEY_THEME_MODE, DEFAULT_THEME_MODE))

    fun observeThemeMode(): Flow<String> =
        observeString(KEY_THEME_MODE).map { normalizeThemeMode(it) }

    suspend fun setThemeMode(mode: String) =
        setString(KEY_THEME_MODE, normalizeThemeMode(mode))

    suspend fun getAppWallpaper(): String =
        normalizeAppWallpaper(getString(KEY_APP_WALLPAPER, DEFAULT_APP_WALLPAPER))

    fun observeAppWallpaper(): Flow<String> =
        observeString(KEY_APP_WALLPAPER).map { normalizeAppWallpaper(it) }

    suspend fun setAppWallpaper(wallpaper: String) =
        setString(KEY_APP_WALLPAPER, normalizeAppWallpaper(wallpaper))

    suspend fun getHomeTariff(): Double =
        getString(KEY_HOME_TARIFF, DEFAULT_HOME_TARIFF).parseNumericSetting()
            ?: DEFAULT_HOME_TARIFF.toDouble()

    suspend fun getDcTariff(): Double =
        getString(KEY_DC_TARIFF, DEFAULT_DC_TARIFF).parseNumericSetting()
            ?: DEFAULT_DC_TARIFF.toDouble()

    suspend fun getTripCostTariff(): Double {
        val raw = getString(KEY_TRIP_COST_TARIFF, "home")
        return when (raw) {
            "home" -> getHomeTariff()
            "dc" -> getDcTariff()
            else -> raw.parseNumericSetting() ?: getHomeTariff()
        }
    }

    suspend fun getTripCostTariffKey(): String =
        getString(KEY_TRIP_COST_TARIFF, "home")

    /** Live (good, bad) pair for UI coloring. Emits on every Settings edit. */
    fun observeConsumptionThresholds(): Flow<Pair<Double, Double>> = combine(
        observeString(KEY_CONSUMPTION_GOOD).map {
            it?.parseNumericSetting() ?: DEFAULT_CONSUMPTION_GOOD.toDouble()
        },
        observeString(KEY_CONSUMPTION_BAD).map {
            it?.parseNumericSetting() ?: DEFAULT_CONSUMPTION_BAD.toDouble()
        },
    ) { good, bad -> good to bad }

    suspend fun getLastEnergyImportTs(): Long =
        getString(KEY_LAST_ENERGYDATA_IMPORT_TS, "0").toLongOrNull() ?: 0L

    suspend fun setLastEnergyImportTs(ts: Long) =
        setString(KEY_LAST_ENERGYDATA_IMPORT_TS, ts.toString())

    suspend fun isTripGpsRecordingEnabled(): Boolean {
        cachedTripGpsRecordingEnabled?.let { return it }
        return (getString(KEY_RECORD_TRIP_GPS, DEFAULT_RECORD_TRIP_GPS)
            .toBooleanStrictOrNull() ?: true).also { cachedTripGpsRecordingEnabled = it }
    }

    suspend fun setTripGpsRecordingEnabled(enabled: Boolean) =
        setString(KEY_RECORD_TRIP_GPS, enabled.toString())

    suspend fun getChargingBaselineSoc(): Int? =
        getString(KEY_CHARGING_BASELINE_SOC, "").toIntOrNull()

    suspend fun getChargePending(): Boolean =
        getString(KEY_CHARGE_PENDING, "") == "true"

    suspend fun setChargePending(pending: Boolean) =
        setString(KEY_CHARGE_PENDING, pending.toString())

    private fun updateHotCache(key: String, value: String) {
        when (key) {
            KEY_BATTERY_CAPACITY -> {
                cachedBatteryCapacity = value.parseNumericSetting() ?: 72.9
            }
            KEY_FUEL_TANK_CAPACITY -> {
                cachedFuelTankCapacity = value.parsePositiveCapacity()
                hasCachedFuelTankCapacity = true
            }
            KEY_RECORD_TRIP_GPS -> {
                cachedTripGpsRecordingEnabled = value.toBooleanStrictOrNull() ?: true
            }
        }
    }

    suspend fun getLastMileageKm(): Float? =
        getString(KEY_LAST_MILEAGE_KM, "").toFloatOrNull()

    suspend fun getLastCapacityKwh(): Float? =
        getString(KEY_LAST_CAPACITY_KWH, "").toFloatOrNull()

    suspend fun getLastStateTs(): Long =
        getString(KEY_LAST_STATE_TS, "0").toLongOrNull() ?: 0L

}

private fun String.parsePositiveCapacity(): Double? =
    parseNumericSetting()?.takeIf { it.isFinite() && it > 0.0 }

private fun normalizeThemeMode(value: String?): String = when (value) {
    "light", "dark", "system" -> value
    else -> SettingsRepository.DEFAULT_THEME_MODE
}

private fun normalizeAppWallpaper(value: String?): String = when (value) {
    "sakura_lake",
    "aurora_twilight",
    "sunrise_lake",
    "tech_night",
    "ink_mountains",
    "glacier_aurora",
    "sunset_canyon" -> value
    else -> SettingsRepository.DEFAULT_APP_WALLPAPER
}
