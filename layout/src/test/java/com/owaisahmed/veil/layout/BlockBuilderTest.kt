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
            line("Links steht der erste", 40f, 100f, 480f),
            line("Rechts steht der erste", 600f, 100f, 1040f),
            line("Teil der linken Spalte", 40f, 150f, 480f),
            line("Teil der rechten Spalte", 600f, 150f, 1040f),
        )
        val blocks = BlockBuilder().build(lines)
        assertEquals(
            listOf("Links steht der erste Teil der linken Spalte", "Rechts steht der erste Teil der rechten Spalte"),
            blocks.map { it.text },
        )
    }

    @Test
    fun differentSizesStaySeparate() {
        val lines = listOf(
            line("Überschrift", 40f, 100f, 800f, h = 80f),
            line("Fließtext darunter", 40f, 190f, 800f, h = 40f),
        )
        assertEquals(2, BlockBuilder().build(lines).size)
    }

    @Test
    fun stackedMenuEntriesStaySeparate() {
        val lines = listOf(
            line("Einstellungen", 40f, 100f, 400f),
            line("Mein Konto", 40f, 150f, 330f),
            line("Hilfe", 40f, 200f, 180f),
        )
        assertEquals(listOf("Einstellungen", "Mein Konto", "Hilfe"), BlockBuilder().build(lines).map { it.text })
    }

    @Test
    fun labelAboveBodyStaysSeparate() {
        val lines = listOf(
            line("Aktuelles", 40f, 100f, 300f),
            line("Die Stadt informiert über neue Öffnungszeiten", 40f, 150f, 1000f),
            line("der Bürgerämter ab dem ersten Oktober.", 40f, 200f, 900f),
        )
        assertEquals(
            listOf("Aktuelles", "Die Stadt informiert über neue Öffnungszeiten der Bürgerämter ab dem ersten Oktober."),
            BlockBuilder().build(lines).map { it.text },
        )
    }

    @Test
    fun narrowColumnStillJoinsWhenTextContinues() {
        val lines = listOf(
            line("Jetzt die", 40f, 100f, 260f),
            line("neue App", 40f, 150f, 250f),
            line("herunterladen", 40f, 200f, 330f),
        )
        assertEquals(listOf("Jetzt die neue App herunterladen"), BlockBuilder().build(lines).map { it.text })
    }
}
