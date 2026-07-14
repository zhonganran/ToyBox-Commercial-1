package com.byd.wjx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.byd.wjx.MainActivity
import com.byd.wjx.R
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * User-visible keepalive service for the shell helper and app background stack.
 *
 * It does four things and keeps all of them observable in ChainLog:
 * - posts a low-importance foreground notification so the process has FGS priority;
 * - holds a PARTIAL_WAKE_LOCK while running, matching the aggressive car-daemon pattern;
 * - registers SCREEN_OFF dynamically and seeds the alarm-based revival watchdog;
 * - starts the core app stack and verifies the shell-uid helper daemon. ADB is opened only when
 *   [HelperBootstrap] actually needs to spawn or replace the helper.
 */
@AndroidEntryPoint
class KeepAliveService : Service() {

    @Inject lateinit var helperBootstrap: HelperBootstrap
    @Inject lateinit var helperClient: HelperClient
    @Inject lateinit var activationManager: com.byd.wjx.activation.ActivationManager
    @Inject lateinit var trackingStateRepository: TrackingStateRepository
    @Inject lateinit var lockSoundConfigSync: LockSoundConfigSync

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val healthHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenOffReceiver: ScreenOffReceiver? = null
    private var startupManager: DaemonStartupManager? = null
    private var healthCheckRunning = false
    @Volatile private var lastLockSoundSyncAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        WjxFileLogger.i(TAG, "onCreate")
        ChainLog.append(this, "KeepAliveService onCreate")
        createNotificationChannel()
        startForegroundCompat("后台保活运行中")
        acquireWakeLock()
        registerScreenOffReceiver()
        ProcessRevivalReceiver.schedule(applicationContext)
        startupManager = DaemonStartupManager(
            applicationContext,
            helperBootstrap,
            helperClient,
            trackingStateRepository,
            serviceScope,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "unknown"
        WjxFileLogger.i(TAG, "onStartCommand reason=$reason startId=$startId")
        ChainLog.append(this, "KeepAliveService start: $reason")
        if (!BootState.isUserUnlocked(this)) {
            ChainLog.append(this, "KeepAlive deferred: user locked")
            WjxFileLogger.i(TAG, "deferred: user locked reason=$reason")
            ProcessRevivalReceiver.schedule(applicationContext)
            return START_STICKY
        }
        val manager = startupManager ?: DaemonStartupManager(
            applicationContext,
            helperBootstrap,
            helperClient,
            trackingStateRepository,
            serviceScope,
        ).also { startupManager = it }
        manager.start(reason)
        serviceScope.launch {
            runCatching { refreshActivationState("start:$reason") }
                .onFailure { WjxFileLogger.w(TAG, "activation refresh failed: ${it.message}") }
            runCatching { syncLockSoundRuntime("start:$reason") }
                .onFailure { WjxFileLogger.w(TAG, "lock sound sync failed: ${it.message}", it) }
        }
        startHealthCheck(manager)
        return START_STICKY
    }

    override fun onDestroy() {
        WjxFileLogger.w(TAG, "onDestroy")
        ChainLog.append(this, "KeepAliveService onDestroy")
        stopHealthCheck()
        unregisterScreenOffReceiver()
        releaseWakeLock()
        serviceScope.cancel()
        ProcessRevivalReceiver.schedule(applicationContext)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        WjxFileLogger.w(TAG, "onTaskRemoved")
        ChainLog.append(this, "KeepAliveService onTaskRemoved")
        ProcessRevivalReceiver.schedule(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "超级玩具箱后台保活",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "维持车机后台服务、悬浮窗和独立进程运行"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.keepalive_notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SuperToyBox:KeepAlive",
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            ChainLog.append(this, "KeepAlive wakelock acquired")
        }.onFailure {
            WjxFileLogger.w(TAG, "wakelock failed: ${it.message}")
            ChainLog.append(this, "KeepAlive wakelock FAILED: ${it.message}")
        }
    }

    private fun renewWakeLock() {
        val lock = wakeLock
        if (lock == null || !lock.isHeld) {
            acquireWakeLock()
            return
        }
        runCatching {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        }.onFailure {
            WjxFileLogger.w(TAG, "wakelock renew failed: ${it.message}")
            ChainLog.append(this, "KeepAlive wakelock renew FAILED: ${it.message}")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) runCatching { it.release() }
        }
        wakeLock = null
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = ScreenOffReceiver.register(this)
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            ScreenOffReceiver.unregister(this, it)
            screenOffReceiver = null
        }
    }

    private fun startHealthCheck(manager: DaemonStartupManager) {
        if (healthCheckRunning) return
        healthCheckRunning = true
        ChainLog.append(this, "KeepAlive health check started")
        healthHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!healthCheckRunning) return
                renewWakeLock()
                if (BootState.isUserUnlocked(this@KeepAliveService)) {
                    manager.start(DaemonStartupManager.REASON_HEALTH_CHECK)
                    serviceScope.launch {
                        runCatching { refreshActivationState("health") }
                            .onFailure { WjxFileLogger.w(TAG, "activation health refresh failed: ${it.message}") }
                        val now = System.currentTimeMillis()
                        if (now - lastLockSoundSyncAtMs >= LOCK_SOUND_SYNC_INTERVAL_MS) {
                            runCatching { syncLockSoundRuntime("health") }
                                .onFailure { WjxFileLogger.w(TAG, "lock sound health sync failed: ${it.message}", it) }
                        }
                    }
                } else {
                    ProcessRevivalReceiver.schedule(applicationContext)
                }
                healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
            }
        }, HEALTH_CHECK_INTERVAL_MS)
    }

    private suspend fun refreshActivationState(reason: String) {
        val snapshot = activationManager.refreshIfNeeded()
        WjxFileLogger.i(
            TAG,
            "activation refresh reason=$reason device=${snapshot.deviceCode.isNotBlank()} " +
                "active=${snapshot.activated || snapshot.trialActive}",
        )
    }

    private suspend fun syncLockSoundRuntime(reason: String) {
        val ok = lockSoundConfigSync.sync("keepalive:$reason")
        if (ok) lastLockSoundSyncAtMs = System.currentTimeMillis()
        WjxFileLogger.i(TAG, "lock sound sync reason=$reason ok=$ok")
    }

    private fun stopHealthCheck() {
        healthCheckRunning = false
        healthHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "wjx_keepalive"
        private const val NOTIFICATION_ID = 1099
        private const val EXTRA_REASON = "reason"
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        private const val WAKE_LOCK_TIMEOUT_MS = HEALTH_CHECK_INTERVAL_MS * 3
        private const val LOCK_SOUND_SYNC_INTERVAL_MS = 5 * 60_000L

        fun start(context: Context, reason: String = "manual") {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KeepAliveService::class.java)
                .putExtra(EXTRA_REASON, reason)
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun delayedKick(context: Context, reason: String, delayMs: Long = 1500L) {
            val appContext = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { start(appContext, reason) }
            }, delayMs)
        }
    }
}
