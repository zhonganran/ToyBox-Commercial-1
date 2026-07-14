package com.byd.wjx.overlay

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.byd.wjx.data.camera.CameraForegroundMatcher
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.util.HomePackageResolver
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DesktopOverlayVisibilityDecision(
    val visible: Boolean,
    val reason: String,
    val trigger: String,
)

internal fun visibilityStateChanged(
    previous: DesktopOverlayVisibilityDecision?,
    next: DesktopOverlayVisibilityDecision,
): Boolean = previous == null ||
    previous.visible != next.visible ||
    previous.reason != next.reason

private data class ForegroundWindowSnapshot(
    val packageName: String,
    val className: String?,
    val source: String,
)

class DesktopOverlayVisibilityController(
    context: Context,
    private val scope: CoroutineScope,
    private val cameraActive: StateFlow<Boolean>,
    private val logTag: String,
    private val allowOnOtherApps: () -> Boolean = { false },
) {
    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val homePackages by lazy { resolveHomePackages() }
    private var cameraActiveValue = cameraActive.value
    private var appForegrounded = DesktopOverlayVisibilityEvents.appForegrounded.value
    private var usageForeground: ForegroundWindowSnapshot? = null
    private var taskForeground: ForegroundWindowSnapshot? = null
    private var foregroundEventTs = 0L
    private var foregroundQueryWarned = false
    private var taskQueryWarned = false
    private var lastConfirmedDesktopAtMs = 0L
    private var desktopHoldUntilMs = 0L
    private var lastDecision: DesktopOverlayVisibilityDecision? = null
    private var callback: ((DesktopOverlayVisibilityDecision) -> Unit)? = null
    private var pollJob: Job? = null
    private var cameraJob: Job? = null
    private var appJob: Job? = null
    private var a11yJob: Job? = null

    fun start(onDecision: (DesktopOverlayVisibilityDecision) -> Unit) {
        if (pollJob?.isActive == true) return
        callback = onDecision
        cameraJob = scope.launch {
            cameraActive.collect {
                cameraActiveValue = it
                refresh("camera")
            }
        }
        appJob = scope.launch {
            DesktopOverlayVisibilityEvents.appForegrounded.collect {
                appForegrounded = it
                refresh("app_foreground")
            }
        }
        a11yJob = scope.launch {
            DesktopOverlayVisibilityEvents.accessibilitySnapshot.collect {
                refresh("a11y")
            }
        }
        pollJob = scope.launch {
            while (true) {
                val foregrounds = withContext(Dispatchers.IO) {
                    readUsageForeground() to readTaskForeground()
                }
                usageForeground = foregrounds.first
                taskForeground = foregrounds.second
                refresh("foreground")
                delay(FOREGROUND_POLL_MS)
            }
        }
        refresh("start")
    }

    fun stop() {
        pollJob?.cancel()
        cameraJob?.cancel()
        appJob?.cancel()
        a11yJob?.cancel()
        pollJob = null
        cameraJob = null
        appJob = null
        a11yJob = null
        callback = null
        lastDecision = null
    }

    fun refresh(trigger: String) {
        val decision = decide(trigger)
        val changed = visibilityStateChanged(lastDecision, decision)
        if (changed) {
            val a11y = freshAccessibilitySnapshot()
            WjxFileLogger.d(
                logTag,
                "desktop visibility trigger=$trigger visible=${decision.visible} reason=${decision.reason} camera=$cameraActiveValue app=$appForegrounded usage=${usageForeground?.packageName.orEmpty()}/${usageForeground?.className.orEmpty()} task=${taskForeground?.packageName.orEmpty()}/${taskForeground?.className.orEmpty()} a11y=${a11y?.packageName.orEmpty()}/${a11y?.className.orEmpty()} home=$homePackages",
            )
        }
        lastDecision = decision
        if (changed || trigger == "show" || trigger == "rebuild" || trigger.startsWith("hold:")) {
            callback?.invoke(decision)
        }
    }

    fun extendDesktopHold(reason: String) {
        val now = SystemClock.elapsedRealtime()
        desktopHoldUntilMs = max(desktopHoldUntilMs, now + DESKTOP_HOLD_AFTER_COMMAND_MS)
        if (lastConfirmedDesktopAtMs <= 0L) {
            val foreground = chooseForegroundSnapshot()
            if (foreground != null &&
                isDesktopPackage(foreground.packageName) &&
                !isLauncherSubPage(foreground.className) &&
                !isMapOrCameraWindow(foreground.packageName, foreground.className)
            ) {
                lastConfirmedDesktopAtMs = now
            }
        }
        WjxFileLogger.d(logTag, "desktop hold extended reason=$reason holdMs=${desktopHoldUntilMs - now}")
        refresh("hold:$reason")
    }

    private fun decide(trigger: String): DesktopOverlayVisibilityDecision {
        if (cameraActiveValue) return DesktopOverlayVisibilityDecision(false, "camera", trigger)
        if (appForegrounded) return DesktopOverlayVisibilityDecision(false, "app_foreground", trigger)
        if (allowOnOtherApps()) return DesktopOverlayVisibilityDecision(true, "allow_other_apps", trigger)

        val now = SystemClock.elapsedRealtime()
        val a11y = freshAccessibilitySnapshot()
        val a11yPkg = a11y?.packageName
        val a11yCls = a11y?.className
        val a11ySnapshot = a11y?.let {
            ForegroundWindowSnapshot(it.packageName, it.className, "a11y:${it.source}")
        }
        val usagePkg = usageForeground?.packageName
        val usageCls = usageForeground?.className
        val taskPkg = taskForeground?.packageName
        val taskCls = taskForeground?.className
        if (isTransientDesktopOverlay(a11yPkg, a11yCls) ||
            isTransientDesktopOverlay(taskPkg, taskCls) ||
            isTransientDesktopOverlay(usagePkg, usageCls)
        ) {
            return if (shouldKeepVisibleDuringDesktopHold(now)) {
                DesktopOverlayVisibilityDecision(true, "desktop_hold", trigger)
            } else {
                DesktopOverlayVisibilityDecision(false, "transient_overlay", trigger)
            }
        }

        val blockingSnapshot = listOfNotNull(a11ySnapshot, taskForeground, usageForeground)
            .firstOrNull { !isIgnoredForBlocking(it.packageName) }
        if (blockingSnapshot != null &&
            isMapOrCameraWindow(blockingSnapshot.packageName, blockingSnapshot.className)
        ) {
            return DesktopOverlayVisibilityDecision(false, "map_or_camera", trigger)
        }

        val foregroundSnapshot = chooseForegroundSnapshot()
            ?: return DesktopOverlayVisibilityDecision(false, "no_foreground", trigger)
        val foreground = foregroundSnapshot.packageName
        if (foreground == appContext.packageName) {
            return DesktopOverlayVisibilityDecision(false, "own_app", trigger)
        }
        val foregroundClass = foregroundSnapshot.className
        val isHome = isDesktopPackage(foreground)
        val a11ySubpageActive = a11y?.let {
            if (foregroundSnapshot.source.startsWith("a11y")) it.launcherSubpage else it.launcherOverlay
        } == true
        val isSubpage = isHome && (a11ySubpageActive || isLauncherSubPage(foregroundClass))
        if (isSubpage) return DesktopOverlayVisibilityDecision(false, "launcher_subpage", trigger)
        if (isHome) {
            lastConfirmedDesktopAtMs = now
            return DesktopOverlayVisibilityDecision(true, "desktop", trigger)
        }
        return DesktopOverlayVisibilityDecision(false, "off_desktop", trigger)
    }

    private fun shouldKeepVisibleDuringDesktopHold(now: Long): Boolean {
        if (now > desktopHoldUntilMs) return false
        if (lastConfirmedDesktopAtMs <= 0L) return false
        return now - lastConfirmedDesktopAtMs <= DESKTOP_HOLD_MEMORY_MS
    }

    private fun chooseForegroundSnapshot(): ForegroundWindowSnapshot? {
        val a11y = freshAccessibilitySnapshot()
        val a11ySnapshot = a11y?.let {
            ForegroundWindowSnapshot(it.packageName, it.className, "a11y:${it.source}")
        }
        val candidates = listOfNotNull(a11ySnapshot, taskForeground, usageForeground)
            .filterNot { isIgnoredForegroundPackage(it.packageName) }
        if (candidates.isEmpty()) return null

        val first = candidates.first()
        if (first.source.startsWith("a11y") && isDesktopPackage(first.packageName)) {
            val nonDesktop = candidates.firstOrNull {
                !it.source.startsWith("a11y") && !isDesktopPackage(it.packageName)
            }
            if (nonDesktop != null) return nonDesktop
        }
        if (isDesktopPackage(first.packageName) && isLauncherSubPage(first.className)) {
            val repaired = candidates.firstOrNull {
                isDesktopPackage(it.packageName) && !isLauncherSubPage(it.className)
            }
            if (repaired != null) return repaired
        }
        return first
    }

    private fun freshAccessibilitySnapshot(): DesktopOverlayVisibilityEvents.AccessibilitySnapshot? {
        val snapshot = DesktopOverlayVisibilityEvents.accessibilitySnapshot.value ?: return null
        if (SystemClock.elapsedRealtime() - snapshot.updatedAtMs > A11Y_FOREGROUND_TTL_MS) return null
        return snapshot
    }

    private fun readUsageForeground(): ForegroundWindowSnapshot? {
        val manager = usageStatsManager ?: return usageForeground
        val now = System.currentTimeMillis()
        val begin = if (foregroundEventTs == 0L) now - INITIAL_FOREGROUND_LOOKBACK_MS else foregroundEventTs + 1L
        return runCatching {
            val events = manager.queryEvents(begin, now + 1L)
            val event = UsageEvents.Event()
            var latestTs = -1L
            var latestPackage: String? = null
            var latestClass: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (isForegroundEvent(event) && event.timeStamp > latestTs) {
                    latestTs = event.timeStamp
                    latestPackage = event.packageName
                    latestClass = event.className
                }
            }
            if (latestPackage != null) {
                foregroundEventTs = max(foregroundEventTs, latestTs)
                ForegroundWindowSnapshot(
                    packageName = latestPackage.orEmpty(),
                    className = latestClass,
                    source = "usage",
                )
            } else {
                if (foregroundEventTs == 0L) foregroundEventTs = now
                usageForeground
            }
        }.onFailure {
            if (!foregroundQueryWarned) {
                foregroundQueryWarned = true
                WjxFileLogger.w(logTag, "foreground query failed: ${it.message}")
            }
        }.getOrDefault(usageForeground)
    }

    private fun readTaskForeground(): ForegroundWindowSnapshot? {
        val manager = activityManager ?: return taskForeground
        return runCatching {
            @Suppress("DEPRECATION")
            val topActivity = manager.getRunningTasks(1).firstOrNull()?.topActivity ?: return@runCatching taskForeground
            val pkg = topActivity.packageName.orEmpty()
            if (pkg.isBlank()) taskForeground else ForegroundWindowSnapshot(
                packageName = pkg,
                className = topActivity.className,
                source = "task",
            )
        }.onFailure {
            if (!taskQueryWarned) {
                taskQueryWarned = true
                WjxFileLogger.w(logTag, "task foreground query failed: ${it.message}")
            }
        }.getOrDefault(taskForeground)
    }

    private fun resolveHomePackages(): Set<String> {
        return HomePackageResolver.resolve(appContext)
    }

    private fun isDesktopPackage(pkg: String?): Boolean =
        HomePackageResolver.isHomePackage(pkg, homePackages)

    private fun isMapOrCameraWindow(pkg: String?, className: String?): Boolean =
        CameraForegroundMatcher.isCameraWindow(pkg, className) ||
            MAP_OR_MEDIA_PACKAGE_KEYWORDS.any { keyword ->
                pkg.orEmpty().contains(keyword, ignoreCase = true)
            }

    private fun isIgnoredForegroundPackage(pkg: String?): Boolean {
        val packageName = pkg.orEmpty()
        if (isIgnoredForBlocking(packageName)) return true
        if (packageName == "com.byd.avc") return true
        return false
    }

    private fun isIgnoredForBlocking(pkg: String?): Boolean {
        val packageName = pkg.orEmpty()
        if (packageName.isBlank()) return true
        if (packageName == appContext.packageName) return true
        if (packageName == "android") return true
        return IGNORED_FOREGROUND_PACKAGE_KEYWORDS.any {
            packageName.contains(it, ignoreCase = true)
        }
    }

    private fun isTransientDesktopOverlay(pkg: String?, className: String?): Boolean {
        val packageName = pkg.orEmpty()
        val cls = className.orEmpty()
        if (packageName.isBlank()) return false
        if (packageName in TRANSIENT_DESKTOP_OVERLAY_PACKAGES) return true
        return TRANSIENT_DESKTOP_OVERLAY_CLASS_KEYWORDS.any { cls.contains(it, ignoreCase = true) }
    }

    private fun isLauncherSubPage(className: String?): Boolean {
        val cls = className.orEmpty()
        return cls.isNotBlank() &&
            LAUNCHER_SUBPAGE_CLASS_KEYWORDS.any { cls.contains(it, ignoreCase = true) }
    }

    private fun isForegroundEvent(event: UsageEvents.Event): Boolean =
        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)

    companion object {
        private const val FOREGROUND_POLL_MS = 1_000L
        private const val INITIAL_FOREGROUND_LOOKBACK_MS = 5L * 60_000L
        private const val A11Y_FOREGROUND_TTL_MS = 3_500L
        private const val DESKTOP_HOLD_AFTER_COMMAND_MS = 30_000L
        private const val DESKTOP_HOLD_MEMORY_MS = 45_000L
        private val MAP_OR_MEDIA_PACKAGE_KEYWORDS = listOf(
            "amap",
            "autonavi",
            "baidu.nav",
            "mapbar",
            "navinfo",
            "auto_map",
            "automap",
            "launchermap",
            "byd.map",
            "byd.navi",
            "dilink.map",
            "iqiyi",
            "youku",
            "bilibili",
            "spotify",
            "kugou",
            "kuwo",
            "wechat",
            "qq.",
            "dingtalk",
            "mediacenter",
        )
        private val IGNORED_FOREGROUND_PACKAGE_KEYWORDS = listOf(
            "systemui",
            "widget",
        )
        private val TRANSIENT_DESKTOP_OVERLAY_PACKAGES = setOf(
            "com.example.amapservice",
        )
        private val TRANSIENT_DESKTOP_OVERLAY_CLASS_KEYWORDS = listOf(
            "TopEmptyActivity",
            "projectionmanager",
        )
        private val LAUNCHER_SUBPAGE_CLASS_KEYWORDS = listOf(
            "Setting",
            "AppList",
            "AppGrid",
            "EditPage",
            "Wallpaper",
            "WidgetPage",
            "AppWidget",
            "WidgetHost",
            "Notification",
            "AllApps",
            "AppsPage",
            "AppCenter",
            "ApplicationCenter",
            "AppStore",
            "AllApp",
            "AppsManager",
            "AppManage",
            "AppDrawer",
            "AppMain",
            "AppPage",
            "AllApplication",
            "ApplicationList",
            "SoftwareStore",
            "ProductCenter",
            "Applications",
            "IconList",
            "SoftwareList",
            "VehicleApp",
            "Navi",
            "Navigation",
            "MapActivity",
            "Amap",
            "AutoMap",
            "GaoDe",
            "Gaode",
            "AutoNavi",
            "MainMap",
            "CarMap",
            "MapMain",
            "MapPage",
            "MapView",
            "Route",
            "Guide",
            "SearchDest",
            "BydMap",
            "DilinkMap",
            "Panorama",
            "Pano",
            "全景",
            "VehicleView",
            "SurroundView",
            "Car360",
            "VehiclePanorama",
        )
    }
}

