package com.owaisahmed.veil.pipeline

import android.content.Context
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.translate.BergamotTranslator
import com.owaisahmed.veil.translate.LlamaTranslator
import com.owaisahmed.veil.translate.MarianTranslator
import com.owaisahmed.veil.translate.Translator
import java.io.File

/**
 * The translation engines Veil can use (D-036, D-062). Firefox Translations (Bergamot) is the default
 * for new installs; a phone that already has OPUS-MT and never picked an engine keeps OPUS-MT.
 */
enum class Engine(val title: String, val detail: String, val spec: ModelSpec, val badge: String? = null) {
    // Listed best first (D-066): Models shows them in this order, and when the selected one is gone
    // the first downloaded one takes over.
    BERGAMOT("Firefox Translations", "Mozilla's model: small, fast and accurate", ModelRegistry.bergamotDeEn, badge = "Recommended"),
    HY_MT2("Hy-MT2 1.8B", "most natural wording, follows your glossary; slow to load, ~2.5 GB RAM", ModelRegistry.hyMt2Q4),
    OPUS_MT("OPUS-MT", "older and larger; Firefox Translations is faster and better", ModelRegistry.opusMtDeEn);

    /**
     * [threads]: generation threads for Hy-MT2 (prompt uses 4, D-033), ONNX threads for OPUS-MT.
     * [log] gets the load steps (OPUS-MT) so a slow or stuck step is visible.
     */
    suspend fun load(context: Context, threads: Int, log: (String) -> Unit = {}): Translator {
        val store = ModelStore(context)
        return when (this) {
            OPUS_MT -> MarianTranslator.load(store.dir(spec), threads, File(context.cacheDir, "ort/${spec.id}"), log)
            BERGAMOT -> BergamotTranslator.load(store.dir(spec))
            HY_MT2 -> LlamaTranslator.load(context, store.file(spec, "model.gguf"), threads = threads, threadsBatch = 4, log = log)
        }
    }

    companion object {
        private const val PREFS = "pipeline"

        fun selected(context: Context): Engine =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("engine", null)
                ?.let { name -> entries.firstOrNull { it.name == name } }
                ?: if (ModelStore(context).isInstalled(OPUS_MT.spec)) OPUS_MT else BERGAMOT

        fun select(context: Context, engine: Engine) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("engine", engine.name).apply()
    }
}
