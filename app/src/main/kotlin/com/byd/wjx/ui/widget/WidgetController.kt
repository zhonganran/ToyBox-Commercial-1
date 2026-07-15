package com.byd.wjx.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.byd.wjx.MainActivity
import com.byd.wjx.data.automation.AutomationButtonDispatcher
import com.byd.wjx.data.local.LocalePreferences
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.di.WidgetDependenciesEntryPoint
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.overlay.OverlayLifecycleOwner
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import com.byd.wjx.R
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import com.byd.wjx.domain.calculator.ConsumptionState
import com.byd.wjx.domain.calculator.Trend
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Singleton controller that owns the floating-widget + trash-zone overlay
 * lifecycle. Called from TrackingService when the preference + permission
 * allow, and from ActivityLifecycleCallbacks to show/hide across fore/back.
 */
// All overlay views are created from applicationContext and cleared in detach().
@SuppressLint("StaticFieldLeak")
object WidgetController {

    private const val TAG = "WidgetController"

    // Widget dimensions in dp — matches FloatingWidgetView layout
    private const val WIDGET_WIDTH_DP = 260
    private const val WIDGET_HEIGHT_DP = 108
    private const val BUTTON_DP = WidgetButtonLayout.BUTTON_DP
    private const val GAP_DP = WidgetButtonLayout.GAP_DP
    private const val DRAG_THRESHOLD_DP = 8
    private const val TRASH_RADIUS_DP = 48
    private const val LONG_PRESS_MS = 1500L
    private const val DOUBLE_TAP_MS = 260L
    // Tap on the left third of the widget launches the user-configured app
    // (default Amap Auto) when zoning is enabled in WidgetPreferences;
    // the right two thirds always open SuperToyBox. Threshold uses the live view
    // width, so it stays proportional across the 70-200% widget size range.
    private const val LEFT_TAP_FRACTION = 1f / 3f

    @Volatile private var appForegrounded: Boolean = false
    @Volatile private var previewMode: Boolean = false

    private var wm: WindowManager? = null
    private var widgetView: ComposeView? = null
    private var widgetLifecycle: OverlayLifecycleOwner? = null
    private var widgetParams: WindowManager.LayoutParams? = null
    private var buttonLayerView: ComposeView? = null
    private var buttonLifecycle: OverlayLifecycleOwner? = null
    private var rootContainer: android.widget.FrameLayout? = null

    // Current widget bounds in px after scale; touch-listener reads these
    // to clamp drag positions correctly when the user resized the widget.
    @Volatile private var currentWidgetWpx: Int = 0
    @Volatile private var currentWidgetHpx: Int = 0

    private var trashView: ComposeView? = null
    private var trashLifecycle: OverlayLifecycleOwner? = null

    private var dataScope: CoroutineScope? = null
    private var dataJob: Job? = null
    private var vehicleStateRepository: VehicleStateRepository? = null
    private var trackingStateRepository: TrackingStateRepository? = null
    private var automationButtonDispatcher: AutomationButtonDispatcher? = null
    private var consumptionAggregator: ConsumptionAggregator? = null
    private lateinit var prefsAlphaFlow: kotlinx.coroutines.flow.Flow<Float>
    private lateinit var prefsScaleFlow: kotlinx.coroutines.flow.Flow<Float>

    // Compose state for the widget data
    private var socState = mutableStateOf<Int?>(null)
    private var rangeState = mutableStateOf<Double?>(null)
    private var consumptionState = mutableStateOf<Double?>(null)
    private var trendState = mutableStateOf(Trend.NONE)
    private var sessionStartedAtState = mutableStateOf<Long?>(null)
    private var tripDistanceKmState = mutableStateOf<Double?>(null)
    private var insideTempState = mutableStateOf<Int?>(null)
    private var batTempState = mutableStateOf<Int?>(null)
    private var voltsState = mutableStateOf<Double?>(null)
    private var alphaState = mutableFloatStateOf(1.0f)
    private var scaleState = mutableFloatStateOf(1.0f)
    private var trashActive = mutableStateOf(false)
    private var expandedState = mutableStateOf(false)
    @Volatile private var collapsedX: Int = 0
    @Volatile private var collapsedY: Int = 0

