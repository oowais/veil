package com.owaisahmed.veil.pipeline

import android.content.Context
import com.owaisahmed.veil.overlay.OverlayStyle

/** How the translation boxes look (D-055). */
object Appearance {
    private const val PREFS = "pipeline"

    enum class TextSize(val title: String, val maxSp: Float) { SMALL("Small", 13f), NORMAL("Normal", 16f), LARGE("Large", 20f) }

    fun textSize(context: Context): TextSize =
        prefs(context).getString("text_size", null)?.let { n -> TextSize.entries.firstOrNull { it.name == n } } ?: TextSize.NORMAL

    fun setTextSize(context: Context, size: TextSize) = prefs(context).edit().putString("text_size", size.name).apply()

    /** How strongly the page-coloured layer covers the blurred original, 0.2–0.9. */
    fun opacity(context: Context): Float = prefs(context).getFloat("box_opacity", OverlayStyle.DEFAULT_VEIL_ALPHA)

    fun setOpacity(context: Context, value: Float) = prefs(context).edit().putFloat("box_opacity", value).apply()

    fun style(context: Context) = OverlayStyle(textMaxSp = textSize(context).maxSp, veilAlpha = opacity(context))

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
