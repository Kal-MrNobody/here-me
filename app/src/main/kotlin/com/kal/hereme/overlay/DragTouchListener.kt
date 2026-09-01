package com.kal.hereme.overlay

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Separates "I am moving the bubble" from "I tapped the bubble". Without the slop check
 * every drag ends in an accidental tap, because a finger never leaves a screen cleanly.
 */
class DragTouchListener(
    view: View,
    private val onDragStart: () -> Unit,
    private val onDragTo: (dx: Int, dy: Int) -> Unit,
    private val onDragFinished: () -> Unit,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragging = false
    private var longPressFired = false

    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressFired = true
            onLongPress()
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downAt = System.currentTimeMillis()
                dragging = false
                longPressFired = false
                view.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY

                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    view.removeCallbacks(longPressRunnable)
                    onDragStart()
                }
                if (dragging) {
                    onDragTo(dx.toInt(), dy.toInt())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.removeCallbacks(longPressRunnable)
                when {
                    dragging -> onDragFinished()
                    longPressFired -> Unit
                    System.currentTimeMillis() - downAt < longPressTimeout -> onTap()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                view.removeCallbacks(longPressRunnable)
                if (dragging) onDragFinished()
                return true
            }
        }
        return false
    }
}
