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
    fun cachedOptimizedGraphsTranslateTheSame() = runBlocking {
        assumeTrue(dir != null && File(dir, "encoder.onnx").exists())
        val cache = kotlin.io.path.createTempDirectory("veil-ort").toFile()
        try {
            val de = "Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an, um fortzufahren."
            val first = MarianTranslator.load(dir!!, cacheDir = cache).use { it.translate(de).text }
            assertTrue(File(cache, "decoder.opt.onnx").exists())
            val lines = ArrayList<String>()
            val second = MarianTranslator.load(dir, cacheDir = cache, log = { lines += it }).use { it.translate(de).text }
            assertTrue(lines.toString(), lines.any { "decoder: optimized graph from cache" in it })
            assertEquals(first, second)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun batchTranslatesEveryText() = runBlocking {
        assumeTrue(dir != null && File(dir, "encoder.onnx").exists())
        // Short and long sentences mixed, so rows are padded and finish at different steps; more than
        // one batch (8 sentences each).
        val texts = listOf(
            "Einstellungen",
            "Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an, um fortzufahren.",
            "Hast du heute Abend Zeit? Wir wollten so gegen acht beim Italiener um die Ecke essen gehen.",
            "Termine reservieren",
            "Mit dem Klick auf „Weiter“ bestätigen Sie, dass Sie die Datenschutzerklärung gelesen haben und der Verarbeitung Ihrer Daten zustimmen.",
            "Achtung: Der Zug nach München Hauptbahnhof fährt heute ausnahmsweise von Gleis 7 ab. Wir bitten um Ihr Verständnis.",
            "Stellenangebote",
            "Die Akku-Optimierung kann dazu führen, dass Benachrichtigungen verzögert zugestellt werden. Möchten Sie die App von der Optimierung ausnehmen?",
            "Neue Beschilderung lenkt Verkehr über Hauptrouten",
        )
        MarianTranslator.load(dir!!).use { tr ->
            val batched = arrayOfNulls<String>(texts.size)
            var calls = 0
            tr.translateAll(texts) { i, r -> batched[i] = r.text; calls++ }
            // Exact words can differ from one-by-one: the int8 model quantizes activations per batch
            // (D-048). Every text must come back once, translated, with every sentence kept.
            assertEquals(texts.size, calls)
            batched.forEachIndexed { i, en -> assertTrue("$i: $en", !en.isNullOrBlank() && en != texts[i]) }
            assertTrue(batched[2], batched[2]!!.contains("tonight", ignoreCase = true))
            assertTrue(batched[5], batched[5]!!.contains("understanding", ignoreCase = true))
        }
    }

    @Test
    fun splitsSentencesButNotAbbreviationsOrNumbers() {
        assertEquals(
            listOf("Das ist z. B. gut.", "Nr. 5 kommt.", "Wirklich?", "„Ja“, sagt er."),
            Sentences.split("Das ist z. B. gut. Nr. 5 kommt. Wirklich? „Ja“, sagt er."),
        )
    }
}