object DesktopOverlayVisibilityEvents {
    data class AccessibilitySnapshot(
        val source: String,
        val packageName: String,
        val className: String,
        val launcherSubpage: Boolean,
        val launcherOverlay: Boolean,
        val updatedAtMs: Long,
    )

    private val lock = Any()
    private val snapshots = linkedMapOf<String, AccessibilitySnapshot>()
    private val _accessibilitySnapshot = MutableStateFlow<AccessibilitySnapshot?>(null)
    val accessibilitySnapshot: StateFlow<AccessibilitySnapshot?> = _accessibilitySnapshot.asStateFlow()

    private val _appForegrounded = MutableStateFlow(false)
    val appForegrounded: StateFlow<Boolean> = _appForegrounded.asStateFlow()

    fun setAppForegrounded(foreground: Boolean) {
        _appForegrounded.value = foreground
    }

    fun notifyAccessibilityForeground(
        context: Context,
        packageName: String?,
        className: String?,
        launcherSubpage: Boolean = false,
        launcherOverlay: Boolean = false,
        source: String = A11Y_SOURCE_DEFAULT,
    ) {
        val appContext = context.applicationContext
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank() || pkg == appContext.packageName) return
        val cls = className?.trim().orEmpty()
        val effectiveLauncherSubpage = isKnownLauncherPackage(pkg) &&
            (launcherSubpage || classIndicatesLauncherSubpage(cls))
        val src = source.trim().ifBlank { A11Y_SOURCE_DEFAULT }
        val snapshot = AccessibilitySnapshot(
            source = src,
            packageName = pkg,
            className = cls,
            launcherSubpage = effectiveLauncherSubpage,
            launcherOverlay = launcherOverlay,
            updatedAtMs = SystemClock.elapsedRealtime(),
        )
        synchronized(lock) {
            snapshots[src] = snapshot
            _accessibilitySnapshot.value = snapshots.values.maxByOrNull { it.updatedAtMs }
        }
    }

    fun clearAccessibilityForeground(source: String? = null) {
        val src = source?.trim()?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            if (src == null) {
                snapshots.clear()
            } else {
                snapshots.remove(src)
            }
            _accessibilitySnapshot.value = snapshots.values.maxByOrNull { it.updatedAtMs }
        }
    }

    private fun isKnownLauncherPackage(pkg: String): Boolean =
        HomePackageResolver.isKnownHomePackage(pkg)

    private fun classIndicatesLauncherSubpage(className: String): Boolean =
        className.isNotBlank() &&
            LAUNCHER_SUBPAGE_CLASS_KEYWORDS.any { className.contains(it, ignoreCase = true) }

    const val A11Y_SOURCE_KEEPALIVE = "keepalive"
    const val A11Y_SOURCE_STEERING = "steering"
    private const val A11Y_SOURCE_DEFAULT = "unknown"
    private val LAUNCHER_SUBPAGE_CLASS_KEYWORDS = listOf(
        "Setting",
        "AppList",
        "AppGrid",
        "EditPage",
        "Wallpaper",
        "WidgetPage",
        "AppWidget",
        "WidgetHost",
        "Notification",
        "AllApps",
        "AppsPage",
        "AppCenter",
        "ApplicationCenter",
        "AppStore",
        "AllApp",
        "AppsManager",
        "AppManage",
        "AppDrawer",
        "AppMain",
        "AppPage",
        "AllApplication",
        "ApplicationList",
        "SoftwareStore",
        "ProductCenter",
        "Applications",
        "IconList",
        "SoftwareList",
        "VehicleApp",
        "Navi",
        "Navigation",
        "MapActivity",
        "Amap",
        "AutoMap",
        "GaoDe",
        "Gaode",
        "AutoNavi",
        "MainMap",
        "CarMap",
        "MapMain",
        "MapPage",
        "MapView",
        "Route",
        "Guide",
        "SearchDest",
        "BydMap",
        "DilinkMap",
        "Panorama",
        "Pano",
        "全景",
        "VehicleView",
        "SurroundView",
        "Car360",
        "VehiclePanorama",
    )
}
