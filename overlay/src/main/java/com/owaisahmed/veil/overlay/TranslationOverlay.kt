package com.owaisahmed.veil.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen, non-touchable overlay window that draws translations over the original text.
 * [context] must be the accessibility service: TYPE_ACCESSIBILITY_OVERLAY needs no extra
 * permission. Main thread only. Milestone 1 rendering: plain boxes in the system light/dark colors,
 * text auto-sized to the box; color sampling, tap-to-toggle and shimmer come in milestone 3.
 */
class TranslationOverlay(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var status: TextView? = null

    val isShowing: Boolean get() = root != null

    private val dark: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun show() {
        if (root != null) return
        val layout = FrameLayout(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Cover the whole screen, bars and cutout included, so view and screen coordinates match.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Veil translation"
        }
        wm.addView(layout, params)
        root = layout
    }

    /** A short line at the top of the screen ("Loading model…"); null removes it. */
    fun setStatus(text: String?) {
        val layout = root ?: return
        if (text == null) {
            status?.let(layout::removeView)
            status = null
            return
        }
        val view = status ?: TextView(context).also { tv ->
            tv.setTextColor(Color.WHITE)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tv.setPadding(dp(14), dp(6), dp(14), dp(6))
            tv.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xDD202124.toInt())
            }
            tv.elevation = dp(4).toFloat()
            layout.addView(tv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(56) })
            status = tv
        }
        view.text = text
        view.bringToFront()
    }

    /** Covers [bounds] (screen coordinates) with [text]. */
    fun addBlock(bounds: Rect, text: String) {
        val layout = root ?: return
        val origin = IntArray(2).also(layout::getLocationOnScreen)
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt())
            setBackgroundColor(if (dark) 0xFF202124.toInt() else Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            val pad = dp(2)
            setPadding(pad, 0, pad, 0)
            setAutoSizeTextTypeUniformWithConfiguration(6, 20, 1, TypedValue.COMPLEX_UNIT_SP)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        layout.addView(tv, FrameLayout.LayoutParams(bounds.width(), bounds.height()).apply {
            leftMargin = bounds.left - origin[0]
            topMargin = bounds.top - origin[1]
        })
        status?.bringToFront()
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        status = null
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
