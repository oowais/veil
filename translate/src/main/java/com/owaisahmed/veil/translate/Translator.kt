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

    /**
     * Translates all [texts] (e.g. every box on a screen), calling [onEach] with each text's index as
     * soon as it is done, in no particular order. Engines that can batch override this; the default
     * goes one by one.
     */
    suspend fun translateAll(
        texts: List<String>,
        targetLanguage: String = "English",
        onEach: suspend (index: Int, Translation) -> Unit,
    ) {
        texts.forEachIndexed { i, t -> onEach(i, translate(t, targetLanguage)) }
    }
}
