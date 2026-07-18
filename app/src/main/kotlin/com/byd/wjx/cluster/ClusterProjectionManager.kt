package com.byd.wjx.cluster

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.electronicmirror.ElectronicMirrorRuntimeGuard
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Owns the cluster projection lifecycle. An overlay SurfaceView is placed on the
 * cluster display in the app process; its Surface backs either an independent
 * VirtualDisplay whose task is pinned to the cluster, or a duplicate display that
 * mirrors the centre screen without moving the centre-screen task.
 *
 * Mirrors OpenBYD ClusterOverlayManager but uses our suspend HelperClient instead
 * of ICarControl shell strings. Single in-memory instance — mode resets to OFF on
 * process death (acceptable for v1; persistence is a later concern).
 *
 * Concurrency: every setMode call runs under [mutex], so two fast polls can't
 * build overlapping overlays or clobber [remoteDisplayId]. The whole transition —
 * including VirtualDisplay creation and any independent-mode launchAndForce — runs inside the
 * lock, so a poll during an in-flight transition is queued, not dropped.
 *
 * State honesty: [currentMode] advances to the requested mode ONLY when the projection
 * fully succeeds (overlay up + VirtualDisplay created, and selected app pinned for
 * independent mode). On ANY failure it falls back to OFF and only pulls the selected
 * app back when independent mode moved it, so the in-memory mode
 * always matches what is actually on screen.
 *
 * Threading: WindowManager add/removeView run on Main; daemon calls are suspend
 * (HelperClient switches to IO internally).
 */
object ClusterProjectionManager {
    private const val TAG = "ClusterProjection"
    private const val DEFAULT_CLUSTER_DISPLAY_ID = 2          // Phase 0: fission display id
    private const val INDEPENDENT_VIRTUAL_DISPLAY_FLAGS = 322 // TRUSTED | OWN_CONTENT_ONLY | PRESENTATION (OpenBYD)
    private const val VD_NAME = "SuperToyBox_Cluster_VD"
    private const val OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY  // 2038, minSdk 29
    private const val OVERLAY_FLAGS = 264                     // FLAG_NOT_FOCUSABLE(8) | FLAG_LAYOUT_IN_SCREEN(256)
    private const val SURFACE_TIMEOUT_MS = 3000L              // give up if the overlay Surface never gets created
    private const val PROJECTION_FAILURE_COOLDOWN_MS = 1500L
    private val DUPLICATE_HELPER_FLAGS = intArrayOf(16, 17, 18, 272, 274)
    private val DUPLICATE_APP_FLAGS = intArrayOf(18, 16, 17)
    private val RECOVERY_DELAYS_MS = longArrayOf(0L, 350L, 900L, 1600L)

    const val PREFS_NAME = ClusterProjectionPreferences.PREFS_NAME
    // Master enable for star-controlled projection (settings switch). Read by SteeringWheelKeyService.
    const val KEY_MIRROR_ENABLED = ClusterProjectionPreferences.KEY_MIRROR_ENABLED
    // Steering-wheel keycode that toggles projection. Default = right star (DEFAULT_TRIGGER_KEYCODE).
    // Stored independently of the master switch so the choice survives turning the feature off.
    const val KEY_TRIGGER_KEYCODE = ClusterProjectionPreferences.KEY_TRIGGER_KEYCODE
    // User-tunable window size, % of the cluster panel (MIN_PROJECTION_PCT..MAX, default = full).
    const val KEY_WIDTH_PCT = ClusterProjectionPreferences.KEY_WIDTH_PCT
    const val KEY_HEIGHT_PCT = ClusterProjectionPreferences.KEY_HEIGHT_PCT
    // User-tunable window position within the free space (MIN_OFFSET_PCT..MAX, default = centered).
    const val KEY_OFFSET_X_PCT = ClusterProjectionPreferences.KEY_OFFSET_X_PCT
    const val KEY_OFFSET_Y_PCT = ClusterProjectionPreferences.KEY_OFFSET_Y_PCT
    // User-tunable content scale: VirtualDisplay density as % of cluster dpi (MIN_SCALE_PCT..MAX,
    // default 100 = native). Tunes what the projected app renders INSIDE the window — how big the
    // UI is and how much map fits — independent of the window size/position.
    const val KEY_SCALE_PCT = ClusterProjectionPreferences.KEY_SCALE_PCT
    // INDEPENDENT = move the selected app to cluster. DUPLICATE = mirror center screen to cluster.
    const val KEY_PROJECTION_LAYOUT = ClusterProjectionPreferences.KEY_PROJECTION_LAYOUT
    // App to project onto the cluster (default Amap Auto). Label is cached only for the settings row.
    const val KEY_TARGET_PACKAGE = ClusterProjectionPreferences.KEY_TARGET_PACKAGE
    const val KEY_TARGET_LABEL = ClusterProjectionPreferences.KEY_TARGET_LABEL
    // Last VirtualDisplay id we created. Persisted so a fresh app process can release the display
    // a prior (dead) process left orphaned in the long-lived daemon, instead of leaking it.
    private const val KEY_LAST_VD_ID = ClusterProjectionPreferences.KEY_LAST_VD_ID

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    @Volatile
    var currentMode: ClusterMode = ClusterMode.OFF
        private set

