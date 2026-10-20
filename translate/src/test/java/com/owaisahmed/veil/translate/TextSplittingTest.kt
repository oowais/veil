package com.owaisahmed.veil.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSplittingTest {

    @Test
    fun chunksKeepWholeSentences() {
        val text = "Erster Satz ist hier. Zweiter Satz folgt jetzt. Dritter Satz endet."
        assertEquals(listOf("Erster Satz ist hier.", "Zweiter Satz folgt jetzt.", "Dritter Satz endet."), Sentences.chunks(text, 30))
        assertEquals(listOf(text), Sentences.chunks(text, 500))
    }

    @Test
    fun overlongSentenceIsCutAtSpaces() {
        val text = (1..50).joinToString(" ") { "Wort$it" }
        val chunks = Sentences.chunks(text, 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun parsesNumberedAnswer() {
        assertEquals(
            listOf("Settings", "My account is here and continues", "Help"),
            NumberedSegments.parse("[1] Settings\n[2] My account is here\nand continues\n\n[3] Help", 3),
        )
    }

    @Test
    fun ignoresChatterBeforeFirstSegment() {
        assertEquals(listOf("A", "B"), NumberedSegments.parse("Here you go:\n[1] A\n[2] B", 2))
    }

    @Test
    fun rejectsMissingDuplicateOrExtraSegments() {
        assertNull(NumberedSegments.parse("[1] A", 2))
        assertNull(NumberedSegments.parse("[1] A\n[1] B", 2))
        assertNull(NumberedSegments.parse("[1] A\n[2] B\n[3] C", 2))
        assertNull(NumberedSegments.parse("[1] A\n[2]", 2))
    }

    @Test
    fun promptNumbersSegmentsOnOneLineEach() {
        val p = NumberedSegments.prompt(listOf("Hallo\nWelt", "Tschüss"), "English", fromOcr = false)
        assertTrue(p.endsWith("[1] Hallo Welt\n[2] Tschüss"))
    }

    @Test
    fun groupsShortTextsAndKeepsLongOnesAlone() {
        val long = "x".repeat(400)
        val texts = listOf("a", "b", long, "c") + List(9) { "d" }
        assertEquals(
            listOf(listOf(0, 1, 3, 4, 5, 6, 7, 8), listOf(2), listOf(9, 10, 11, 12)),
            LlamaTranslator.groups(texts),
        )
    }

    @Test
    fun glossaryTermsGoFirstInBothPrompts() {
        val terms = listOf("Sitzung" to "session")
        val single = LlamaTranslator.prompt("Die Sitzung ist abgelaufen.", "English", terms = terms)
        val head = "Refer to the translation below:\nSitzung is translated as session\n\nTranslate"
        assertTrue(single.startsWith(head))
        val batch = NumberedSegments.prompt(listOf("Sitzung", "Hilfe"), "English", false, terms)
        assertTrue(batch.startsWith(head))
        assertTrue(LlamaTranslator.prompt("Hilfe", "English").startsWith("Translate"))
    }
}
