package com.owaisahmed.veil.pipeline

import android.content.Context

/** Where the text on screen comes from (D-055). */
enum class TextSource(val title: String, val description: String) {
    BOTH("Both", "App text first, then text in images (needs text recognition)."),
    ACCESSIBILITY("App text only", "Fastest. Misses text inside images and in some apps and games."),
    OCR("Images only", "Reads the screenshot only. For apps whose text Android can't see.");

    companion object {
        private const val PREFS = "pipeline"

        fun selected(context: Context): TextSource =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("text_source", null)
                ?.let { name -> entries.firstOrNull { it.name == name } } ?: BOTH

        fun select(context: Context, source: TextSource) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("text_source", source.name).apply()
    }
}
