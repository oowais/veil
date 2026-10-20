package com.owaisahmed.veil.translate

/** Sentence splitting for German text, shared by the engines. */
object Sentences {
    // A sentence ends at . ! ? followed by space and an upper-case letter or opening quote, so
    // lower case ("z. B. die") and digits ("Nr. 5") don't split.
    private val sentenceEnd = Regex("""(?<=[.!?…])\s+(?=[\p{Lu}„"«])""")
    /** "z." in "z. B.": a single letter before the period is an abbreviation, not a sentence end. */
    private val abbreviationEnd = Regex("""(^|\s)\p{L}\.$""")

    fun split(text: String): List<String> {
        val out = ArrayList<String>()
        for (part in text.split(sentenceEnd).map(String::trim).filter(String::isNotEmpty)) {
            if (out.isNotEmpty() && abbreviationEnd.containsMatchIn(out.last())) out[out.size - 1] += " $part"
            else out += part
        }
        return out
    }

    /**
     * Whole sentences packed into pieces of at most [maxChars] (a single longer sentence is cut at
     * the last space before the limit), so a long text fits the model's context.
     */
    fun chunks(text: String, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (sentence in split(text)) {
            for (piece in cut(sentence, maxChars)) {
                if (cur.isNotEmpty() && cur.length + 1 + piece.length > maxChars) {
                    out += cur.toString()
                    cur.setLength(0)
                }
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(piece)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun cut(sentence: String, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        var rest = sentence
        while (rest.length > maxChars) {
            val at = rest.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
            out += rest.substring(0, at).trim()
            rest = rest.substring(at).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}
