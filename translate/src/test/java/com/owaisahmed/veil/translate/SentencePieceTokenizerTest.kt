package com.owaisahmed.veil.translate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the Kotlin tokenizer against ids from the real SentencePiece library (Python
 * `sentencepiece` + `vocab.json`, Xenova/opus-mt-de-en). Needs the model's `source.spm` and
 * `vocab.json`: set VEIL_OPUS_DIR to their folder, otherwise the test is skipped.
 */
class SentencePieceTokenizerTest {
    private val dir = System.getenv("VEIL_OPUS_DIR")?.takeIf { it.isNotEmpty() }?.let(::File)

    private val expected = listOf(
        "Einstellungen" to intArrayOf(9046, 0),
        "Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an, um fortzufahren." to intArrayOf(289, 4371, 29, 30861, 3, 1199, 10157, 42, 63, 4268, 39, 2, 91, 45080, 3, 0),
        "Sehr geehrte Damen und Herren, anbei erhalten Sie die Rechnung für den Monat September. Bitte überweisen Sie den Betrag innerhalb von 14 Tagen." to intArrayOf(5450, 21556, 18, 7214, 10, 4988, 2, 39, 4824, 701, 42, 11, 3808, 28, 25, 4296, 729, 3, 1199, 81, 9156, 42, 25, 6142, 972, 21, 536, 2108, 3, 0),
        "Die Stadtverwaltung teilt mit, dass die Hauptstraße ab Montag wegen Bauarbeiten für drei Wochen gesperrt wird. Eine Umleitung ist ausgeschildert." to intArrayOf(55, 479, 7003, 13411, 30, 2, 87, 11, 36816, 245, 5632, 1677, 40295, 28, 567, 2326, 34024, 80, 3, 525, 580, 6710, 29, 4353, 20453, 46, 3, 0),
        "Hast du heute Abend Zeit? Wir wollten so gegen acht beim Italiener um die Ecke essen gehen." to intArrayOf(5239, 143, 666, 2801, 296, 31, 182, 7150, 88, 481, 4916, 716, 1755, 45, 91, 11, 14015, 5175, 916, 3, 0),
        "Mit dem Klick auf „Weiter“ bestätigen Sie, dass Sie die Datenschutzerklärung gelesen haben und der Verarbeitung Ihrer Daten zustimmen." to intArrayOf(376, 57, 14294, 37, 198, 30212, 1, 9096, 42, 2, 87, 42, 11, 369, 5862, 9659, 114, 10, 9, 3401, 1144, 369, 22652, 3, 0),
        "Achtung: Der Zug nach München Hauptbahnhof fährt heute ausnahmsweise von Gleis 7 ab. Wir bitten um Ihr Verständnis." to intArrayOf(8273, 16, 119, 5064, 96, 3814, 25941, 15646, 666, 74, 12711, 11499, 21, 33161, 300, 245, 3, 182, 8240, 91, 469, 7318, 3, 0),
        "Die Akku-Optimierung kann dazu führen, dass Benachrichtigungen verzögert zugestellt werden. Möchten Sie die App von der Optimierung ausnehmen?" to intArrayOf(55, 14537, 13, 11610, 9466, 1092, 134, 812, 1535, 2, 87, 17, 23897, 15, 33653, 24, 7624, 61, 3, 9977, 42, 11, 2539, 21, 9, 14402, 74, 8805, 31, 0),
        "Laut aktueller Prognose steigen die Temperaturen am Wochenende auf über 30 Grad, in höheren Lagen sind vereinzelt Gewitter möglich." to intArrayOf(9848, 10284, 45, 28975, 13373, 11, 15033, 121, 8901, 37, 81, 347, 2630, 2, 5, 8672, 933, 34, 70, 258, 33319, 46, 518, 24027, 845, 3, 0),
        "Bitte bewahren Sie diese Unter- lagen sorgfältig auf, da sie für die Steuererklärung benötigt werden." to intArrayOf(1199, 17355, 42, 170, 890, 13, 19886, 9541, 37, 2, 310, 76, 28, 11, 2536, 14198, 6297, 61, 3, 0),
        // Compatibility forms, quotes, emoji, URLs, whitespace, non-Latin.
        "Straße №5 – „Größe“ ½ km² 🙂 www.bonn.de/öffnungszeiten?id=42" to intArrayOf(3766, 200, 724, 112, 198, 37118, 1, 17, 28889, 391, 217, 17, 48810, 9712, 3, 5475, 34, 3, 178, 75, 20129, 26025, 31, 1228, 5864, 3469, 0),
        "ＡＢＣ　Ｚｕｇ…" to intArrayOf(7451, 186, 5064, 98, 0),
        "  Tabs\tund\nZeilen  " to intArrayOf(12209, 6, 10, 21337, 0),
        "Café 東京 Ärger" to intArrayOf(13983, 17, 49456, 49404, 23052, 0),
    )

    @Test
    fun encodesLikeSentencePiece() {
        assumeTrue(dir != null && File(dir, "source.spm").exists())
        val tok = SentencePieceTokenizer.load(File(dir, "source.spm"), File(dir, "vocab.json"))
        for ((text, ids) in expected) assertArrayEquals(text, ids, tok.encode(text))
    }

    @Test
    fun decodesTargetIds() {
        assumeTrue(dir != null && File(dir, "source.spm").exists())
        val tok = SentencePieceTokenizer.load(File(dir, "source.spm"), File(dir, "vocab.json"))
        assertEquals("Your session has expired. Please sign up again to proceed.", tok.decode(intArrayOf(778, 6274, 99, 34885, 3, 1140, 3667, 150, 696, 12, 16172, 3)))
    }

    @Test
    fun normalizesWhitespaceAndCompatibilityForms() {
        assertEquals("a b ﬁ".let { "a b fi" }, SentencePieceTokenizer.normalize("  a\n\tb  ﬁ "))
    }
}
