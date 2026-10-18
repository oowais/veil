package com.owaisahmed.veil.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockBuilderTest {

    private fun line(text: String, left: Float, top: Float, right: Float, h: Float = 40f) =
        Line(text, left, top, right, top + h)

    @Test
    fun joinsHyphenatedWord() {
        assertEquals("Die Übersetzung läuft", joinLines(listOf("Die Über-", "setzung läuft")))
    }

    @Test
    fun keepsHyphenBeforeCapital() {
        // "Ein- und Ausgang" style compounds keep the hyphen when the next line starts uppercase.
        assertEquals("Ein- Ausgang", joinLines(listOf("Ein-", "Ausgang")))
    }

    @Test
    fun mergesWrappedParagraph() {
        val lines = listOf(
            line("Das ist ein langer Satz, der über", 40f, 100f, 1000f),
            line("mehrere Zeilen geht und hier", 40f, 150f, 950f),
            line("endet.", 40f, 200f, 200f),
        )
        val blocks = BlockBuilder().build(lines)
        assertEquals(1, blocks.size)
        assertEquals("Das ist ein langer Satz, der über mehrere Zeilen geht und hier endet.", blocks[0].text)
    }

    @Test
    fun splitsOnLargeGap() {
        val lines = listOf(
            line("Erster Absatz", 40f, 100f, 600f),
            line("Zweiter Absatz", 40f, 200f, 600f),
        )
        assertEquals(2, BlockBuilder().build(lines).size)
    }

    @Test
    fun keepsColumnsApart() {
        val lines = listOf(
            line("Links eins", 40f, 100f, 480f),
            line("Rechts eins", 600f, 100f, 1040f),
            line("Links zwei", 40f, 150f, 480f),
            line("Rechts zwei", 600f, 150f, 1040f),
        )
        val blocks = BlockBuilder().build(lines)
        assertEquals(listOf("Links eins Links zwei", "Rechts eins Rechts zwei"), blocks.map { it.text })
    }

    @Test
    fun differentSizesStaySeparate() {
        val lines = listOf(
            line("Überschrift", 40f, 100f, 800f, h = 80f),
            line("Fließtext darunter", 40f, 190f, 800f, h = 40f),
        )
        assertEquals(2, BlockBuilder().build(lines).size)
    }
}