    // Keep the live overlay strongly referenced so OFF/reproject can reliably remove it from WindowManager.
    @SuppressLint("StaticFieldLeak")
    private var overlayView: View? = null
    private var remoteDisplayId: Int = -1
    private var remoteDisplayOwner: RemoteDisplayOwner = RemoteDisplayOwner.NONE
    private val appMirrorDisplays = mutableMapOf<Int, VirtualDisplay>()
    private val intentionalOverlayRemovals = mutableSetOf<View>()
    // Package actually pinned on the cluster (the one we launchAndForce'd). pullBackToMain tugs THIS
    // back, not the live settings target — the two differ when the user switches the projection app
    // mid-projection, and tugging the new target would strand the old app on the cluster.
    private var projectedPackage: String? = null
    // PROJECT_MEDIA has no app-side query API (unlike SYSTEM_ALERT_WINDOW / canDrawOverlays),
    // so we grant both via the daemon once per process the first time we project.
    private var projectionPermissionsGranted = false
    private var clusterWidth: Int = 1280
    private var clusterHeight: Int = 480
    private var clusterDensityDpi: Int = 320
    private var recoveryToken: Int = 0
    private var lastProjectionFailureAtMs: Long = 0L

    private enum class RemoteDisplayOwner { NONE, HELPER, APP, MEDIA_PROJECTION }

    private data class DisplayHandle(
        val id: Int,
        val owner: RemoteDisplayOwner,
    )

    private data class ProjectionSurfacePlan(
        val geo: ClusterGeometry,
        val render: RenderPlan,
        val surfaceWidth: Int,
        val surfaceHeight: Int,
        val surfaceX: Int,
        val surfaceY: Int,
    )

