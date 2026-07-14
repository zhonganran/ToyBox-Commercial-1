package com.byd.wjx.cluster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class ClusterMirrorService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseMirrorDisplay()
            mediaProjection = null
            completeProjectionRequest(false)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mirrorDisplayId: Int = -1

    override fun onCreate() {
        super.onCreate()
        instance = this
        WjxFileLogger.i(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onDestroy() {
        WjxFileLogger.i(TAG, "onDestroy")
        releaseProjection()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                WjxFileLogger.i(TAG, "prepare projection service")
                startForegroundCompat(getString(R.string.cluster_mirror_notification_preparing))
            }
            ACTION_SET_PROJECTION -> {
                WjxFileLogger.i(TAG, "set projection result")
                startForegroundCompat(getString(R.string.cluster_mirror_notification_ready))
                handleProjectionResult(intent)
            }
            ACTION_RELEASE -> {
                WjxFileLogger.i(TAG, "release projection service")
                releaseProjection()
                stopForegroundRemove()
                stopSelf()
            }
            else -> return START_NOT_STICKY
        }
        return START_STICKY
    }

    fun createMirrorVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface,
    ): Int? {
        val projection = mediaProjection ?: return null
        val oldDisplay = virtualDisplay
        val oldDisplayId = mirrorDisplayId
        return try {
            val display = projection.createVirtualDisplay(
                name,
                width,
                height,
                densityDpi,
                MEDIA_PROJECTION_FLAGS,
                surface,
                null,
                null,
            )
            val id = display?.display?.displayId ?: -1
            if (id > 0) {
                virtualDisplay = display
                mirrorDisplayId = id
                if (oldDisplayId != id) oldDisplay?.release()
                startForegroundCompat(getString(R.string.cluster_mirror_notification_active))
                WjxFileLogger.i(TAG, "create mirror display id=$id ${width}x$height@$densityDpi old=$oldDisplayId")
                id
            } else {
                display?.release()
                WjxFileLogger.w(TAG, "createMirrorVirtualDisplay returned invalid id=$id")
                null
            }
        } catch (e: Exception) {
            WjxFileLogger.e(TAG, "createMirrorVirtualDisplay failed: ${e.message}", e)
            null
        }
    }

    fun releaseMirrorDisplay() {
        if (mirrorDisplayId != -1) WjxFileLogger.i(TAG, "release mirror display id=$mirrorDisplayId")
        virtualDisplay?.release()
        virtualDisplay = null
        mirrorDisplayId = -1
    }

    private fun handleProjectionResult(intent: Intent) {
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            WjxFileLogger.e(TAG, "projection result missing data")
            releaseProjectionAndStopSelf()
            return
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = runCatching { manager?.getMediaProjection(resultCode, resultData) }.getOrNull()
        if (projection == null) {
            WjxFileLogger.e(TAG, "getMediaProjection returned null resultCode=$resultCode")
            releaseProjectionAndStopSelf()
            return
        }
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)
        WjxFileLogger.i(TAG, "media projection ready resultCode=$resultCode")
        completeProjectionRequest(true)
    }

    private fun releaseProjection() {
        releaseMirrorDisplay()
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        completeProjectionRequest(false)
    }

    private fun releaseProjectionAndStopSelf() {
        releaseProjection()
        stopForegroundRemove()
        stopSelf()
    }

    private fun stopForegroundRemove() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.cluster_mirror_notification_title))
                .setContentText(text)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.cluster_mirror_notification_title))
                .setContentText(text)
                .setOngoing(true)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.cluster_mirror_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "ClusterMirror"
        private const val CHANNEL_ID = "wjx_cluster_mirror"
        private const val NOTIFICATION_ID = 7323
        private const val MEDIA_PROJECTION_FLAGS = 16
        private const val PROJECTION_TIMEOUT_MS = 60_000L

        const val ACTION_PREPARE = "com.byd.wjx.action.CLUSTER_MIRROR_PREPARE"
        const val ACTION_SET_PROJECTION = "com.byd.wjx.action.CLUSTER_MIRROR_SET_PROJECTION"
        const val ACTION_RELEASE = "com.byd.wjx.action.CLUSTER_MIRROR_RELEASE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        private var instance: ClusterMirrorService? = null
        private val requestLock = Any()
        private var pendingProjectionRequest: CompletableDeferred<Boolean>? = null

        suspend fun ensureProjection(context: Context): Boolean {
            instance?.mediaProjection?.let { return true }
            val appContext = context.applicationContext
            val deferred = CompletableDeferred<Boolean>()
            synchronized(requestLock) {
                pendingProjectionRequest?.complete(false)
                pendingProjectionRequest = deferred
            }
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, ClusterMirrorService::class.java).setAction(ACTION_PREPARE),
                )
                appContext.startActivity(
                    Intent(appContext, ClusterMirrorConsentActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                WjxFileLogger.e(TAG, "start projection consent failed: ${e.message}", e)
                synchronized(requestLock) {
                    if (pendingProjectionRequest === deferred) {
                        pendingProjectionRequest = null
                    }
                }
                runCatching { stop(appContext) }
                return false
            }
            val ok = withTimeoutOrNull(PROJECTION_TIMEOUT_MS) { deferred.await() } == true
            if (!ok) {
                WjxFileLogger.w(TAG, "projection consent timeout")
                synchronized(requestLock) {
                    if (pendingProjectionRequest === deferred) {
                        pendingProjectionRequest = null
                    }
                }
                stop(appContext)
            }
            return ok
        }

        fun createMirrorVirtualDisplay(
            name: String,
            width: Int,
            height: Int,
            densityDpi: Int,
            surface: Surface,
        ): Int? {
            return instance?.createMirrorVirtualDisplay(name, width, height, densityDpi, surface)
        }

        fun releaseMirrorDisplay() {
            instance?.releaseMirrorDisplay()
        }

        fun releaseProjectionAndStop() {
            instance?.run {
                releaseProjection()
                stopForegroundRemove()
                stopSelf()
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, ClusterMirrorService::class.java).setAction(ACTION_RELEASE),
            )
        }

        private fun completeProjectionRequest(ok: Boolean) {
            synchronized(requestLock) {
                pendingProjectionRequest?.complete(ok)
                pendingProjectionRequest = null
            }
        }
    }
}