    @Synchronized
    fun attach(context: Context) {
        if (appForegrounded && !previewMode) return  // race-guard, but preview wins
        if (widgetView != null) return               // already attached

        val appCtx = context.applicationContext
        val dependencies = EntryPointAccessors.fromApplication(
            appCtx,
            WidgetDependenciesEntryPoint::class.java,
        )
        vehicleStateRepository = dependencies.vehicleStateRepository()
        trackingStateRepository = dependencies.trackingStateRepository()
        automationButtonDispatcher = dependencies.automationButtonDispatcher()
        consumptionAggregator = dependencies.consumptionAggregator()
        val prefs = WidgetPreferences(appCtx)
        // long-press hid widget until user opens MainActivity (preview wins)
        if (prefs.isHiddenUntilAppLaunch() && !previewMode) return

        // AppCompatDelegate.setApplicationLocales does not always refresh the
        // applicationContext's Configuration, so the overlay's ComposeView ends
        // up resolving stringResource against a stale locale. Wrap appCtx with
        // an explicit locale context so the widget always matches the user's
        // chosen language.
        val viewCtx = localizedContext(appCtx)

        val windowManager = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager

        prefsAlphaFlow = prefs.alphaFlow()
        prefsScaleFlow = prefs.scaleFlow()
        val metrics = viewCtx.resources.displayMetrics

        val initialScale = prefs.getScale()
        scaleState.floatValue = initialScale
        val widgetWpx = (dp(viewCtx, WIDGET_WIDTH_DP) * initialScale).toInt()
        val widgetHpx = (dp(viewCtx, WIDGET_HEIGHT_DP) * initialScale).toInt()
        currentWidgetWpx = widgetWpx
        currentWidgetHpx = widgetHpx
        val (startX, startY) = resolveStartPosition(prefs, metrics, widgetWpx, widgetHpx)

        val params = WindowManager.LayoutParams(
            widgetWpx,
            widgetHpx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        widgetParams = params

        val lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
        widgetLifecycle = lifecycleOwner
        collapsedX = startX
        collapsedY = startY
        expandedState.value = false

        val panelCompose = ComposeView(viewCtx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FloatingWidgetView(
                    soc = socState.value,
                    rangeKm = rangeState.value,
                    consumption = consumptionState.value,
                    trend = trendState.value,
                    sessionStartedAt = sessionStartedAtState.value,
                    tripDistanceKm = tripDistanceKmState.value,
                    insideTemp = insideTempState.value,
                    batTemp = batTempState.value,
                    voltage12v = voltsState.value,
                    alpha = alphaState.floatValue,
                    scaleFactor = scaleState.floatValue,
                )
            }
            setOnTouchListener(WidgetTouchListener(viewCtx, prefs, metrics))
        }
        widgetView = panelCompose

        val buttonLifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
        buttonLifecycle = buttonLifecycleOwner
        val buttonCompose = ComposeView(viewCtx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(buttonLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(buttonLifecycleOwner)
            setContent {
                WidgetButtonPanel(
                    expanded = expandedState.value,
                    scaleFactor = scaleState.floatValue,
                    onButtonClick = { number -> onWidgetButtonClick(viewCtx, number) },
                )
            }
        }
        buttonLayerView = buttonCompose

        val root = android.widget.FrameLayout(viewCtx)
        root.addView(
            buttonCompose,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            panelCompose,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ),
        )
        root.setViewTreeLifecycleOwner(lifecycleOwner)
        root.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        rootContainer = root

        try {
            windowManager.addView(root, params)
            WjxFileLogger.i(TAG, "widget attached size=${widgetWpx}x${widgetHpx} pos=$startX,$startY preview=$previewMode")
        } catch (e: Exception) {
            Log.e(TAG, "addView failed: ${e.message}")
            WjxFileLogger.e(TAG, "addView failed: ${e.message}", e)
            detach()
            return
        }

        startDataSubscription()
    }

    @Synchronized
    fun setAppForegrounded(foreground: Boolean) {
        appForegrounded = foreground
        if (foreground && !previewMode) detach()
    }

