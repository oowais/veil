package com.owaisahmed.veil.pipeline

import android.content.Context

/**
 * Your own translations for terms, as "German = English" lines (D-055). Only Hy-MT2 uses them;
 * OPUS-MT can't take instructions.
 */
object Glossary {
    private const val PREFS = "pipeline"

    fun text(context: Context): String = prefs(context).getString("glossary", "") ?: ""

    fun setText(context: Context, text: String) = prefs(context).edit().putString("glossary", text).apply()

    fun terms(context: Context): List<Pair<String, String>> = parse(text(context))

    /** Lines "German = English"; blank lines, lines without "=" or with an empty side are skipped. */
    fun parse(text: String): List<Pair<String, String>> = text.lines().mapNotNull { line ->
        val i = line.indexOf('=')
        if (i < 0) return@mapNotNull null
        val de = line.substring(0, i).trim()
        val en = line.substring(i + 1).trim()
        if (de.isEmpty() || en.isEmpty()) null else de to en
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
