package com.byd.wjx.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.hypot

object OverlayDragRules {
    fun exceededSlop(dx: Float, dy: Float, slopPx: Float): Boolean = hypot(dx, dy) > slopPx
}

/**
 * Shared overlay gesture owner. Child Compose controls receive normal taps; once the finger moves
 * beyond 8dp, the container intercepts the gesture and starts dragging.
 */
class DraggableOverlayContainer(context: Context) : FrameLayout(context) {
    interface Callbacks {
        fun onDragStart(rawX: Float, rawY: Float)
        fun onDrag(rawX: Float, rawY: Float)
        fun onDragEnd(cancelled: Boolean)
    }

    var dragCallbacks: Callbacks? = null
    private val dragSlopPx = 8f * resources.displayMetrics.density
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && OverlayDragRules.exceededSlop(
                        event.rawX - downRawX,
                        event.rawY - downRawY,
                        dragSlopPx,
                    )
                ) {
                    dragging = true
                    dragCallbacks?.onDragStart(downRawX, downRawY)
                }
                return dragging
            }
        }
        return false
    }

    // This container only consumes intercepted drags; child Compose views own all tap handling.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> dragCallbacks?.onDrag(event.rawX, event.rawY)
            MotionEvent.ACTION_UP -> {
                dragCallbacks?.onDragEnd(cancelled = false)
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                dragCallbacks?.onDragEnd(cancelled = true)
                dragging = false
            }
        }
        return true
    }
}