    /**
     * Settings UI calls this while user drags the alpha or size slider so the
     * widget pops over Settings, letting the user see the change live. Set to
     * false on screen leave (DisposableEffect.onDispose) to restore normal
     * foreground-detach behavior.
     */
    @Synchronized
    fun setPreviewMode(context: Context, active: Boolean) {
        previewMode = active
        val appCtx = context.applicationContext
        val prefs = WidgetPreferences(appCtx)
        if (active) {
            // Skip preview-attach silently if overlay permission was revoked —
            // attach() would throw on addView. User toggles widget back on
            // through the main switch which handles the permission flow.
            if (prefs.isEnabled() &&
                widgetView == null &&
                android.provider.Settings.canDrawOverlays(appCtx)
            ) attach(appCtx)
        } else if (appForegrounded) {
            detach()
        }
    }

    @Synchronized
    fun detach() {
        dataJob?.cancel()
        dataJob = null
        dataScope?.cancel()
        dataScope = null

        hideTrashZone()

        widgetView?.setOnTouchListener(null)
        rootContainer?.let { v ->
            try {
                wm?.removeView(v)
                WjxFileLogger.i(TAG, "widget detached")
            } catch (e: Exception) {
                Log.w(TAG, "removeView widget: ${e.message}")
                WjxFileLogger.w(TAG, "removeView widget: ${e.message}")
            }
        }
        widgetLifecycle?.onDestroy()
        buttonLifecycle?.onDestroy()
        widgetView = null
        buttonLayerView = null
        rootContainer = null
        widgetLifecycle = null
        buttonLifecycle = null
        widgetParams = null
        expandedState.value = false
        wm = null
    }

    private fun startDataSubscription() {
        val vehicleRepository = vehicleStateRepository ?: run {
            WjxFileLogger.e(TAG, "vehicle state repository unavailable; widget data subscription skipped")
            return
        }
        val trackingRepository = trackingStateRepository ?: run {
            WjxFileLogger.e(TAG, "tracking state repository unavailable; widget data subscription skipped")
            return
        }
        val consumption = consumptionAggregator ?: run {
            WjxFileLogger.e(TAG, "consumption aggregator unavailable; widget data subscription skipped")
            return
        }
        val scope = CoroutineScope(Dispatchers.Main)
        dataScope = scope
        val uiFlow = combine(
            consumption.state,
            prefsAlphaFlow,
            prefsScaleFlow,
        ) { c, a, s -> UiBundle(c, a, s) }
        dataJob = scope.launch {
            combine(
                vehicleRepository.state,
                trackingRepository.state,
                uiFlow,
            ) { vehicleState, trackingState, bundled ->
                WidgetSnapshot(
                    data = vehicleState.data,
                    range = trackingState.rangeKm,
                    sessionStartedAt = trackingState.sessionStartedAt,
                    tripDistanceKm = trackingState.tripDistanceKm,
                    consumption = bundled.consumption,
                    alpha = bundled.alpha,
                    scale = bundled.scale,
                    cameraActive = trackingState.cameraActive,
                )
            }.collect { snap ->
                socState.value = snap.data?.soc
                rangeState.value = snap.range
                insideTempState.value = snap.data?.insideTemp
                batTempState.value = snap.data?.avgBatTemp
                voltsState.value = snap.data?.voltage12v
                sessionStartedAtState.value = snap.sessionStartedAt
                tripDistanceKmState.value = snap.tripDistanceKm
                consumptionState.value = snap.consumption.displayValue
                trendState.value = snap.consumption.trend
                alphaState.floatValue = snap.alpha

                if (scaleState.floatValue != snap.scale) {
                    scaleState.floatValue = snap.scale
                    applyScaleChange(snap.scale)
                }

                // Hide widget while the BYD camera surface is up (rear, front
                // auto-pop, 360°, parking app — all map to com.byd.avc).
                val hideForCamera = snap.cameraActive
                rootContainer?.visibility = if (hideForCamera) View.GONE else View.VISIBLE
                if (hideForCamera) hideTrashZone()
            }
        }
    }

