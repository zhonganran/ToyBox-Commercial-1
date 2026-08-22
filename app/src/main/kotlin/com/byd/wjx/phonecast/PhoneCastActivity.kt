package com.byd.wjx.phonecast

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SuperToyBoxTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PhoneCastPhase {
    CONNECTING,
    PLAYING,
    RETRYING,
}

private data class PhoneCastUiState(
    val phase: PhoneCastPhase,
    val message: String,
)

@UnstableApi
class PhoneCastActivity : AppCompatActivity(), Player.Listener {
    private lateinit var player: ExoPlayer
    private lateinit var preferences: PhoneCastPreferences
    private var streamUrl = ""
    private var endpointLabel = "手机投屏"
    private var scaleMode by mutableStateOf(PhoneCastScaleMode.CROP)
    private var uiState by mutableStateOf(
        PhoneCastUiState(PhoneCastPhase.CONNECTING, "正在连接"),
    )
    private var controlsVisible by mutableStateOf(true)
    private var retryAttempt = 0
    private var retryJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var controlsJob: Job? = null
    private val destroyed = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(AndroidColor.BLACK)
        configureImmersiveMode()

        preferences = PhoneCastPreferences(this)
        if (!readSession(intent)) {
            finish()
            return
        }
        player = buildPlayer()
        setContent {
            SuperToyBoxTheme {
                PhoneCastScreen(
                    player = player,
                    endpointLabel = endpointLabel,
                    state = uiState,
                    scaleMode = scaleMode,
                    controlsVisible = controlsVisible,
                    onRevealControls = ::revealControls,
                    onClose = ::finish,
                    onRetry = { startPlayback(resetRetryBudget = true) },
                    onScaleModeSelected = ::applyScaleMode,
                )
            }
        }
        startPlayback(resetRetryBudget = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readSession(intent) && ::player.isInitialized) {
            startPlayback(resetRetryBudget = true)
        }
    }

    override fun onResume() {
        super.onResume()
        configureImmersiveMode()
        if (::player.isInitialized) {
            player.playWhenReady = true
            if (player.playerError != null) startPlayback(resetRetryBudget = false)
        }
    }

    override fun onPause() {
        if (::player.isInitialized) player.playWhenReady = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureImmersiveMode()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                if (uiState.phase != PhoneCastPhase.RETRYING) {
                    uiState = PhoneCastUiState(PhoneCastPhase.CONNECTING, "正在连接 $endpointLabel")
                }
            }
            Player.STATE_READY -> {
                connectTimeoutJob?.cancel()
                connectTimeoutJob = null
                retryJob?.cancel()
                retryJob = null
                retryAttempt = 0
                uiState = PhoneCastUiState(PhoneCastPhase.PLAYING, "已连接 $endpointLabel")
                scheduleControlsHide()
            }
            Player.STATE_ENDED -> scheduleRetry("视频流已结束")
            Player.STATE_IDLE -> Unit
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        WjxFileLogger.w(
            TAG,
            "RTSP playback error endpoint=$endpointLabel code=${error.errorCodeName} message=${error.message}",
        )
        scheduleRetry("连接已中断")
    }

    private fun buildPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        return ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .also {
                it.addListener(this)
                it.setHandleAudioBecomingNoisy(true)
                it.playWhenReady = true
            }
    }

    private fun readSession(intent: Intent): Boolean {
        val saved = preferences.snapshot()
        val requestedUrl = intent.getStringExtra(EXTRA_URL).orEmpty().ifBlank { saved.url }
        val normalized = PhoneCastUrl.normalize(requestedUrl) ?: return false
        val requestedScale = intent.getStringExtra(EXTRA_SCALE_MODE)
        streamUrl = normalized
        endpointLabel = PhoneCastUrl.endpointLabel(normalized)
        scaleMode = if (requestedScale == null) saved.scaleMode else PhoneCastScaleMode.fromStored(requestedScale)
        preferences.save(normalized, scaleMode)
        return true
    }

    private fun startPlayback(resetRetryBudget: Boolean) {
        if (!::player.isInitialized || destroyed.get()) return
        retryJob?.cancel()
        retryJob = null
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        if (resetRetryBudget) retryAttempt = 0
        controlsVisible = true
        uiState = PhoneCastUiState(PhoneCastPhase.CONNECTING, "正在连接 $endpointLabel")
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(streamUrl))
        runCatching {
            player.stop()
            player.clearMediaItems()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
            connectTimeoutJob = lifecycleScope.launch {
                delay(CONNECT_TIMEOUT_MS)
                connectTimeoutJob = null
                if (uiState.phase == PhoneCastPhase.CONNECTING) scheduleRetry("连接超时")
            }
        }.onFailure {
            WjxFileLogger.w(TAG, "RTSP prepare failed endpoint=$endpointLabel: ${it.message}", it)
            scheduleRetry("无法连接手机")
        }
    }

    private fun scheduleRetry(reason: String) {
        if (destroyed.get() || retryJob?.isActive == true) return
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        if (::player.isInitialized) player.stop()
        retryAttempt++
        val delaySeconds = min(MAX_RETRY_DELAY_SECONDS, retryAttempt * RETRY_STEP_SECONDS)
        controlsVisible = true
        uiState = PhoneCastUiState(
            PhoneCastPhase.RETRYING,
            "$reason，${delaySeconds} 秒后重连",
        )
        retryJob = lifecycleScope.launch {
            delay(delaySeconds * 1_000L)
            retryJob = null
            if (!destroyed.get()) startPlayback(resetRetryBudget = false)
        }
    }

    private fun applyScaleMode(mode: PhoneCastScaleMode) {
        scaleMode = mode
        preferences.setScaleMode(mode)
        revealControls()
    }

    private fun revealControls() {
        controlsVisible = true
        scheduleControlsHide()
    }

    private fun scheduleControlsHide() {
        controlsJob?.cancel()
        if (uiState.phase != PhoneCastPhase.PLAYING) return
        controlsJob = lifecycleScope.launch {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        destroyed.set(true)
        retryJob?.cancel()
        connectTimeoutJob?.cancel()
        controlsJob?.cancel()
        if (::player.isInitialized) {
            player.removeListener(this)
            player.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneCastActivity"
        private const val EXTRA_URL = "phone_cast_url"
        private const val EXTRA_SCALE_MODE = "phone_cast_scale_mode"
        private const val MIN_BUFFER_MS = 500
        private const val MAX_BUFFER_MS = 1_500
        private const val BUFFER_FOR_PLAYBACK_MS = 200
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 400
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val RETRY_STEP_SECONDS = 2
        private const val MAX_RETRY_DELAY_SECONDS = 8
        private const val CONTROLS_HIDE_DELAY_MS = 3_500L

        fun start(context: Context, url: String, scaleMode: PhoneCastScaleMode) {
            val normalized = PhoneCastUrl.normalize(url) ?: return
            PhoneCastPreferences(context).save(normalized, scaleMode)
            context.startActivity(
                Intent(context, PhoneCastActivity::class.java)
                    .putExtra(EXTRA_URL, normalized)
                    .putExtra(EXTRA_SCALE_MODE, scaleMode.storedValue)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        }
    }
}

@UnstableApi
@Composable
private fun PhoneCastScreen(
    player: ExoPlayer,
    endpointLabel: String,
    state: PhoneCastUiState,
    scaleMode: PhoneCastScaleMode,
    controlsVisible: Boolean,
    onRevealControls: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onScaleModeSelected: (PhoneCastScaleMode) -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onRevealControls() } },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    isClickable = true
                    setOnClickListener { onRevealControls() }
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.resizeMode = scaleMode.toResizeMode()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.phase != PhoneCastPhase.PLAYING) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(34.dp))
                Text(state.message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "退出投屏", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("手机投屏", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(endpointLabel, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp)
                }
                IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "重新连接", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = state.message,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(18.dp))
                SingleChoiceSegmentedButtonRow {
                    PhoneCastScaleMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = scaleMode == mode,
                            onClick = { onScaleModeSelected(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = PhoneCastScaleMode.entries.size,
                            ),
                        ) {
                            Text(mode.label, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
private fun PhoneCastScaleMode.toResizeMode(): Int = when (this) {
    PhoneCastScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    PhoneCastScaleMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    PhoneCastScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
}
