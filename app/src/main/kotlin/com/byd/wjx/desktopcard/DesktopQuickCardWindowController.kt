package com.byd.wjx.desktopcard

import android.content.Context
import android.graphics.Point
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.overlay.DraggableOverlayContainer
import com.byd.wjx.ui.overlay.OverlayLifecycleOwner
import com.byd.wjx.ui.theme.SuperToyBoxTheme
import com.byd.wjx.ui.theme.ThemeMode
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

/** Owns the desktop card's WindowManager view and its drag/resize lifecycle. */
class DesktopQuickCardWindowController(
    context: Context,
    private val repository: DesktopQuickCardRepository,
    private val renderState: StateFlow<DesktopQuickCardRenderState>,
    private val callbacks: Callbacks,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: DraggableOverlayContainer? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0
    private var collapsedAnchorX: Int? = null
    private var collapsedAnchorTopY: Int? = null
    private var horizontalAnchor = DesktopQuickCardHorizontalAnchor.START

    fun show(config: DesktopQuickCardConfig) {
        ensureView(config)
        composeView?.visibility = View.VISIBLE
    }

    fun removeView() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeViewInternal()
        } else {
            mainHandler.post(::removeViewInternal)
        }
    }

    fun updateIconSize() {
        if (renderState.value.expanded) {
            updateLayoutAfterContentResize(restoreCollapsedAnchor = false)
            return
        }
        val view = composeView ?: return
        view.requestLayout()
        view.post {
            view.post {
                val params = layoutParams ?: return@post
                clampPosition(params)
                runCatching { windowManager.updateViewLayout(view, params) }
                repository.savePosition(params.x, params.y)
            }
        }
    }

    private fun ensureView(config: DesktopQuickCardConfig) {
        if (composeView == null) {
            val owner = OverlayLifecycleOwner().also { it.onCreate() }
            val contentView = ComposeView(appContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            }
            val view = DraggableOverlayContainer(appContext).apply {
                addView(
                    contentView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                dragCallbacks = object : DraggableOverlayContainer.Callbacks {
                    override fun onDragStart(rawX: Float, rawY: Float) {
                        beginDrag(rawX, rawY)
                    }

                    override fun onDrag(rawX: Float, rawY: Float) {
                        moveToRaw(rawX, rawY)
                    }

                    override fun onDragEnd(cancelled: Boolean) {
                        finishDrag(cancelled)
                    }
                }
            }
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            contentView.setContent {
                val state by renderState.collectAsState()
                SuperToyBoxTheme(themeMode = state.themeMode) {
                    DesktopQuickCardContent(
                        state = state,
                        onExpand = ::expandCard,
                        onCollapse = ::collapseCard,
                        onCommandClick = callbacks::onCommandClick,
                        onSoundClick = callbacks::onSoundClick,
                    )
                }
            }
            val start = initialPosition(config)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = start.x
                y = start.y
            }
            runCatching { windowManager.addView(view, params) }
                .onSuccess {
                    composeView = view
                    lifecycleOwner = owner
                    layoutParams = params
                    if (!config.positionInitialized) repository.savePosition(params.x, params.y)
                    view.post {
                        val beforeX = params.x
                        val beforeY = params.y
                        clampPosition(params)
                        runCatching { windowManager.updateViewLayout(view, params) }
                        if (params.x != beforeX || params.y != beforeY) {
                            repository.savePosition(params.x, params.y)
                        }
                    }
                }
                .onFailure { error ->
                    owner.onDestroy()
                    WjxFileLogger.e(TAG, "add overlay failed: ${error.message}", error)
                }
        } else {
            val params = layoutParams ?: return
            if (!renderState.value.expanded) {
                val target = initialPosition(config)
                if (params.x != target.x || params.y != target.y) {
                    params.x = target.x
                    params.y = target.y
                    clampPosition(params)
                    runCatching { windowManager.updateViewLayout(composeView, params) }
                }
            }
            if (!config.positionInitialized) repository.savePosition(params.x, params.y)
            composeView?.post {
                val beforeX = params.x
                val beforeY = params.y
                clampPosition(params)
                runCatching { windowManager.updateViewLayout(composeView, params) }
                if (params.x != beforeX || params.y != beforeY) {
                    repository.savePosition(params.x, params.y)
                }
            }
        }
    }

    private fun expandCard() {
        if (renderState.value.expanded) return
        val view = composeView
        layoutParams?.let { params ->
            val collapsedWidth = view?.width?.takeIf { it > 0 } ?: collapsedIconSizePx()
            horizontalAnchor = DesktopQuickCardAnchor.edgeFor(
                collapsedX = params.x,
                collapsedWidth = collapsedWidth,
                screenWidth = screenSize().x,
            )
            collapsedAnchorX = params.x
            collapsedAnchorTopY = params.y
        }
        callbacks.onExpand(horizontalAnchor)
        updateLayoutAfterContentResize(restoreCollapsedAnchor = false)
    }

    private fun collapseCard() {
        if (!renderState.value.expanded) return
        callbacks.onCollapse()
        updateLayoutAfterContentResize(restoreCollapsedAnchor = true)
    }

    private fun updateLayoutAfterContentResize(restoreCollapsedAnchor: Boolean) {
        val view = composeView ?: return
        view.requestLayout()
        view.post {
            view.post {
                val params = layoutParams ?: return@post
                collapsedAnchorX?.let { collapsedX ->
                    params.x = DesktopQuickCardAnchor.expandedX(
                        collapsedX = collapsedX,
                        collapsedWidth = collapsedIconSizePx(),
                        expandedWidth = view.width,
                        anchor = horizontalAnchor,
                    )
                }
                collapsedAnchorTopY?.let { params.y = it }
                clampPosition(params)
                runCatching { windowManager.updateViewLayout(view, params) }
                collapsedAnchorX = DesktopQuickCardAnchor.collapsedX(
                    expandedX = params.x,
                    expandedWidth = view.width,
                    collapsedWidth = collapsedIconSizePx(),
                    anchor = horizontalAnchor,
                )
                collapsedAnchorTopY = params.y
                if (restoreCollapsedAnchor) {
                    repository.savePosition(params.x, params.y)
                    collapsedAnchorX = null
                    collapsedAnchorTopY = null
                }
            }
        }
    }

    private fun beginDrag(rawX: Float, rawY: Float) {
        val params = layoutParams ?: return
        dragStartRawX = rawX
        dragStartRawY = rawY
        dragStartWindowX = params.x
        dragStartWindowY = params.y
        WjxFileLogger.i(TAG, "drag start x=${params.x} y=${params.y}")
    }

    private fun moveToRaw(rawX: Float, rawY: Float) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.x = dragStartWindowX + (rawX - dragStartRawX).roundToInt()
        params.y = dragStartWindowY + (rawY - dragStartRawY).roundToInt()
        clampPosition(params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun finishDrag(cancelled: Boolean) {
        layoutParams?.let { params ->
            if (renderState.value.expanded) {
                val expandedWidth = composeView?.width?.takeIf { it > 0 } ?: collapsedIconSizePx()
                collapsedAnchorX = DesktopQuickCardAnchor.collapsedX(
                    expandedX = params.x,
                    expandedWidth = expandedWidth,
                    collapsedWidth = collapsedIconSizePx(),
                    anchor = horizontalAnchor,
                )
                collapsedAnchorTopY = params.y
                val collapsedX = DesktopQuickCardAnchor.collapsedX(
                    expandedX = params.x,
                    expandedWidth = expandedWidth,
                    collapsedWidth = collapsedIconSizePx(),
                    anchor = horizontalAnchor,
                )
                val position = clampCollapsedPosition(collapsedX, params.y)
                repository.savePosition(position.x, position.y)
            } else {
                repository.savePosition(params.x, params.y)
            }
            WjxFileLogger.i(TAG, "drag end x=${params.x} y=${params.y} cancelled=$cancelled")
        }
    }

    private fun clampCollapsedPosition(x: Int, y: Int): Point {
        val screen = screenSize()
        val iconSize = collapsedIconSizePx()
        return Point(
            x.coerceIn(0, (screen.x - iconSize).coerceAtLeast(0)),
            y.coerceIn(0, (screen.y - iconSize).coerceAtLeast(0)),
        )
    }

    private fun collapsedIconSizePx(): Int =
        (collapsedDesktopQuickCardIconSizeDp(renderState.value.config) *
            appContext.resources.displayMetrics.density).roundToInt()

    private fun clampPosition(params: WindowManager.LayoutParams) {
        val screen = screenSize()
        val width = composeView?.width?.takeIf { it > 0 } ?: 1
        val height = composeView?.height?.takeIf { it > 0 } ?: 1
        params.x = params.x.coerceIn(0, (screen.x - width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screen.y - height).coerceAtLeast(0))
    }

    private fun initialPosition(config: DesktopQuickCardConfig): Point {
        if (config.positionInitialized) return Point(config.x, config.y)
        val screen = screenSize()
        return Point((screen.x * DEFAULT_X_RATIO).roundToInt(), (screen.y * DEFAULT_Y_RATIO).roundToInt())
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point = Point().also(windowManager.defaultDisplay::getRealSize)

    private fun removeViewInternal() {
        val view = composeView
        composeView = null
        layoutParams = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        collapsedAnchorX = null
        collapsedAnchorTopY = null
        if (view != null) runCatching { windowManager.removeViewImmediate(view) }
    }

    interface Callbacks {
        fun onExpand(anchor: DesktopQuickCardHorizontalAnchor)
        fun onCollapse()
        fun onCommandClick(command: String)
        fun onSoundClick(path: String)
    }

    private companion object {
        const val TAG = "DesktopQuickCardWindow"
        const val DEFAULT_X_RATIO = 0.042f
        const val DEFAULT_Y_RATIO = 0.115f
    }
}
