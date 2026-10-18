package com.owaisahmed.veil.capture

/**
 * Decides which on-screen texts are German, without a language model (D-034). Short UI labels
 * ("Einstellungen") carry little evidence alone, so the whole screen is judged first: on a German
 * screen every text with letters is translated unless it is clearly English.
 */
object GermanFilter {

    private val germanWords = setOf(
        "der", "die", "das", "und", "ist", "nicht", "mit", "für", "ein", "eine", "einen", "zu", "von",
        "den", "dem", "des", "auf", "sie", "wir", "ich", "bitte", "sind", "wird", "werden", "auch",
        "oder", "bei", "nach", "aus", "wie", "noch", "nur", "kann", "können", "hier", "jetzt", "über",
        "im", "am", "zum", "zur", "vom", "es", "ihre", "ihr", "ihnen", "sich", "dass", "wenn", "alle",
        "mehr", "weiter", "zurück", "abbrechen", "einstellungen", "suchen", "schließen", "speichern",
        "anmelden", "abmelden", "löschen", "hinzufügen", "teilen", "öffnen",
    )
    private val englishWords = setOf(
        "the", "and", "is", "of", "to", "you", "with", "for", "this", "that", "are", "be", "your",
        "it", "on", "not", "can", "will", "have", "from", "by", "or", "we", "all", "more", "settings",
        "search", "share", "open", "close", "cancel", "save", "delete", "back", "next", "sign",
    )
    private val wordRegex = Regex("""\p{L}+""")

    /** Positive: German evidence, negative: English. */
    fun score(text: String): Int {
        var score = text.count { it in "äöüÄÖÜß" } * 2
        for (m in wordRegex.findAll(text)) {
            val w = m.value.lowercase()
            if (w in germanWords) score += 2
            if (w in englishWords) score -= 2
        }
        return score
    }

    /** The texts to translate: nothing if the screen as a whole isn't German. */
    fun <T> select(items: List<T>, text: (T) -> String): List<T> {
        val withLetters = items.filter { text(it).any(Char::isLetter) }
        if (withLetters.sumOf { score(text(it)) } <= 0) return emptyList()
        return withLetters.filter { score(text(it)) >= 0 }
    }
}
