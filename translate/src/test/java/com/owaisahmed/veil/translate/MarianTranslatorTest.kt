package com.owaisahmed.veil.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the real OPUS-MT model on desktop ONNX Runtime and compares with a Python reference
 * (onnxruntime + sentencepiece, same greedy loop). Set VEIL_OPUS_MODEL to a folder laid out like
 * the app's model folder (encoder.onnx, decoder.onnx, source.spm, vocab.json); otherwise skipped.
 */
class MarianTranslatorTest {
    private val dir = System.getenv("VEIL_OPUS_MODEL")?.takeIf { it.isNotEmpty() }?.let(::File)

    @Test
    fun matchesPythonReference() = runBlocking {
        assumeTrue(dir != null && File(dir, "encoder.onnx").exists())
        MarianTranslator.load(dir!!).use { tr ->
            for ((de, en) in listOf(
        "Einstellungen" to "Settings",
        "Mit dem Klick auf „Weiter“ bestätigen Sie, dass Sie die Datenschutzerklärung gelesen haben und der Verarbeitung Ihrer Daten zustimmen." to "By clicking on \"Continue\", you confirm that you have read the privacy policy and agree to the processing of your data.",
        "Laut aktueller Prognose steigen die Temperaturen am Wochenende auf über 30 Grad, in höheren Lagen sind vereinzelt Gewitter möglich." to "According to the current forecast, the temperatures rise to over 30 degrees at the weekend, and in higher altitudes, there are some thunderstorms possible.",
        "Bitte bewahren Sie diese Unter- lagen sorgfältig auf, da sie für die Steuererklärung benötigt werden." to "Please keep these documents carefully as they are required for the tax return.",
            )) assertEquals(de, en, tr.translate(de).text)
        }
    }

    @Test
    fun keepsEverySentence() = runBlocking {
        assumeTrue(dir != null && File(dir, "encoder.onnx").exists())
        MarianTranslator.load(dir!!).use { tr ->
            // Whole, the model drops the first question; split into sentences it keeps both.
            val en = tr.translate("Hast du heute Abend Zeit? Wir wollten so gegen acht beim Italiener um die Ecke essen gehen.").text
            assertTrue(en, en.contains("tonight", ignoreCase = true))
        }
    }

    @Test
    fun splitsSentencesButNotAbbreviationsOrNumbers() {
        assertEquals(
            listOf("Das ist z. B. gut.", "Nr. 5 kommt.", "Wirklich?", "„Ja“, sagt er."),
            MarianTranslator.splitSentences("Das ist z. B. gut. Nr. 5 kommt. Wirklich? „Ja“, sagt er."),
        )
    }
}
