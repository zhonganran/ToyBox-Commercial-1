package com.byd.wjx.statusbar

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.overlay.OverlayLifecycleOwner
import com.byd.wjx.ui.theme.SuperToyBoxTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CustomStatusBarController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderState = MutableStateFlow(CustomStatusBarRenderState())
    private val _replacementReady = MutableStateFlow(false)
    val replacementReady: StateFlow<Boolean> = _replacementReady.asStateFlow()

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var permissionWarningShown = false

    fun render(state: CustomStatusBarRenderState) {
        mainHandler.post {
            renderState.value = state
            val visibility = CustomStatusBarVisibility.evaluate(
                config = state.config,
                overlayPermissionGranted = Settings.canDrawOverlays(appContext),
                cameraActive = state.cameraActive,
            )
            if (!visibility.show) {
                if (visibility.reason == CustomStatusBarVisibilityReason.OVERLAY_PERMISSION_MISSING && !permissionWarningShown) {
                    WjxFileLogger.w(TAG, "overlay permission missing; custom status bar hidden")
                    permissionWarningShown = true
                }
                if (visibility.reason != CustomStatusBarVisibilityReason.OVERLAY_PERMISSION_MISSING) {
                    permissionWarningShown = false
                }
                _replacementReady.value = visibility.reason == CustomStatusBarVisibilityReason.CAMERA_ACTIVE
                removeView(animated = visibility.reason == CustomStatusBarVisibilityReason.CAMERA_ACTIVE)
                return@post
            }
            permissionWarningShown = false
            ensureView(state.config)
            if (composeView == null) {
                _replacementReady.value = false
                return@post
            }
            updateWindow(state.config)
            composeView?.let(::showView)
            _replacementReady.value = true
        }
    }

    fun stop() {
        mainHandler.post {
            _replacementReady.value = false
            removeView(animated = false)
        }
    }

    private fun ensureView(config: CustomStatusBarConfig) {
        if (composeView != null) return
        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        val view = ComposeView(appContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            alpha = 0f
            translationY = -config.heightDp.dpToPx() * ENTRY_TRANSLATION_RATIO
            setContent {
                val state by renderState.collectAsState()
                SuperToyBoxTheme(themeMode = state.themeMode) {
                    CustomStatusBarContent(state)
                }
            }
        }
        val params = createLayoutParams(config)
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                composeView = view
                lifecycleOwner = owner
                layoutParams = params
                WjxFileLogger.i(TAG, "overlay added")
            }
            .onFailure { error ->
                owner.onDestroy()
                WjxFileLogger.e(TAG, "add overlay failed: ${error.message}", error)
            }
    }

    private fun updateWindow(config: CustomStatusBarConfig) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        val desiredHeight = config.heightDp.dpToPx()
        if (params.height == desiredHeight) return
        params.height = desiredHeight
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { error -> WjxFileLogger.w(TAG, "update overlay failed: ${error.message}", error) }
    }

    private fun createLayoutParams(config: CustomStatusBarConfig): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            config.heightDp.dpToPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

    private fun windowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun showView(view: View) {
        view.visibility = View.VISIBLE
        if (view.alpha >= 1f && view.translationY == 0f) return
        view.animate().cancel()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTRY_DURATION_MS)
            .start()
    }

    private fun removeView(animated: Boolean) {
        val view = composeView ?: return
        view.animate().cancel()
        if (animated && view.isAttachedToWindow && view.alpha > 0f) {
            view.animate()
                .alpha(0f)
                .translationY(-view.height * EXIT_TRANSLATION_RATIO)
                .setDuration(EXIT_DURATION_MS)
                .withEndAction { detachView(view) }
                .start()
            return
        }
        detachView(view)
    }

    private fun detachView(view: View) {
        if (composeView !== view) return
        runCatching { windowManager.removeViewImmediate(view) }
        composeView = null
        layoutParams = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun Float.dpToPx(): Int = (this * appContext.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "CustomStatusBar"
        const val ENTRY_DURATION_MS = 260L
        const val EXIT_DURATION_MS = 160L
        const val ENTRY_TRANSLATION_RATIO = 0.42f
        const val EXIT_TRANSLATION_RATIO = 0.35f
    }
}
