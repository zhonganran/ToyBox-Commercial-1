package com.byd.wjx.cluster

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ClusterProjectionOverlayHandle internal constructor(internal val view: View)

/** Owns the cluster overlay view, its Surface lifecycle, and make-before-break view replacement. */
internal class ClusterProjectionOverlayController {
    private val intentionalRemovals = mutableSetOf<View>()

    @SuppressLint("StaticFieldLeak")
    private var currentHandle: ClusterProjectionOverlayHandle? = null

    val current: ClusterProjectionOverlayHandle?
        get() = currentHandle

    val hasOverlay: Boolean
        get() = currentHandle != null

    suspend fun addAndAwaitSurface(
        context: Context,
        display: Display,
        plan: ProjectionSurfacePlan,
        onUnexpectedSurfaceDestroyed: () -> Unit,
    ): Surface {
        val ready = CompletableDeferred<Surface>()
        withContext(Dispatchers.Main) {
            val displayContext = context.createDisplayContext(display)
            val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val container = FrameLayout(displayContext).apply {
                clipChildren = true
                clipToPadding = true
            }
            val handle = ClusterProjectionOverlayHandle(container)
            val surfaceView = SurfaceView(displayContext)
            val surfaceParams = FrameLayout.LayoutParams(
                plan.surfaceWidth,
                plan.surfaceHeight,
                Gravity.TOP or Gravity.START,
            ).apply {
                leftMargin = plan.surfaceX
                topMargin = plan.surfaceY
            }
            container.addView(surfaceView, surfaceParams)

            surfaceView.holder.setFixedSize(plan.render.bufferWidth, plan.render.bufferHeight)
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    WjxFileLogger.d(
                        TAG,
                        "surfaceCreated buffer ${plan.render.bufferWidth}x${plan.render.bufferHeight} " +
                            "window ${plan.geo.width}x${plan.geo.height} " +
                            "surface ${plan.surfaceWidth}x${plan.surfaceHeight}+${plan.surfaceX},${plan.surfaceY}",
                    )
                    if (!ready.isCompleted) ready.complete(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    WjxFileLogger.d(TAG, "surfaceDestroyed")
                    val intentional = intentionalRemovals.remove(container)
                    if (!intentional && currentHandle === handle) onUnexpectedSurfaceDestroyed()
                }
            })

            val overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                OVERLAY_TYPE,
                OVERLAY_FLAGS,
                PixelFormat.TRANSLUCENT,
            )
            windowManager.addView(container, overlayParams)
            currentHandle = handle
            ClusterOverlayEpoch.markChanged()
        }
        return ready.await()
    }

    suspend fun discardCurrentAndRestore(previous: ClusterProjectionOverlayHandle) {
        val discarded = currentHandle
        currentHandle = previous
        if (discarded != null && discarded !== previous) remove(discarded)
    }

    suspend fun removeCurrent() {
        currentHandle?.let { remove(it) }
        currentHandle = null
    }

    suspend fun remove(handle: ClusterProjectionOverlayHandle) {
        if (currentHandle === handle) currentHandle = null
        intentionalRemovals.add(handle.view)
        withContext(Dispatchers.Main) {
            try {
                val windowManager = handle.view.context
                    .getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(handle.view)
                ClusterOverlayEpoch.markChanged()
            } catch (error: Exception) {
                intentionalRemovals.remove(handle.view)
                WjxFileLogger.w(TAG, "removeView failed: ${error.message}")
            }
        }
        intentionalRemovals.remove(handle.view)
    }

    private companion object {
        const val TAG = "ClusterProjection"
        const val OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        const val OVERLAY_FLAGS = 264 // FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN
    }
}
