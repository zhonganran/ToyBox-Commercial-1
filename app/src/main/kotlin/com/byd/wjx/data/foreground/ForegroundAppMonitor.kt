package com.byd.wjx.data.foreground

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ForegroundApp(
    val packageName: String? = null,
    val className: String? = null,
    val observedAtMs: Long = 0L,
    val source: ForegroundAppSource = ForegroundAppSource.UNKNOWN,
)

enum class ForegroundAppSource {
    UNKNOWN,
    APP_LIFECYCLE,
    ACCESSIBILITY_LIVE,
    ACCESSIBILITY_EVENT,
    RUNNING_TASK,
    USAGE_EVENT,
    CACHE,
}

/**
 * Process-wide foreground-app source. Camera suppression, desktop cards and desktop lyrics all
 * subscribe to this detector so they cannot disagree or create duplicate polling. The source
 * order is own-app lifecycle, live accessibility window, task stack, UsageEvents,
 * recent accessibility event, then a short cache.
 */
@Singleton
class ForegroundAppMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val _foreground = MutableStateFlow(ForegroundApp())
    val foreground: StateFlow<ForegroundApp> = _foreground.asStateFlow()

    private var job: Job? = null
    private val lifecycleLock = Any()
    private val owners = linkedSetOf<String>()
    private val rescanRequests = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var lastEventTimestamp = 0L
    @Volatile private var ownAppInForeground = false

    fun start(owner: String) {
        synchronized(lifecycleLock) {
            owners.add(owner)
            if (job?.isActive == true) return
            while (rescanRequests.tryReceive().isSuccess) Unit
            lastEventTimestamp = 0L
            AccessibilityForegroundBridge.setRescanListener {
                requestImmediateRescan("accessibility_window")
            }
            job = applicationScope.launch(Dispatchers.IO) {
                while (true) {
                    readForeground()?.let { latest ->
                        val current = _foreground.value
                        if (latest.packageName != current.packageName ||
                            latest.className != current.className ||
                            latest.source != current.source
                        ) {
                            _foreground.value = latest
                            WjxFileLogger.i(
                                TAG,
                                "foreground pkg=${latest.packageName} cls=${latest.className} " +
                                    "source=${latest.source} ts=${latest.observedAtMs}",
                            )
                        }
                    }
                    ensureActive()
                    val interval = if (_foreground.value.packageName == null) {
                        UNKNOWN_POLL_INTERVAL_MS
                    } else {
                        POLL_INTERVAL_MS
                    }
                    withTimeoutOrNull(interval) { rescanRequests.receive() }
                }
            }
            WjxFileLogger.i(TAG, "started owner=$owner owners=${owners.size}")
        }
    }

    fun requestImmediateRescan(reason: String) {
        rescanRequests.trySend(Unit)
        WjxFileLogger.i(TAG, "rescan requested reason=$reason")
    }

    fun setOwnAppForeground(foreground: Boolean, reason: String) {
        if (ownAppInForeground == foreground) return
        ownAppInForeground = foreground
        requestImmediateRescan(reason)
    }

    fun stop(owner: String) {
        synchronized(lifecycleLock) {
            owners.remove(owner)
            if (owners.isNotEmpty()) {
                WjxFileLogger.i(TAG, "retained after stop owner=$owner owners=${owners.size}")
                return
            }
            job?.cancel()
            job = null
            AccessibilityForegroundBridge.setRescanListener(null)
            lastEventTimestamp = 0L
            ownAppInForeground = false
            _foreground.value = ForegroundApp()
            WjxFileLogger.i(TAG, "stopped owner=$owner")
        }
    }

    private fun readForeground(): ForegroundApp? {
        val now = System.currentTimeMillis()
        if (ownAppInForeground) {
            return ForegroundApp(
                packageName = appContext.packageName,
                observedAtMs = now,
                source = ForegroundAppSource.APP_LIFECYCLE,
            )
        }

        val accessibility = AccessibilityForegroundBridge.snapshot(now)
            ?.takeUnless { it.packageName == appContext.packageName }
        val task = readRunningTask(now)
        val usage = readLatestUsageEvent(now)

        // Live accessibility is the fastest source for Launcher pages and full-screen vehicle UI.
        // A task result is preferred over a stale accessibility event, preserving the layered
        // detector instead of the old 4.1 UsageStats-only route.
        if (accessibility?.source == ForegroundAppSource.ACCESSIBILITY_LIVE) return accessibility
        if (task != null) return task
        if (usage != null) return usage
        if (accessibility != null) return accessibility

        return _foreground.value
            .takeIf { it.packageName != null && it.packageName != appContext.packageName }
            ?.copy(source = ForegroundAppSource.CACHE)
    }

    @Suppress("DEPRECATION")
    private fun readRunningTask(nowMs: Long): ForegroundApp? = try {
        val component = activityManager.getRunningTasks(1).firstOrNull()?.topActivity ?: return null
        component.packageName
            .takeIf { it.isNotBlank() && it != appContext.packageName }
            ?.let { packageName ->
                ForegroundApp(
                    packageName = packageName,
                    className = component.className,
                    observedAtMs = nowMs,
                    source = ForegroundAppSource.RUNNING_TASK,
                )
            }
    } catch (error: Exception) {
        WjxFileLogger.w(TAG, "query running task failed: ${error.message}")
        null
    }

    private fun readLatestUsageEvent(now: Long): ForegroundApp? {
        val begin = if (lastEventTimestamp == 0L) {
            now - INITIAL_LOOKBACK_MS
        } else {
            lastEventTimestamp + 1L
        }
        return try {
            val events = usageStats.queryEvents(begin, now + 1L)
            val event = UsageEvents.Event()
            var latest: ForegroundApp? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    (latest == null || event.timeStamp > latest.observedAtMs)
                ) {
                    latest = ForegroundApp(
                        packageName = event.packageName,
                        className = event.className,
                        observedAtMs = event.timeStamp,
                        source = ForegroundAppSource.USAGE_EVENT,
                    )
                }
            }
            latest?.also {
                lastEventTimestamp = maxOf(lastEventTimestamp, it.observedAtMs)
            } ?: run {
                // Avoid repeatedly walking the same initial window while AppOps is unavailable.
                if (lastEventTimestamp == 0L) lastEventTimestamp = now
                null
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "query foreground failed: ${error.message}")
            null
        }
    }

    private companion object {
        const val TAG = "ForegroundAppMonitor"
        const val POLL_INTERVAL_MS = 900L
        const val UNKNOWN_POLL_INTERVAL_MS = 2_000L
        const val INITIAL_LOOKBACK_MS = 5L * 60_000L
    }
}
