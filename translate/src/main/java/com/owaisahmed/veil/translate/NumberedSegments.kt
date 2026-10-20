package com.owaisahmed.veil.translate

/**
 * Several short texts in one Hy-MT2 prompt as numbered lines (`[1] …`), so a screen costs one
 * prompt instead of one per box (D-054). [parse] only accepts an answer with every number exactly
 * once; anything else makes the caller translate the texts one by one.
 */
object NumberedSegments {
    private val numbered = Regex("""^\s*\[(\d+)]\s*(.*)$""")
    private val whitespace = Regex("""\s+""")

    fun prompt(texts: List<String>, targetLanguage: String, fromOcr: Boolean, terms: List<Pair<String, String>> = emptyList()): String =
        LlamaTranslator.glossaryPrefix(terms) + "Translate the following numbered segments into $targetLanguage. " +
            (if (fromOcr) LlamaTranslator.OCR_NOTE else "") +
            "Each segment is separate. Output one line per segment, starting with the same number in " +
            "square brackets, in the same order, with only the translated result and no explanation:\n\n" +
            texts.mapIndexed { i, t -> "[${i + 1}] ${t.replace(whitespace, " ").trim()}" }.joinToString("\n")

    /** The translations in order, or null if the answer doesn't have exactly segments 1..[count]. */
    fun parse(output: String, count: Int): List<String>? {
        val found = arrayOfNulls<StringBuilder>(count)
        var current: StringBuilder? = null
        for (line in output.lines()) {
            val m = numbered.find(line)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                if (n !in 1..count || found[n - 1] != null) return null
                current = StringBuilder(m.groupValues[2].trim()).also { found[n - 1] = it }
            } else if (line.isNotBlank()) {
                // A segment's translation wrapped onto a second line; text before [1] is chatter.
                current?.append(' ')?.append(line.trim()) ?: continue
            }
        }
        if (found.any { it == null || it.isBlank() }) return null
        return found.map { it.toString() }
    }
}
