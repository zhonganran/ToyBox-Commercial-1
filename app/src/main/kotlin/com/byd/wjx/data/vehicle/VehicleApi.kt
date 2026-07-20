package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.remote.DiParsData

/**
 * Single in-process facade for vehicle reads + writes.
 *
 * Reads delegate to VehicleGateway / NativeParsReader. Writes go through
 * HelperClient and WriteAllowlist.
 *
 * Returns null on any read failure (sentinel, daemon down, parse failure).
 * Returns Result.failure(VehicleWriteError) on any write failure — never throws.
 * The error type carries actionName + details for structured logging.
 */
interface VehicleApi {
    // On-demand snapshots not already represented by VehicleStateRepository.
    suspend fun readBatterySnapshot(): BatteryReading?
    suspend fun readSnapshot(): DiParsData?

    // Write entrypoints: user-facing command dispatch plus structured methods.
    // All `write*` methods wired to WriteAllowlist + HelperClient (Group C).
    // Failure reason available via Result.exceptionOrNull() as VehicleWriteError.
    suspend fun dispatch(commandString: String): Result<Unit>
    suspend fun writeAcOn(): Result<Unit>
    suspend fun writeAcOff(): Result<Unit>
    suspend fun writeSetDriverTemp(celsius: Int): Result<Unit>   // range 16..30
    suspend fun writeWindowDriver(percent: Int): Result<Unit>    // range 0..100
    suspend fun writeWindowPassenger(percent: Int): Result<Unit> // range 0..100
    suspend fun writeWindowRearLeft(percent: Int): Result<Unit>  // range 0..100
    suspend fun writeWindowRearRight(percent: Int): Result<Unit> // range 0..100
    suspend fun writeLockDoors(): Result<Unit>
    suspend fun writeUnlockDoors(): Result<Unit>
    suspend fun writeSunroof(mode: SunroofMode): Result<Unit>
    suspend fun writeSunshade(open: Boolean): Result<Unit>
}
