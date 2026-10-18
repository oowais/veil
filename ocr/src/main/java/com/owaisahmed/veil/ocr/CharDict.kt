package com.owaisahmed.veil.ocr

import java.io.File

/**
 * Reads `PostProcess.character_dict` from a PaddleOCR `inference.yml`.
 * Only handles the list shape Paddle writes: `  - 'x'` or `  - x`, single-quoted with '' escapes.
 */
object CharDict {

    fun fromYaml(file: File): List<String> = parse(file.readLines(Charsets.UTF_8))

    fun parse(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { it.trim() == "character_dict:" }
        require(start >= 0) { "character_dict not found" }
        val out = ArrayList<String>()
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("- ") && trimmed != "-") break
            var v = if (trimmed == "-") "" else trimmed.substring(2)
            if (v.length >= 2 && v.startsWith("'") && v.endsWith("'")) {
                v = v.substring(1, v.length - 1).replace("''", "'")
            }
            out += v
        }
        return out
    }
}
