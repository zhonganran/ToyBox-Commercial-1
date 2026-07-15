package com.byd.wjx.led

/** Fixed firmware scenes exposed by the vehicle LED panel. */
enum class LedScene(
    val title: String,
    val remotePresetId: Int,
    val priority: Int,
) {
    DEFAULT("微笑", 6, 0),
    LEFT_TURN("左转", 3, 70),
    RIGHT_TURN("右转", 5, 70),
    TEMPORARY_STOP("临时停靠", 4, 40),
    REVERSE_PARKING("倒车入库", 11, 90),
    AUTOPILOT("自动驾驶", 14, 60),
    EXIT_VEHICLE("下车", 19, 50),
    HAZARD("双闪", 20, 100),
    KEEP_DISTANCE("保持车距", 8, 55),
}

enum class LedConnectionPhase {
    DISABLED,
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    READY,
    RETRYING,
    ERROR,
}

data class LedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

data class LedConnectionState(
    val phase: LedConnectionPhase = LedConnectionPhase.IDLE,
    val address: String = "",
    val name: String = "",
    val message: String = "未连接",
    val retryInSeconds: Int? = null,
) {
    val ready: Boolean get() = phase == LedConnectionPhase.READY
}

data class LedPreferenceSnapshot(
    val enabled: Boolean = false,
    val deviceAddress: String = "",
    val deviceName: String = "",
    val brightness: Int = 70,
)

data class LedVehicleSignals(
    val speed: Int? = null,
    val turnSignalLeft: Int? = null,
    val turnSignalRight: Int? = null,
    val hazardLights: Int? = null,
    val gear: Int? = null,
    val powerState: Int? = null,
    val driverDoor: Int? = null,
    val adasLccNcaStatus: Int? = null,
    val adasTjaState: Int? = null,
    val laneKeepState: Int? = null,
    val brakePedalDepth: Int? = null,
    val lightBrake: Int? = null,
    val lightReversing: Int? = null,
)

data class LedRuntimeState(
    val currentScene: LedScene = LedScene.DEFAULT,
    val lastSceneChangedAt: Long = 0L,
    val lastCommandAt: Long = 0L,
    val lastError: String? = null,
)
