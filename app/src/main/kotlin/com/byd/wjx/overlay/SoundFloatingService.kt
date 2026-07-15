package com.byd.wjx.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.storage.WjxDirectories
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class SoundFloatingService : Service() {
    @Inject lateinit var trackingStateRepository: TrackingStateRepository
    @Inject lateinit var profileDetector: CarProfileDetector
    @Inject lateinit var soundLibrary: SoundLibrary
    private lateinit var settings: SoundFloatingSettings
    private lateinit var windowManager: WindowManager
    private lateinit var hailingSettings: ExteriorHailingSettings
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var dragging = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visibilityController: DesktopOverlayVisibilityController? = null
    private var lastAppliedVisibility: Boolean? = null
    private var playingPath: String? = null
    private var hailingRunning: Boolean = false
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
            val path = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE_PATH).orEmpty()
            val state = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE).orEmpty()
            val oldPath = playingPath
            when (state) {
                WjxOutsidePlayService.STATE_STARTED -> {
                    if (path.isNotBlank()) playingPath = path
                }
                WjxOutsidePlayService.STATE_STOPPED,
                WjxOutsidePlayService.STATE_COMPLETED,
                WjxOutsidePlayService.STATE_ERROR -> {
                    if (path.isBlank() || playingPath == path) playingPath = null
                }
            }
            if (oldPath != playingPath && overlayView != null) {
                rebuildOverlay()
            }
        }
    }
    private val hailingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxRealtimeHailingService.ACTION_HAILING_STATE) return
            val old = hailingRunning
            hailingRunning = intent.getStringExtra(WjxRealtimeHailingService.EXTRA_STATE) ==
                WjxRealtimeHailingService.STATE_STARTED
            val message = intent.getStringExtra(WjxRealtimeHailingService.EXTRA_MESSAGE).orEmpty()
            if (message.isNotBlank()) Toast.makeText(this@SoundFloatingService, message, Toast.LENGTH_SHORT).show()
            if (old != hailingRunning && overlayView != null) {
                rebuildOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        WjxFileLogger.i(TAG, "onCreate")
        settings = SoundFloatingSettings(applicationContext)
        windowManager = getSystemService(WindowManager::class.java)
        hailingSettings = ExteriorHailingSettings(applicationContext)
        WjxDirectories.ensureBaseDirs()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("音效悬浮窗待命"))
        registerPlaybackReceiver()
        registerHailingReceiver()
        startDesktopVisibilityController()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WjxFileLogger.i(TAG, "onStartCommand action=${intent?.action.orEmpty()} enabled=${settings.enabled} expanded=${settings.expanded}")
        when (intent?.action) {
            ACTION_HIDE -> {
                settings.enabled = false
                WjxFileLogger.i(TAG, "hide requested")
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (settings.enabled) rebuildOverlay() else stopSelf()
            }
            else -> {
                settings.enabled = true
                showOverlay()
            }
        }
        return if (settings.enabled) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        WjxFileLogger.i(TAG, "onDestroy")
        visibilityController?.stop()
        visibilityController = null
        serviceScope.cancel()
        runCatching { unregisterReceiver(playbackReceiver) }
            .onFailure { WjxFileLogger.w(TAG, "unregister playback receiver failed: ${it.message}") }
        runCatching { unregisterReceiver(hailingReceiver) }
            .onFailure { WjxFileLogger.w(TAG, "unregister hailing receiver failed: ${it.message}") }
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (!settings.enabled) return
        if (!Settings.canDrawOverlays(this)) {
            WjxFileLogger.w(TAG, "show blocked: SYSTEM_ALERT_WINDOW denied")
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            settings.enabled = false
            stopSelf()
            return
        }
        overlayView?.let { existing ->
            if (existing.parent == null) {
                WjxFileLogger.w(TAG, "overlay view detached before show; recreating")
                overlayView = null
                params = null
                lastAppliedVisibility = null
            } else {
                rebuildOverlay()
                return
            }
        }
        if (overlayView != null) {
            rebuildOverlay()
            return
        }

        val view = buildOverlayView()
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val size = currentSize()
        val metrics = realDisplayMetrics()
        val initialX = if (settings.x == Int.MIN_VALUE) {
            max(0, metrics.widthPixels - size.first - dp(24))
        } else {
            settings.x.coerceIn(0, max(0, metrics.widthPixels - size.first))
        }
        val initialY = if (settings.y == Int.MIN_VALUE) {
            dp(96)
        } else {
            settings.y.coerceIn(0, max(0, metrics.heightPixels - size.second))
        }

        val lp = WindowManager.LayoutParams(
            size.first,
            size.second,
            overlayType(),
            overlayFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = initialX
            y = initialY
        }

        runCatching {
            windowManager.addView(view, lp)
            overlayView = view
            params = lp
            lastAppliedVisibility = null
            visibilityController?.refresh("show")
            WjxFileLogger.i(TAG, "overlay shown expanded=${settings.expanded} size=${size.first}x${size.second} pos=$initialX,$initialY")
        }.onFailure {
            WjxFileLogger.e(TAG, "add overlay failed: ${it.message}", it)
            overlayView = null
            params = null
            settings.enabled = false
            stopSelf()
        }
    }

    private fun rebuildOverlay() {
        val oldView = overlayView
        val lp = params
        if (oldView == null || lp == null) {
            showOverlay()
            return
        }
        if (oldView.parent == null) {
            WjxFileLogger.w(TAG, "overlay view detached before rebuild; recreating")
            overlayView = null
            params = null
            lastAppliedVisibility = null
            showOverlay()
            return
        }
        val newView = buildOverlayView()
        if (oldView is ViewGroup) {
            oldView.removeAllViews()
            while (newView.childCount > 0) {
                val child = newView.getChildAt(0)
                newView.removeViewAt(0)
                oldView.addView(child)
            }
            oldView.background = newView.background
            oldView.setPadding(newView.paddingLeft, newView.paddingTop, newView.paddingRight, newView.paddingBottom)
            oldView.setOnTouchListener(if (settings.expanded) null else View.OnTouchListener(::handleCollapsedTouch))
            oldView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val size = currentSize()
        lp.width = size.first
        lp.height = size.second
        clampParams(lp)
        visibilityController?.refresh("rebuild")
        runCatching { windowManager.updateViewLayout(oldView, lp) }
            .onFailure { WjxFileLogger.w(TAG, "rebuild update layout failed: ${it.message}") }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { WjxFileLogger.w(TAG, "remove overlay failed: ${it.message}") }
        }
        overlayView = null
        params = null
        lastAppliedVisibility = null
    }

    private fun startDesktopVisibilityController() {
        if (visibilityController != null) return
        visibilityController = DesktopOverlayVisibilityController(
            context = applicationContext,
            scope = serviceScope,
            cameraActive = trackingStateRepository.cameraActive,
            logTag = TAG,
        ).also { controller ->
            controller.start(::applyOverlayVisibility)
        }
    }

    private fun registerPlaybackReceiver() {
        runCatching {
            ContextCompat.registerReceiver(
                this,
                playbackReceiver,
                IntentFilter(WjxOutsidePlayService.ACTION_PLAYBACK_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            WjxFileLogger.i(TAG, "playback receiver registered")
        }.onFailure { WjxFileLogger.w(TAG, "register playback receiver failed: ${it.message}") }
    }

    private fun registerHailingReceiver() {
        runCatching {
            ContextCompat.registerReceiver(
                this,
                hailingReceiver,
                IntentFilter(WjxRealtimeHailingService.ACTION_HAILING_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            WjxFileLogger.i(TAG, "hailing receiver registered")
        }.onFailure { WjxFileLogger.w(TAG, "register hailing receiver failed: ${it.message}") }
    }

    private fun applyOverlayVisibility(decision: DesktopOverlayVisibilityDecision) {
        val visible = decision.visible
        if (overlayView != null && lastAppliedVisibility != visible) {
            WjxFileLogger.d(
                TAG,
                "overlayVisibility=${if (visible) "VISIBLE" else "GONE"} reason=${decision.reason} trigger=${decision.trigger}",
            )
        }
        lastAppliedVisibility = visible
        overlayView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun buildOverlayView(): LinearLayout =
        if (settings.expanded) buildExpandedView() else buildCollapsedView()

    private fun buildExpandedView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0x66162235, dp(14).toFloat(), 0x5538BDF8)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "超级玩具箱"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setOnTouchListener(::handleDragTouch)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
        header.addView(headerButton("-") { setExpanded(false) })
        header.addView(space(dp(8), 1))
        header.addView(headerButton("X") { hideByUser() })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))

        root.addView(space(1, dp(10)))

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(soundGrid(), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildCollapsedView(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = null
            setOnTouchListener(::handleCollapsedTouch)
            addView(ImageView(this@SoundFloatingService).apply {
                applyCollapsedIcon(this)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                alpha = settings.iconAlpha
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

    private fun applyCollapsedIcon(imageView: ImageView) {
        when (settings.iconStyle) {
            SoundFloatingSettings.ICON_STYLE_TOOLBOX -> {
                imageView.setImageResource(R.drawable.sound_float_toolbox)
            }
            SoundFloatingSettings.ICON_STYLE_CUSTOM -> {
                val bitmap = BitmapFactory.decodeFile(settings.customIconPath)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    WjxFileLogger.w(TAG, "custom icon invalid path=${settings.customIconPath}")
                    imageView.setImageResource(R.drawable.sound_float_face)
                }
            }
            else -> imageView.setImageResource(R.drawable.sound_float_face)
        }
    }

    private fun soundGrid(): LinearLayout {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val sounds = soundLibrary.listSounds()
        val totalTiles = 1 + sounds.size
        (0 until totalTiles step GRID_COLUMNS).forEachIndexed { rowIndex, rowStart ->
            if (rowIndex > 0) grid.addView(space(1, dp(8)))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            repeat(GRID_COLUMNS) { column ->
                val tileIndex = rowStart + column
                val tile = when {
                    tileIndex == 0 -> hailingTile()
                    tileIndex <= sounds.size -> {
                        val sound = sounds[tileIndex - 1]
                        soundTile(sound, playingPath == sound.file.absolutePath) { toggleSound(sound) }
                    }
                    else -> View(this)
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(68), 1f))
                if (column < GRID_COLUMNS - 1) row.addView(space(dp(8), 1))
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        }
        return grid
    }

    private fun hailingTile(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(5))
            background = if (hailingRunning) {
                rounded(0xAA55FF88.toInt(), dp(10).toFloat(), 0xCCB8FFCE.toInt())
            } else {
                rounded(0x6638BDF8, dp(10).toFloat(), 0x5538BDF8)
            }
            setOnClickListener { toggleHailing() }
            addView(ImageView(this@SoundFloatingService).apply {
                setImageResource(if (hailingRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now)
                setColorFilter(if (hailingRunning) 0xFF102B1E.toInt() else Color.WHITE)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(this@SoundFloatingService).apply {
                text = if (hailingRunning) "停止喊话" else "实时喊话"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (hailingRunning) 0xFF102B1E.toInt() else Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

    private fun soundTile(
        sound: SoundLibrary.SoundItem,
        isPlaying: Boolean,
        click: () -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(5))
            background = if (isPlaying) {
                rounded(0xAA55FF88.toInt(), dp(10).toFloat(), 0xCCB8FFCE.toInt())
            } else {
                rounded(0x66247CFF, dp(10).toFloat(), 0x5538BDF8)
            }
            setOnClickListener { click() }
            addView(ImageView(this@SoundFloatingService).apply {
                setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                setColorFilter(if (isPlaying) 0xFF102B1E.toInt() else Color.WHITE)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(this@SoundFloatingService).apply {
                text = sound.name
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isPlaying) 0xFF102B1E.toInt() else Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

    private fun toggleSound(sound: SoundLibrary.SoundItem) {
        visibilityController?.extendDesktopHold("sound_toggle")
        val path = sound.file.absolutePath
        if (playingPath == path) {
            WjxFileLogger.i(TAG, "floating stop sound name=${sound.name} path=$path")
            WjxOutsidePlayService.stop(this)
            playingPath = null
            rebuildOverlay()
            Toast.makeText(this, "已停止：${sound.name}", Toast.LENGTH_SHORT).show()
            return
        }
        val channel = hailingSettings.effectiveChannel(profileDetector.detect())
        WjxFileLogger.i(TAG, "floating play sound name=${sound.name} path=$path channel=$channel")
        if (hailingRunning) {
            hailingRunning = false
            WjxRealtimeHailingService.stop(this, notifyIfIdle = true)
        }
        playingPath = path
        rebuildOverlay()
        WjxOutsidePlayService.play(this, sound.file, channel)
        Toast.makeText(this, "车外播放：${sound.name}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleHailing() {
        visibilityController?.extendDesktopHold("hailing_toggle")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WjxFileLogger.w(TAG, "floating hailing blocked: RECORD_AUDIO denied")
            Toast.makeText(this, "请先允许麦克风权限", Toast.LENGTH_SHORT).show()
            return
        }
        val channel = hailingSettings.effectiveChannel(profileDetector.detect())
        if (hailingRunning) {
            WjxFileLogger.i(TAG, "floating stop hailing channel=$channel")
            WjxRealtimeHailingService.stop(this, notifyIfIdle = true)
            Toast.makeText(this, "正在停止实时喊话", Toast.LENGTH_SHORT).show()
        } else {
            WjxFileLogger.i(TAG, "floating start hailing channel=$channel")
            WjxOutsidePlayService.stop(this)
            playingPath = null
            WjxRealtimeHailingService.start(this, channel)
            Toast.makeText(this, "正在准备实时喊话：${channel.shortLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setExpanded(expanded: Boolean) {
        WjxFileLogger.i(TAG, "setExpanded=$expanded")
        visibilityController?.extendDesktopHold("expand")
        settings.expanded = expanded
        rebuildOverlay()
    }

    private fun hideByUser() {
        WjxFileLogger.i(TAG, "hideByUser")
        settings.enabled = false
        removeOverlay()
        stopSelf()
    }

    private fun handleCollapsedTouch(view: View, event: MotionEvent): Boolean =
        handleDragTouch(view, event, tapAction = { setExpanded(true) })

    private fun handleDragTouch(view: View, event: MotionEvent): Boolean =
        handleDragTouch(view, event, tapAction = null)

    private fun handleDragTouch(view: View, event: MotionEvent, tapAction: (() -> Unit)?): Boolean {
        val lp = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downWindowX = lp.x
                downWindowY = lp.y
                dragging = false
                view.isPressed = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > dp(DRAG_SLOP_DP) || abs(dy) > dp(DRAG_SLOP_DP))) {
                    dragging = true
                }
                if (dragging) {
                    lp.x = downWindowX + dx.toInt()
                    lp.y = downWindowY + dy.toInt()
                    clampParams(lp)
                    overlayView?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                if (dragging) {
                    settings.savePosition(lp.x, lp.y)
                    WjxFileLogger.i(TAG, "position saved x=${lp.x} y=${lp.y}")
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    tapAction?.invoke()
                }
                dragging = false
                return true
            }
        }
        return false
    }

    private fun currentSize(): Pair<Int, Int> =
        if (settings.expanded) {
            val rows = ((soundLibrary.listSounds().size + 1 + GRID_COLUMNS - 1) / GRID_COLUMNS).coerceAtLeast(1)
            val visibleRows = min(rows, VISIBLE_SOUND_ROWS)
            val heightDp = 20 + 32 + 10 + (visibleRows * 68) + ((visibleRows - 1) * 8)
            dp(320) to dp(heightDp)
        } else {
            val collapsedDp = (76f * settings.iconScale).roundToInt().coerceIn(50, 112)
            dp(collapsedDp) to dp(collapsedDp)
        }

    private fun clampParams(lp: WindowManager.LayoutParams) {
        val metrics = realDisplayMetrics()
        lp.x = lp.x.coerceIn(0, max(0, metrics.widthPixels - lp.width))
        lp.y = lp.y.coerceIn(0, max(0, metrics.heightPixels - lp.height))
    }

    @Suppress("DEPRECATION")
    private fun realDisplayMetrics(): DisplayMetrics {
        val realMetrics = DisplayMetrics()
        val resourceMetrics = resources.displayMetrics
        return runCatching {
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            if (realMetrics.widthPixels <= 0 || realMetrics.heightPixels <= 0) {
                resourceMetrics
            } else {
                realMetrics.density = resourceMetrics.density
                realMetrics.scaledDensity = resourceMetrics.scaledDensity
                realMetrics.densityDpi = resourceMetrics.densityDpi
                realMetrics.xdpi = resourceMetrics.xdpi
                realMetrics.ydpi = resourceMetrics.ydpi
                realMetrics
            }
        }.getOrDefault(resourceMetrics)
    }

    private fun overlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun overlayFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun headerButton(text: String, click: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(0x22FFFFFF, dp(8).toFloat(), 0x22FFFFFF)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        }

    private fun rounded(color: Int, radius: Float, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }

    private fun space(width: Int, height: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(width, height) }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "音效悬浮窗", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_dialer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ACTION_SHOW = "com.byd.wjx.action.SHOW_SOUND_FLOATING"
        private const val ACTION_HIDE = "com.byd.wjx.action.HIDE_SOUND_FLOATING"
        private const val ACTION_REFRESH = "com.byd.wjx.action.REFRESH_SOUND_FLOATING"
        private const val CHANNEL_ID = "wjx_sound_floating"
        private const val NOTIFICATION_ID = 1102
        private const val GRID_COLUMNS = 3
        private const val VISIBLE_SOUND_ROWS = 3
        private const val DRAG_SLOP_DP = 8
        private const val TAG = "SoundFloating"

        fun start(context: Context) {
            startServiceCompat(
                context,
                Intent(context, SoundFloatingService::class.java).setAction(ACTION_SHOW),
            )
        }

        fun startIfAllowed(context: Context) {
            val appContext = context.applicationContext
            if (SoundFloatingSettings(appContext).enabled && Settings.canDrawOverlays(appContext)) {
                start(appContext)
            }
        }

        fun refresh(context: Context) {
            startServiceCompat(
                context,
                Intent(context, SoundFloatingService::class.java).setAction(ACTION_REFRESH),
            )
        }

        fun hide(context: Context) {
            startServiceCompat(
                context,
                Intent(context, SoundFloatingService::class.java).setAction(ACTION_HIDE),
            )
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}
