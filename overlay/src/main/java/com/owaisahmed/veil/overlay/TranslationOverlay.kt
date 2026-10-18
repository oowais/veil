package com.owaisahmed.veil.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen overlay window over the app (D-038). [context] must be the accessibility service:
 * TYPE_ACCESSIBILITY_OVERLAY needs no extra permission. Main thread only.
 *
 * - Translate mode ([translating]): each text gets a box with the original blurred behind it
 *   (from [screen]) and the English on top. Tap a box: original ↔ English. Long-press: copy it.
 * - Copy-text mode: boxes are only outlined, the original stays readable. Tap a box: copy it.
 * - Toolbar (draggable, remembers where it was put), like Lens / Pixel screen translation: "German → English", show
 *   original, Copy German, Copy English, Close (copy-text mode: Copy all, Close).
 * - Boxes pulse until their translation arrives.
 * - Tapping outside the boxes or Back closes it ([onClose]).
 */
class TranslationOverlay(
    private val context: Context,
    private val screen: Bitmap?,
    private val translating: Boolean,
    private val onClose: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var status: TextView? = null
    private var toggleButton: TextView? = null
    private val blocks = ArrayList<Block>()
    private var showOriginal = false

    val isShowing: Boolean get() = root != null

    /** One box on screen. [setTranslation] fills it in when the translator is done. */
    inner class Block internal constructor(val original: String, private val text: TextView?, private val box: View) {
        var translation: String? = null
            private set

        val shown: String get() = if (showOriginal || translation == null) original else translation!!

        private val pulse: ObjectAnimator? = text?.let {
            ObjectAnimator.ofFloat(box, View.ALPHA, 1f, 0.55f).apply {
                duration = 700
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }

        fun setTranslation(english: String) {
            translation = english
            pulse?.cancel()
            box.alpha = 1f
            refresh()
        }

        internal fun stop() = pulse?.cancel()

        internal fun refresh() {
            text?.text = if (translation == null && !showOriginal) "…" else shown
        }
    }

    fun show() {
        if (root != null) return
        val layout = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onClose()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        // Taps that miss every box and the toolbar close the overlay.
        layout.setOnClickListener { onClose() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable and touchable: it gets Back and the taps on boxes and toolbar.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Cover the whole screen, bars and cutout included, so view and screen coordinates match.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Veil"
        }
        wm.addView(layout, params)
        root = layout
        addToolbar(layout)
    }

    /** A short line at the top of the screen ("Loading model…"); null removes it. */
    fun setStatus(text: String?) {
        val layout = root ?: return
        if (text == null) {
            status?.let(layout::removeView)
            status = null
            return
        }
        val view = status ?: pill().also { tv ->
            layout.addView(tv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = insets().top + dp(12) })
            status = tv
        }
        view.text = text
        view.bringToFront()
    }

    /** Adds a box over [bounds] (screen coordinates) for [original]. */
    fun addBlock(bounds: Rect, original: String): Block? {
        val layout = root ?: return null
        val origin = IntArray(2).also(layout::getLocationOnScreen)
        val box = FrameLayout(context)
        val block: Block
        if (translating) {
            val avg = screen?.let { averageColor(it, bounds) } ?: if (dark()) 0xFF202124.toInt() else Color.WHITE
            val light = luminance(avg) > 0.5
            screen?.let { box.addView(blurredCrop(it, bounds), FrameLayout.LayoutParams(MATCH, MATCH)) }
            // A veil in the page's own color keeps the blurred original quiet behind the English.
            val blurred = screen != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            box.addView(View(context).apply { setBackgroundColor(withAlpha(avg, if (blurred) VEIL_ALPHA_BLURRED else VEIL_ALPHA_PLAIN)) },
                FrameLayout.LayoutParams(MATCH, MATCH))
            val tv = TextView(context).apply {
                setTextColor(if (light) 0xFF202124.toInt() else 0xFFF1F3F4.toInt())
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(2), 0, dp(2), 0)
                val minPx = sp(6f)
                val maxPx = minOf(sp(TEXT_MAX_SP), bounds.height() * TEXT_MAX_OF_BOX).coerceAtLeast(minPx + 1)
                setAutoSizeTextTypeUniformWithConfiguration(minPx.toInt(), maxPx.toInt(), 1, TypedValue.COMPLEX_UNIT_PX)
            }
            box.addView(tv, FrameLayout.LayoutParams(MATCH, MATCH))
            block = Block(original, tv, box)
            box.setOnClickListener {
                showOriginalFor(block, tv)
            }
            box.setOnLongClickListener { copy(block.shown); true }
            box.clipToOutline = true
            box.outlineProvider = rounded(dp(4).toFloat())
        } else {
            box.background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(0x264285F4)
                setStroke(dp(2), 0xFF4285F4.toInt())
            }
            block = Block(original, null, box)
            box.setOnClickListener { copy(original) }
        }
        block.refresh()
        layout.addView(box, layout.indexOfChild(toolbar), FrameLayout.LayoutParams(bounds.width(), bounds.height()).apply {
            leftMargin = bounds.left - origin[0]
            topMargin = bounds.top - origin[1]
        })
        blocks += block
        return block
    }

    fun dismiss() {
        main.removeCallbacksAndMessages(null)
        blocks.forEach(Block::stop)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        status = null
        toolbar = null
        blocks.clear()
    }

    // --- toolbar ---

    private var toolbar: DragBar? = null

    private fun addToolbar(layout: FrameLayout) {
        val bar = DragBar(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xEE202124.toInt())
            }
            elevation = dp(6).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        // Handle: shows the toolbar can be dragged away from the text.
        bar.addView(View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0x66FFFFFF)
            }
        }, LinearLayout.LayoutParams(dp(32), dp(4)).apply { topMargin = dp(4); bottomMargin = dp(2) })
        fun row(vararg views: View) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            views.forEach { addView(it) }
        }
        // Rows must be WRAP_CONTENT: next to the fixed-width handle, MATCH_PARENT rows don't count toward
        // the bar's width, which then shrinks to the handle and wraps every label letter by letter.
        fun addRow(vararg views: View) = bar.addView(
            row(*views),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        if (translating) {
            val label = TextView(context).apply {
                text = "German → English"
                setTextColor(0xFFBDC1C6.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(16), dp(8), dp(8), dp(8))
            }
            addRow(label, button("Show original") { toggleAll() }.also { toggleButton = it })
            addRow(
                button("Copy German") { copyAll(english = false) },
                button("Copy English") { copyAll(english = true) },
                button("Close") { onClose() },
            )
        } else {
            addRow(button("Copy all") { copyAll(english = false) }, button("Close") { onClose() })
        }
        layout.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = insets().bottom + dp(16) })
        toolbar = bar
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

    private fun toggleAll() {
        showOriginal = !showOriginal
        toggleButton?.text = if (showOriginal) "Show English" else "Show original"
        blocks.forEach(Block::refresh)
    }

    /** Tap on one box: flip just that one for a moment by showing the other text. */
    private fun showOriginalFor(block: Block, tv: TextView) {
        val other = if (tv.text.toString() == block.original) (block.translation ?: block.original) else block.original
        tv.text = other
    }

    /** All boxes in reading order: the German, or the English where it is ready. */
    private fun copyAll(english: Boolean) {
        if (blocks.isEmpty()) return
        copy(blocks.joinToString("\n\n") { if (english) it.translation ?: it.original else it.original })
    }

    private fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Veil", text))
        setStatus("Copied")
        main.postDelayed({ if (status?.text == "Copied") setStatus(null) }, 1500)
    }

    // --- drawing helpers ---

    private fun blurredCrop(screen: Bitmap, bounds: Rect): ImageView {
        val r = Rect(bounds)
        val inside = r.intersect(0, 0, screen.width, screen.height)
        return ImageView(context).apply {
            if (inside && r.width() > 0 && r.height() > 0) setImageBitmap(Bitmap.createBitmap(screen, r.left, r.top, r.width(), r.height()))
            scaleType = ImageView.ScaleType.FIT_XY
            // Light: softens the German behind the English while the page stays recognizable.
            val radius = minOf(bounds.height() * BLUR_OF_BOX, dp(BLUR_MAX_DP).toFloat()).coerceAtLeast(dp(2).toFloat())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            }
        }
    }

    private fun pill() = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xDD202124.toInt())
        }
        elevation = dp(4).toFloat()
        isClickable = true
    }

    private fun insets() = wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.systemBars())

    private fun dark() =
        context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics)

    private companion object {
        const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT

        fun rounded(radius: Float) = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }

        /** Mean color of the box area, sampled on a coarse grid. */
        fun averageColor(bmp: Bitmap, bounds: Rect): Int {
            val r = Rect(bounds)
            if (!r.intersect(0, 0, bmp.width, bmp.height) || r.isEmpty) return Color.WHITE
            var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
            val step = maxOf(1, minOf(r.width(), r.height()) / 8)
            var y = r.top
            while (y < r.bottom) {
                var x = r.left
                while (x < r.right) {
                    val c = bmp.getPixel(x, y)
                    rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++
                    x += step
                }
                y += step
            }
            return Color.rgb((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
        }

        fun luminance(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255

        // Look of translated boxes (tuned on the Pixel 9; D-045).
        private const val BLUR_OF_BOX = 0.15f // blur radius as a share of the box height…
        private const val BLUR_MAX_DP = 8 // …capped at this
        private const val VEIL_ALPHA_BLURRED = 0.45f // page-colored layer over the blurred original
        private const val VEIL_ALPHA_PLAIN = 0.92f // without blur (Android 11) the original must stay hidden
        private const val TEXT_MAX_SP = 16f
        private const val TEXT_MAX_OF_BOX = 0.6f // largest text as a share of the box height

        fun withAlpha(c: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    }
}
