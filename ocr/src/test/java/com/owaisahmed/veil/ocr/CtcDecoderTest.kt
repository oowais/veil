package com.owaisahmed.veil.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class CtcDecoderTest {

    private val charset = listOf("Ü", "b", "e", "r")

    private fun oneHot(vararg classes: Int): FloatArray {
        val n = charset.size + 1
        val out = FloatArray(classes.size * n)
        classes.forEachIndexed { t, c -> out[t * n + c] = 0.9f }
        return out
    }

    @Test
    fun collapsesRepeatsAndDropsBlanks() {
        // Ü Ü _ b e e _ r  → "Über"
        val probs = oneHot(1, 1, 0, 2, 3, 3, 0, 4)
        val r = CtcDecoder(charset).decode(probs, 8, charset.size + 1)
        assertEquals("Über", r.text)
        assertEquals(0.9f, r.score, 1e-6f)
    }

    @Test
    fun blankSeparatesDoubleLetters() {
        // e _ e → "ee"
        val probs = oneHot(3, 0, 3)
        assertEquals("ee", CtcDecoder(charset).decode(probs, 3, charset.size + 1).text)
    }

    @Test
    fun respectsOffsetForBatchedOutput() {
        val first = oneHot(2, 0)
        val second = oneHot(4, 3)
        val probs = first + second
        assertEquals("re", CtcDecoder(charset).decode(probs, 2, charset.size + 1, offset = first.size).text)
    }
}
