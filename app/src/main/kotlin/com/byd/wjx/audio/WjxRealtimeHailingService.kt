package com.byd.wjx.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.os.IBinder
import android.os.Process
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class WjxRealtimeHailingService : Service() {
    @Inject lateinit var router: BydExteriorAudioRouter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionMutex = Mutex()
    private var engine: RealtimeHailingEngine? = null
    private var activeChannel: ExteriorChannel? = null
    private val audioSettings by lazy { ExteriorHailingSettings(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("实时喊话待命"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val channel = ExteriorChannel.fromName(intent.getStringExtra(EXTRA_CHANNEL))
                serviceScope.launch { actionMutex.withLock { startHailing(channel) } }
            }
            ACTION_STOP -> {
                val notifyIfIdle = intent.getBooleanExtra(EXTRA_NOTIFY_IDLE, false)
                serviceScope.launch { actionMutex.withLock { stopHailing(stopService = true, notifyIfIdle = notifyIfIdle) } }
            }
            ACTION_UPDATE_TUNING -> {
                serviceScope.launch { actionMutex.withLock { updateHailingTuning() } }
            }
            ACTION_TOGGLE -> {
                val channel = ExteriorChannel.fromName(intent.getStringExtra(EXTRA_CHANNEL))
                serviceScope.launch {
                    actionMutex.withLock {
                        if (engine?.isRunning == true || serviceActive) {
                            stopHailing(stopService = true, notifyIfIdle = true)
                        } else {
                            startHailing(channel)
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(1_200L) { actionMutex.withLock { stopHailing(stopService = false) } }
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startHailing(channel: ExteriorChannel) {
        if (engine?.isRunning == true) return
        serviceActive = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WjxFileLogger.w(TAG, "start blocked: RECORD_AUDIO denied")
            broadcastState(STATE_ERROR, "未授权麦克风")
            updateNotification("麦克风权限未授权")
            serviceActive = false
            delay(500L)
            stopSelf()
            return
        }

        WjxFileLogger.i(TAG, "start requested channel=$channel")
        updateNotification("正在准备实时喊话")
        WjxOutsidePlayService.stop(this)
        delay(140L)

        val tuning = audioSettings.realtimeTuning()
        val armed = router.arm(channel, BydExteriorAudioRouter.Usage.Talk, tuning.volumePercent)
        WjxFileLogger.i(TAG, "route arm channel=$channel volume=${tuning.volumePercent} result=$armed")
        if (!armed) {
            broadcastState(STATE_ERROR, "车外喊话通道打开失败")
            updateNotification("车外喊话通道打开失败")
            serviceActive = false
            delay(700L)
            stopSelf()
            return
        }

        activeChannel = channel
        val nextEngine = RealtimeHailingEngine(
            context = applicationContext,
            channel = channel,
            initialTuning = tuning,
            onError = { reason ->
                WjxFileLogger.w(TAG, "engine error: $reason")
                serviceScope.launch { actionMutex.withLock { stopHailing(stopService = true, state = STATE_ERROR, message = reason) } }
            },
        )
        if (!nextEngine.start()) {
            WjxFileLogger.w(TAG, "engine failed to start")
            runCatching { router.disarm(channel) }
            activeChannel = null
            broadcastState(STATE_ERROR, "麦克风或输出初始化失败")
            updateNotification("麦克风或输出初始化失败")
            serviceActive = false
            delay(700L)
            stopSelf()
            return
        }
        engine = nextEngine
        broadcastState(STATE_STARTED, "实时喊话已开启")
        updateNotification("正在实时喊话")
        WjxFileLogger.i(
            TAG,
            "started channel=$channel volume=${tuning.volumePercent} mic=${tuning.micGainPercent} noise=${tuning.noiseSuppressionPercent}",
        )
    }

    private suspend fun stopHailing(
        stopService: Boolean,
        state: String = STATE_STOPPED,
        message: String = "实时喊话已停止",
        notifyIfIdle: Boolean = false,
    ) {
        val currentEngine = engine
        val hadEngine = currentEngine != null
        val channel = activeChannel
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
        if (hadEngine || state == STATE_ERROR || notifyIfIdle) {
            broadcastState(state, message)
            updateNotification(message)
            WjxFileLogger.i(TAG, "stopped state=$state message=$message channel=$channel")
        }
        serviceActive = false
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
            if (!released) {
                WjxFileLogger.w(TAG, "hailing route release timeout channel=$channel")
            }
        }.onFailure {
            WjxFileLogger.w(TAG, "route disarm failed: ${it.message}")
        }
    }

    private suspend fun updateHailingTuning() {
        val currentEngine = engine
        if (currentEngine?.isRunning != true) {
            serviceActive = false
            stopSelf()
            return
        }
        val tuning = audioSettings.realtimeTuning()
        currentEngine.updateTuning(tuning)
        activeChannel?.let { channel ->
            router.updateVolume(channel, BydExteriorAudioRouter.Usage.Talk, tuning.volumePercent)
        }
        updateNotification("正在实时喊话")
        WjxFileLogger.i(
            TAG,
            "tuning updated volume=${tuning.volumePercent} mic=${tuning.micGainPercent} noise=${tuning.noiseSuppressionPercent}",
        )
    }

    private fun broadcastState(state: String, message: String) {
        sendBroadcast(
            Intent(ACTION_HAILING_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "实时喊话", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun updateNotification(text: String) {
        startForeground(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.byd.wjx.action.START_REALTIME_HAILING"
        private const val ACTION_STOP = "com.byd.wjx.action.STOP_REALTIME_HAILING"
        private const val ACTION_TOGGLE = "com.byd.wjx.action.TOGGLE_REALTIME_HAILING"
        private const val ACTION_UPDATE_TUNING = "com.byd.wjx.action.UPDATE_REALTIME_HAILING_TUNING"
        const val ACTION_HAILING_STATE = "com.byd.wjx.action.REALTIME_HAILING_STATE"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_NOTIFY_IDLE = "notify_idle"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        const val STATE_STARTED = "started"
        const val STATE_STOPPED = "stopped"
        const val STATE_ERROR = "error"
        private const val CHANNEL_ID = "wjx_realtime_hailing"
        private const val NOTIFICATION_ID = 1103
        private const val TAG = "WjxRealtimeHailing"
        private const val ROUTE_RELEASE_DRAIN_MS = 360L
        private const val ROUTE_RELEASE_TIMEOUT_MS = 1_000L
        @Volatile private var serviceActive = false

        fun start(context: Context, channel: ExteriorChannel) {
            serviceActive = true
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, WjxRealtimeHailingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_CHANNEL, channel.name)
            )
        }

        fun stop(context: Context, notifyIfIdle: Boolean = false): Boolean {
            val wasActive = serviceActive
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, WjxRealtimeHailingService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_NOTIFY_IDLE, notifyIfIdle)
            )
            return wasActive
        }

        fun toggle(context: Context, channel: ExteriorChannel) {
            if (serviceActive) {
                stop(context, notifyIfIdle = true)
            } else {
                start(context, channel)
            }
        }

        fun updateTuning(context: Context) {
            if (!serviceActive) return
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, WjxRealtimeHailingService::class.java)
                    .setAction(ACTION_UPDATE_TUNING)
            )
        }
    }
}
