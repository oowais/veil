package com.owaisahmed.veil.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class CharDictTest {

    @Test
    fun parsesPaddleYamlList() {
        val yml = """
            PostProcess:
              name: CTCLabelDecode
              character_dict:
              - '!'
              - ''''
              - \
              - ä
              - ß
            Other: 1
        """.trimIndent().lines()
        assertEquals(listOf("!", "'", "\\", "ä", "ß"), CharDict.parse(yml))
    }
}
