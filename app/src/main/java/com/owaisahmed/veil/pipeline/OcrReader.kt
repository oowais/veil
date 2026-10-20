package com.owaisahmed.veil.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.owaisahmed.veil.capture.TextNode
import com.owaisahmed.veil.layout.BlockBuilder
import com.owaisahmed.veil.layout.Line
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.ocr.CharDict
import com.owaisahmed.veil.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Text in pixels (images, canvas apps) from the screenshot: PP-OCRv6 → lines → paragraphs
 * (BlockBuilder). Complements the accessibility text, which is exact but misses images (D-040).
 * The engine is created on first use and kept, like the translator.
 */
class OcrReader(context: Context) {
    /** [found]: new paragraphs; the rest explains what was left out, for the log. */
    data class Read(val found: List<TextNode>, val lines: Int, val inBars: Int, val covered: List<TextNode>)

    private val app = context.applicationContext
    private val store = ModelStore(context)
    private val spec = ModelRegistry.ocrSmall
    private val lock = Mutex()
    private var engine: OcrEngine? = null

    val available: Boolean get() = store.isInstalled(spec)

    suspend fun preload() = lock.withLock { withContext(Dispatchers.IO) { engineLocked() } }

    private fun engineLocked(): OcrEngine = engine ?: OcrEngine(
        store.file(spec, "det.onnx"),
        store.file(spec, "rec.onnx"),
        CharDict.fromYaml(store.file(spec, "rec.yml")),
    ).also { engine = it }

    /**
     * Paragraphs found in [screen], skipping the bars at the top and bottom ([topPx], [bottomPx]:
     * status bar clock and icons are not worth translating) and anything already covered by
     * [known] (accessibility text).
     */
    suspend fun read(screen: Bitmap, known: List<TextNode>, topPx: Int, bottomPx: Int): Read = lock.withLock {
        withContext(Dispatchers.Default) {
            val result = engineLocked().recognize(screen)
            val lines = result.lines
                .filter { it.quad.top >= topPx && it.quad.bottom <= screen.height - bottomPx }
                .map { Line(it.text, it.quad.left, it.quad.top, it.quad.right, it.quad.bottom) }
            val (covered, found) = BlockBuilder(keepLabelsApart = OcrSettings.keepLabelsApart(app)).build(lines)
                .map { TextNode(it.text, Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())) }
                .partition { block -> known.any { covers(it.bounds, block.bounds) } }
            Read(found, result.lines.size, result.lines.size - lines.size, covered)
        }
    }

    suspend fun close() = lock.withLock {
        engine?.close()
        engine = null
    }

    private companion object {
        /** [a] covers [b] when b's center is inside a, or more than half of b lies inside a. */
        fun covers(a: Rect, b: Rect): Boolean {
            if (a.contains(b.centerX(), b.centerY())) return true
            val i = Rect()
            if (!i.setIntersect(a, b)) return false
            return i.width().toLong() * i.height() * 2 > b.width().toLong() * b.height()
        }
    }
}
