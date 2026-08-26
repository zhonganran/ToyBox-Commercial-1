package com.byd.wjx.desktopcard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.theme.ThemeMode
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class DesktopQuickCardController @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: DesktopQuickCardRepository,
    private val commandExecutor: DesktopQuickCardCommandExecutor,
    private val vehicleStateRepository: VehicleStateRepository,
    private val carProfileDetector: CarProfileDetector,
    private val featureController: DesktopQuickCardFeatureController,
    private val hailingController: DesktopQuickCardHailingController,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderState = MutableStateFlow(DesktopQuickCardRenderState())
    private val mediaController = DesktopQuickCardMediaController(
        context = appContext,
        repository = repository,
        vehicleStateRepository = vehicleStateRepository,
        carProfileDetector = carProfileDetector,
        callbacks = object : DesktopQuickCardMediaController.Callbacks {
            override fun onActiveSoundChanged(path: String?) {
                renderState.update { it.copy(activeSoundPath = path) }
            }
        },
    )
    private val windowController = DesktopQuickCardWindowController(
        context = appContext,
        repository = repository,
        renderState = renderState,
        callbacks = object : DesktopQuickCardWindowController.Callbacks {
            override fun onExpand(anchor: DesktopQuickCardHorizontalAnchor) {
                val features = featureController.currentState()
                renderState.update {
                    it.copy(
                        expanded = true,
                        horizontalAnchor = anchor,
                        clusterCardEnabled = features.clusterCardEnabled,
                        clusterCard2Enabled = features.clusterCard2Enabled,
                        desktopLyricsEnabled = features.desktopLyricsEnabled,
                        clusterLyricsEnabled = features.clusterLyricsEnabled,
                    )
                }
            }

            override fun onCollapse() {
                renderState.update { it.copy(expanded = false) }
            }

            override fun onCommandClick(command: String) = executeCommand(command)

            override fun onSoundClick(path: String) = toggleSound(path)
        },
    )

    private var scope: CoroutineScope? = null
    private var commandSequence = 0L

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        mediaController.start()
        featureController.start(scope, ::applyFeatureState)
        applyHailingState(
            hailingController.start(
                object : DesktopQuickCardHailingController.Callbacks {
                    override fun onStateChanged(state: DesktopQuickCardHailingState) {
                        applyHailingState(state)
                    }

                    override fun onError(message: String) {
                        mainHandler.post {
                            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ),
        )
    }

    fun render(config: DesktopQuickCardConfig, theme: ThemeMode) {
        mainHandler.post {
            val previous = renderState.value
            val iconSizeChanged = collapsedDesktopQuickCardIconSizeDp(previous.config) !=
                collapsedDesktopQuickCardIconSizeDp(config)
            mediaController.reconcile(previous.activeSoundPath, config)
            hailingController.reconcile(config)
            val features = featureController.currentState()
            renderState.update {
                it.copy(
                    config = config,
                    themeMode = theme,
                    floatingIcon = repository.iconForId(config.floatingIconId),
                    clusterCardEnabled = features.clusterCardEnabled,
                    clusterCard2Enabled = features.clusterCard2Enabled,
                    desktopLyricsEnabled = features.desktopLyricsEnabled,
                    clusterLyricsEnabled = features.clusterLyricsEnabled,
                )
            }
            if (!config.enabled) {
                renderState.update { it.copy(expanded = false) }
                windowController.removeView()
                return@post
            }
            if (!Settings.canDrawOverlays(appContext)) {
                WjxFileLogger.w(TAG, "overlay permission missing; desktop card hidden")
                windowController.removeView()
                return@post
            }
            windowController.show(config)
            if (iconSizeChanged) windowController.updateIconSize()
        }
    }

    fun stop() {
        commandSequence += 1L
        mediaController.stop(renderState.value.activeSoundPath)
        hailingController.stop()
        featureController.stop()
        renderState.update {
            it.copy(
                expanded = false,
                activeCommand = null,
                activeSoundPath = null,
                realtimeHailingRunning = false,
                realtimeHailingBusy = false,
            )
        }
        windowController.removeView()
        scope = null
    }

    private fun executeCommand(command: String) {
        val worker = scope ?: return
        val item = DesktopQuickCardCommandCatalog.byCommand[command] ?: return
        if (item.kind == DesktopQuickCardCommandKind.REALTIME_HAILING) {
            hailingController.toggle(
                DesktopQuickCardHailingState(
                    running = renderState.value.realtimeHailingRunning,
                    busy = renderState.value.realtimeHailingBusy,
                ),
            )
            return
        }
        if (item.kind in FEATURE_TOGGLE_KINDS) {
            val result = featureController.toggle(item)
            applyFeatureState(result.state)
            Toast.makeText(appContext, result.message, Toast.LENGTH_SHORT).show()
            return
        }
        val token = ++commandSequence
        renderState.update { it.copy(activeCommand = command) }
        WjxFileLogger.i(TAG, "command start command=$command label=${item.label}")
        worker.launch {
            val result = commandExecutor.execute(item, OWNER)
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

    private fun toggleSound(path: String) {
        if (mediaController.toggle(path, renderState.value.activeSoundPath) == DesktopQuickCardSoundResult.MISSING) {
            Toast.makeText(appContext, "音效文件已不存在", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFeatureState(state: DesktopQuickCardFeatureState) {
        renderState.update {
            it.copy(
                clusterCardEnabled = state.clusterCardEnabled,
                clusterCard2Enabled = state.clusterCard2Enabled,
                desktopLyricsEnabled = state.desktopLyricsEnabled,
                clusterLyricsEnabled = state.clusterLyricsEnabled,
            )
        }
    }

    private fun applyHailingState(state: DesktopQuickCardHailingState) {
        renderState.update {
            it.copy(
                realtimeHailingRunning = state.running,
                realtimeHailingBusy = state.busy,
                activeSoundPath = if (state.clearActiveSound) null else it.activeSoundPath,
            )
        }
    }

    companion object {
        private const val TAG = "DesktopQuickCard"
        private const val OWNER = "desktop_quick_card"
        private const val COMMAND_HIGHLIGHT_MS = 900L
        private val FEATURE_TOGGLE_KINDS = setOf(
            DesktopQuickCardCommandKind.CLUSTER_CARD,
            DesktopQuickCardCommandKind.DESKTOP_LYRICS,
            DesktopQuickCardCommandKind.CLUSTER_LYRICS,
        )
    }
}
