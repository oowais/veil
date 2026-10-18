package com.owaisahmed.veil.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Hy-MT2 (or any chat GGUF) through llama.cpp. Load is slow (~seconds); keep one instance warm. */
class LlamaTranslator private constructor(private val handle: Long) : Translator {

    private val lock = Mutex()
    private var closed = false

    override suspend fun translate(text: String, targetLanguage: String): Translation = lock.withLock {
        check(!closed) { "translator closed" }
        withContext(Dispatchers.Default) {
            val out = LlamaNative.complete(handle, prompt(text, targetLanguage), maxTokens = text.length * 2 + 64)
                ?: throw IOException("llama.cpp completion failed (see logcat tag VeilLlama)")
            val s = LlamaNative.lastStats(handle)
            Translation(out.trim(), TranslationStats(s[0].toInt(), s[1], s[2].toInt(), s[3]))
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            LlamaNative.unload(handle)
        }
    }

    companion object {
        @Volatile private var backendReady = false

        /** Prompt format from the Hy-MT2 model card ("Default Translation", English variant). */
        fun prompt(text: String, targetLanguage: String): String =
            "Translate the following text into $targetLanguage. Note that you should only output the " +
                "translated result without any additional explanation:\n\n$text"

        fun systemInfo(context: Context): String {
            ensureBackend(context)
            return LlamaNative.systemInfo()
        }

        suspend fun load(context: Context, model: File, nCtx: Int = 2048, threads: Int = 4): LlamaTranslator =
            withContext(Dispatchers.IO) {
                ensureBackend(context)
                val h = LlamaNative.load(model.path, nCtx, threads)
                if (h == 0L) throw IOException("Could not load ${model.name}")
                LlamaTranslator(h)
            }

        @Synchronized
        private fun ensureBackend(context: Context) {
            if (!backendReady) {
                LlamaNative.init(context.applicationInfo.nativeLibraryDir)
                backendReady = true
            }
        }
    }
}