    @Synchronized
    private fun applyScaleChange(scale: Float) {
        val view = widgetView ?: return
        val params = widgetParams ?: return
        val windowManager = wm ?: return
        val ctx = view.context ?: return
        val metrics = ctx.resources.displayMetrics
        val widgetWpx = (dp(ctx, WIDGET_WIDTH_DP) * scale).toInt()
        val widgetHpx = (dp(ctx, WIDGET_HEIGHT_DP) * scale).toInt()
        currentWidgetWpx = widgetWpx
        currentWidgetHpx = widgetHpx
        // Force-fit the explicit pixel size so WindowManager respects the new
        // bounds (WRAP_CONTENT alone doesn't re-measure on density override).
        params.width = widgetWpx
        params.height = widgetHpx
        // Re-clamp current position so an enlarged widget doesn't end up off-screen.
        val (clampedX, clampedY) = DragGestureLogic.clampToScreen(
            x = params.x,
            y = params.y,
            widgetWidth = widgetWpx,
            widgetHeight = widgetHpx,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
        )
        params.x = clampedX
        params.y = clampedY
        try {
            windowManager.updateViewLayout(rootContainer ?: view, params)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout scale: ${e.message}")
            WjxFileLogger.w(TAG, "updateViewLayout scale failed: ${e.message}")
        }
        if (expandedState.value) {
            collapsePanel()
            expandPanel()
        }
    }

    private data class WidgetSnapshot(
        val data: com.byd.wjx.data.remote.DiParsData?,
        val range: Double?,
        val sessionStartedAt: Long?,
        val tripDistanceKm: Double?,
        val consumption: ConsumptionState,
        val alpha: Float,
        val scale: Float,
        val cameraActive: Boolean,
    )

    private data class UiBundle(
        val consumption: ConsumptionState,
        val alpha: Float,
        val scale: Float,
    )

    @Synchronized
    private fun expandPanel() {
        if (expandedState.value) return
        val root = rootContainer ?: return
        val params = widgetParams ?: return
        val windowManager = wm ?: return
        val ctx = root.context ?: return
        val metrics = ctx.resources.displayMetrics
        collapsedX = params.x
        collapsedY = params.y
        val scale = scaleState.floatValue
        val box = WidgetButtonLayout.expandedWindow(
            collapsedX = params.x,
            collapsedY = params.y,
            panelWpx = currentWidgetWpx,
            panelHpx = currentWidgetHpx,
            buttonPx = (dp(ctx, BUTTON_DP) * scale).toInt(),
            gapPx = (dp(ctx, GAP_DP) * scale).toInt(),
            screenW = metrics.widthPixels,
            screenH = metrics.heightPixels,
        )
        params.x = box.x
        params.y = box.y
        params.width = box.width
        params.height = box.height
        try {
            windowManager.updateViewLayout(root, params)
        } catch (e: Exception) {
            Log.w(TAG, "expand updateViewLayout: ${e.message}")
            WjxFileLogger.w(TAG, "expand updateViewLayout failed: ${e.message}")
        }
        expandedState.value = true
    }

    @Synchronized
    private fun collapsePanel() {
        if (!expandedState.value) return
        val root = rootContainer ?: return
        val params = widgetParams ?: return
        val windowManager = wm ?: return
        expandedState.value = false
        params.x = collapsedX
        params.y = collapsedY
        params.width = currentWidgetWpx
        params.height = currentWidgetHpx
        try {
            windowManager.updateViewLayout(root, params)
        } catch (e: Exception) {
            Log.w(TAG, "collapse updateViewLayout: ${e.message}")
            WjxFileLogger.w(TAG, "collapse updateViewLayout failed: ${e.message}")
        }
    }

    private fun togglePanel() {
        if (expandedState.value) collapsePanel() else expandPanel()
    }

    private fun onWidgetButtonClick(context: Context, number: Int) {
        val dispatcher = automationButtonDispatcher
        if (dispatcher == null) {
            WjxFileLogger.w(TAG, "automation button dispatcher unavailable id=$number")
            showNoRuleToast(context, number)
            collapsePanel()
            return
        }
        dispatcher.fire(number) { matched ->
            if (matched == 0) {
                showNoRuleToast(context, number)
            }
        }
        collapsePanel()
    }

