package com.owaisahmed.veil.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Groups OCR lines into paragraphs. Line b joins the block whose last line is a when:
 * - the vertical gap is at most [maxGapRatio] × median line height,
 * - their heights are within [heightTolerance],
 * - they overlap horizontally by at least half the narrower line, or share a left edge (±1 line height).
 * A line ending a sentence followed by a larger-than-usual gap starts a new block.
 */
class BlockBuilder(
    private val maxGapRatio: Float = 0.8f,
    private val heightTolerance: Float = 0.25f,
    private val minOverlap: Float = 0.5f,
) {

    fun build(input: List<Line>): List<Block> {
        val lines = input.filter { it.text.isNotBlank() && it.height > 0 }
            .sortedWith(compareBy<Line> { it.top }.thenBy { it.left })
        if (lines.isEmpty()) return emptyList()
        val medianH = lines.map { it.height }.sorted()[lines.size / 2]

        val open = ArrayList<MutableList<Line>>()
        for (line in lines) {
            val target = open
                .filter { canJoin(it.last(), line, medianH) }
                .minByOrNull { line.top - it.last().bottom }
            if (target != null) target += line else open += mutableListOf(line)
        }
        return open.map { Block(it) }.sortedWith(compareBy<Block> { it.top }.thenBy { it.left })
    }

    private fun canJoin(a: Line, b: Line, medianH: Float): Boolean {
        val gap = b.top - a.bottom
        if (gap > maxGapRatio * medianH || gap < -0.5f * min(a.height, b.height)) return false

        val hRatio = max(a.height, b.height) / min(a.height, b.height)
        if (hRatio > 1 + heightTolerance) return false

        val overlap = min(a.right, b.right) - max(a.left, b.left)
        val sameLeft = abs(a.left - b.left) <= max(a.height, b.height)
        if (overlap < minOverlap * min(a.width, b.width) && !sameLeft) return false

        // Sentence end + noticeably larger gap than tight line spacing → likely a new paragraph.
        val endsSentence = a.text.trimEnd().lastOrNull()?.let { it in ".!?:" } == true
        if (endsSentence && gap > 0.5f * medianH) return false

        return true
    }
}
