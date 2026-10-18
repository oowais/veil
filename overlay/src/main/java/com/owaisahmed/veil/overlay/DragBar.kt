package com.owaisahmed.veil.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * A toolbar the user can drag anywhere on screen, so it never sits on top of the text they want
 * to read. Taps still reach the buttons; a move beyond the touch slop becomes a drag. The offset
 * from the default position (bottom center) is remembered across swipes and restarts.
 */
internal class DragBar(context: Context) : LinearLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val prefs = context.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    private var downX = 0f
    private var downY = 0f
    private var startTx = 0f
    private var startTy = 0f
    private var dragging = false

    init {
        // Restored after layout, once the parent's size is known for clamping.
        addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or_: Int, ob: Int) {
                removeOnLayoutChangeListener(this)
                moveTo(prefs.getFloat("dx", 0f), prefs.getFloat("dy", 0f))
            }
        })
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                startTx = translationX; startTy = translationY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> if (!dragging && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                dragging = true
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                startTx = translationX; startTy = translationY
            }
            MotionEvent.ACTION_MOVE -> {
                dragging = true
                moveTo(startTx + ev.rawX - downX, startTy + ev.rawY - downY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) prefs.edit().putFloat("dx", translationX).putFloat("dy", translationY).apply()
                dragging = false
            }
        }
        return true // also swallows taps on the bar's gaps, which must not close the overlay
    }

    /** Moves by an offset from the default spot, kept fully on screen. */
    private fun moveTo(dx: Float, dy: Float) {
        val parent = parent as? View ?: return
        translationX = dx.coerceIn(-left.toFloat(), (parent.width - right).toFloat())
        translationY = dy.coerceIn(-top.toFloat(), (parent.height - bottom).toFloat())
    }
}