    private fun showNoRuleToast(context: Context, number: Int) {
        try {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.widget_button_no_rules, number),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } catch (_: Exception) { }
    }

    // --- Trash zone ---

    internal fun showTrashZone(context: Context) {
        if (trashView != null) return
        val windowManager = wm ?: return

        val lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
        trashLifecycle = lifecycleOwner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                TrashZoneView(active = trashActive.value)
            }
        }
        trashView = compose

        try {
            windowManager.addView(compose, params)
            WjxFileLogger.i(TAG, "trash zone shown")
        } catch (e: Exception) {
            Log.w(TAG, "addView trash: ${e.message}")
            WjxFileLogger.w(TAG, "addView trash failed: ${e.message}")
        }
    }

    internal fun hideTrashZone() {
        trashActive.value = false
        trashView?.let { v ->
            try { wm?.removeView(v) } catch (_: Exception) {}
        }
        trashLifecycle?.onDestroy()
        trashView = null
        trashLifecycle = null
    }

    internal fun setTrashActive(active: Boolean) {
        trashActive.value = active
    }

    // --- Helpers ---

    private fun resolveStartPosition(
        prefs: WidgetPreferences,
        metrics: DisplayMetrics,
        widgetWpx: Int,
        widgetHpx: Int,
    ): Pair<Int, Int> {
        val savedX = prefs.getX()
        val savedY = prefs.getY()
        return if (savedX == 0 && savedY == 0) {
            // Default: centered on screen so user notices it immediately.
            val x = (metrics.widthPixels - widgetWpx) / 2
            val y = (metrics.heightPixels - widgetHpx) / 2
            DragGestureLogic.clampToScreen(
                x = x,
                y = y,
                widgetWidth = widgetWpx,
                widgetHeight = widgetHpx,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
            )
        } else {
            DragGestureLogic.clampToScreen(
                x = savedX,
                y = savedY,
                widgetWidth = widgetWpx,
                widgetHeight = widgetHpx,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
            )
        }
    }

    /**
     * Called from SettingsViewModel after the user switches app language.
     * Detaches the overlay so the next attach (driven by ActivityLifecycle
     * callbacks when the user leaves Settings) recreates ComposeView against
     * the freshly-localized Configuration context.
     */
    @Synchronized
    fun relocale() {
        if (widgetView != null) detach()
    }

    private fun localizedContext(appCtx: Context): Context {
        val lang = LocalePreferences(appCtx).getLanguage() ?: return appCtx
        val config = Configuration(appCtx.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(lang))
        }
        return appCtx.createConfigurationContext(config)
    }

    private fun dp(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    private fun dpFromMetrics(metrics: DisplayMetrics, dp: Int): Int =
        (dp * metrics.density).toInt()

    // --- Touch handling ---

    private class WidgetTouchListener(
        private val context: Context,
        private val prefs: WidgetPreferences,
        private val metrics: DisplayMetrics,
    ) : android.view.View.OnTouchListener {

        // Read from controller state on each gesture so resize-mid-session
        // (e.g. user moved the size slider in Settings) doesn't leave us
        // clamping with stale dimensions.
        private val widgetWpx: Int get() = currentWidgetWpx
        private val widgetHpx: Int get() = currentWidgetHpx

        private var downX = 0f
        private var downY = 0f
        private var initialParamX = 0
        private var initialParamY = 0
        private var dragging = false
        private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null
        private var singleTapRunnable: Runnable? = null
        private var lastTapUpMs: Long = 0L

        override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
            val params = widgetParams ?: return false
            val windowManager = wm ?: return false
            val thresholdPx = dpFromMetrics(metrics, DRAG_THRESHOLD_DP)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialParamX = params.x
                    initialParamY = params.y
                    dragging = false
                    // Schedule long-press: if finger stays still for LONG_PRESS_MS,
                    // hide widget until user opens SuperToyBox. Canceled in ACTION_MOVE
                    // as soon as finger travels past drag threshold, and in ACTION_UP.
                    val runnable = Runnable {
                        if (dragging) return@Runnable
                        prefs.setHiddenUntilAppLaunch(true)
                        detach()
                        try {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.widget_toast_hidden),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } catch (_: Exception) {}
                    }
                    longPressRunnable = runnable
                    longPressHandler.postDelayed(runnable, LONG_PRESS_MS)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    var dx = (event.rawX - downX).toInt()
                    var dy = (event.rawY - downY).toInt()
                    val totalDistPx = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toInt()
                    if (!dragging && totalDistPx > thresholdPx) {
                        dragging = true
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        singleTapRunnable?.let { longPressHandler.removeCallbacks(it) }
                        singleTapRunnable = null
                        if (expandedState.value) {
                            collapsePanel()
                            initialParamX = params.x
                            initialParamY = params.y
                            downX = event.rawX
                            downY = event.rawY
                            dx = 0
                            dy = 0
                        }
                        showTrashZone(context)
                    }
                    if (dragging) {
                        val (clampedX, clampedY) = DragGestureLogic.clampToScreen(
                            x = initialParamX + dx,
                            y = initialParamY + dy,
                            widgetWidth = widgetWpx,
                            widgetHeight = widgetHpx,
                            screenWidth = metrics.widthPixels,
                            screenHeight = metrics.heightPixels,
                        )
                        params.x = clampedX
                        params.y = clampedY
                        try {
                            windowManager.updateViewLayout(rootContainer ?: v, params)
                        } catch (_: Exception) {}

                        // Trash zone hit-test (center-to-center)
                        val widgetCx = clampedX + widgetWpx / 2
                        val widgetCy = clampedY + widgetHpx / 2
                        val trashCx = metrics.widthPixels / 2
                        val trashRadiusPx = dpFromMetrics(metrics, TRASH_RADIUS_DP)
                        val trashBottomMarginPx = dpFromMetrics(metrics, 24 + 36)  // 24dp from bottom + half of 72dp halo
                        val trashCy = metrics.heightPixels - trashBottomMarginPx
                        val inside = DragGestureLogic.isInsideTrash(
                            widgetCx = widgetCx, widgetCy = widgetCy,
                            trashCx = trashCx, trashCy = trashCy,
                            radiusPx = trashRadiusPx,
                        )
                        setTrashActive(inside)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    longPressRunnable = null
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    val wasTap = DragGestureLogic.isTap(0, 0, dx, dy, thresholdPx)

                    if (wasTap) {
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                            val isLeftTap = prefs.isLeftTapZoningEnabled() &&
                                WidgetGestureLogic.isLeftZone(event.x, v.width, LEFT_TAP_FRACTION)
                            if (isLeftTap) {
                                launchLeftApp()
                            } else if (!prefs.isButtonsEnabled()) {
                                openSuperToyBox()
                            } else {
                                val now = android.os.SystemClock.uptimeMillis()
                                val pending = singleTapRunnable
                                if (pending != null &&
                                    WidgetGestureLogic.isWithinDoubleTapWindow(lastTapUpMs, now, DOUBLE_TAP_MS)
                                ) {
                                    longPressHandler.removeCallbacks(pending)
                                    singleTapRunnable = null
                                    lastTapUpMs = 0L
                                    openSuperToyBox()
                                } else {
                                    lastTapUpMs = now
                                    val runnable = Runnable {
                                        singleTapRunnable = null
                                        togglePanel()
                                    }
                                    singleTapRunnable = runnable
                                    longPressHandler.postDelayed(runnable, DOUBLE_TAP_MS)
                                }
                            }
                        }
                    } else {
                        // End drag
                        if (trashActive.value) {
                            // Dropped into trash → hide + disable
                            prefs.setEnabled(false)
                            detach()
                            return true
                        } else {
                            collapsedX = params.x
                            collapsedY = params.y
                            prefs.savePosition(params.x, params.y)
                        }
                        hideTrashZone()
                    }
                    dragging = false
                    return true
                }
            }
            return false
        }

        private fun openSuperToyBox() {
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                Log.w(TAG, "open app failed: ${e.message}")
                WjxFileLogger.w(TAG, "open app failed: ${e.message}")
            }
        }

        private fun launchLeftApp() {
            try {
                val intent = context.packageManager
                    .getLaunchIntentForPackage(prefs.getLeftTapAppPackage())
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent != null) context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "left tap launch failed: ${e.message}")
                WjxFileLogger.w(TAG, "left tap launch failed: ${e.message}")
            }
        }
    }
}
