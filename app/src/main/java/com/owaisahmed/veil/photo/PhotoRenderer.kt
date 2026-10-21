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
 * box gets a cover in the colour around it and the English in a contrasting colour. All covers go
 * down before any text, so a neighbouring box can't hide text that runs to its edge (D-061). The
 * cover is fully solid (no blur in a saved picture, and any see-through shows the German), and
 * the English is at most the size of the original lines, smaller if it needs more room.
 */
object PhotoRenderer {
    private const val COVER_ALPHA = 1f
    /** Text size as a share of the original line height (a line box is taller than its letters). */
    private const val TEXT_OF_LINE = 0.75f

    /** [lineHeight]: height of one original line in photo px, 0 if unknown. */
    data class Box(val bounds: Rect, val english: String?, val lineHeight: Int)

    fun render(photo: Bitmap, boxes: List<Box>): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val shown = boxes.filter { !it.english.isNullOrBlank() }
        val areas = shown.map { box ->
            val pad = (box.bounds.height() * 0.04f).toInt().coerceIn(2, 12)
            val r = Rect(box.bounds).apply { inset(-pad, -pad) }
            r.intersect(0, 0, out.width, out.height)
            val avg = TranslationOverlay.averageColor(photo, box.bounds)
            Triple(r, avg, pad)
        }
        for ((r, avg, pad) in areas) {
            if (r.isEmpty) continue
            fill.color = TranslationOverlay.withAlpha(avg, COVER_ALPHA)
            canvas.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), pad.toFloat(), pad.toFloat(), fill)
        }
        shown.forEachIndexed { i, box ->
            val (r, avg, pad) = areas[i]
            if (r.isEmpty) return@forEachIndexed
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (TranslationOverlay.luminance(avg) > 0.5) 0xFF202124.toInt() else 0xFFF1F3F4.toInt()
            }
            val maxSize = if (box.lineHeight > 0) box.lineHeight * TEXT_OF_LINE else r.height() * 0.6f
            val layout = fit(box.english!!, paint, r.width() - 2 * pad, r.height(), maxSize)
            canvas.save()
            canvas.clipRect(r)
            canvas.translate(r.left + pad.toFloat(), r.top + (r.height() - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
        return out
    }

    /** The largest text size up to [maxSize] (binary search) whose wrapped layout fits [w]×[h]; never below 8 px. */
    private fun fit(s: String, paint: TextPaint, w: Int, h: Int, maxSize: Float): StaticLayout {
        fun layout(size: Float): StaticLayout {
            paint.textSize = size
            return StaticLayout.Builder.obtain(s, 0, s.length, paint, w.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        }
        fun fits(l: StaticLayout) = l.height <= h && (0 until l.lineCount).all { i -> l.getLineWidth(i) <= w }
        var lo = 8f
        var hi = maxSize
        if (hi <= lo) return layout(lo)
        if (fits(layout(hi))) return layout(hi)
        repeat(12) {
            val mid = (lo + hi) / 2
            // A word wider than the box would be broken mid-word; treat that as too big too.
            if (fits(layout(mid))) lo = mid else hi = mid
        }
        return layout(lo)
    }
}
