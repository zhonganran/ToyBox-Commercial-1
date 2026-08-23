package com.byd.wjx.electronicmirror

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.core.content.ContextCompat
import com.byd.wjx.cluster.CENTER_OFFSET_PCT
import com.byd.wjx.cluster.ClusterMode
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.ClusterProjectionPreferences
import com.byd.wjx.cluster.MAX_PROJECTION_PCT
import com.byd.wjx.cluster.geometryFor
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.VehicleRawRead
import com.byd.wjx.data.vehicle.VehicleReadSpec
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turn signal driven electronic rearview mirror using BYD's hidden AVM camera stack.
 * Both camera streams stay warm: left turn is shown on the cluster and right turn is shown in
 * the movable center-screen PiP. Cars without a projection display use mirrored PiP windows.
 */
@Singleton
class ElectronicMirrorController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ElectronicMirrorPreferences,
    private val helper: HelperClient,
) {
    private val camera = AvmCameraClient()
    private val telemetry = ElectronicMirrorTelemetryGate()
    private val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var serviceScope: CoroutineScope? = null
    @Volatile private var fastLoop: Job? = null

    private var cameraThread: ExecutorCoroutineDispatcher? = null
    private var compositorJob: Job? = null
    private var leftWindow: PreviewWindow? = null
    private var rightWindow: PreviewWindow? = null
    private var clusterOnMainScreen = false
    private var appliedPipRect: Rect? = null
    private val pendingDetach = mutableListOf<PreviewWindow>()

    private var shownSide = ElectronicMirrorSide.NONE
    private var lastRequestedSide = ElectronicMirrorSide.NONE
    private var cameraOpen = false
    private var compositorPowered = false
    private var compositorTarget = false
    private var discovered = false
    private var overlayPermissionGranted = false
    private var windowsAttachedAt = 0L
    private var cameraOpenedAt = 0L
    private var coolingSince = 0L
    private var retryAt = 0L
    private var tearingDown = false

    fun start(scope: CoroutineScope) {
        serviceScope = scope
    }

    fun stop() {
        fastLoop?.cancel()
        fastLoop = null
        serviceScope = null
        ownScope.launch {
            teardownNow("service stop")
            retryPendingDetach()
            cameraThread?.close()
            cameraThread = null
            ownScope.coroutineContext[Job]?.cancelChildren()
        }
    }

    fun onVehicleData(data: DiParsData) {
        val armed = electronicMirrorArmed(
            enabled = preferences.enabled,
            gear = data.gear,
            speedKmh = data.speed,
            thresholdKmh = preferences.thresholdKmh,
        )
        if (armed) startFastLoop() else stopFastLoop()
    }

    private fun startFastLoop() {
        if (fastLoop?.isActive == true) return
        val scope = serviceScope ?: return
        telemetry.reset(SystemClock.elapsedRealtime())
        WjxFileLogger.i(TAG, "fast loop start")
        fastLoop = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun stopFastLoop() {
        val running = fastLoop ?: return
        fastLoop = null
        running.cancel()
        WjxFileLogger.i(TAG, "fast loop stop")
        ownScope.launch { teardownNow("disarmed") }
    }

    private fun cancelFastLoop() {
        val running = fastLoop ?: return
        fastLoop = null
        running.cancel()
    }

    private suspend fun tick() {
        retryPendingDetach()
        if (!preferences.enabled) {
            awaitTeardown("feature disabled")
            cancelFastLoop()
            return
        }

        val glowEnabled = preferences.bsdGlow
        val raw = helper.readBatch(if (glowEnabled) BATCH_WITH_BSD else BATCH_CORE)
        val now = SystemClock.elapsedRealtime()
        val sample = raw?.let {
            ElectronicMirrorSample(
                turnSignal = it.intAt(INDEX_TURN_SIGNAL),
                speedKmh = it.floatAt(INDEX_SPEED),
                gear = it.intAt(INDEX_GEAR),
            )
        }
        val state = telemetry.onSample(sample, now)
        if (state.mustClose) {
            if (anythingUp()) awaitTeardown("telemetry lost")
            return
        }

        val gearIsReverse = (sample?.gear ?: state.lastValid?.gear) == ELECTRONIC_MIRROR_GEAR_REVERSE
        val decision = decideElectronicMirror(
            ElectronicMirrorInput(
                turnSignal = sample?.turnSignal,
                speedKmh = sample?.speedKmh,
                gearIsReverse = gearIsReverse,
                thresholdKmh = preferences.thresholdKmh,
                telemetryAgeMs = state.ageMs,
            ),
        )
        if (gearIsReverse) {
            if (anythingUp()) awaitTeardown("reverse gear")
            return
        }

        if (decision.show != lastRequestedSide) {
            if (decision.show != ElectronicMirrorSide.NONE) retryAt = 0L
            lastRequestedSide = decision.show
        }

        if (decision.cameraWarm) {
            coolingSince = 0L
            ensureWarm(now)
        } else {
            if (coolingSince == 0L) coolingSince = now
            if (now - coolingSince >= COOL_DOWN_MS && anythingUp()) {
                awaitTeardown("camera cold")
            }
        }

        applyPipGeometry()
        applyShow(if (cameraOpen) decision.show else ElectronicMirrorSide.NONE)
        if (glowEnabled && raw != null) {
            leftWindow?.setGlow(
                shownSide == ElectronicMirrorSide.LEFT && raw.intAt(INDEX_BSD_LEFT) == BSD_OBJECT,
            )
            rightWindow?.setGlow(
                shownSide == ElectronicMirrorSide.RIGHT && raw.intAt(INDEX_BSD_RIGHT) == BSD_OBJECT,
            )
        }
    }

    private suspend fun ensureWarm(now: Long) {
        if (now < retryAt) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            failCamera("camera permission missing", now)
            return
        }
        if (!ensureOverlayAccess()) {
            failCamera("overlay permission missing", now)
            return
        }

        if (leftWindow == null && rightWindow == null) {
            attachWindows()
            windowsAttachedAt = now
            if (attachedWindowCount() != REQUIRED_PREVIEW_COUNT) {
                failCamera("preview windows incomplete", now)
            }
            return
        }

        if (!cameraOpen) {
            val surfaces = buildMap {
                leftWindow?.surface?.let { put(PREVIEW_INDEX_LEFT, it) }
                rightWindow?.surface?.let { put(PREVIEW_INDEX_RIGHT, it) }
            }
            if (surfaces.size < attachedWindowCount()) {
                if (now - windowsAttachedAt >= SURFACE_TIMEOUT_MS) failCamera("surface timeout", now)
                return
            }
            val opened = withContext(cameraDispatcher()) {
                if (!discovered) {
                    discovered = camera.discover()
                }
                discovered && camera.cameraId >= 0 && camera.openWarm(surfaces)
            }
            if (!opened) {
                failCamera("AVM open failed", now)
                return
            }
            if (leftWindow == null && rightWindow == null) {
                withContext(cameraDispatcher()) { camera.close() }
                return
            }
            cameraOpen = true
            cameraOpenedAt = now
            WjxFileLogger.i(TAG, "camera warm indexes=${surfaces.keys.joinToString()}")
            return
        }

        val gotAnyFrame = leftWindow?.hasValidFrame == true || rightWindow?.hasValidFrame == true
        if (!gotAnyFrame && now - cameraOpenedAt >= FIRST_FRAME_TIMEOUT_MS) {
            failCamera("first frame timeout", now)
        }
    }

    private suspend fun ensureOverlayAccess(): Boolean {
        if (!overlayPermissionGranted) {
            overlayPermissionGranted = runCatching { helper.grantOverlayPermission() }.getOrDefault(false)
        }
        if (Settings.canDrawOverlays(context)) return true
        repeat(5) {
            delay(150L)
            if (Settings.canDrawOverlays(context)) return true
        }
        return false
    }

    private suspend fun failCamera(reason: String, now: Long) {
        WjxFileLogger.w(TAG, "$reason; retry in ${RETRY_DELAY_MS}ms")
        awaitTeardown(reason)
        retryAt = now + RETRY_DELAY_MS
    }

    private fun attachWindows() {
        val display = clusterDisplay()
        if (display == null) {
            attachMirrorWindow()
        } else {
            clusterGeometry(display)?.let { rect ->
                val window = PreviewWindow("cluster")
                val displayContext = context.createDisplayContext(display)
                if (window.attach(displayContext, windowParams(rect), offscreenX(rect.width()))) {
                    leftWindow = window
                }
            }
        }

        val rect = pipRect()
        val pip = PreviewWindow("pip")
        if (pip.attach(context, windowParams(rect), offscreenX(rect.width()))) {
            rightWindow = pip
            appliedPipRect = rect
        }
    }

    private fun clusterGeometry(display: Display): Rect? {
        val metrics = realMetrics(display)
        val projection = ClusterProjectionPreferences(context).snapshot()
        val geometry = geometryFor(
            mode = ClusterMode.FULLSCREEN,
            clusterW = metrics.widthPixels,
            clusterH = metrics.heightPixels,
            widthPct = projection.widthPct.takeIf { it > 0 } ?: MAX_PROJECTION_PCT,
            heightPct = projection.heightPct.takeIf { it > 0 } ?: MAX_PROJECTION_PCT,
            offsetXPct = projection.offsetXPct.takeIf { it >= 0 } ?: CENTER_OFFSET_PCT,
            offsetYPct = projection.offsetYPct.takeIf { it >= 0 } ?: CENTER_OFFSET_PCT,
        ) ?: return null
        return Rect(
            geometry.xOffset,
            geometry.yOffset,
            geometry.xOffset + geometry.width,
            geometry.yOffset + geometry.height,
        )
    }

    private fun attachMirrorWindow() {
        val rect = mirrorRect(pipRect())
        val window = PreviewWindow("left-pip")
        if (window.attach(context, windowParams(rect), offscreenX(rect.width()))) {
            leftWindow = window
            clusterOnMainScreen = true
        }
    }

    private fun applyPipGeometry() {
        if (rightWindow == null && !clusterOnMainScreen) return
        val rect = pipRect()
        if (rect == appliedPipRect) return
        var applied = true
        rightWindow?.let { if (!it.setGeometry(rect, offscreenX(rect.width()))) applied = false }
        if (clusterOnMainScreen) {
            val mirror = mirrorRect(rect)
            leftWindow?.let {
                if (!it.setGeometry(mirror, offscreenX(mirror.width()))) applied = false
            }
        }
        if (applied) appliedPipRect = rect
    }

    private fun mirrorRect(rect: Rect): Rect {
        val displayWidth = realMetrics(defaultDisplay()).widthPixels
        val left = (displayWidth - rect.right).coerceAtLeast(0)
        return Rect(left, rect.top, left + rect.width(), rect.bottom)
    }

    private fun pipRect(): Rect {
        val metrics = realMetrics(defaultDisplay())
        val widthPct = preferences.pipWidthPct
        if (preferences.pipXPx == ElectronicMirrorPreferences.UNSET_PX ||
            preferences.pipYPx == ElectronicMirrorPreferences.UNSET_PX
        ) {
            return ElectronicMirrorPreferences.defaultPipRect(
                metrics.widthPixels,
                metrics.heightPixels,
                widthPct,
            )
        }
        val size = ElectronicMirrorPreferences.pipSize(metrics.widthPixels, widthPct)
        val left = preferences.pipXPx.coerceIn(
            0,
            (metrics.widthPixels - size.width).coerceAtLeast(0),
        )
        val top = preferences.pipYPx.coerceIn(0, (metrics.heightPixels - size.height).coerceAtLeast(0))
        return Rect(left, top, left + size.width, top + size.height)
    }

    private fun windowParams(rect: Rect): WindowManager.LayoutParams = WindowManager.LayoutParams(
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

    private fun clusterDisplay(): Display? {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = manager.displays.filter {
            it.name.contains(CLUSTER_DISPLAY_NAME, ignoreCase = true)
        }
        return displays.firstOrNull { it.name.endsWith("_1") } ?: displays.firstOrNull()
    }

    private fun defaultDisplay(): Display? =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)

    private fun realMetrics(display: Display?): DisplayMetrics {
        if (display == null) return context.resources.displayMetrics
        @Suppress("DEPRECATION")
        return DisplayMetrics().also { display.getRealMetrics(it) }
    }

    private fun applyShow(side: ElectronicMirrorSide) {
        if (side == shownSide) return
        val clusterOk = leftWindow?.setShown(side == ElectronicMirrorSide.LEFT) ?: true
        val pipOk = rightWindow?.setShown(side == ElectronicMirrorSide.RIGHT) ?: true
        if (!clusterOk || !pipOk) {
            WjxFileLogger.w(TAG, "show $shownSide -> $side failed; retrying")
            return
        }

        val previous = shownSide
        shownSide = side
        WjxFileLogger.i(TAG, "show $previous -> $side")
        if (side == ElectronicMirrorSide.LEFT) {
            requestCompositor(true)
        } else {
            leftWindow?.setGlow(false)
            if (previous == ElectronicMirrorSide.LEFT) requestCompositor(false)
        }
        if (side != ElectronicMirrorSide.RIGHT) rightWindow?.setGlow(false)
    }

    private fun requestCompositor(on: Boolean) {
        if (on == compositorTarget) return
        if (on && (leftWindow == null || clusterOnMainScreen)) return
        compositorTarget = on
        compositorJob?.cancel()
        compositorJob = ownScope.launch { applyCompositor(on) }
    }

    private suspend fun applyCompositor(on: Boolean) {
        if (!on && ClusterProjectionManager.currentMode == ClusterMode.FULLSCREEN) {
            compositorPowered = false
            compositorTarget = false
            WjxFileLogger.i(TAG, "compositor kept on for active cluster projection")
            return
        }
        val ok = runCatching { helper.setClusterContainerMode(on) }.getOrDefault(false)
        if (ok) compositorPowered = on else compositorTarget = compositorPowered
        WjxFileLogger.i(TAG, "compositor ${if (on) "on" else "off"} ok=$ok")
    }

    private suspend fun awaitTeardown(reason: String) {
        ownScope.launch { teardownNow(reason) }.join()
    }

    private suspend fun teardownNow(reason: String) {
        if (!anythingUp() || tearingDown) return
        tearingDown = true
        WjxFileLogger.i(TAG, "teardown reason=$reason")
        try {
            if (cameraOpen) {
                withContext(cameraDispatcher()) { camera.close() }
                cameraOpen = false
            }
            applyShow(ElectronicMirrorSide.NONE)
            val compositorMayBeOn = compositorPowered || compositorTarget
            compositorJob?.cancel()
            compositorJob = null
            compositorTarget = false
            if (compositorMayBeOn) applyCompositor(false)
            releaseWindow(leftWindow)
            leftWindow = null
            clusterOnMainScreen = false
            releaseWindow(rightWindow)
            rightWindow = null
            appliedPipRect = null
        } finally {
            shownSide = ElectronicMirrorSide.NONE
            lastRequestedSide = ElectronicMirrorSide.NONE
            windowsAttachedAt = 0L
            cameraOpenedAt = 0L
            coolingSince = 0L
            tearingDown = false
        }
    }

    private fun releaseWindow(window: PreviewWindow?) {
        if (window != null && !window.detach()) pendingDetach += window
    }

    private fun retryPendingDetach() {
        pendingDetach.removeAll { it.detach() }
        if (pendingDetach.isNotEmpty()) {
            WjxFileLogger.w(TAG, "${pendingDetach.size} preview window(s) still attached")
        }
    }

    private fun attachedWindowCount(): Int =
        (if (leftWindow != null) 1 else 0) + (if (rightWindow != null) 1 else 0)

    private fun anythingUp(): Boolean =
        leftWindow != null || rightWindow != null || cameraOpen || compositorPowered || compositorTarget

    private fun offscreenX(width: Int): Int = -(width + OFFSCREEN_MARGIN_PX)

    private fun cameraDispatcher(): ExecutorCoroutineDispatcher = cameraThread
        ?: Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "electronic-mirror-camera") }
            .asCoroutineDispatcher()
            .also { cameraThread = it }

    private inner class PreviewWindow(
        private val label: String,
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

        var surface: Surface? = null
            private set

        val hasValidFrame: Boolean get() = frames >= 2

        fun attach(
            windowContext: Context,
            layoutParams: WindowManager.LayoutParams,
            offscreenX: Int,
        ): Boolean = try {
            val texture = TextureView(windowContext).apply {
                surfaceTextureListener = this@PreviewWindow
                setOpaque(true)
            }
            val border = View(windowContext).apply {
                background = GradientDrawable().apply {
                    setStroke(
                        (GLOW_STROKE_DP * windowContext.resources.displayMetrics.density).roundToInt(),
                        GLOW_COLOR,
                    )
                    setColor(Color.TRANSPARENT)
                }
                visibility = View.GONE
            }
            val frame = FrameLayout(windowContext).apply {
                setBackgroundColor(Color.BLACK)
                addView(texture, FrameLayout.LayoutParams(-1, -1))
                addView(border, FrameLayout.LayoutParams(-1, -1))
            }
            shownX = layoutParams.x
            hiddenX = offscreenX
            layoutParams.x = hiddenX
            val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(frame, layoutParams)
            windowManager = wm
            params = layoutParams
            container = frame
            textureView = texture
            glow = border
            true
        } catch (error: Throwable) {
            WjxFileLogger.w(TAG, "$label attach failed: ${error.javaClass.simpleName}:${error.message}")
            false
        }

        fun setShown(value: Boolean): Boolean {
            val view = container ?: return true
            val layout = params ?: return true
            if (shown == value) return true
            val previousX = layout.x
            layout.x = if (value) shownX else hiddenX
            return runCatching { windowManager?.updateViewLayout(view, layout) }
                .onSuccess { shown = value }
                .onFailure { layout.x = previousX }
                .isSuccess
        }

        fun setGeometry(rect: Rect, offscreenX: Int): Boolean {
            val view = container ?: return true
            val layout = params ?: return true
            val old = intArrayOf(shownX, hiddenX, layout.width, layout.height, layout.x, layout.y)
            shownX = rect.left
            hiddenX = offscreenX
            layout.width = rect.width()
            layout.height = rect.height()
            layout.y = rect.top
            layout.x = if (shown) shownX else hiddenX
            return runCatching { windowManager?.updateViewLayout(view, layout) }
                .onFailure {
                    shownX = old[0]
                    hiddenX = old[1]
                    layout.width = old[2]
                    layout.height = old[3]
                    layout.x = old[4]
                    layout.y = old[5]
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

        fun detach(): Boolean {
            setGlow(false)
            val view = container ?: return true
            val error = runCatching { windowManager?.removeView(view) }.exceptionOrNull()
            if (error != null && error !is IllegalArgumentException) return false
            container = null
            surface?.release()
            surface = null
            textureView = null
            glow = null
            params = null
            windowManager = null
            frames = 0
            shown = false
            return true
        }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            surface?.release()
            surface = Surface(texture)
            frames = 0
            applyCrop(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            applyCrop(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            ownScope.launch {
                teardownNow("$label surface destroyed")
                runCatching { texture.release() }
            }
            return false
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            if (frames < 2) frames++
        }

        private fun applyCrop(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            val view = textureView ?: return
            val aspect = width.toFloat() / height
            val cropWidth = min(SOURCE_WIDTH, SOURCE_HEIGHT * aspect)
            val cropHeight = min(SOURCE_HEIGHT, SOURCE_WIDTH / aspect)
            val left = (SOURCE_WIDTH - cropWidth) / 2f
            val top = (SOURCE_HEIGHT - cropHeight) / 2f
            val source = RectF(
                left / SOURCE_WIDTH * width,
                top / SOURCE_HEIGHT * height,
                (left + cropWidth) / SOURCE_WIDTH * width,
                (top + cropHeight) / SOURCE_HEIGHT * height,
            )
            val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
            view.setTransform(Matrix().apply {
                setRectToRect(source, destination, Matrix.ScaleToFit.FILL)
            })
        }
    }

    private companion object {
        const val TAG = "ElectronicMirror"
        const val TX_INT = 5
        const val TX_FLOAT = 7
        val BATCH_CORE = listOf(
            VehicleReadSpec(TX_INT, 1004, 950009900),
            VehicleReadSpec(TX_FLOAT, 1013, -1807745016),
            VehicleReadSpec(TX_INT, 1011, 555745336),
        )
        val BATCH_WITH_BSD = BATCH_CORE + listOf(
            VehicleReadSpec(TX_INT, 1038, 1098907664),
            VehicleReadSpec(TX_INT, 1038, 1098907666),
        )
        const val INDEX_TURN_SIGNAL = 0
        const val INDEX_SPEED = 1
        const val INDEX_GEAR = 2
        const val INDEX_BSD_LEFT = 3
        const val INDEX_BSD_RIGHT = 4
        const val BSD_OBJECT = 2
        const val PREVIEW_INDEX_LEFT = 2
        const val PREVIEW_INDEX_RIGHT = 3
        const val REQUIRED_PREVIEW_COUNT = 2
        const val TICK_MS = 150L
        const val COOL_DOWN_MS = 10_000L
        const val SURFACE_TIMEOUT_MS = 8_000L
        const val FIRST_FRAME_TIMEOUT_MS = 3_000L
        const val RETRY_DELAY_MS = 3_000L
        const val GLOW_HALF_PERIOD_MS = 800L
        const val GLOW_MIN_ALPHA = 0.35f
        const val GLOW_STROKE_DP = 12f
        const val GLOW_COLOR = 0xFFFF7A00.toInt()
        const val OFFSCREEN_MARGIN_PX = 100
        const val CLUSTER_DISPLAY_NAME = "XDJAScreenProjection"
        const val OVERLAY_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        const val SOURCE_WIDTH = 1920f
        const val SOURCE_HEIGHT = 1300f

        fun List<VehicleRawRead>.intAt(index: Int): Int? = getOrNull(index)
            ?.takeIf { it.accepted }
            ?.value
            ?.let(SentinelDecoder::decodeInt)

        fun List<VehicleRawRead>.floatAt(index: Int): Float? = getOrNull(index)
            ?.takeIf { it.accepted }
            ?.value
            ?.let(SentinelDecoder::parseFloatFromShellInt)
    }
}
