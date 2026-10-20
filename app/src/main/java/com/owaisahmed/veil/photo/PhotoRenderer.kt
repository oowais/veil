package com.owaisahmed.veil.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.owaisahmed.veil.overlay.TranslationOverlay

/**
 * Draws translations into a copy of the photo (D-057), like the overlay draws them on screen: each
 * box gets a cover in the colour around it and the English in a contrasting colour, as large as
 * fits. No blur here; the cover is nearly solid so the German doesn't show through.
 */
object PhotoRenderer {
    private const val COVER_ALPHA = 0.9f

    fun render(photo: Bitmap, boxes: List<Pair<Rect, String?>>): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((bounds, english) in boxes) {
            if (english.isNullOrBlank()) continue
            val pad = (bounds.height() * 0.08f).toInt().coerceAtLeast(2)
            val r = Rect(bounds).apply { inset(-pad, -pad) }
            if (!r.intersect(0, 0, out.width, out.height)) continue
            val avg = TranslationOverlay.averageColor(photo, bounds)
            fill.color = TranslationOverlay.withAlpha(avg, COVER_ALPHA)
            canvas.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), pad.toFloat(), pad.toFloat(), fill)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (TranslationOverlay.luminance(avg) > 0.5) 0xFF202124.toInt() else 0xFFF1F3F4.toInt()
            }
            val layout = fit(english, paint, r.width(), r.height())
            canvas.save()
            canvas.translate(r.left.toFloat(), r.top + (r.height() - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
        return out
    }

    /** The largest text size (binary search) whose wrapped layout fits [w]×[h]; never below 8 px. */
    private fun fit(s: String, paint: TextPaint, w: Int, h: Int): StaticLayout {
        fun layout(size: Float): StaticLayout {
            paint.textSize = size
            return StaticLayout.Builder.obtain(s, 0, s.length, paint, w.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        }
        var lo = 8f
        var hi = h * 0.8f
        if (hi <= lo) return layout(lo)
        repeat(12) {
            val mid = (lo + hi) / 2
            val l = layout(mid)
            // A word wider than the box would be broken mid-word; treat that as too big too.
            if (l.height <= h && (0 until l.lineCount).all { i -> l.getLineWidth(i) <= w }) lo = mid else hi = mid
        }
        return layout(lo)
    }
}
