package com.owaisahmed.veil.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class GlossaryTest {
    @Test
    fun parsesLinesAndSkipsBrokenOnes() {
        val text = """
            Sitzung = session
            Anmeldung=registration

            kein Gleichheitszeichen
             = nur Englisch
            Bürgeramt = citizens' office = town hall
        """.trimIndent()
        assertEquals(
            listOf("Sitzung" to "session", "Anmeldung" to "registration", "Bürgeramt" to "citizens' office = town hall"),
            Glossary.parse(text),
        )
    }
}
