package com.owaisahmed.veil.capture

import android.graphics.Rect

/** One piece of on-screen text with its bounds in screen coordinates. */
data class TextNode(val text: String, val bounds: Rect)

/** Where on-screen text comes from: the accessibility tree now, screenshot + OCR in milestone 2. */
interface TextSource {
    suspend fun capture(): List<TextNode>
}