    /**
     * Drive the projection to [mode], serialized under [mutex]. Idempotent — a no-op when already
     * in [mode]. Auto-launch is delegated to the daemon's [launchAndForce] (it [launchApp]s Navi
     * when its task is absent), so a press with Navi closed launches it onto the cluster.
     * OFF always tears the projection down.
     */
    fun setMode(context: Context, mode: ClusterMode, helper: HelperClient, bootstrap: HelperBootstrap) {
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (mode == ClusterMode.OFF) cancelPendingRecovery()
                if (mode == ClusterMode.FULLSCREEN && ElectronicMirrorRuntimeGuard.isActive) {
                    WjxFileLogger.i(TAG, "projection request deferred: electronic mirror owns cluster priority")
                    return@withLock
                }
                if (mode == ClusterMode.FULLSCREEN && isFailureCoolingDown()) {
                    WjxFileLogger.w(TAG, "projection request ignored during failure cooldown")
                    return@withLock
                }
                if (mode == currentMode) return@withLock
                WjxFileLogger.i(TAG, "setMode: $currentMode -> $mode")
                applyModeLocked(appContext, mode, helper, bootstrap)
            }
        }
    }

    /**
     * Steering-wheel toggle: flip projection between cluster and center. Reads [currentMode] (success-honest —
     * a failed FULLSCREEN stays OFF, so the next press retries) and drives [setMode] to the other
     * state. Safe from the a11y key thread: setMode hops to [scope] and serializes under [mutex].
     */
    fun toggle(context: Context, helper: HelperClient, bootstrap: HelperBootstrap) =
        setMode(context, nextMode(currentMode), helper, bootstrap)

    /**
     * Self-enable our steering-wheel accessibility service via the daemon, so star control works on
     * a clean install with NO ADB (DiLink has no a11y settings UI). Called when the user turns the
     * settings switch on. [bootstrap] starts the daemon first if it is not up yet; the daemon op is
     * idempotent (force re-bind of our own Secure-settings entry, never clobbering other apps).
     */
    fun enableStarControl(helper: HelperClient, bootstrap: HelperBootstrap) {
        scope.launch {
            if (!bootstrap.ensureCurrent()) {
                WjxFileLogger.e(TAG, "helper daemon not running; cannot self-enable a11y"); return@launch
            }
            val ok = helper.enableAccessibilityService()
            WjxFileLogger.i(TAG, "enableStarControl: a11y enabled=$ok")
        }
    }

    /**
     * Warm up the MediaProjection fallback while Settings is in the foreground. Most vehicles should
     * use the helper duplicate flags first, but if those are rejected this avoids the first steering
     * wheel press having to launch a screen-capture consent UI from the background.
     */
    fun prepareDuplicateProjection(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val ok = ClusterMirrorService.ensureProjection(appContext)
            WjxFileLogger.i(TAG, "prepare duplicate MediaProjection fallback ok=$ok")
        }
    }

    fun stopDuplicateProjectionFallback() {
        scope.launch {
            ClusterMirrorService.releaseProjectionAndStop()
        }
    }

    /**
     * Apply the size currently saved in prefs to the live projection (size-slider change). No-op
     * unless we are actively projecting FULLSCREEN — when OFF the new size is picked up on the next
     * star press, since [project] reads the size prefs fresh. When projecting, [swapToNewSize] does
     * an in-place make-before-break resize so Navi never bounces to the main screen; only if that
     * fails do we fall back to a full (visibly bouncing) rebuild. Serialized under [mutex].
     */
    fun reproject(context: Context, helper: HelperClient, bootstrap: HelperBootstrap) {
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (currentMode != ClusterMode.FULLSCREEN) return@withLock
                WjxFileLogger.i(TAG, "reproject: in-place resize")
                if (!swapToNewSize(appContext, helper, bootstrap)) {
                    WjxFileLogger.w(TAG, "in-place resize failed; rebuilding projection")
                    applyModeLocked(appContext, ClusterMode.FULLSCREEN, helper, bootstrap)
                }
            }
        }
    }

    /**
     * Resize the live projection without dropping Navi back to the main screen. Stands up a fresh
     * overlay + VirtualDisplay at the saved size and moves Navi straight onto it, THEN releases the
     * old overlay + VirtualDisplay. Relocating a task to another display is fine, but *removing* a
     * task's only display is what bounces it to the main screen — so by moving Navi onto the new
     * cluster display before releasing the old one, the cluster just redraws at the new size.
     *
     * Caller holds [mutex]. Returns false ONLY when state is left uncertain (Navi may have moved
     * onto a released display) so [reproject] does a clean rebuild; on the benign failures (new
     * overlay/VD never came up) it returns true and leaves the existing projection running untouched.
     */
    private suspend fun swapToNewSize(
        context: Context, helper: HelperClient, bootstrap: HelperBootstrap,
    ): Boolean {
        if (!bootstrap.ensureCurrent()) return true        // daemon gone; keep what's on screen
        val oldOverlay = overlayView ?: return true
        val oldVdId = remoteDisplayId
        val oldOwner = remoteDisplayOwner
        val display = resolveClusterDisplay(context) ?: return true
        val layout = readProjectionLayout(context)
        val projectionPlan = buildProjectionSurfacePlan(context, layout) ?: return true

        // addOverlayAndAwaitSurface points overlayView at the NEW container; oldOverlay keeps the old
        // one so we can drop it after Navi has moved. remoteDisplayId is untouched until we commit.
        val surface = try {
            withTimeoutOrNull(SURFACE_TIMEOUT_MS) {
                addOverlayAndAwaitSurface(context, display, projectionPlan, helper, bootstrap)
            }
        } catch (e: Exception) {
            WjxFileLogger.e(TAG, "resize: new overlay threw: ${e.message}"); null
        }
        if (surface == null) {
            WjxFileLogger.e(TAG, "resize: new overlay Surface not ready; keeping current size")
            discardNewOverlayKeepOld(oldOverlay); return true
        }
        val newDisplay = createDisplayForLayout(context, helper, layout, projectionPlan.render, surface)
        if (newDisplay == null) {
            WjxFileLogger.e(TAG, "resize: createVirtualDisplay failed; keeping current size")
            discardNewOverlayKeepOld(oldOverlay); return true
        }
        if (layout == ProjectionLayout.INDEPENDENT) {
            val pkg = targetPackage(context)
            if (!helper.launchAndForce(pkg, newDisplay.id, projectionPlan.render.bufferWidth, projectionPlan.render.bufferHeight)) {
                // The app may already have been moved onto newVd; release it and let the caller rebuild.
                WjxFileLogger.e(TAG, "resize: launchAndForce failed")
                releaseDisplay(newDisplay.id, newDisplay.owner, helper)
                discardNewOverlayKeepOld(oldOverlay); return false
            }
            projectedPackage = pkg
        } else {
            if (projectedPackage != null) pullBackToMain(context, helper, focus = true)
            projectedPackage = null
        }
        // New projection is live. Commit the new id, then drop the old overlay + VirtualDisplay.
        remoteDisplayId = newDisplay.id
        remoteDisplayOwner = newDisplay.owner
        persistDisplayHandle(context, newDisplay)
        if (oldVdId != -1 && !(oldOwner == RemoteDisplayOwner.MEDIA_PROJECTION && newDisplay.owner == RemoteDisplayOwner.MEDIA_PROJECTION)) {
            releaseDisplay(oldVdId, oldOwner, helper)
        }
        removeOverlayView(oldOverlay)
        WjxFileLogger.i(TAG, "resize: swapped to ${projectionPlan.geo.width}x${projectionPlan.geo.height} ($layout vd $oldVdId -> ${newDisplay.id})")
        return true
    }

    /**
     * Failed-resize cleanup: remove the half-built NEW overlay and restore [overlayView] to the
     * still-running old projection, so nothing leaks and the surfaceDestroyed guard keeps matching.
     */
    private suspend fun discardNewOverlayKeepOld(oldOverlay: View) {
        val newOverlay = overlayView
        overlayView = oldOverlay
        if (newOverlay != null && newOverlay !== oldOverlay) removeOverlayView(newOverlay)
    }

    /** Remove an overlay container from its display's WindowManager (main thread, never throws). */
    private suspend fun removeOverlayView(view: View) {
        intentionalOverlayRemovals.add(view)
        withContext(Dispatchers.Main) {
            try {
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            } catch (e: Exception) {
                intentionalOverlayRemovals.remove(view)
                WjxFileLogger.w(TAG, "removeView failed: ${e.message}")
            }
        }
    }

    fun readProjectionLayout(context: Context): ProjectionLayout =
        ClusterProjectionPreferences(context).snapshot().layout

    /** Package to project — user-selectable in settings, defaults to Amap Auto. */
    private fun targetPackage(context: Context): String =
        ClusterProjectionPreferences(context).snapshot().targetPackage

    /** Persist the user's chosen trigger keycode (from the learn-button dialog). */
    fun setTriggerKeyCode(context: Context, keyCode: Int) {
        ClusterProjectionPreferences(context).setTriggerKeyCode(keyCode)
    }

    /** Remember the last VirtualDisplay id so a future process can release it if we die holding it. */
    private fun saveLastVdId(context: Context, id: Int) {
        ClusterProjectionPreferences(context).saveLastVirtualDisplayId(id)
    }

    private fun clearLastVdId(context: Context) {
        ClusterProjectionPreferences(context).clearLastVirtualDisplayId()
    }

    private fun persistDisplayHandle(context: Context, handle: DisplayHandle) {
        if (handle.owner == RemoteDisplayOwner.HELPER) saveLastVdId(context, handle.id)
        else clearLastVdId(context)
    }

    /**
     * Release a cluster display a prior process orphaned in the long-lived daemon. No-op when none
     * was recorded or the id is already gone (the daemon only releases displays in its own map).
     */
    private suspend fun releaseOrphanedDisplay(context: Context, helper: HelperClient) {
        val orphan = ClusterProjectionPreferences(context).snapshot().lastVirtualDisplayId
        if (orphan != ClusterProjectionPreferences.NO_VIRTUAL_DISPLAY) {
            WjxFileLogger.i(TAG, "releasing orphaned VirtualDisplay id=$orphan from a prior session")
            if (!helper.releaseVirtualDisplay(orphan)) {
                WjxFileLogger.w(TAG, "orphaned VirtualDisplay id=$orphan release failed; clearing stale handle")
            }
            clearLastVdId(context)
        }
    }

    /** Caller MUST hold [mutex]. Sets currentMode = mode only on full success, else OFF. */
    private suspend fun applyModeLocked(
        context: Context, mode: ClusterMode, helper: HelperClient, bootstrap: HelperBootstrap,
    ) {
        when (mode) {
            ClusterMode.OFF -> {
                if (projectedPackage != null) pullBackToMain(context, helper, focus = true)
                hideOverlay(context, helper)
                projectedPackage = null
                currentMode = ClusterMode.OFF
            }
            ClusterMode.FULLSCREEN -> {
                if (project(context, mode, helper, bootstrap)) {
                    lastProjectionFailureAtMs = 0L
                    currentMode = mode
                } else {
                    // projection failed: keep state honest. project() already tore down the
                    // overlay/VD on its failure paths; make sure the independent target is back
                    // on the main screen. Duplicate mode never moves the center-screen task.
                    WjxFileLogger.e(TAG, "projection failed; falling back to OFF")
                    if (readProjectionLayout(context) == ProjectionLayout.INDEPENDENT) {
                        pullBackToMain(context, helper, focus = true)
                    }
                    projectedPackage = null
                    lastProjectionFailureAtMs = SystemClock.elapsedRealtime()
                    currentMode = ClusterMode.OFF
                }
            }
        }
    }

    /** Returns true only when the overlay is up, the VirtualDisplay exists, and Navi is pinned. */
    private suspend fun project(
        context: Context, mode: ClusterMode, helper: HelperClient, bootstrap: HelperBootstrap,
    ): Boolean {
        if (!bootstrap.ensureCurrent()) {
            WjxFileLogger.e(TAG, "helper daemon not running; aborting projection"); return false
        }
        val layout = readProjectionLayout(context)
        if (overlayView != null) hideOverlay(context, helper)  // defensive: never stack overlays
        if (!ensureOverlayPermission(context, helper)) {
            WjxFileLogger.e(TAG, "overlay permission unavailable; aborting projection"); return false
        }
        val display = resolveClusterDisplay(context) ?: run {
            WjxFileLogger.e(TAG, "cluster display not found"); return false
        }
        val projectionPlan = buildProjectionSurfacePlan(context, layout, mode) ?: return false

        return try {
            val surface = withTimeoutOrNull(SURFACE_TIMEOUT_MS) {
                addOverlayAndAwaitSurface(context, display, projectionPlan, helper, bootstrap)
            }
            if (surface == null) {
                WjxFileLogger.e(TAG, "overlay Surface not ready within ${SURFACE_TIMEOUT_MS}ms")
                hideOverlay(context, helper); return false
            }
            // Release a stale VD (a prior release that failed) before overwriting the id. If it
            // fails AGAIN, abort rather than dropping the only handle — otherwise the daemon-side
            // VirtualDisplay leaks unrecoverably.
            if (remoteDisplayId != -1) {
                val staleId = remoteDisplayId
                val staleOwner = remoteDisplayOwner
                if (!releaseDisplay(staleId, staleOwner, helper)) {
                    WjxFileLogger.w(TAG, "stale releaseDisplay($staleId/$staleOwner) failed; clearing handle and continuing")
                    if (staleOwner == RemoteDisplayOwner.HELPER) clearLastVdId(context)
                }
                remoteDisplayId = -1
                remoteDisplayOwner = RemoteDisplayOwner.NONE
                if (staleOwner == RemoteDisplayOwner.HELPER) clearLastVdId(context)
            } else {
                // Cold start (fresh process): release any display this app orphaned in the daemon
                // before its last death, so cluster displays can't pile up across restarts. The
                // daemon only releases ids in its own map, so a reused/stale id is a safe no-op.
                releaseOrphanedDisplay(context, helper)
            }
            val displayHandle = createDisplayForLayout(context, helper, layout, projectionPlan.render, surface)
            if (displayHandle == null) {
                WjxFileLogger.e(TAG, "createVirtualDisplay failed"); hideOverlay(context, helper); return false
            }
            remoteDisplayId = displayHandle.id
            remoteDisplayOwner = displayHandle.owner
            persistDisplayHandle(context, displayHandle)
            if (layout == ProjectionLayout.INDEPENDENT) {
                val pkg = targetPackage(context)
                WjxFileLogger.i(TAG, "VirtualDisplay id=${displayHandle.id} ${projectionPlan.render.bufferWidth}x${projectionPlan.render.bufferHeight}@${projectionPlan.render.densityDpi}; launchAndForce $pkg")
                val ok = helper.launchAndForce(pkg, displayHandle.id, projectionPlan.render.bufferWidth, projectionPlan.render.bufferHeight)
                if (!ok) {
                    WjxFileLogger.e(TAG, "launchAndForce failed"); hideOverlay(context, helper); return false
                }
                projectedPackage = pkg
            } else {
                WjxFileLogger.i(TAG, "Duplicate VirtualDisplay id=${displayHandle.id} owner=${displayHandle.owner} ${projectionPlan.render.bufferWidth}x${projectionPlan.render.bufferHeight}@${projectionPlan.render.densityDpi} surface=${projectionPlan.surfaceWidth}x${projectionPlan.surfaceHeight}+${projectionPlan.surfaceX},${projectionPlan.surfaceY}")
                projectedPackage = null
            }
            true
        } catch (e: Exception) {
            // wm.addView (BadTokenException) or any reflective daemon call can throw — tear the
            // overlay down and report failure so applyModeLocked falls back to OFF + pull-back,
            // keeping currentMode honest.
            WjxFileLogger.e(TAG, "projection threw: ${e.message}", e)
            hideOverlay(context, helper); false
        }
    }

    /**
     * App-side display lookup. The cluster's projection surfaces are virtual displays owned by
     * com.byd.containerservice, named "*XDJAScreenProjection*" (1280x480). Validated on-car
     * 2026-06-02: the panel composites the "..._1" surface in Full mode, so we pick it by name;
     * if it is absent we take the first projection surface, else fall back to id 2. Name-based
     * selection survives containerservice reassigning display ids at boot. Updates cluster W/H/dpi.
     */
    private fun resolveClusterDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val projectionDisplays = dm.displays.filter {
            it.name.contains("XDJAScreenProjection", ignoreCase = true)
        }
        val match = projectionDisplays.firstOrNull { it.name.endsWith("_1") }
            ?: projectionDisplays.firstOrNull()
            ?: dm.getDisplay(DEFAULT_CLUSTER_DISPLAY_ID)
        if (match != null) {
            val point = Point()
            @Suppress("DEPRECATION") match.getRealSize(point)
            clusterWidth = point.x
            clusterHeight = point.y
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION") match.getMetrics(metrics)
            if (metrics.densityDpi > 0) clusterDensityDpi = metrics.densityDpi
            WjxFileLogger.i(TAG, "cluster display id=${match.displayId} ${clusterWidth}x$clusterHeight dpi=$clusterDensityDpi")
        }
        return match
    }

    /**
     * Independent projection renders the target app directly into the user-tuned cluster window.
     * Duplicate projection mirrors the centre screen, whose aspect ratio is usually different from
     * the instrument panel. For duplicate mode we therefore force a full-panel crop-fill surface:
     * no inherited mini-window size, no letterbox, and no stretching.
     */
    private fun buildProjectionSurfacePlan(
        context: Context,
        layout: ProjectionLayout,
        mode: ClusterMode = ClusterMode.FULLSCREEN,
    ): ProjectionSurfacePlan? {
        if (mode == ClusterMode.OFF) return null
        return if (layout == ProjectionLayout.DUPLICATE) {
            duplicateFullScreenPlan(context)
        } else {
            val preferences = ClusterProjectionPreferences(context).snapshot()
            val geo = geometryFor(
                mode,
                clusterWidth,
                clusterHeight,
                preferences.widthPct,
                preferences.heightPct,
                preferences.offsetXPct,
                preferences.offsetYPct,
            ) ?: return null
            val render = renderPlanFor(geo, clusterDensityDpi, preferences.scalePct)
            ProjectionSurfacePlan(
                geo = geo,
                render = render,
                surfaceWidth = geo.width,
                surfaceHeight = geo.height,
                surfaceX = geo.xOffset,
                surfaceY = geo.yOffset,
            )
        }
    }

    private fun duplicateFullScreenPlan(context: Context): ProjectionSurfacePlan {
        val geo = ClusterGeometry(clusterWidth, clusterHeight, 0, 0)
        val (sourceW, sourceH) = resolveCenterDisplaySize(context)
        val sourceAspect = if (sourceW > 0 && sourceH > 0) {
            sourceW.toFloat() / sourceH.toFloat()
        } else {
            clusterWidth.toFloat() / clusterHeight.toFloat()
        }
        val clusterAspect = clusterWidth.toFloat() / clusterHeight.toFloat()
        val surfaceWidth: Int
        val surfaceHeight: Int
        if (sourceAspect > clusterAspect) {
            surfaceHeight = clusterHeight
            surfaceWidth = (clusterHeight * sourceAspect).roundToInt().coerceAtLeast(clusterWidth)
        } else {
            surfaceWidth = clusterWidth
            surfaceHeight = (clusterWidth / sourceAspect).roundToInt().coerceAtLeast(clusterHeight)
        }
        val surfaceX = (clusterWidth - surfaceWidth) / 2
        val surfaceY = (clusterHeight - surfaceHeight) / 2
        val render = RenderPlan(surfaceWidth, surfaceHeight, clusterDensityDpi)
        WjxFileLogger.i(
            TAG,
            "duplicate fullscreen crop source=${sourceW}x$sourceH cluster=${clusterWidth}x$clusterHeight " +
                "buffer=${render.bufferWidth}x${render.bufferHeight} surface=${surfaceWidth}x$surfaceHeight+$surfaceX,$surfaceY",
        )
        return ProjectionSurfacePlan(geo, render, surfaceWidth, surfaceHeight, surfaceX, surfaceY)
    }

    private fun resolveCenterDisplaySize(context: Context): Pair<Int, Int> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return clusterWidth to clusterHeight
        val point = Point()
        @Suppress("DEPRECATION") display.getRealSize(point)
        return if (point.x > 0 && point.y > 0) point.x to point.y else clusterWidth to clusterHeight
    }

    /**
     * Builds the overlay container + SurfaceView on the cluster display (main thread) and
     * suspends until the Surface is created, returning the live Surface for the daemon to wrap.
     * Sets [overlayView] so a later hideOverlay() can tear it down even if we time out waiting.
     */
    private suspend fun addOverlayAndAwaitSurface(
        context: Context,
        display: Display,
        plan: ProjectionSurfacePlan,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
    ): Surface {
        val ready = CompletableDeferred<Surface>()
        withContext(Dispatchers.Main) {
            val displayContext = context.createDisplayContext(display)
            val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val container = FrameLayout(displayContext).apply {
                clipChildren = true
                clipToPadding = true
            }
            val surfaceView = SurfaceView(displayContext)
            // The overlay always covers the cluster panel. Independent mode places the SurfaceView in
            // the user-tuned window. Duplicate mode may deliberately make the SurfaceView larger than
            // the panel and center it, so the mirrored centre-screen image is crop-filled instead of
            // letterboxed.
            val surfaceParams = FrameLayout.LayoutParams(
                plan.surfaceWidth,
                plan.surfaceHeight,
                Gravity.TOP or Gravity.START,
            ).apply {
                leftMargin = plan.surfaceX
                topMargin = plan.surfaceY
            }
            container.addView(surfaceView, surfaceParams)

            surfaceView.holder.setFixedSize(plan.render.bufferWidth, plan.render.bufferHeight)
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    WjxFileLogger.d(
                        TAG,
                        "surfaceCreated buffer ${plan.render.bufferWidth}x${plan.render.bufferHeight} " +
                            "window ${plan.geo.width}x${plan.geo.height} surface ${plan.surfaceWidth}x${plan.surfaceHeight}+${plan.surfaceX},${plan.surfaceY}",
                    )
                    if (!ready.isCompleted) ready.complete(holder.surface)
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    WjxFileLogger.d(TAG, "surfaceDestroyed")
                    // Safety net: if the system tore the Surface down outside hideOverlay(), the
                    // VirtualDisplay would render into a dead Surface — release it (mirrors OpenBYD).
                    // Identity guard: during an OFF->FULLSCREEN re-projection this OLD overlay's
                    // callback must not release the NEW VirtualDisplay created for the next overlay.
                    scope.launch {
                        mutex.withLock {
                            val intentional = intentionalOverlayRemovals.remove(container)
                            if (overlayView === container) {
                                val shouldRecover = !intentional && shouldRecoverProjection(context)
                                releaseRemoteDisplayIfAlive(context, helper)
                                if (shouldRecover) {
                                    scheduleProjectionRecovery(context, helper, bootstrap, "surfaceDestroyed")
                                }
                            }
                        }
                    }
                }
            })

            val overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                OVERLAY_TYPE,
                OVERLAY_FLAGS,
                PixelFormat.TRANSLUCENT,  // -3
            )
            wm.addView(container, overlayParams)
            overlayView = container
        }
        return ready.await()
    }

    /** Move the projected app's task back to the main display and (optionally) refocus it. */
    private suspend fun pullBackToMain(context: Context, helper: HelperClient, focus: Boolean) {
        val pkg = projectedPackage ?: targetPackage(context)
        val taskId = helper.getTaskId(pkg) ?: run {
            WjxFileLogger.d(TAG, "pullBackToMain: projected task ($pkg) not found"); return
        }
        helper.moveTaskToDisplay(taskId, 0)
        helper.setTaskBounds(taskId, 0, 0, 0, 0)
        if (focus) helper.setFocusedTask(taskId)
    }

    /**
     * Release the VirtualDisplay and remove the overlay (main thread). [remoteDisplayId] is
     * cleared ONLY after a confirmed release so a failed release can be retried instead of
     * leaking the daemon-side VirtualDisplay.
     */
    private suspend fun hideOverlay(context: Context, helper: HelperClient) {
        val id = remoteDisplayId
        if (id != -1) {
            val owner = remoteDisplayOwner
            if (releaseDisplay(id, owner, helper)) {
                remoteDisplayId = -1
                remoteDisplayOwner = RemoteDisplayOwner.NONE
                if (owner == RemoteDisplayOwner.HELPER) clearLastVdId(context)
            } else {
                WjxFileLogger.w(TAG, "releaseDisplay($id/$owner) failed; keeping id for retry")
            }
        }
        withContext(Dispatchers.Main) {
            overlayView?.let { v ->
                intentionalOverlayRemovals.add(v)
                try {
                    val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(v)
                } catch (e: Exception) {
                    intentionalOverlayRemovals.remove(v)
                    WjxFileLogger.w(TAG, "removeView failed: ${e.message}")
                }
            }
            overlayView = null
        }
    }

    /** surfaceDestroyed safety net; caller holds [mutex]. No-op once the id is already cleared. */
    private suspend fun releaseRemoteDisplayIfAlive(context: Context, helper: HelperClient) {
        val id = remoteDisplayId
        if (id != -1) {
            val owner = remoteDisplayOwner
            WjxFileLogger.d(TAG, "surfaceDestroyed: releasing leaked VirtualDisplay $id/$owner")
            if (releaseDisplay(id, owner, helper)) {
                remoteDisplayId = -1
                remoteDisplayOwner = RemoteDisplayOwner.NONE
                if (owner == RemoteDisplayOwner.HELPER) clearLastVdId(context)
            }
        }
    }

    private fun shouldRecoverProjection(context: Context): Boolean {
        val enabled = ClusterProjectionPreferences(context).isEnabled()
        return enabled && currentMode == ClusterMode.FULLSCREEN
    }

    private fun cancelPendingRecovery() {
        recoveryToken++
    }

    private fun isFailureCoolingDown(): Boolean {
        val failedAt = lastProjectionFailureAtMs
        return failedAt > 0L && SystemClock.elapsedRealtime() - failedAt < PROJECTION_FAILURE_COOLDOWN_MS
    }

    private fun scheduleProjectionRecovery(
        context: Context,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
        reason: String,
    ) {
        val appContext = context.applicationContext
        val token = ++recoveryToken
        scope.launch {
            for ((attempt, delayMs) in RECOVERY_DELAYS_MS.withIndex()) {
                if (delayMs > 0) delay(delayMs)
                mutex.withLock {
                    if (token != recoveryToken) return@launch
                    if (!shouldRecoverProjection(appContext)) return@launch
                    if (overlayView != null && remoteDisplayId != -1) return@launch
                    WjxFileLogger.w(TAG, "recover projection after $reason, attempt=${attempt + 1}")
                    if (project(appContext, ClusterMode.FULLSCREEN, helper, bootstrap)) {
                        currentMode = ClusterMode.FULLSCREEN
                        WjxFileLogger.i(TAG, "projection recovered after $reason")
                        return@launch
                    }
                }
            }
            mutex.withLock {
                if (token == recoveryToken && shouldRecoverProjection(appContext)) {
                    WjxFileLogger.e(TAG, "projection recovery exhausted after $reason; falling back to OFF")
                    projectedPackage = null
                    currentMode = ClusterMode.OFF
                }
            }
        }
    }

    private suspend fun createDisplayForLayout(
        context: Context,
        helper: HelperClient,
        layout: ProjectionLayout,
        plan: RenderPlan,
        surface: Surface,
    ): DisplayHandle? = when (layout) {
        ProjectionLayout.INDEPENDENT -> createIndependentDisplay(helper, plan, surface)
        ProjectionLayout.DUPLICATE -> createDuplicateDisplay(context, helper, plan, surface)
    }

    private suspend fun createIndependentDisplay(
        helper: HelperClient,
        plan: RenderPlan,
        surface: Surface,
    ): DisplayHandle? {
        val id = helper.createVirtualDisplay(
            VD_NAME,
            plan.bufferWidth,
            plan.bufferHeight,
            plan.densityDpi,
            INDEPENDENT_VIRTUAL_DISPLAY_FLAGS,
            surface,
        )
        return id?.let { DisplayHandle(it, RemoteDisplayOwner.HELPER) }
    }

    private suspend fun createDuplicateDisplay(
        context: Context,
        helper: HelperClient,
        plan: RenderPlan,
        surface: Surface,
    ): DisplayHandle? {
        for (flag in DUPLICATE_HELPER_FLAGS) {
            val id = helper.createVirtualDisplay(
                VD_NAME,
                plan.bufferWidth,
                plan.bufferHeight,
                plan.densityDpi,
                flag,
                surface,
            )
            if (id != null) {
                WjxFileLogger.i(TAG, "duplicate display via helper flag=$flag id=$id")
                return DisplayHandle(id, RemoteDisplayOwner.HELPER)
            }
        }

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        for (flag in DUPLICATE_APP_FLAGS) {
            val display = runCatching {
                displayManager.createVirtualDisplay(
                    VD_NAME,
                    plan.bufferWidth,
                    plan.bufferHeight,
                    plan.densityDpi,
                    surface,
                    flag,
                )
            }.getOrNull()
            val id = display?.display?.displayId ?: -1
            if (id > 0 && display != null) {
                appMirrorDisplays[id] = display
                WjxFileLogger.i(TAG, "duplicate display via app DisplayManager flag=$flag id=$id")
                return DisplayHandle(id, RemoteDisplayOwner.APP)
            }
            display?.release()
        }

        if (ClusterMirrorService.ensureProjection(context)) {
            val id = ClusterMirrorService.createMirrorVirtualDisplay(
                VD_NAME,
                plan.bufferWidth,
                plan.bufferHeight,
                plan.densityDpi,
                surface,
            )
            if (id != null) {
                WjxFileLogger.i(TAG, "duplicate display via MediaProjection id=$id")
                return DisplayHandle(id, RemoteDisplayOwner.MEDIA_PROJECTION)
            }
        }
        return null
    }

    private suspend fun releaseDisplay(id: Int, owner: RemoteDisplayOwner, helper: HelperClient): Boolean {
        return when (owner) {
            RemoteDisplayOwner.NONE -> true
            RemoteDisplayOwner.HELPER -> helper.releaseVirtualDisplay(id)
            RemoteDisplayOwner.APP -> {
                appMirrorDisplays.remove(id)?.release()
                true
            }
            RemoteDisplayOwner.MEDIA_PROJECTION -> {
                ClusterMirrorService.releaseProjectionAndStop()
                true
            }
        }
    }

    private suspend fun ensureOverlayPermission(context: Context, helper: HelperClient): Boolean {
        // Grant SYSTEM_ALERT_WINDOW + PROJECT_MEDIA via the daemon once per process. We can't gate
        // on PROJECT_MEDIA (no app-side query), and SYSTEM_ALERT_WINDOW may already be granted, so
        // the daemon call must run unconditionally — not only when canDrawOverlays is false.
        if (!projectionPermissionsGranted) {
            WjxFileLogger.i(TAG, "granting overlay + project_media via daemon")
            if (helper.grantOverlayPermission()) projectionPermissionsGranted = true
        }
        if (Settings.canDrawOverlays(context)) return true
        WjxFileLogger.w(TAG, "SYSTEM_ALERT_WINDOW still missing; waiting for grant to apply")
        repeat(10) {
            delay(200)
            if (Settings.canDrawOverlays(context)) return true
        }
        return false
    }
}
