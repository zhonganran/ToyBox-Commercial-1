package com.byd.wjx.desktopcard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.data.automation.ActionDispatcher
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.AutoDoorController
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.overlay.OverlayLifecycleOwner
import com.byd.wjx.ui.overlay.DraggableOverlayContainer
import com.byd.wjx.ui.theme.SuperToyBoxTheme
import com.byd.wjx.ui.theme.ThemeMode
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class DesktopQuickCardController @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: DesktopQuickCardRepository,
    private val actionDispatcher: ActionDispatcher,
    private val autoDoorController: AutoDoorController,
    private val vehicleStateRepository: VehicleStateRepository,
    private val carProfileDetector: CarProfileDetector,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderState = MutableStateFlow(DesktopQuickCardRenderState())
    private val exteriorSettings = ExteriorHailingSettings(appContext)

    private var scope: CoroutineScope? = null
    private var composeView: DraggableOverlayContainer? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var commandSequence = 0L
    private var receiverRegistered = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0
    private var collapsedAnchorX: Int? = null
    private var collapsedAnchorTopY: Int? = null
    private var horizontalAnchor = DesktopQuickCardHorizontalAnchor.START

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
            if (intent.getStringExtra(WjxOutsidePlayService.EXTRA_OWNER) != OWNER) return
            val path = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE_PATH).orEmpty()
            val playbackState = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE)
            WjxFileLogger.i(TAG, "sound state=$playbackState path=$path")
            when (playbackState) {
                WjxOutsidePlayService.STATE_STARTED -> renderState.update { it.copy(activeSoundPath = path) }
                WjxOutsidePlayService.STATE_STOPPED,
                WjxOutsidePlayService.STATE_COMPLETED,
                WjxOutsidePlayService.STATE_ERROR,
                -> renderState.update { current ->
                    if (current.activeSoundPath == path || path.isBlank()) current.copy(activeSoundPath = null) else current
                }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        ContextCompat.registerReceiver(
            appContext,
            playbackReceiver,
            IntentFilter(WjxOutsidePlayService.ACTION_PLAYBACK_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun render(config: DesktopQuickCardConfig, theme: ThemeMode) {
        mainHandler.post {
            val previous = renderState.value
            if (shouldStopDesktopQuickCardSound(previous.activeSoundPath, config.soundPaths, config.enabled)) {
                WjxFileLogger.i(TAG, "stopping removed/disabled sound path=${previous.activeSoundPath}")
                WjxOutsidePlayService.stop(appContext, owner = OWNER)
            }
            renderState.update {
                it.copy(
                    config = config,
                    themeMode = theme,
                    floatingIcon = repository.iconForId(config.floatingIconId),
                )
            }
            if (!config.enabled) {
                renderState.update { it.copy(expanded = false) }
                removeView()
                return@post
            }
            if (!Settings.canDrawOverlays(appContext)) {
                WjxFileLogger.w(TAG, "overlay permission missing; desktop card hidden")
                removeView()
                return@post
            }
            ensureView(config)
            composeView?.visibility = View.VISIBLE
        }
    }

    fun stop() {
        commandSequence += 1L
        if (renderState.value.activeSoundPath != null) {
            WjxOutsidePlayService.stop(appContext, owner = OWNER)
        }
        renderState.update {
            it.copy(
                expanded = false,
                activeCommand = null,
                activeSoundPath = null,
            )
        }
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(playbackReceiver) }
            receiverRegistered = false
        }
        mainHandler.post { removeView() }
        scope = null
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
                        onCommandClick = ::executeCommand,
                        onSoundClick = ::toggleSound,
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

    private fun executeCommand(command: String) {
        val worker = scope ?: return
        val item = DesktopQuickCardCommandCatalog.byCommand[command] ?: return
        val token = ++commandSequence
        renderState.update { it.copy(activeCommand = command) }
        WjxFileLogger.i(TAG, "command start command=$command label=${item.label}")
        worker.launch {
            val result = when (item.kind) {
                DesktopQuickCardCommandKind.AUTOMATION -> {
                    val data = vehicleStateRepository.fullSnapshot.value?.data
                    val dispatchResult = actionDispatcher.dispatch(
                        action = ActionDef(command = item.payload, displayName = item.label),
                        data = data,
                        owner = OWNER,
                    )
                    CommandExecutionResult(dispatchResult.success, dispatchResult.reason)
                }

                DesktopQuickCardCommandKind.AUTO_DOOR -> {
                    val doorResult = autoDoorController.toggleDoor(item.payload)
                    CommandExecutionResult(doorResult.success, doorResult.message)
                }

            }
            if (result.success) {
                WjxFileLogger.i(TAG, "command success command=$command")
            } else {
                WjxFileLogger.w(TAG, "command failed command=$command reason=${result.message}")
                mainHandler.post {
                    Toast.makeText(appContext, result.message ?: "控制执行失败", Toast.LENGTH_SHORT).show()
                }
            }
            delay(COMMAND_HIGHLIGHT_MS)
            renderState.update { current ->
                if (commandSequence == token && current.activeCommand == command) {
                    current.copy(activeCommand = null)
                } else {
                    current
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
        renderState.update { it.copy(expanded = true, horizontalAnchor = horizontalAnchor) }
        updateLayoutAfterContentResize(restoreCollapsedAnchor = false)
    }

    private fun collapseCard() {
        if (!renderState.value.expanded) return
        renderState.update { it.copy(expanded = false) }
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

    private data class CommandExecutionResult(
        val success: Boolean,
        val message: String?,
    )

    private fun toggleSound(path: String) {
        val file = File(path)
        if (!file.isFile) {
            repository.refresh()
            Toast.makeText(appContext, "音效文件已不存在", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "sound missing path=$path")
            return
        }
        val stopping = renderState.value.activeSoundPath == path
        WjxFileLogger.i(TAG, "sound ${if (stopping) "stop" else "start"} path=$path")
        val data = vehicleStateRepository.fullSnapshot.value?.data
        WjxOutsidePlayService.toggle(
            context = appContext,
            file = file,
            channel = exteriorSettings.effectiveChannel(carProfileDetector.detect()),
            loop = false,
            owner = OWNER,
            reverseActive = data?.gear == REVERSE_GEAR,
        )
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
        (DESKTOP_QUICK_CARD_COLLAPSED_ICON_DP * appContext.resources.displayMetrics.density).roundToInt()

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

    private fun removeView() {
        val view = composeView
        composeView = null
        layoutParams = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        collapsedAnchorX = null
        collapsedAnchorTopY = null
        if (view != null) runCatching { windowManager.removeViewImmediate(view) }
    }

    companion object {
        private const val TAG = "DesktopQuickCard"
        private const val OWNER = "desktop_quick_card"
        private const val REVERSE_GEAR = 2
        private const val COMMAND_HIGHLIGHT_MS = 900L
        private const val DEFAULT_X_RATIO = 0.042f
        private const val DEFAULT_Y_RATIO = 0.115f
    }
}
