package com.owaisahmed.veil.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanFilterTest {
    private fun select(vararg texts: String) = GermanFilter.select(texts.toList()) { it }

    @Test
    fun germanScreenKeepsShortLabelsAndDropsEnglishAndNumbers() {
        val picked = select(
            "Einstellungen",
            "Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an.",
            "16:50",
            "Sign in with Google to continue",
            "Bahnersatzverkehr",
        )
        assertEquals(
            listOf("Einstellungen", "Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an.", "Bahnersatzverkehr"),
            picked,
        )
    }

    @Test
    fun englishScreenSelectsNothing() {
        assertTrue(select("Settings", "Your session has expired. Please sign in again.", "Bahnhof").isEmpty())
    }

    @Test
    fun umlautsCountAsGerman() {
        assertTrue(GermanFilter.score("Größe") > 0)
        assertTrue(GermanFilter.score("Please open the settings") < 0)
    }
}
