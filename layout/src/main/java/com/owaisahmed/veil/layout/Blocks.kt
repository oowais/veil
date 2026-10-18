package com.owaisahmed.veil.layout

/** One recognised text line with its axis-aligned bounds in screen pixels. */
data class Line(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val width: Float get() = right - left
}

/** A paragraph: lines that belong together, plus the joined text to translate. */
data class Block(val lines: List<Line>) {
    val left: Float = lines.minOf { it.left }
    val top: Float = lines.minOf { it.top }
    val right: Float = lines.maxOf { it.right }
    val bottom: Float = lines.maxOf { it.bottom }
    val text: String = joinLines(lines.map { it.text })
}

/** Joins wrapped lines; `Über-` + `setzung` → `Übersetzung`, otherwise a single space. */
fun joinLines(lines: List<String>): String {
    val sb = StringBuilder()
    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (sb.isEmpty()) { sb.append(line); continue }
        val prevHyphen = sb.length >= 2 && sb.last() == '-' && sb[sb.length - 2].isLetter()
        if (prevHyphen && line.first().isLowerCase()) {
            sb.setLength(sb.length - 1)
            sb.append(line)
        } else {
            sb.append(' ').append(line)
        }
    }
    return sb.toString()
}
