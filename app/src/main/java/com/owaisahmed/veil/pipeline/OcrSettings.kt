package com.owaisahmed.veil.pipeline

import android.content.Context

/** How OCR lines are grouped into boxes (D-052). */
object OcrSettings {
    private const val PREFS = "pipeline"
    private const val KEEP_LABELS_APART = "ocr_keep_labels_apart"

    /** On: short stacked lines (buttons, menu entries, a heading above text) stay separate boxes (D-051). */
    fun keepLabelsApart(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEEP_LABELS_APART, true)

    fun setKeepLabelsApart(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEEP_LABELS_APART, on).apply()
}
