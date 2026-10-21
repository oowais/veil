package com.owaisahmed.veil.capture

import android.graphics.Rect

/** One piece of on-screen text with its bounds in screen coordinates. */
/** [lineHeight]: height of one line of the original in px, when known (OCR, D-061); 0 otherwise. */
data class TextNode(val text: String, val bounds: Rect, val lineHeight: Int = 0)

/** Where on-screen text comes from: the accessibility tree now, screenshot + OCR in milestone 2. */
interface TextSource {
    suspend fun capture(): List<TextNode>
}
