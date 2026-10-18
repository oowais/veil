package com.owaisahmed.veil.pipeline

import android.content.Context
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.translate.LlamaTranslator
import com.owaisahmed.veil.translate.MarianTranslator
import com.owaisahmed.veil.translate.Translator

/** The translation engines Veil can use (D-036). OPUS-MT is the default. */
enum class Engine(val label: String, val spec: ModelSpec) {
    OPUS_MT("Fast: OPUS-MT (~110 MB, loads in about a second)", ModelRegistry.opusMtDeEn),
    HY_MT2("Quality: Hy-MT2 1.8B (~1.1 GB, slow to load)", ModelRegistry.hyMt2Q4);

    /** [threads]: generation threads for Hy-MT2 (prompt uses 4, D-033), ONNX threads for OPUS-MT. */
    suspend fun load(context: Context, threads: Int): Translator {
        val store = ModelStore(context)
        return when (this) {
            OPUS_MT -> MarianTranslator.load(store.dir(spec), threads)
            HY_MT2 -> LlamaTranslator.load(context, store.file(spec, "model.gguf"), threads = threads, threadsBatch = 4)
        }
    }

    companion object {
        private const val PREFS = "pipeline"

        fun selected(context: Context): Engine =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("engine", null)
                ?.let { name -> entries.firstOrNull { it.name == name } } ?: OPUS_MT

        fun select(context: Context, engine: Engine) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("engine", engine.name).apply()
    }
}
