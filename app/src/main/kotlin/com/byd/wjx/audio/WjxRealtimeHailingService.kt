package com.byd.wjx.audio

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class WjxRealtimeHailingService : Service() {
    @Inject lateinit var router: BydExteriorAudioRouter
    @Inject lateinit var audioUsage: AudioUsageCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionMutex = Mutex()
    private val commands = Channel<RealtimeHailingServiceCommand>(Channel.UNLIMITED)
    private var engine: RealtimeHailingEngine? = null
    private var activeChannel: ExteriorChannel? = null
    private val audioSettings by lazy { ExteriorHailingSettings(applicationContext) }
    private val notification by lazy { RealtimeHailingNotification(this) }

    override fun onCreate() {
        super.onCreate()
        val initialMessage = RealtimeHailingStateStore.snapshot().message.ifBlank { "实时喊话待命" }
        notification.start(initialMessage)
        serviceScope.launch {
            for (command in commands) {
                actionMutex.withLock { handle(command) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = RealtimeHailingServiceCommandContract.fromIntent(intent)
        if (command == null) {
            WjxFileLogger.w(TAG, "ignored service start with missing or unknown action=${intent?.action}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (commands.trySend(command).isFailure) {
            WjxFileLogger.w(TAG, "command queue unavailable action=${intent?.action}")
            publishState(RealtimeHailingPhase.Error, "实时喊话服务不可用")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commands.close()
        val currentEngine = engine
        val channel = activeChannel
        engine = null
        activeChannel = null
        currentEngine?.muteForRouteRelease()
        if (currentEngine != null || channel != null) {
            cleanupScope.launch {
                runCatching {
                    currentEngine?.stopForRouteRelease()
                    if (channel != null) {
                        delay(ROUTE_RELEASE_DRAIN_MS)
                        router.disarm(channel)
                    }
                }.onFailure {
                    WjxFileLogger.w(TAG, "service destroy cleanup failed: ${it.message}", it)
                }
            }
        }
        if (RealtimeHailingStateStore.snapshot().isActive) {
            RealtimeHailingStateStore.publish(
                RealtimeHailingState(
                    phase = RealtimeHailingPhase.Idle,
                    message = "实时喊话已停止",
                ),
            )
        }
        audioUsage.setActive(AudioOwner.RealtimeHailing, false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handle(command: RealtimeHailingServiceCommand) {
        when (command) {
            is RealtimeHailingServiceCommand.Start -> startHailing(command.channel)
            is RealtimeHailingServiceCommand.Stop -> stopHailing(
                stopService = true,
                finalPhase = command.finalPhase,
                message = command.message,
                notifyIfIdle = command.notifyIfIdle,
            )
            RealtimeHailingServiceCommand.UpdateTuning -> updateHailingTuning()
        }
    }

    private suspend fun startHailing(channel: ExteriorChannel) {
        if (engine?.isRunning == true) {
            publishState(RealtimeHailingPhase.Running, "实时喊话已开启", activeChannel ?: channel)
            return
        }
        if (!isStartStillRequested(channel)) {
            WjxFileLogger.i(TAG, "start skipped because request was superseded channel=$channel")
            return
        }
        publishState(RealtimeHailingPhase.Starting, "正在准备实时喊话", channel)
        audioUsage.setActive(AudioOwner.RealtimeHailing, true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WjxFileLogger.w(TAG, "start blocked: RECORD_AUDIO denied")
            failStart("未授权麦克风", "麦克风权限未授权", delayBeforeStopMs = 500L)
            return
        }

        WjxFileLogger.i(TAG, "start requested channel=$channel")
        notification.update("正在准备实时喊话")
        WjxOutsidePlayService.stop(this)
        val released = audioUsage.awaitInactive(
            owners = HAILING_BLOCKING_OWNERS,
            timeoutMs = AUDIO_RESOURCE_RELEASE_TIMEOUT_MS,
        )
        if (!released) {
            WjxFileLogger.w(TAG, "start blocked: competing audio resource release timeout")
            failStart("麦克风或车外音频未能及时释放", "麦克风资源释放超时")
            return
        }
        delay(AUDIO_RESOURCE_SETTLE_MS)
        if (!isStartStillRequested(channel)) {
            WjxFileLogger.i(TAG, "start cancelled before route arm channel=$channel")
            return
        }

        val tuning = audioSettings.realtimeTuning()
        val routed = router.armVerified(channel, BydExteriorAudioRouter.Usage.Talk, tuning.volumePercent)
        if (routed == null) {
            WjxFileLogger.w(TAG, "route unavailable requested=$channel volume=${tuning.volumePercent}")
            failStart("车外喊话通道打开失败", "车外喊话通道打开失败", delayBeforeStopMs = 700L)
            return
        }
        val hailingChannel = routed.channel
        if (!isStartStillRequested(channel)) {
            WjxFileLogger.i(TAG, "start cancelled after route arm channel=$hailingChannel")
            runCatching { router.disarm(hailingChannel) }
            return
        }
        WjxFileLogger.i(
            TAG,
            "route ready requested=$channel active=$hailingChannel volume=${tuning.volumePercent} ${routed.summary()}",
        )

        activeChannel = hailingChannel
        val nextEngine = RealtimeHailingEngine(
            context = applicationContext,
            channel = hailingChannel,
            initialTuning = tuning,
            onError = { reason ->
                WjxFileLogger.w(TAG, "engine error: $reason")
                commands.trySend(
                    RealtimeHailingServiceCommand.Stop(
                        notifyIfIdle = true,
                        finalPhase = RealtimeHailingPhase.Error,
                        message = reason,
                    ),
                )
            },
        )
        if (!nextEngine.start()) {
            WjxFileLogger.w(TAG, "engine failed to start")
            runCatching { router.disarm(hailingChannel) }
            activeChannel = null
            failStart("麦克风或输出初始化失败", "麦克风或输出初始化失败", delayBeforeStopMs = 700L)
            return
        }
        engine = nextEngine
        if (!isStartStillRequested(channel)) {
            WjxFileLogger.i(TAG, "start cancelled after engine initialization channel=$hailingChannel")
            releaseHailingRouteSilently(hailingChannel, nextEngine)
            engine = null
            activeChannel = null
            audioUsage.setActive(AudioOwner.RealtimeHailing, false)
            return
        }
        publishState(RealtimeHailingPhase.Running, "实时喊话已开启", hailingChannel)
        notification.update("正在实时喊话")
        WjxFileLogger.i(
            TAG,
            "started requested=$channel active=$hailingChannel volume=${tuning.volumePercent} " +
                "mic=${tuning.micGainPercent} noise=${tuning.noiseSuppressionPercent}",
        )
    }

    private fun isStartStillRequested(channel: ExteriorChannel): Boolean {
        return RealtimeHailingStateStore.snapshot().isStartRequestFor(channel)
    }

    private suspend fun failStart(
        message: String,
        notification: String,
        delayBeforeStopMs: Long = 0L,
    ) {
        publishState(RealtimeHailingPhase.Error, message, activeChannel)
        this.notification.update(notification)
        audioUsage.setActive(AudioOwner.RealtimeHailing, false)
        if (delayBeforeStopMs > 0L) delay(delayBeforeStopMs)
        stopSelf()
    }

    private suspend fun stopHailing(
        stopService: Boolean,
        finalPhase: RealtimeHailingPhase = RealtimeHailingPhase.Idle,
        message: String = "实时喊话已停止",
        notifyIfIdle: Boolean = false,
    ) {
        val currentEngine = engine
        val hadEngine = currentEngine != null
        val channel = activeChannel
        val wasActive = hadEngine || channel != null || RealtimeHailingStateStore.snapshot().isActive
        if (wasActive && finalPhase != RealtimeHailingPhase.Error) {
            publishState(RealtimeHailingPhase.Stopping, "正在停止实时喊话", channel)
        }

        var engineStoppedForRoute = false
        activeChannel = null
        if (channel != null && hadEngine) {
            releaseHailingRouteSilently(channel, currentEngine)
            engineStoppedForRoute = true
        } else if (channel != null) {
            runCatching { router.disarm(channel) }
                .onFailure { WjxFileLogger.w(TAG, "route disarm failed: ${it.message}") }
        }
        if (!engineStoppedForRoute) currentEngine?.stop()
        engine = null
        if (hadEngine || finalPhase == RealtimeHailingPhase.Error || notifyIfIdle || wasActive) {
            publishState(finalPhase, message)
            notification.update(message)
            WjxFileLogger.i(TAG, "stopped phase=$finalPhase message=$message channel=$channel")
        }
        audioUsage.setActive(AudioOwner.RealtimeHailing, false)
        if (stopService) stopSelf()
    }

    private suspend fun releaseHailingRouteSilently(
        channel: ExteriorChannel,
        currentEngine: RealtimeHailingEngine?,
    ) {
        WjxFileLogger.i(TAG, "mute and stop hailing output before route release channel=$channel")
        currentEngine?.stopForRouteRelease()
        delay(ROUTE_RELEASE_DRAIN_MS)
        WjxFileLogger.i(TAG, "release hailing route silently channel=$channel")
        runCatching {
            val released = withTimeoutOrNull(ROUTE_RELEASE_TIMEOUT_MS) {
                router.disarm(channel)
                true
            } ?: false
            if (!released) WjxFileLogger.w(TAG, "hailing route release timeout channel=$channel")
        }.onFailure {
            WjxFileLogger.w(TAG, "route disarm failed: ${it.message}")
        }
    }

    private suspend fun updateHailingTuning() {
        val currentEngine = engine
        if (currentEngine?.isRunning != true) {
            audioUsage.setActive(AudioOwner.RealtimeHailing, false)
            if (RealtimeHailingStateStore.snapshot().isActive) {
                publishState(RealtimeHailingPhase.Idle, "实时喊话已停止")
            }
            stopSelf()
            return
        }
        val tuning = audioSettings.realtimeTuning()
        currentEngine.updateTuning(tuning)
        activeChannel?.let { channel ->
            router.updateVolume(channel, BydExteriorAudioRouter.Usage.Talk, tuning.volumePercent)
        }
        notification.update("正在实时喊话")
        WjxFileLogger.i(
            TAG,
            "tuning updated volume=${tuning.volumePercent} mic=${tuning.micGainPercent} " +
                "noise=${tuning.noiseSuppressionPercent}",
        )
    }

    private fun publishState(
        phase: RealtimeHailingPhase,
        message: String,
        channel: ExteriorChannel? = null,
    ) {
        val state = RealtimeHailingState(phase = phase, message = message, channel = channel)
        RealtimeHailingStateStore.publish(state)
        sendBroadcast(RealtimeHailingStateContract.intent(packageName, state))
    }

    companion object {
        private const val TAG = "WjxRealtimeHailing"
        private const val ROUTE_RELEASE_DRAIN_MS = 360L
        private const val ROUTE_RELEASE_TIMEOUT_MS = 1_000L
        private const val AUDIO_RESOURCE_RELEASE_TIMEOUT_MS = 2_400L
        private const val AUDIO_RESOURCE_SETTLE_MS = 320L
        private val HAILING_BLOCKING_OWNERS = setOf(AudioOwner.ExteriorPlayback)
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun state(): RealtimeHailingState = RealtimeHailingServiceGateway.state()

        fun start(context: Context, channel: ExteriorChannel): Boolean =
            RealtimeHailingServiceGateway.start(context, channel)

        fun stop(context: Context, notifyIfIdle: Boolean = false): Boolean =
            RealtimeHailingServiceGateway.stop(context, notifyIfIdle)

        fun toggle(context: Context, channel: ExteriorChannel) {
            RealtimeHailingServiceGateway.toggle(context, channel)
        }

        fun updateTuning(context: Context) {
            RealtimeHailingServiceGateway.updateTuning(context)
        }
    }
}
