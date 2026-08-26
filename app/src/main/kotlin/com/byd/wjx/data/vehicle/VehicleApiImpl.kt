package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton

/** Public vehicle API facade over command translation and the guarded write executor. */
@Singleton
class VehicleApiImpl @Inject constructor(
    private val parsReader: ParsReader,
    private val gateway: VehicleGateway,
    allowlist: WriteAllowlist,
    writeLogDao: VehicleWriteLogDao,
    seatChannelStore: SeatChannelStore = InMemorySeatChannelStore(),
) : VehicleApi {
    private val writeExecutor = VehicleWriteExecutor(
        gateway = gateway,
        allowlist = allowlist,
        auditLogger = VehicleWriteAuditLogger(writeLogDao),
    )
    private val adaptiveSeatChannel = AdaptiveSeatChannel(
        writer = SeatWriter { actionName, value -> doWriteOutcome(actionName, value) },
        store = seatChannelStore,
    )

    override suspend fun readBatterySnapshot(): BatteryReading? = gateway.readBatterySnapshot()

    override suspend fun readSnapshot(): DiParsData? = parsReader.fetchVehicleSnapshot()?.data

    override suspend fun dispatch(commandString: String): Result<Unit> {
        val resolved = CommandTranslator.resolve(commandString)
        if (resolved.isEmpty()) {
            WjxFileLogger.w(TAG, "dispatch: unknown command '$commandString'")
            return writeExecutor.rejectUnknownCommand(commandString)
        }
        if (resolved.size == 1) {
            val command = resolved.single()
            VehicleSeatCommandResolver.resolve(command.actionName, command.value)?.let { target ->
                return if (adaptiveSeatChannel.actuate(target.group, target.level)) {
                    Result.success(Unit)
                } else {
                    Result.failure(VehicleWriteError.Unsupported(command.actionName))
                }
            }
            return doWrite(command.actionName, command.value)
        }

        var firstError: Throwable? = null
        resolved.forEach { command ->
            val result = doWrite(command.actionName, command.value)
            if (result.isFailure && firstError == null) firstError = result.exceptionOrNull()
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun writeAcOn(): Result<Unit> = doWrite("ac_on", 0)

    override suspend fun writeAcOff(): Result<Unit> = doWrite("ac_off", 0)

    override suspend fun writeSetDriverTemp(celsius: Int): Result<Unit> =
        doWrite("ac_temp_main", celsius)

    override suspend fun writeWindowDriver(percent: Int): Result<Unit> =
        doWrite("window_driver_pos", percent)

    override suspend fun writeWindowPassenger(percent: Int): Result<Unit> =
        doWrite("window_passenger_pos", percent)

    override suspend fun writeWindowRearLeft(percent: Int): Result<Unit> =
        doWrite("window_rear_left_pos", percent)

    override suspend fun writeWindowRearRight(percent: Int): Result<Unit> =
        doWrite("window_rear_right_pos", percent)

    override suspend fun writeLockDoors(): Result<Unit> = doWrite("doors_lock", 2)

    override suspend fun writeUnlockDoors(): Result<Unit> = doWrite("doors_unlock", 1)

    override suspend fun writeSunroof(mode: SunroofMode): Result<Unit> =
        doWrite("sunroof_${mode.name.lowercase()}", mode.value)

    override suspend fun writeSunshade(open: Boolean): Result<Unit> =
        doWrite(if (open) "sunshade_open" else "sunshade_close", if (open) 1 else 2)

    internal suspend fun doWrite(actionName: String, value: Int): Result<Unit> =
        writeExecutor.write(actionName, value)

    internal suspend fun doWriteOutcome(actionName: String, value: Int): WriteOutcome =
        writeExecutor.writeOutcome(actionName, value)

    private companion object {
        const val TAG = "VehicleApiImpl"
    }
}
