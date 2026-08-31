package com.byd.wjx.camera.blindspot

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.byd.wjx.cluster.ClusterContainerCoordinator
import com.byd.wjx.cluster.ClusterDisplayResolver
import com.byd.wjx.cluster.ClusterFrameUi7
import com.byd.wjx.cluster.ClusterOverlayEpoch
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.VehicleRawRead
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class BlindSpotController @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: BlindSpotPreferences,
    private val helper: HelperClient,
) {
    private val appContext = context.applicationContext
    private val camera = AvmCameraAdapter()
    private val telemetry = BlindSpotTelemetryGate()
    private val signalProfile = BlindSpotVehicleProfileResolver().resolve()
    private val clusterContainer = ClusterContainerCoordinator.get(appContext)
    private val clusterFrame = ClusterFrameUi7.get(appContext)
    private val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var running = false
    @Volatile private var factoryCameraActive = false
    private var fastLoop: Job? = null
    private var factoryCameraJob: Job? = null
    private var cameraThread: ExecutorCoroutineDispatcher? = null
    private val windows = linkedMapOf<BlindSpotSide, PreviewWindow>()
    private var shownSides = emptySet<BlindSpotSide>()
    private var shownAt = 0L
    private var lastRequestedSides = emptySet<BlindSpotSide>()
    private var cameraAttemptInFlight = false
    private var cameraOpen = false
    private var clusterLayerHeld = false
    private var discovered = false
    private var windowsAttachedAt = 0L
    private var coolingSince = 0L
    private var retryAt = 0L
    private var appliedPreviewIndexes: Pair<Int, Int>? = null
    private var appliedTargets: Pair<BlindSpotDisplayTarget, BlindSpotDisplayTarget>? = null
    private var tearingDown = false
    private var observedClusterOverlayEpoch = ClusterOverlayEpoch.current()
    private val pendingDetach = mutableListOf<PreviewWindow>()

    fun start(factoryCamera: StateFlow<Boolean>) {
        running = true
        factoryCameraJob?.cancel()
        factoryCameraActive = factoryCamera.value
        BlindSpotRuntimeState.publish(
            if (factoryCameraActive) {
                BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE
            } else {
                BlindSpotStatusCode.WAITING
            },
            signalProfile.id,
        )
        factoryCameraJob = ownScope.launch {
            factoryCamera.collect { active ->
                val changed = factoryCameraActive != active
                factoryCameraActive = active
                if (active) {
                    stopFastLoop("factory camera active")
                    BlindSpotRuntimeState.publish(BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE)
                } else if (changed && running) {
                    BlindSpotRuntimeState.publish(BlindSpotStatusCode.WAITING, signalProfile.id)
                }
            }
        }
        if (!signalProfile.verified) {
            WjxFileLogger.w(TAG, "unverified vehicle signal profile: ${signalProfile.id}")
        }
        WjxFileLogger.i(TAG, "runtime started")
    }

    fun stop() {
        running = false
        factoryCameraJob?.cancel()
        factoryCameraJob = null
        factoryCameraActive = false
        ownScope.launch {
            fastLoop?.cancel()
            fastLoop = null
            teardownNow("runtime stopped")
            retryPendingDetach()
            cameraThread?.close()
            cameraThread = null
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.DISABLED)
            WjxFileLogger.i(TAG, "runtime stopped")
        }
    }

    fun onPollSnapshot(data: DiParsData, accessAllowed: Boolean) {
        val snapshot = preferences.snapshot()
        if (!snapshot.enabled || !accessAllowed) {
            ownScope.launch {
                if (!running) return@launch
                stopFastLoop("feature disabled or locked")
                BlindSpotRuntimeState.publish(BlindSpotStatusCode.DISABLED)
            }
            return
        }
        if (factoryCameraActive) {
            ownScope.launch {
                if (!running) return@launch
                stopFastLoop("factory camera active")
                BlindSpotRuntimeState.publish(BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE)
            }
            return
        }
        val manualSide = BlindSpotRuntimeState.manualPreview.value
        val manualArmed = manualSide != BlindSpotSide.NONE &&
            data.gear in MANUAL_PREVIEW_GEARS &&
            (data.speed ?: Int.MAX_VALUE) <= MANUAL_PREVIEW_MAX_SPEED_KMH
        val armed = blindSpotArmed(
            snapshot.enabled && accessAllowed,
            data.gear,
            data.speed,
            snapshot.minSpeedKmh,
            snapshot.maxSpeedKmh,
        ) || snapshot.enabled && accessAllowed && manualArmed
        ownScope.launch {
            if (!running) return@launch
            if (armed) startFastLoop() else stopFastLoop("disarmed")
        }
    }

    private fun startFastLoop() {
        if (fastLoop?.isActive == true) return
        telemetry.reset(SystemClock.elapsedRealtime())
        fastLoop = ownScope.launch {
            WjxFileLogger.i(TAG, "fast telemetry loop started")
            while (isActive && running) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun stopFastLoop(reason: String) {
        fastLoop?.cancel()
        fastLoop = null
        if (anythingUp()) ownScope.launch { teardownNow(reason) }
    }

    private suspend fun tick() {
        retryPendingDetach()
        val prefs = preferences.snapshot()
        if (!prefs.enabled || !running) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.DISABLED)
            teardownNow("feature disabled")
            fastLoop?.cancel()
            fastLoop = null
            return
        }
        if (factoryCameraActive) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE)
            if (anythingUp()) teardownNow("factory camera active")
            return
        }

        val overlayEpoch = ClusterOverlayEpoch.current()
        if (
            windows.values.any(PreviewWindow::usesCluster) &&
            overlayEpoch != observedClusterOverlayEpoch
        ) {
            observedClusterOverlayEpoch = overlayEpoch
            teardownNow("instrument overlay z-order changed")
        } else {
            observedClusterOverlayEpoch = overlayEpoch
        }

        val reads = helper.readBatch(
            if (prefs.bsdGlow || prefs.bsdOnly) signalProfile.readsWithBsd else signalProfile.coreReads,
        )
        val now = SystemClock.elapsedRealtime()
        val sample = reads?.let {
            BlindSpotSample(
                blink = it.intAt(INDEX_BLINK),
                speedKmh = it.floatAt(INDEX_SPEED),
                gear = it.intAt(INDEX_GEAR),
                steeringAngleDeg = it.floatAt(INDEX_STEERING),
            )
        }
        val state = telemetry.onSample(sample, now)
        if (state.mustClose) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.TELEMETRY_UNAVAILABLE)
            if (anythingUp()) teardownNow("vehicle telemetry unavailable")
            return
        }

        val currentGear = sample?.gear
        if (currentGear == null) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.TELEMETRY_UNAVAILABLE, "gear")
            if (anythingUp()) teardownNow("gear unavailable")
            return
        }
        val gearIsReverse = currentGear == BLIND_SPOT_GEAR_REVERSE
        if (gearIsReverse) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.REVERSE_GUARD)
            if (anythingUp()) teardownNow("reverse gear")
            return
        }
        val decision = decideBlindSpot(
            BlindSpotInput(
                blink = sample?.blink,
                speedKmh = sample?.speedKmh,
                steeringAngleDeg = sample?.steeringAngleDeg,
                gearIsReverse = false,
                gearIsForward = currentGear == BLIND_SPOT_GEAR_DRIVE,
                minSpeedKmh = prefs.minSpeedKmh,
                maxSpeedKmh = prefs.maxSpeedKmh,
                telemetryAgeMs = state.ageMs,
                bsdOnly = prefs.bsdOnly,
                leftBsdActive = reads.bsdActiveAt(INDEX_BSD_LEFT),
                rightBsdActive = reads.bsdActiveAt(INDEX_BSD_RIGHT),
                sharpTurnEnabled = prefs.sharpTurnEnabled,
                sharpTurnAngleDeg = prefs.sharpTurnAngleDeg,
            ),
        )
        val manualSide = BlindSpotRuntimeState.manualPreview.value
        val manualAllowed = manualSide != BlindSpotSide.NONE &&
            currentGear in MANUAL_PREVIEW_GEARS &&
            (sample.speedKmh ?: Float.MAX_VALUE) <= MANUAL_PREVIEW_MAX_SPEED_KMH
        val requestedSides = if (manualAllowed) setOf(manualSide) else decision.show
        val cameraWarm = manualAllowed || decision.cameraWarm
        if (requestedSides != lastRequestedSides) {
            if (requestedSides.isNotEmpty()) retryAt = 0L
            lastRequestedSides = requestedSides
        }
        if (cameraWarm) {
            coolingSince = 0L
            ensureWarm(now, prefs)
        } else {
            if (coolingSince == 0L) coolingSince = now
            if (now - coolingSince >= COOL_DOWN_MS && anythingUp()) teardownNow("camera cooled down")
        }
        applyWindowSettings(prefs)
        applyShow(if (cameraOpen) requestedSides else emptySet())
        if (prefs.bsdGlow) {
            windows[BlindSpotSide.LEFT]?.setGlow(
                BlindSpotSide.LEFT in shownSides && reads.bsdActiveAt(INDEX_BSD_LEFT) == true,
            )
            windows[BlindSpotSide.RIGHT]?.setGlow(
                BlindSpotSide.RIGHT in shownSides && reads.bsdActiveAt(INDEX_BSD_RIGHT) == true,
            )
        }
    }

    private suspend fun ensureWarm(now: Long, prefs: BlindSpotPreferenceSnapshot) {
        if (now < retryAt) return
        val requestedIndexes = prefs.left.previewIndex to prefs.right.previewIndex
        val requestedTargets = prefs.left.target to prefs.right.target
        if (cameraOpen && appliedPreviewIndexes != requestedIndexes) {
            teardownNow("camera preview indexes changed")
            return
        }
        if (windows.isNotEmpty() && appliedTargets != requestedTargets) {
            teardownNow("display targets changed")
            return
        }
        if (windows.values.any(PreviewWindow::fellBackFromCluster) && resolveClusterDisplay() != null) {
            teardownNow("instrument display became available")
            return
        }
        if (windows.isEmpty()) {
            if (attachWindows(prefs)) {
                windowsAttachedAt = now
            } else {
                windowsAttachedAt = 0L
                retryAt = now + RETRY_DELAY_MS
            }
            return
        }
        if (!cameraOpen) {
            val surfaces = buildMap {
                windows.forEach { (side, window) ->
                    window.surface?.let { put(prefs.side(side).previewIndex, it) }
                }
            }
            if (surfaces.size < attachedWindowCount()) {
                if (now - windowsAttachedAt >= SURFACE_TIMEOUT_MS) failCamera("surface timeout", now)
                return
            }
            cameraAttemptInFlight = true
            val opened = try {
                withContext(cameraDispatcher()) {
                    if (!discovered) {
                        discovered = camera.discover()
                    }
                    camera.cameraId >= 0 && camera.openWarm(surfaces)
                }
            } finally {
                cameraAttemptInFlight = false
            }
            if (discovered) {
                windows.values.forEach { it.updateSourceSize(camera.previewWidth, camera.previewHeight) }
            }
            if (!opened) {
                failCamera("AVM open failed", now)
                return
            }
            if (factoryCameraActive || tearingDown || windows.isEmpty()) {
                withContext(cameraDispatcher()) { camera.close() }
                return
            }
            cameraOpen = true
            appliedPreviewIndexes = requestedIndexes
            WjxFileLogger.i(TAG, "camera warm indexes=${surfaces.keys.joinToString()}")
            return
        }

        val waitingForFirstFrame = shownSides.firstOrNull { windows[it]?.hasValidFrame == false }
        if (waitingForFirstFrame != null) {
            if (blindSpotFirstFrameTimedOut(true, false, shownAt, now, FIRST_FRAME_TIMEOUT_MS)) {
                failCamera("${waitingForFirstFrame.name.lowercase()} first frame timeout", now)
            }
            return
        }
        val stalledSide = shownSides.firstOrNull { side ->
            windows[side]?.let {
                blindSpotFrameStalled(true, it.hasValidFrame, maxOf(it.lastFrameAt, shownAt), now)
            } == true
        }
        if (stalledSide != null) failCamera("${stalledSide.name.lowercase()} camera frame frozen", now)
        if (shownSides.isEmpty()) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.READY, signalProfile.id)
        }
    }

    private suspend fun attachWindows(prefs: BlindSpotPreferenceSnapshot): Boolean {
        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.CAMERA_PERMISSION_REQUIRED)
            WjxFileLogger.w(TAG, "CAMERA permission missing")
            return false
        }
        val overlayReady = Settings.canDrawOverlays(appContext) || helper.grantOverlayPermission()
        if (!overlayReady) {
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.OVERLAY_PERMISSION_REQUIRED)
            WjxFileLogger.w(TAG, "overlay permission missing")
            return false
        }

        observedClusterOverlayEpoch = ClusterOverlayEpoch.current()
        val clusterDisplay = resolveClusterDisplay()
        val created = linkedMapOf<BlindSpotSide, PreviewWindow>()
        var attachFailed = false
        listOf(BlindSpotSide.LEFT, BlindSpotSide.RIGHT).forEach { side ->
            val config = prefs.side(side)
            val useCluster = config.target == BlindSpotDisplayTarget.CLUSTER && clusterDisplay != null
            val fellBackFromCluster = config.target == BlindSpotDisplayTarget.CLUSTER && !useCluster
            val displayContext = if (useCluster) {
                appContext.createDisplayContext(checkNotNull(clusterDisplay))
            } else {
                appContext
            }
            val display = if (useCluster) clusterDisplay else defaultDisplay()
            val metrics = realMetrics(display)
            val geometry = blindSpotDisplayGeometry(config, useCluster)
            val rect = BlindSpotPreferences.windowRect(metrics.widthPixels, metrics.heightPixels, geometry)
            val targetLabel = if (useCluster) "instrument" else "center"
            val window = PreviewWindow(
                "$targetLabel-${side.name.lowercase()}",
                side,
                useCluster,
                fellBackFromCluster,
            )
            window.updateCalibration(config.calibration)
            if (window.attach(displayContext, previewParams(rect), offscreenX(rect.width()))) {
                created[side] = window
                if (fellBackFromCluster) {
                    WjxFileLogger.w(TAG, "${side.name.lowercase()} cluster unavailable; using center")
                }
            } else {
                attachFailed = true
            }
        }
        if (attachFailed || created.size != REQUIRED_WINDOW_COUNT) {
            created.values.forEach(::releaseWindow)
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.CAMERA_UNAVAILABLE, "overlay pair")
            WjxFileLogger.w(TAG, "overlay pair attach failed count=${created.size}")
            return false
        }
        windows.putAll(created)
        appliedTargets = prefs.left.target to prefs.right.target
        return true
    }

    private fun applyWindowSettings(prefs: BlindSpotPreferenceSnapshot) {
        windows.forEach { (side, window) ->
            val config = prefs.side(side)
            val display = if (window.usesCluster) resolveClusterDisplay() else defaultDisplay()
            val metrics = realMetrics(display)
            val geometry = blindSpotDisplayGeometry(config, window.usesCluster)
            val rect = BlindSpotPreferences.windowRect(metrics.widthPixels, metrics.heightPixels, geometry)
            window.setGeometry(rect, offscreenX(rect.width()))
            window.updateCalibration(config.calibration)
        }
    }

    private fun previewParams(rect: Rect) = WindowManager.LayoutParams(
        rect.width(),
        rect.height(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        OVERLAY_FLAGS,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = rect.left
        y = rect.top
    }

    private fun resolveClusterDisplay(): Display? {
        return ClusterDisplayResolver.resolve(appContext)
    }

    private fun defaultDisplay(): Display? =
        (appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)

    private fun realMetrics(display: Display?): DisplayMetrics {
        if (display == null) return appContext.resources.displayMetrics
        @Suppress("DEPRECATION")
        return DisplayMetrics().also(display::getRealMetrics)
    }

    private suspend fun applyShow(sides: Set<BlindSpotSide>) {
        val requested = sides.filterTo(linkedSetOf()) { it != BlindSpotSide.NONE && windows.containsKey(it) }
        if (requested == shownSides) return
        var acquiredForShow = false
        val needsCluster = requested.any { windows[it]?.usesCluster == true }
        if (needsCluster && !clusterLayerHeld) {
            val powered = clusterContainer.setOwner(
                helper,
                ClusterContainerCoordinator.Owner.CAMERA,
                true,
            )
            if (!powered) {
                BlindSpotRuntimeState.publish(
                    BlindSpotStatusCode.CAMERA_UNAVAILABLE,
                    "cluster compositor",
                )
                return
            }
            clusterLayerHeld = true
            acquiredForShow = true
            clusterFrame.apply(helper, ClusterFrameUi7.Owner.CAMERA)
        }
        val previous = shownSides
        val results = windows.map { (side, window) -> window.setShown(side in requested) }
        if (results.any { !it }) {
            val rollbackSucceeded = windows.all { (side, window) -> window.setShown(side in previous) }
            if (!rollbackSucceeded) {
                windows.values.forEach(PreviewWindow::forceHide)
                shownSides = emptySet()
                releaseInstrumentLayer()
            } else if (acquiredForShow) {
                releaseInstrumentLayer()
            }
            BlindSpotRuntimeState.publish(BlindSpotStatusCode.CAMERA_UNAVAILABLE, "window visibility")
            WjxFileLogger.w(
                TAG,
                "window visibility update failed; rollback=${if (rollbackSucceeded) previous else "hidden"}",
            )
            return
        }
        shownSides = requested
        if (requested.isNotEmpty()) shownAt = SystemClock.elapsedRealtime()
        WjxFileLogger.i(TAG, "show $previous -> $requested")
        windows.forEach { (side, window) -> if (side !in requested) window.setGlow(false) }
        if (!needsCluster && clusterLayerHeld) releaseInstrumentLayer()
        when (requested) {
            setOf(BlindSpotSide.LEFT) -> BlindSpotRuntimeState.publish(BlindSpotStatusCode.SHOWING_LEFT)
            setOf(BlindSpotSide.RIGHT) -> BlindSpotRuntimeState.publish(BlindSpotStatusCode.SHOWING_RIGHT)
            setOf(BlindSpotSide.LEFT, BlindSpotSide.RIGHT) ->
                BlindSpotRuntimeState.publish(BlindSpotStatusCode.SHOWING_BOTH)
            else -> BlindSpotRuntimeState.publish(BlindSpotStatusCode.READY, signalProfile.id)
        }
    }

    private fun hideWindowsImmediately() {
        windows.values.forEach {
            it.forceHide()
            it.setGlow(false)
        }
        shownSides = emptySet()
    }

    private suspend fun releaseInstrumentLayer() {
        if (!clusterLayerHeld) return
        clusterFrame.restore(helper, ClusterFrameUi7.Owner.CAMERA)
        clusterContainer.setOwner(
            helper,
            ClusterContainerCoordinator.Owner.CAMERA,
            false,
        )
        clusterLayerHeld = false
    }

    private suspend fun failCamera(reason: String, now: Long) {
        val requestedSides = lastRequestedSides
        BlindSpotRuntimeState.publish(BlindSpotStatusCode.CAMERA_UNAVAILABLE, reason)
        WjxFileLogger.w(TAG, "$reason; retry in ${RETRY_DELAY_MS}ms")
        teardownNow(reason)
        lastRequestedSides = requestedSides
        retryAt = now + RETRY_DELAY_MS
    }

    private suspend fun teardownNow(reason: String) {
        if (!anythingUp() || tearingDown) return
        tearingDown = true
        WjxFileLogger.i(TAG, "teardown reason=$reason")
        try {
            // The factory reverse camera must own the screen before any blocking AVM close call.
            hideWindowsImmediately()
            releaseInstrumentLayer()
            if (cameraOpen || cameraAttemptInFlight) {
                withContext(cameraDispatcher()) { camera.close() }
                cameraOpen = false
            }
            windows.values.forEach(::releaseWindow)
            windows.clear()
        } finally {
            shownSides = emptySet()
            lastRequestedSides = emptySet()
            windowsAttachedAt = 0L
            appliedPreviewIndexes = null
            appliedTargets = null
            coolingSince = 0L
            tearingDown = false
        }
    }

    private fun releaseWindow(window: PreviewWindow?) {
        if (window != null && !window.detach()) pendingDetach += window
    }

    private fun retryPendingDetach() {
        pendingDetach.removeAll { it.detach() }
    }

    private fun attachedWindowCount(): Int = windows.size

    private fun anythingUp(): Boolean =
        windows.isNotEmpty() || cameraOpen || cameraAttemptInFlight || clusterLayerHeld

    private fun offscreenX(width: Int) = -(width + OFFSCREEN_MARGIN_PX)

    private fun cameraDispatcher(): ExecutorCoroutineDispatcher =
        cameraThread ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wjx-blind-spot-camera")
        }.asCoroutineDispatcher().also { cameraThread = it }

    private inner class PreviewWindow(
        private val label: String,
        private val side: BlindSpotSide,
        val usesCluster: Boolean,
        val fellBackFromCluster: Boolean,
    ) : TextureView.SurfaceTextureListener {
        private var windowManager: WindowManager? = null
        private var params: WindowManager.LayoutParams? = null
        private var container: View? = null
        private var textureView: TextureView? = null
        private var glow: View? = null
        private var glowAnimator: ValueAnimator? = null
        private var frames = 0
        private var shownX = 0
        private var hiddenX = 0
        private var shown = false
        private var sourceWidth = AvmCameraAdapter.DEFAULT_PREVIEW_WIDTH.toFloat()
        private var sourceHeight = AvmCameraAdapter.DEFAULT_PREVIEW_HEIGHT.toFloat()
        private var calibration = BlindSpotPreferences.defaultCalibration()
        private var gpuRenderer: BlindSpotGpuDewarpRenderer? = null
        private var rendererStartupEpoch = 0L
        private var rendererStartupInFlight = false

        @Volatile var lastFrameAt = 0L
            private set
        var surface: Surface? = null
            private set
        val hasValidFrame: Boolean get() = frames >= 2

        fun attach(
            context: Context,
            layoutParams: WindowManager.LayoutParams,
            offscreenX: Int,
        ): Boolean = try {
            val texture = TextureView(context).apply {
                surfaceTextureListener = this@PreviewWindow
                setOpaque(true)
            }
            val border = View(context).apply {
                background = GradientDrawable().apply {
                    setStroke((GLOW_STROKE_DP * context.resources.displayMetrics.density).roundToInt(), GLOW_COLOR)
                    setColor(Color.TRANSPARENT)
                }
                visibility = View.GONE
            }
            val frame = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                addView(texture, FrameLayout.LayoutParams(-1, -1))
                addView(border, FrameLayout.LayoutParams(-1, -1))
            }
            shownX = layoutParams.x
            hiddenX = offscreenX
            layoutParams.x = hiddenX
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(frame, layoutParams)
            windowManager = wm
            params = layoutParams
            container = frame
            textureView = texture
            glow = border
            WjxFileLogger.i(TAG, "$label window attached")
            true
        } catch (error: Throwable) {
            WjxFileLogger.w(TAG, "$label window add failed: ${error.message}", error)
            false
        }

        fun setShown(value: Boolean): Boolean {
            val view = container ?: return true
            val layoutParams = params ?: return true
            if (shown == value) return true
            if (value) view.visibility = View.VISIBLE
            val previousX = layoutParams.x
            layoutParams.x = if (value) shownX else hiddenX
            return runCatching { windowManager?.updateViewLayout(view, layoutParams) }
                .onSuccess {
                    shown = value
                    gpuRenderer?.setRenderingEnabled(value)
                }
                .onFailure {
                    layoutParams.x = previousX
                    if (value) view.visibility = View.INVISIBLE
                }
                .isSuccess
        }

        fun forceHide() {
            setShown(false)
            container?.visibility = View.INVISIBLE
            gpuRenderer?.setRenderingEnabled(false)
            shown = false
        }

        fun setGeometry(rect: Rect, offscreenX: Int): Boolean {
            val view = container ?: return true
            val layoutParams = params ?: return true
            val previous = Rect(layoutParams.x, layoutParams.y, layoutParams.width, layoutParams.height)
            val previousShownX = shownX
            val previousHiddenX = hiddenX
            shownX = rect.left
            hiddenX = offscreenX
            layoutParams.width = rect.width()
            layoutParams.height = rect.height()
            layoutParams.y = rect.top
            layoutParams.x = if (shown) shownX else hiddenX
            return runCatching { windowManager?.updateViewLayout(view, layoutParams) }
                .onFailure {
                    shownX = previousShownX
                    hiddenX = previousHiddenX
                    layoutParams.x = previous.left
                    layoutParams.y = previous.top
                    layoutParams.width = previous.right
                    layoutParams.height = previous.bottom
                }
                .isSuccess
        }

        fun setGlow(active: Boolean) {
            val border = glow ?: return
            if (active == (glowAnimator != null)) return
            if (!active) {
                glowAnimator?.cancel()
                glowAnimator = null
                border.visibility = View.GONE
                return
            }
            border.visibility = View.VISIBLE
            glowAnimator = ValueAnimator.ofFloat(1f, GLOW_MIN_ALPHA).apply {
                duration = GLOW_HALF_PERIOD_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { border.alpha = it.animatedValue as Float }
                start()
            }
        }

        fun updateSourceSize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            sourceWidth = width.toFloat()
            sourceHeight = height.toFloat()
            gpuRenderer?.updateInputSize(width, height)
            val view = textureView ?: return
            applyTransform(view.width, view.height)
        }

        fun updateCalibration(value: BlindSpotCalibration) {
            if (calibration == value) return
            calibration = value
            val view = textureView ?: return
            gpuRenderer?.update(value, view.width, view.height)
            applyTransform(view.width, view.height)
        }

        fun detach(): Boolean {
            setGlow(false)
            val view = container ?: return true
            val error = runCatching { windowManager?.removeView(view) }.exceptionOrNull()
            if (error != null && error !is IllegalArgumentException) {
                WjxFileLogger.w(TAG, "$label window remove failed: ${error.message}", error)
                return false
            }
            container = null
            val renderer = gpuRenderer
            gpuRenderer = null
            if (renderer != null) renderer.release() else surface?.release()
            surface = null
            textureView = null
            glow = null
            params = null
            windowManager = null
            frames = 0
            lastFrameAt = 0L
            shown = false
            return true
        }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            val startupEpoch = ++rendererStartupEpoch
            rendererStartupInFlight = true
            gpuRenderer = null
            surface = null
            frames = 0
            lastFrameAt = 0L
            ownScope.launch {
                val renderer = withContext(Dispatchers.IO) {
                    runCatching {
                        BlindSpotGpuDewarpRenderer.start(
                            outputTexture = texture,
                            outputWidth = width,
                            outputHeight = height,
                            calibration = calibration,
                            onFallback = { reason ->
                                WjxFileLogger.w(TAG, "$label GPU dewarp raw fallback: $reason")
                                ownScope.launch {
                                    if (startupEpoch != rendererStartupEpoch) return@launch
                                    val view = textureView ?: return@launch
                                    applyTransform(view.width, view.height)
                                }
                            },
                            onMappingApplied = {
                                ownScope.launch {
                                    if (startupEpoch != rendererStartupEpoch) return@launch
                                    val view = textureView ?: return@launch
                                    applyTransform(view.width, view.height)
                                }
                            },
                            onFatalError = { error ->
                                WjxFileLogger.w(TAG, "$label GPU dewarp failed: ${error.message}", error)
                                ownScope.launch {
                                    if (
                                        startupEpoch == rendererStartupEpoch &&
                                        !tearingDown &&
                                        running
                                    ) {
                                        failCamera("$label GPU dewarp failed", SystemClock.elapsedRealtime())
                                    }
                                }
                            },
                        )
                    }.onFailure { error ->
                        WjxFileLogger.w(TAG, "$label GPU startup failed: ${error.message}", error)
                    }.getOrNull()
                }
                if (startupEpoch == rendererStartupEpoch) rendererStartupInFlight = false
                val view = textureView
                val stillCurrent = startupEpoch == rendererStartupEpoch &&
                    container != null &&
                    view?.surfaceTexture === texture
                if (!stillCurrent) {
                    if (renderer != null) renderer.release() else runCatching { texture.release() }
                    return@launch
                }
                gpuRenderer = renderer
                renderer?.updateInputSize(sourceWidth.roundToInt(), sourceHeight.roundToInt())
                renderer?.update(calibration, view.width, view.height)
                renderer?.setRenderingEnabled(shown)
                surface = renderer?.cameraSurface ?: Surface(texture).also {
                    WjxFileLogger.w(TAG, "$label GPU unavailable; using raw TextureView")
                }
                applyTransform(view.width, view.height)
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            gpuRenderer?.update(calibration, width, height)
            applyTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            val startupOwnsTexture = rendererStartupInFlight
            rendererStartupEpoch++
            rendererStartupInFlight = false
            val renderer = gpuRenderer
            gpuRenderer = null
            if (renderer != null) renderer.release() else surface?.release()
            surface = null
            ownScope.launch {
                teardownNow("$label surface destroyed")
            }
            return renderer == null && !startupOwnsTexture
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            if (frames < 2) frames++
            lastFrameAt = SystemClock.elapsedRealtime()
        }

        private fun applyTransform(width: Int, height: Int) {
            val view = textureView ?: return
            val corrected = gpuRenderer?.isDewarpActive == true
            val values = blindSpotTransformValues(
                width,
                height,
                if (corrected) width.toFloat() else sourceWidth,
                if (corrected) height.toFloat() else sourceHeight,
                calibration,
                corrected,
            ) ?: return
            view.setTransform(Matrix().apply { setValues(values) })
        }
    }

    private companion object {
        const val TAG = "BlindSpot"
        const val INDEX_BLINK = 0
        const val INDEX_SPEED = 1
        const val INDEX_GEAR = 2
        const val INDEX_STEERING = 3
        const val INDEX_BSD_LEFT = 4
        const val INDEX_BSD_RIGHT = 5
        const val TICK_MS = 150L
        const val COOL_DOWN_MS = 10_000L
        const val SURFACE_TIMEOUT_MS = 8_000L
        const val FIRST_FRAME_TIMEOUT_MS = 3_000L
        const val RETRY_DELAY_MS = 3_000L
        const val GLOW_HALF_PERIOD_MS = 800L
        const val GLOW_MIN_ALPHA = 0.35f
        const val GLOW_STROKE_DP = 10f
        const val GLOW_COLOR = 0xFFFF7A00.toInt()
        const val OFFSCREEN_MARGIN_PX = 100
        const val REQUIRED_WINDOW_COUNT = 2
        val MANUAL_PREVIEW_GEARS = setOf(1, 3)
        const val MANUAL_PREVIEW_MAX_SPEED_KMH = 1
        const val OVERLAY_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        fun List<VehicleRawRead>.intAt(index: Int): Int? = getOrNull(index)
            ?.takeIf(VehicleRawRead::accepted)
            ?.value
            ?.let(SentinelDecoder::decodeInt)

        fun List<VehicleRawRead>.floatAt(index: Int): Float? = getOrNull(index)
            ?.takeIf(VehicleRawRead::accepted)
            ?.value
            ?.let(SentinelDecoder::parseFloatFromShellInt)

        fun List<VehicleRawRead>.bsdActiveAt(index: Int): Boolean? =
            blindSpotBsdActive(intAt(index))
    }
}
