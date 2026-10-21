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
 *
 * UI guard: short isolated lines (buttons, tabs, menu entries) stay their own blocks. Unless b
 * clearly continues a (a ends with `-` or `,`, or b starts lowercase), they don't join when
 * - both are short labels (at most [labelWords] words), e.g. stacked menu entries, or
 * - a is much narrower than b, so a didn't wrap (a heading or label above body text).
 * [keepLabelsApart] off turns the guard off (a user setting, D-052).
 *
 * Gap guard (D-061): paragraphs on signs and forms are often only a little further apart than
 * their lines. A gap clearly wider than the typical line gap of the whole image, or than the gap
 * already inside the block, starts a new block.
 */
class BlockBuilder(
    private val maxGapRatio: Float = 0.8f,
    private val heightTolerance: Float = 0.25f,
    private val minOverlap: Float = 0.5f,
    private val labelWords: Int = 3,
    private val wrapRatio: Float = 0.6f,
    private val keepLabelsApart: Boolean = true,
) {

    fun build(input: List<Line>): List<Block> {
        val lines = input.filter { it.text.isNotBlank() && it.height > 0 }
            .sortedWith(compareBy<Line> { it.top }.thenBy { it.left })
        if (lines.isEmpty()) return emptyList()
        val medianH = lines.map { it.height }.sorted()[lines.size / 2]
        val typicalGap = typicalGap(lines, medianH)

        val open = ArrayList<MutableList<Line>>()
        for (line in lines) {
            val target = open
                .filter { canJoin(it.last(), line, medianH) && !gapTooWide(it, line, typicalGap, medianH) }
                .minByOrNull { line.top - it.last().bottom }
            if (target != null) target += line else open += mutableListOf(line)
        }
        return open.map { Block(it) }.sortedWith(compareBy<Block> { it.top }.thenBy { it.left })
    }

    /**
     * The median gap between each line and the nearest line below it that it could join (overlapping
     * columns, gap within [maxGapRatio]); null with fewer than 3 such pairs.
     */
    private fun typicalGap(lines: List<Line>, medianH: Float): Float? {
        val gaps = lines.mapNotNull { a ->
            lines.asSequence()
                .filter { b -> b !== a && b.top >= a.top + a.height / 2 }
                .filter { b -> min(a.right, b.right) - max(a.left, b.left) >= minOverlap * min(a.width, b.width) }
                .map { b -> b.top - a.bottom }
                .filter { g -> g > -0.5f * medianH && g <= maxGapRatio * medianH }
                .minOrNull()
        }
        return if (gaps.size < 3) null else gaps.sorted()[gaps.size / 2]
    }

    /** True when [b] sits clearly further below [block] than lines usually do (D-061). */
    private fun gapTooWide(block: List<Line>, b: Line, typicalGap: Float?, medianH: Float): Boolean {
        val gap = b.top - block.last().bottom
        val slack = WIDE_GAP_SLACK * medianH
        if (typicalGap != null && gap > WIDE_GAP_FACTOR * max(typicalGap, 0f) + slack) return true
        if (block.size >= 2) {
            val inside = block.last().top - block[block.size - 2].bottom
            if (gap > WIDE_GAP_FACTOR * max(inside, 0f) + slack) return true
        }
        return false
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

        val continues = a.text.trimEnd().lastOrNull()?.let { it == '-' || it == ',' } == true ||
            b.text.trimStart().firstOrNull()?.isLowerCase() == true ||
            a.text.trim().substringAfterLast(' ') in CONTINUING_WORDS
        if (keepLabelsApart && !continues) {
            if (isLabel(a) && isLabel(b)) return false
            if (a.width < wrapRatio * b.width) return false
        }

        return true
    }

    private val whitespace = Regex("\\s+")

    private fun isLabel(l: Line) = l.text.trim().split(whitespace).size <= labelWords

    private companion object {
        /** A line ending in one of these (articles, prepositions, conjunctions) goes on below. */
        val CONTINUING_WORDS = setOf(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer",
            "und", "oder", "aber", "an", "auf", "in", "im", "mit", "zu", "zum", "zur", "für", "von",
            "vom", "bei", "nach", "aus", "über", "unter", "vor", "um", "am", "als", "wie", "dass",
        )
        const val WIDE_GAP_FACTOR = 1.6f
        /** Allowance in line heights, so tiny typical gaps (lines almost touching) don't split. */
        const val WIDE_GAP_SLACK = 0.15f
    }
}
