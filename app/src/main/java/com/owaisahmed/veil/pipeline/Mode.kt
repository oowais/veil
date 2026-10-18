package com.owaisahmed.veil.pipeline

import android.content.Context

/** What the gesture does (D-039). */
enum class Mode(val label: String) {
    TRANSLATE("Translate German to English"),
    COPY_TEXT("Copy text: show the text on screen so you can copy it, no translation");

    companion object {
        private const val PREFS = "pipeline"

        fun selected(context: Context): Mode =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null)
                ?.let { name -> entries.firstOrNull { it.name == name } } ?: TRANSLATE

        fun select(context: Context, mode: Mode) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }
}
