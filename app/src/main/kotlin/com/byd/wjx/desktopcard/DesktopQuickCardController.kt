package com.byd.wjx.desktopcard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.clustercard.ClusterCardRepository
import com.byd.wjx.clustercard.ClusterCardSlot
import com.byd.wjx.data.automation.ActionDispatcher
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.AutoDoorController
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.lyrics.LyricSettings
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    private val activationManager: ActivationManager,
    private val clusterCardRepository: ClusterCardRepository,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderState = MutableStateFlow(DesktopQuickCardRenderState())
    private val exteriorSettings = ExteriorHailingSettings(appContext)
    private val lyricSettings = LyricSettings(appContext)
    private val lyricPrefs = appContext.getSharedPreferences(LyricSettings.PREFS_NAME, Context.MODE_PRIVATE)

    private var scope: CoroutineScope? = null
    private var composeView: DraggableOverlayContainer? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var commandSequence = 0L
    private var receiverRegistered = false
    private var hailingReceiverRegistered = false
    private var lyricPreferenceListenerRegistered = false
    private var clusterCardStateJob: Job? = null
    private var hailingStartedByCard = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0
    private var collapsedAnchorX: Int? = null
    private var collapsedAnchorTopY: Int? = null
    private var horizontalAnchor = DesktopQuickCardHorizontalAnchor.START

    private val lyricPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mainHandler.post {
            val desktopEnabled = lyricSettings.desktopEnabled
            val clusterEnabled = lyricSettings.clusterEnabled
            renderState.update { current ->
                if (
                    current.desktopLyricsEnabled == desktopEnabled &&
                    current.clusterLyricsEnabled == clusterEnabled
                ) {
                    current
                } else {
                    current.copy(
                        desktopLyricsEnabled = desktopEnabled,
                        clusterLyricsEnabled = clusterEnabled,
                    )
                }
            }
        }
    }

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

    private val hailingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxRealtimeHailingService.ACTION_HAILING_STATE) return
            val state = intent.getStringExtra(WjxRealtimeHailingService.EXTRA_STATE).orEmpty()
            val message = intent.getStringExtra(WjxRealtimeHailingService.EXTRA_MESSAGE).orEmpty()
            WjxFileLogger.i(TAG, "hailing state=$state message=$message")
            when (state) {
                WjxRealtimeHailingService.STATE_STARTED -> renderState.update {
                    it.copy(
                        realtimeHailingRunning = true,
                        realtimeHailingBusy = false,
                        activeSoundPath = null,
                    )
                }

                WjxRealtimeHailingService.STATE_STOPPED,
                WjxRealtimeHailingService.STATE_ERROR,
                -> {
                    hailingStartedByCard = false
                    renderState.update {
                        it.copy(
                            realtimeHailingRunning = false,
                            realtimeHailingBusy = false,
                        )
                    }
                    if (state == WjxRealtimeHailingService.STATE_ERROR) {
                        Toast.makeText(
                            appContext,
                            message.ifBlank { "实时喊话启动失败" },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
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
        ContextCompat.registerReceiver(
            appContext,
            hailingReceiver,
            IntentFilter(WjxRealtimeHailingService.ACTION_HAILING_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        hailingReceiverRegistered = true
        lyricPrefs.registerOnSharedPreferenceChangeListener(lyricPreferenceListener)
        lyricPreferenceListenerRegistered = true
        clusterCardStateJob = scope.launch {
            combine(clusterCardRepository.config, clusterCardRepository.config2) { primary, secondary ->
                primary.enabled to secondary.enabled
            }.collect { (primaryEnabled, secondaryEnabled) ->
                renderState.update { current ->
                    if (
                        current.clusterCardEnabled == primaryEnabled &&
                        current.clusterCard2Enabled == secondaryEnabled
                    ) {
                        current
                    } else {
                        current.copy(
                            clusterCardEnabled = primaryEnabled,
                            clusterCard2Enabled = secondaryEnabled,
                        )
                    }
                }
            }
        }
        renderState.update {
            it.copy(
                realtimeHailingRunning = WjxRealtimeHailingService.isActive(),
                clusterCardEnabled = clusterCardRepository.config.value.enabled,
                clusterCard2Enabled = clusterCardRepository.config2.value.enabled,
                desktopLyricsEnabled = lyricSettings.desktopEnabled,
                clusterLyricsEnabled = lyricSettings.clusterEnabled,
            )
        }
    }

    fun render(config: DesktopQuickCardConfig, theme: ThemeMode) {
        mainHandler.post {
            val previous = renderState.value
            val iconSizeChanged = collapsedDesktopQuickCardIconSizeDp(previous.config) !=
                collapsedDesktopQuickCardIconSizeDp(config)
            val cardAvailable = config.enabled
            if (shouldStopDesktopQuickCardSound(previous.activeSoundPath, config.soundPaths, cardAvailable)) {
                WjxFileLogger.i(TAG, "stopping removed/disabled sound path=${previous.activeSoundPath}")
                WjxOutsidePlayService.stop(appContext, owner = OWNER)
            }
            if (
                hailingStartedByCard &&
                (!cardAvailable || DESKTOP_QUICK_CARD_REALTIME_HAILING !in config.functionCommands)
            ) {
                WjxFileLogger.i(TAG, "stopping removed/disabled realtime hailing")
                renderState.update { it.copy(realtimeHailingBusy = true) }
                if (requestRealtimeHailingStop(notifyIfIdle = true, showFailure = true)) {
                    hailingStartedByCard = false
                }
            }
            renderState.update {
                it.copy(
                    config = config,
                    themeMode = theme,
                    floatingIcon = repository.iconForId(config.floatingIconId),
                    clusterCardEnabled = clusterCardRepository.config.value.enabled,
                    clusterCard2Enabled = clusterCardRepository.config2.value.enabled,
                    desktopLyricsEnabled = lyricSettings.desktopEnabled,
                    clusterLyricsEnabled = lyricSettings.clusterEnabled,
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
            if (iconSizeChanged) updateLayoutAfterIconResize()
            composeView?.visibility = View.VISIBLE
        }
    }

    fun stop() {
        commandSequence += 1L
        if (renderState.value.activeSoundPath != null) {
            WjxOutsidePlayService.stop(appContext, owner = OWNER)
        }
        if (hailingStartedByCard && WjxRealtimeHailingService.isActive()) {
            runCatching { WjxRealtimeHailingService.stop(appContext, notifyIfIdle = false) }
                .onFailure { error ->
                    WjxFileLogger.e(TAG, "realtime hailing cleanup failed: ${error.message}", error)
                }
        }
        hailingStartedByCard = false
        renderState.update {
            it.copy(
                expanded = false,
                activeCommand = null,
                activeSoundPath = null,
                realtimeHailingRunning = false,
                realtimeHailingBusy = false,
            )
        }
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(playbackReceiver) }
            receiverRegistered = false
        }
        if (hailingReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(hailingReceiver) }
            hailingReceiverRegistered = false
        }
        if (lyricPreferenceListenerRegistered) {
            lyricPrefs.unregisterOnSharedPreferenceChangeListener(lyricPreferenceListener)
            lyricPreferenceListenerRegistered = false
        }
        clusterCardStateJob?.cancel()
        clusterCardStateJob = null
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
        if (item.kind == DesktopQuickCardCommandKind.REALTIME_HAILING) {
            toggleRealtimeHailing()
            return
        }
        if (item.kind == DesktopQuickCardCommandKind.CLUSTER_CARD) {
            toggleClusterCard(item.payload)
            return
        }
        if (item.kind == DesktopQuickCardCommandKind.DESKTOP_LYRICS) {
            toggleDesktopLyrics()
            return
        }
        if (item.kind == DesktopQuickCardCommandKind.CLUSTER_LYRICS) {
            toggleClusterLyrics()
            return
        }
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

                DesktopQuickCardCommandKind.REALTIME_HAILING -> error("handled before command dispatch")
                DesktopQuickCardCommandKind.CLUSTER_CARD -> error("handled before command dispatch")
                DesktopQuickCardCommandKind.DESKTOP_LYRICS -> error("handled before command dispatch")
                DesktopQuickCardCommandKind.CLUSTER_LYRICS -> error("handled before command dispatch")

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

    private fun toggleRealtimeHailing() {
        if (renderState.value.realtimeHailingBusy) return
        if (renderState.value.realtimeHailingRunning || WjxRealtimeHailingService.isActive()) {
            WjxFileLogger.i(TAG, "realtime hailing stop requested")
            renderState.update { it.copy(realtimeHailingBusy = true) }
            requestRealtimeHailingStop(notifyIfIdle = true, showFailure = true)
            return
        }
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(appContext, "请先在应用设置中允许麦克风权限", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "realtime hailing blocked: RECORD_AUDIO denied")
            return
        }

        val channel = exteriorSettings.effectiveChannel(carProfileDetector.detect())
        WjxFileLogger.i(TAG, "realtime hailing start requested channel=$channel")
        WjxOutsidePlayService.stop(appContext)
        renderState.update {
            it.copy(
                realtimeHailingBusy = true,
                activeSoundPath = null,
            )
        }
        hailingStartedByCard = true
        runCatching { WjxRealtimeHailingService.start(appContext, channel) }
            .onFailure { error ->
                hailingStartedByCard = false
                renderState.update {
                    it.copy(realtimeHailingRunning = false, realtimeHailingBusy = false)
                }
                WjxFileLogger.e(TAG, "realtime hailing start failed: ${error.message}", error)
                Toast.makeText(appContext, "实时喊话启动失败", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleClusterLyrics() {
        val enabled = !lyricSettings.clusterEnabled
        if (enabled && !activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)) {
            Toast.makeText(appContext, "仪表歌词需要激活后使用", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "cluster lyrics toggle blocked: activation required")
            return
        }
        if (enabled && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "cluster lyrics toggle blocked: overlay permission missing")
            return
        }

        lyricSettings.clusterEnabled = enabled
        renderState.update { it.copy(clusterLyricsEnabled = enabled) }
        Toast.makeText(
            appContext,
            if (enabled) "仪表歌词已开启" else "仪表歌词已关闭",
            Toast.LENGTH_SHORT,
        ).show()
        WjxFileLogger.i(TAG, "cluster lyrics enabled=$enabled from desktop card")
    }

    private fun toggleDesktopLyrics() {
        val enabled = !lyricSettings.desktopEnabled
        if (enabled && !activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)) {
            Toast.makeText(appContext, "桌面歌词需要激活后使用", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "desktop lyrics toggle blocked: activation required")
            return
        }
        if (enabled && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "desktop lyrics toggle blocked: overlay permission missing")
            return
        }

        lyricSettings.desktopEnabled = enabled
        renderState.update { it.copy(desktopLyricsEnabled = enabled) }
        Toast.makeText(
            appContext,
            if (enabled) "桌面歌词已开启" else "桌面歌词已关闭",
            Toast.LENGTH_SHORT,
        ).show()
        WjxFileLogger.i(TAG, "desktop lyrics enabled=$enabled from desktop card")
    }

    private fun toggleClusterCard(payload: String) {
        val slot = if (payload == "secondary") ClusterCardSlot.SECONDARY else ClusterCardSlot.PRIMARY
        val currentConfig = clusterCardRepository.current(slot)
        val enabled = !currentConfig.enabled
        if (enabled && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            WjxFileLogger.w(TAG, "${slot.label} toggle blocked: overlay permission missing")
            return
        }

        clusterCardRepository.setEnabled(slot, enabled)
        renderState.update {
            when (slot) {
                ClusterCardSlot.PRIMARY -> it.copy(clusterCardEnabled = enabled)
                ClusterCardSlot.SECONDARY -> it.copy(clusterCard2Enabled = enabled)
            }
        }
        val hasMetrics = clusterCardRepository.current(slot).metricIds.isNotEmpty()
        Toast.makeText(
            appContext,
            when {
                !enabled -> "${slot.label}已关闭"
                hasMetrics -> "${slot.label}已开启"
                else -> "${slot.label}已开启，请先添加显示项目"
            },
            Toast.LENGTH_SHORT,
        ).show()
        WjxFileLogger.i(TAG, "${slot.label} enabled=$enabled from desktop card")
    }

    private fun requestRealtimeHailingStop(notifyIfIdle: Boolean, showFailure: Boolean): Boolean =
        runCatching {
            WjxRealtimeHailingService.stop(appContext, notifyIfIdle)
        }.onFailure { error ->
            renderState.update {
                it.copy(
                    realtimeHailingRunning = WjxRealtimeHailingService.isActive(),
                    realtimeHailingBusy = false,
                )
            }
            WjxFileLogger.e(TAG, "realtime hailing stop failed: ${error.message}", error)
            if (showFailure) {
                Toast.makeText(appContext, "实时喊话停止失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }.isSuccess

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
        renderState.update {
            it.copy(
                expanded = true,
                horizontalAnchor = horizontalAnchor,
                clusterCardEnabled = clusterCardRepository.config.value.enabled,
                clusterCard2Enabled = clusterCardRepository.config2.value.enabled,
                desktopLyricsEnabled = lyricSettings.desktopEnabled,
                clusterLyricsEnabled = lyricSettings.clusterEnabled,
            )
        }
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

    private fun updateLayoutAfterIconResize() {
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
