package com.byd.wjx.xiaozhi.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.xiaozhi.domain.XiaozhiPhase
import kotlin.math.roundToInt

internal data class XiaozhiStatusPresentation(
    val visible: Boolean,
    val title: String = "",
    val detail: String = "",
    val accentColor: Int = Color.TRANSPARENT,
)

internal fun xiaozhiStatusPresentation(phase: XiaozhiPhase): XiaozhiStatusPresentation = when (phase) {
    XiaozhiPhase.Connecting -> XiaozhiStatusPresentation(
        visible = true,
        title = "小智 · 正在连接",
        detail = "正在准备自动对话",
        accentColor = 0xFFFF9D3D.toInt(),
    )
    XiaozhiPhase.Listening -> XiaozhiStatusPresentation(
        visible = true,
        title = "小智 · 正在聆听",
        detail = "请直接说话 · 15秒无语音自动关闭",
        accentColor = 0xFF17C789.toInt(),
    )
    XiaozhiPhase.Thinking -> XiaozhiStatusPresentation(
        visible = true,
        title = "小智 · 正在思考",
        detail = "已经听完，正在处理",
        accentColor = 0xFFFF9D3D.toInt(),
    )
    XiaozhiPhase.Speaking -> XiaozhiStatusPresentation(
        visible = true,
        title = "小智 · 正在回答",
        detail = "回答结束后可以直接继续说",
        accentColor = 0xFF4B8DFF.toInt(),
    )
    XiaozhiPhase.NotConfigured, XiaozhiPhase.Idle, XiaozhiPhase.Error ->
        XiaozhiStatusPresentation(visible = false)
}

/** Lightweight, non-touchable desktop indicator owned by the voice foreground service. */
internal class XiaozhiStatusOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var dot: View? = null
    private var title: TextView? = null
    private var detail: TextView? = null
    private var permissionWarningLogged = false

    fun update(phase: XiaozhiPhase) {
        val presentation = xiaozhiStatusPresentation(phase)
        if (!presentation.visible) {
            hide()
            return
        }
        if (!Settings.canDrawOverlays(appContext)) {
            if (!permissionWarningLogged) {
                permissionWarningLogged = true
                WjxFileLogger.w(TAG, "overlay permission missing; foreground notification remains available")
            }
            hide()
            return
        }
        permissionWarningLogged = false
        if (root == null) show()
        dot?.background = circle(presentation.accentColor)
        title?.apply {
            text = presentation.title
            setTextColor(presentation.accentColor)
        }
        detail?.text = presentation.detail
    }

    fun hide() {
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
        dot = null
        title = null
        detail = null
    }

    private fun show() {
        val indicator = View(appContext)
        val primaryText = TextView(appContext).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            includeFontPadding = false
        }
        val secondaryText = TextView(appContext).apply {
            textSize = 11f
            setTextColor(0xFFC3CED7.toInt())
            includeFontPadding = false
            setPadding(0, dp(3f), 0, 0)
        }
        val texts = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            addView(primaryText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            addView(secondaryText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumWidth = dp(280f)
            setPadding(dp(18f), dp(11f), dp(20f), dp(11f))
            background = roundedBackground()
            elevation = dp(10f).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(indicator, LinearLayout.LayoutParams(dp(10f), dp(10f)).apply {
                marginEnd = dp(13f)
            })
            addView(texts, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(30f)
        }
        runCatching { windowManager.addView(container, params) }
            .onSuccess {
                root = container
                dot = indicator
                title = primaryText
                detail = secondaryText
                WjxFileLogger.i(TAG, "desktop status overlay shown")
            }
            .onFailure { error ->
                WjxFileLogger.w(TAG, "show failed: ${error.message}", error)
            }
    }

    private fun roundedBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24f).toFloat()
        setColor(0xEE17232D.toInt())
        setStroke(dp(1f), 0x99506673.toInt())
    }

    private fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Float): Int =
        (value * appContext.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "XiaozhiStatusOverlay"
    }
}
