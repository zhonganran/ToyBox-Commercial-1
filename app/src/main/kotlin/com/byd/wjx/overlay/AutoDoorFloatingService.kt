package com.byd.wjx.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.data.vehicle.AutoDoorControlResult
import com.byd.wjx.data.vehicle.AutoDoorController
import com.byd.wjx.data.vehicle.AutoDoorSideSnapshot
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AutoDoorFloatingService : Service() {
    @Inject lateinit var autoDoorController: AutoDoorController
    @Inject lateinit var trackingStateRepository: TrackingStateRepository

    private lateinit var settings: AutoDoorFloatingSettings
    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visibilityController: DesktopOverlayVisibilityController? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastAppliedVisibility: Boolean? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var dragging = false
    private var runningSide: String? = null
    private var doorStates: Map<String, AutoDoorSideSnapshot> = emptyMap()
    private var doorStateReadFailed = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var postCommandRefreshJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        settings = AutoDoorFloatingSettings(applicationContext)
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, buildNotification("自动门小组件待命"))
        }
        startDesktopVisibilityController()
        WjxFileLogger.i(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WjxFileLogger.i(TAG, "onStartCommand action=${intent?.action.orEmpty()} enabled=${settings.enabled}")
        when (intent?.action) {
            ACTION_HIDE -> {
                settings.enabled = false
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (settings.enabled) {
                    rebuildOverlay()
                    refreshDoorStates("manual_refresh")
                } else {
                    stopSelf()
                }
            }
            ACTION_APPEARANCE -> {
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
        cancelLongPress()
        visibilityController?.stop()
        visibilityController = null
        scope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (!settings.enabled) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
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
                refreshDoorStates("already_visible")
                return
            }
        }
        if (overlayView != null) {
            rebuildOverlay()
            refreshDoorStates("already_visible")
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
            dp(160)
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
            alpha = settings.alpha
        }
        runCatching {
            windowManager.addView(view, lp)
            overlayView = view
            params = lp
            lastAppliedVisibility = null
            visibilityController?.refresh("show")
            refreshDoorStates("show")
            WjxFileLogger.i(TAG, "overlay shown size=${size.first}x${size.second} pos=$initialX,$initialY locked=${settings.locked}")
        }.onFailure {
            WjxFileLogger.e(TAG, "add overlay failed: ${it.message}", it)
            overlayView = null
            params = null
            settings.enabled = false
            stopSelf()
        }
    }

    private fun rebuildOverlay() {
        val oldView = overlayView as? ViewGroup
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
        oldView.removeAllViews()
        while (newView.childCount > 0) {
            val child = newView.getChildAt(0)
            newView.removeViewAt(0)
            oldView.addView(child)
        }
        oldView.background = newView.background
        oldView.setPadding(newView.paddingLeft, newView.paddingTop, newView.paddingRight, newView.paddingBottom)
        oldView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val size = currentSize()
        lp.width = size.first
        lp.height = size.second
        lp.alpha = settings.alpha
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
            scope = scope,
            cameraActive = trackingStateRepository.cameraActive,
            logTag = TAG,
        ).also { controller ->
            controller.start(::applyOverlayVisibility)
        }
    }

    private fun applyOverlayVisibility(decision: DesktopOverlayVisibilityDecision) {
        val visible = decision.visible
        if (overlayView != null && lastAppliedVisibility != visible) {
            WjxFileLogger.d(TAG, "overlayVisibility=${if (visible) "VISIBLE" else "GONE"} reason=${decision.reason} trigger=${decision.trigger}")
        }
        lastAppliedVisibility = visible
        overlayView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun buildOverlayView(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            setPadding(0, 0, 0, 0)
            background = null
            addView(brandPanel(), LinearLayout.LayoutParams(sdp(116), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(space(sdp(10), 1))
            addView(doorCard(SIDE_LEFT, "左侧滑门", 0xFF8EE8FF.toInt()), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(space(sdp(8), 1))
            addView(doorCard(SIDE_RIGHT, "右侧滑门", 0xFFFFD074.toInt()), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }

    private fun brandPanel(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(sdp(6), sdp(6), sdp(6), sdp(6))
            setOnTouchListener(::handleDragTouch)
            setOnLongClickListener {
                toggleLockedByUser()
                true
            }
            background = glassBackground(0x3AFFFFFF, 0x4DFFFFFF)
            elevation = sdp(5).toFloat()
            addView(
                RoundedCarView(context),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }

    private fun doorCard(key: String, title: String, accent: Int): LinearLayout {
        val state = doorStates[key]
        val isRunning = runningSide == key
        val status = when {
            isRunning -> "动作中"
            state != null -> state.statusText
            doorStateReadFailed -> "状态不可读"
            else -> "读取中"
        }
        val actionText = when {
            isRunning -> "暂停"
            state?.nextCommand == 2 -> "关闭"
            state?.nextCommand == 3 -> "暂停"
            else -> "打开"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(sdp(8), sdp(7), sdp(8), sdp(7))
            background = cardBackground(accent, isRunning)
            elevation = sdp(if (isRunning) 8 else 5).toFloat()
            isClickable = true
            isEnabled = runningSide == null || isRunning
            alpha = if (runningSide != null && !isRunning) 0.58f else 1f
            setOnClickListener { runToggleCommand(key, title) }
            setOnLongClickListener {
                toggleLockedByUser()
                true
            }

            addView(
                DoorGlyphView(context, key == SIDE_LEFT, accent),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(text(title, 13f, Color.WHITE, Typeface.DEFAULT_BOLD), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sdp(20)))
            addView(text(status, 12f, 0xD9FFFFFF.toInt(), Typeface.DEFAULT_BOLD), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sdp(18)))
            addView(chip(actionText, accent.withAlpha(0x2E)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, sdp(20)))
        }
    }

    private fun refreshDoorStates(reason: String) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val states = runCatching {
                withContext(Dispatchers.IO) { autoDoorController.readDoorStates(listOf(SIDE_LEFT, SIDE_RIGHT)) }
            }.onFailure {
                WjxFileLogger.w(TAG, "refresh states failed reason=$reason error=${it.message}", it)
            }.getOrDefault(emptyList())
            if (states.isNotEmpty()) {
                doorStates = states.associateBy { it.key }
                doorStateReadFailed = false
                WjxFileLogger.d(TAG, "states reason=$reason ${states.joinToString(" · ") { "${it.shortLabel}${it.statusText}" }}")
            } else {
                doorStateReadFailed = true
                WjxFileLogger.w(TAG, "states empty reason=$reason")
            }
            if (overlayView != null) rebuildOverlay()
        }
    }

    private fun runToggleCommand(key: String, title: String) {
        if (runningSide != null) return
        visibilityController?.extendDesktopHold("door_command_start")
        runningSide = key
        rebuildOverlay()
        scope.launch {
            val latestState = runCatching {
                withContext(Dispatchers.IO) { autoDoorController.readDoorStates(listOf(key)).firstOrNull() }
            }.onFailure {
                WjxFileLogger.w(TAG, "$title read latest state failed: ${it.message}", it)
            }.getOrNull()
            if (latestState != null) {
                doorStates = doorStates + (key to latestState)
                doorStateReadFailed = false
            }
            val state = latestState ?: doorStates[key]
            val commandValue = state?.nextCommand ?: COMMAND_OPEN
            val commandLabel = state?.nextCommandLabel ?: "开门"
            rebuildOverlay()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    autoDoorController.runDoors(listOf(key), commandValue, title)
                }
            }.onFailure {
                WjxFileLogger.e(TAG, "$title $commandLabel failed", it)
            }.getOrElse {
                AutoDoorControlResult(
                    success = false,
                    message = "$title $commandLabel 失败：${it.message ?: "未知异常"}",
                )
            }
            runningSide = null
            WjxFileLogger.i(TAG, "side=$key command=$commandLabel success=${result.success} message=${result.message}")
            Toast.makeText(this@AutoDoorFloatingService, result.message, Toast.LENGTH_SHORT).show()
            visibilityController?.extendDesktopHold("door_command_finish")
            rebuildOverlay()
            schedulePostCommandRefresh(key)
        }
    }

    private fun schedulePostCommandRefresh(key: String) {
        postCommandRefreshJob?.cancel()
        postCommandRefreshJob = scope.launch {
            listOf(450L, 1_000L, 1_600L, 2_400L).forEachIndexed { index, delayMs ->
                delay(delayMs)
                if (overlayView == null) return@launch
                refreshDoorStates("after_command_${key}_$index")
            }
        }
    }

    private fun handleDragTouch(view: View, event: MotionEvent): Boolean {
        val lp = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downWindowX = lp.x
                downWindowY = lp.y
                dragging = false
                view.isPressed = true
                val runnable = Runnable {
                    if (!dragging) {
                        view.isPressed = false
                        toggleLockedByUser()
                    }
                }
                longPressRunnable = runnable
                longPressHandler.postDelayed(runnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > sdp(DRAG_SLOP_DP) || abs(dy) > sdp(DRAG_SLOP_DP))) {
                    dragging = true
                    cancelLongPress()
                }
                if (dragging && !settings.locked) {
                    lp.x = downWindowX + dx.toInt()
                    lp.y = downWindowY + dy.toInt()
                    clampParams(lp)
                    overlayView?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                view.isPressed = false
                if (dragging && !settings.locked) {
                    settings.savePosition(lp.x, lp.y)
                    WjxFileLogger.i(TAG, "position saved x=${lp.x} y=${lp.y}")
                }
                dragging = false
                return true
            }
        }
        return false
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun toggleLockedByUser() {
        settings.locked = !settings.locked
        WjxFileLogger.i(TAG, "locked=${settings.locked}")
        Toast.makeText(
            this,
            if (settings.locked) "自动门小组件已固定" else "自动门小组件已解锁，可拖动",
            Toast.LENGTH_SHORT,
        ).show()
        rebuildOverlay()
    }

    private fun currentSize(): Pair<Int, Int> {
        val scale = settings.scale
        return (dp(BASE_WIDTH_DP) * scale).roundToInt() to (dp(BASE_HEIGHT_DP) * scale).roundToInt()
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

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun overlayFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun chip(label: String, color: Int): TextView =
        TextView(this).apply {
            text = label
            textSize = scaledText(10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xE8FFFFFF.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = sdp(54)
            setPadding(sdp(8), 0, sdp(8), 0)
            background = rounded(color, sdp(10).toFloat(), 0x12FFFFFF)
            setShadowLayer(1.4f, 0f, 0.8f, 0x66000000)
        }

    private fun text(label: String, size: Float, color: Int, face: Typeface): TextView =
        TextView(this).apply {
            text = label
            textSize = scaledText(size)
            typeface = face
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setShadowLayer(1.8f, 0f, 1f, 0x77000000)
        }

    private fun cardBackground(accent: Int, active: Boolean): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                if (active) accent.withAlpha(0x78) else 0x66162235,
                if (active) accent.withAlpha(0x42) else 0x4A101827,
            ),
        ).apply {
            cornerRadius = sdp(15).toFloat()
            setStroke(strokePx(), if (active) accent.withAlpha(0xCC) else 0x66FFFFFF)
        }

    private fun glassBackground(color: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color, color.withAlpha(0x24)),
        ).apply {
            cornerRadius = sdp(15).toFloat()
            setStroke(strokePx(), strokeColor)
        }

    private fun rounded(color: Int, radius: Float, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != 0) setStroke(strokePx(), strokeColor)
        }

    private fun space(width: Int, height: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(width, height) }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "自动门小组件", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun sdp(value: Int): Int =
        (value * resources.displayMetrics.density * settings.scale).roundToInt().coerceAtLeast(1)

    private fun scaledText(size: Float): Float =
        size * settings.scale

    private fun strokePx(): Int =
        max(1, (resources.displayMetrics.density * settings.scale).roundToInt())

    private class DoorGlyphView(
        context: Context,
        private val left: Boolean,
        private val accent: Int,
    ) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            strokeWidth = 3.2f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.FILL
        }
        private val doorRect = RectF()
        private val arrowPath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            doorRect.set(w * 0.42f, h * 0.14f, w * 0.68f, h * 0.82f)
            canvas.drawRoundRect(doorRect, h * 0.09f, h * 0.09f, linePaint)
            canvas.drawLine(doorRect.right, h * 0.28f, doorRect.right + w * 0.13f, h * 0.28f, linePaint)
            canvas.drawLine(doorRect.right, h * 0.68f, doorRect.right + w * 0.13f, h * 0.68f, linePaint)
            val direction = if (left) -1f else 1f
            val x = if (left) doorRect.left - w * 0.16f else doorRect.right + w * 0.2f
            val y = h * 0.48f
            arrowPath.apply {
                reset()
                moveTo(x, y)
                lineTo(x - direction * w * 0.12f, y - h * 0.13f)
                lineTo(x - direction * w * 0.12f, y + h * 0.13f)
                close()
            }
            canvas.drawPath(arrowPath, accentPaint)
        }
    }

    private class RoundedCarView(context: Context) : View(context) {
        private val bitmap: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.auto_door_car_icon)
        private val src = Rect()
        private val dst = RectF()
        private val clip = Path()
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val radius = h * 0.13f
            dst.set(0f, 0f, w, h)
            clip.reset()
            clip.addRoundRect(dst, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            bitmap?.let { source ->
                val viewRatio = w / h
                val bitmapRatio = source.width.toFloat() / source.height.toFloat()
                if (bitmapRatio > viewRatio) {
                    val srcWidth = (source.height * viewRatio).toInt().coerceAtLeast(1)
                    val left = ((source.width - srcWidth) / 2).coerceAtLeast(0)
                    src.set(left, 0, left + srcWidth, source.height)
                } else {
                    val srcHeight = (source.width / viewRatio).toInt().coerceAtLeast(1)
                    val top = ((source.height - srcHeight) / 2).coerceAtLeast(0)
                    src.set(0, top, source.width, top + srcHeight)
                }
                canvas.drawBitmap(source, src, dst, imagePaint)
                canvas.drawRect(dst, veilPaint)
            }
            canvas.restore()
        }
    }

    companion object {
        private const val ACTION_SHOW = "com.byd.wjx.action.SHOW_AUTO_DOOR_FLOATING"
        private const val ACTION_HIDE = "com.byd.wjx.action.HIDE_AUTO_DOOR_FLOATING"
        private const val ACTION_REFRESH = "com.byd.wjx.action.REFRESH_AUTO_DOOR_FLOATING"
        private const val ACTION_APPEARANCE = "com.byd.wjx.action.UPDATE_AUTO_DOOR_FLOATING_APPEARANCE"
        private const val CHANNEL_ID = "wjx_auto_door_floating"
        private const val NOTIFICATION_ID = 1103
        private const val TAG = "AutoDoorFloating"
        private const val SIDE_LEFT = "slide_l"
        private const val SIDE_RIGHT = "slide_r"
        private const val COMMAND_OPEN = 1
        private const val BASE_WIDTH_DP = 392
        private const val BASE_HEIGHT_DP = 126
        private const val LONG_PRESS_MS = 900L
        private const val DRAG_SLOP_DP = 8

        fun start(context: Context) {
            startServiceCompat(
                context,
                Intent(context, AutoDoorFloatingService::class.java).setAction(ACTION_SHOW),
            )
        }

        fun startIfAllowed(context: Context) {
            val appContext = context.applicationContext
            if (AutoDoorFloatingSettings(appContext).enabled && android.provider.Settings.canDrawOverlays(appContext)) {
                start(appContext)
            }
        }

        fun refresh(context: Context) {
            startServiceCompat(
                context,
                Intent(context, AutoDoorFloatingService::class.java).setAction(ACTION_REFRESH),
            )
        }

        fun refreshAppearance(context: Context) {
            startServiceCompat(
                context,
                Intent(context, AutoDoorFloatingService::class.java).setAction(ACTION_APPEARANCE),
            )
        }

        fun hide(context: Context) {
            startServiceCompat(
                context,
                Intent(context, AutoDoorFloatingService::class.java).setAction(ACTION_HIDE),
            )
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}
