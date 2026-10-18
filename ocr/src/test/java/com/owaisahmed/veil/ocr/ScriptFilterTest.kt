package com.owaisahmed.veil.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptFilterTest {

    @Test
    fun keepsGermanDigitsAndPunctuation() {
        assertTrue(OcrEngine.isLatinText("Nordbrücken-Sperrung"))
        assertTrue(OcrEngine.isLatinText("Straße, 16:50 € „Weiter“"))
        assertTrue(OcrEngine.isLatinText("800"))
    }

    @Test
    fun dropsCjkMisreads() {
        assertFalse(OcrEngine.isLatinText("『三刀"))
        assertFalse(OcrEngine.isLatinText("月"))
        assertFalse(OcrEngine.isLatinText("Bonn 月"))
    }
}
