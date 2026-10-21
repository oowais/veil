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
    fun stackedEntriesJoinWhenGuardIsOff() {
        val lines = listOf(
            line("Einstellungen", 40f, 100f, 400f),
            line("Mein Konto", 40f, 150f, 330f),
        )
        assertEquals(listOf("Einstellungen Mein Konto"), BlockBuilder(keepLabelsApart = false).build(lines).map { it.text })
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

    @Test
    fun signParagraphsSplitOnSlightlyWiderGaps() {
        // Lines 50 high, 12 apart inside a paragraph, 34 apart between paragraphs (under 0.8 × 50).
        val lines = listOf(
            line("montags geschlossen", 120f, 100f, 700f, h = 50f),
            line("Bei schlechtem Wetter achten", 80f, 184f, 820f, h = 50f),
            line("Sie bitte auf die Fahne", 120f, 246f, 760f, h = 50f),
            line("Zufahrt nur für Mitarbeiter", 100f, 330f, 800f, h = 50f),
            line("und Anlieferer mit Genehmigung", 80f, 392f, 830f, h = 50f),
            line("Hunde sind auf dem", 140f, 476f, 740f, h = 50f),
            line("Gaststättengelände an der", 110f, 538f, 780f, h = 50f),
            line("Leine zu führen", 200f, 600f, 640f, h = 50f),
        )
        assertEquals(
            listOf(
                "montags geschlossen",
                "Bei schlechtem Wetter achten Sie bitte auf die Fahne",
                "Zufahrt nur für Mitarbeiter und Anlieferer mit Genehmigung",
                "Hunde sind auf dem Gaststättengelände an der Leine zu führen",
            ),
            BlockBuilder().build(lines).map { it.text },
        )
    }
}
