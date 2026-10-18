package com.owaisahmed.veil.translate

data class TranslationStats(
    val promptTokens: Int,
    val promptMs: Double,
    val generatedTokens: Int,
    val generateMs: Double,
) {
    val promptTokensPerSec: Double get() = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0
    val generateTokensPerSec: Double get() = if (generateMs > 0) generatedTokens * 1000.0 / generateMs else 0.0
}

data class Translation(val text: String, val stats: TranslationStats)

interface Translator : AutoCloseable {
    /** [targetLanguage] is the full English language name, e.g. "English". */
    suspend fun translate(text: String, targetLanguage: String = "English"): Translation
}
