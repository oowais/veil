package com.owaisahmed.veil.pipeline

import android.content.Context
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.translate.LlamaTranslator
import com.owaisahmed.veil.translate.MarianTranslator
import com.owaisahmed.veil.translate.Translator
import java.io.File

/** The translation engines Veil can use (D-036). OPUS-MT is the default. */
enum class Engine(val title: String, val detail: String, val spec: ModelSpec) {
    OPUS_MT("Fast: OPUS-MT", "loads in about a second", ModelRegistry.opusMtDeEn),
    HY_MT2("Quality: Hy-MT2 1.8B", "better wording, slow to load, ~2.5 GB RAM", ModelRegistry.hyMt2Q4);

    /**
     * [threads]: generation threads for Hy-MT2 (prompt uses 4, D-033), ONNX threads for OPUS-MT.
     * [log] gets the load steps (OPUS-MT) so a slow or stuck step is visible.
     */
    suspend fun load(context: Context, threads: Int, log: (String) -> Unit = {}): Translator {
        val store = ModelStore(context)
        return when (this) {
            OPUS_MT -> MarianTranslator.load(store.dir(spec), threads, File(context.cacheDir, "ort/${spec.id}"), log)
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
