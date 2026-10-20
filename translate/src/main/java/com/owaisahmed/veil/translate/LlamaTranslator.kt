package com.owaisahmed.veil.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Hy-MT2 (or any chat GGUF) through llama.cpp. Load is slow (~seconds); keep one instance warm.
 * A screen's short texts go out together as numbered segments; long texts are split into sentence
 * chunks that fit the context (D-054).
 */
class LlamaTranslator private constructor(private val handle: Long, private val log: (String) -> Unit) : Translator {

    private val lock = Mutex()
    private var closed = false

    override suspend fun translate(text: String, targetLanguage: String, fromOcr: Boolean): Translation = lock.withLock {
        translateLocked(text, targetLanguage, fromOcr)
    }

    /**
     * Short texts are packed into numbered batches (in reading order); a batch whose answer doesn't
     * parse is translated again one text at a time. Long texts always go alone.
     */
    override suspend fun translateAll(
        texts: List<String>,
        targetLanguage: String,
        fromOcr: Boolean,
        onEach: suspend (index: Int, Translation) -> Unit,
    ) = lock.withLock {
        for (group in groups(texts)) {
            if (group.size == 1) {
                onEach(group[0], translateLocked(texts[group[0]], targetLanguage, fromOcr))
                continue
            }
            val batch = group.map { texts[it] }
            val r = complete(NumberedSegments.prompt(batch, targetLanguage, fromOcr), batch.sumOf { it.length } * 2 + 16 * batch.size + 64)
            val parsed = NumberedSegments.parse(r.text, batch.size)
            if (parsed == null) {
                log("batch of ${batch.size} didn't come back numbered, translating one by one")
                for (i in group) onEach(i, translateLocked(texts[i], targetLanguage, fromOcr))
                continue
            }
            // The batch's time is shared by text length; good enough for the activity log.
            val total = batch.sumOf { it.length }.coerceAtLeast(1).toDouble()
            group.forEachIndexed { k, i ->
                val f = batch[k].length / total
                val st = r.stats
                onEach(i, Translation(parsed[k], TranslationStats((st.promptTokens * f).toInt(), st.promptMs * f, (st.generatedTokens * f).toInt(), st.generateMs * f)))
            }
        }
    }

    /** Must hold [lock]. Texts longer than [CHUNK_CHARS] are translated chunk by chunk and joined. */
    private suspend fun translateLocked(text: String, targetLanguage: String, fromOcr: Boolean): Translation {
        val chunks = Sentences.chunks(text, CHUNK_CHARS)
        if (chunks.size <= 1) return complete(prompt(text, targetLanguage, fromOcr), text.length * 2 + 64)
        val parts = chunks.map { complete(prompt(it, targetLanguage, fromOcr), it.length * 2 + 64) }
        return Translation(
            parts.joinToString(" ") { it.text },
            TranslationStats(parts.sumOf { it.stats.promptTokens }, parts.sumOf { it.stats.promptMs }, parts.sumOf { it.stats.generatedTokens }, parts.sumOf { it.stats.generateMs }),
        )
    }

    private suspend fun complete(prompt: String, maxTokens: Int): Translation {
        check(!closed) { "translator closed" }
        return withContext(Dispatchers.Default) {
            val out = LlamaNative.complete(handle, prompt, maxTokens)
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

        /** Longest text sent in one prompt; ~1–2 tokens per German char keeps prompt + answer in 2048. */
        private const val CHUNK_CHARS = 600
        /** Texts up to this long are batched; at most [BATCH_TEXTS] and [BATCH_CHARS] per batch. */
        private const val SEGMENT_CHARS = 300
        private const val BATCH_TEXTS = 8
        private const val BATCH_CHARS = 600

        /** Indices of [texts] in batches, in order; long texts form a group of their own. */
        internal fun groups(texts: List<String>): List<List<Int>> {
            val out = ArrayList<List<Int>>()
            var cur = ArrayList<Int>()
            var chars = 0
            for ((i, t) in texts.withIndex()) {
                if (t.length > SEGMENT_CHARS) {
                    out += listOf(i)
                    continue
                }
                if (cur.size == BATCH_TEXTS || (cur.isNotEmpty() && chars + t.length > BATCH_CHARS)) {
                    out += cur
                    cur = ArrayList()
                    chars = 0
                }
                cur += i
                chars += t.length
            }
            if (cur.isNotEmpty()) out += cur
            return out.sortedBy { it.first() }
        }

        /**
         * Prompt format from the Hy-MT2 model card ("Default Translation", English variant). For OCR
         * text one extra sentence says where it came from, so the model reads past its artefacts.
         */
        fun prompt(text: String, targetLanguage: String, fromOcr: Boolean = false): String =
            "Translate the following text into $targetLanguage. " + (if (fromOcr) OCR_NOTE else "") +
                "Note that you should only output the translated result without any additional explanation:\n\n$text"

        const val OCR_NOTE = "The text was read from a screenshot by OCR, so it may contain wrong line " +
            "breaks, split words or misread characters; translate what was meant. "

        fun systemInfo(context: Context): String {
            ensureBackend(context)
            return LlamaNative.systemInfo()
        }

        /** [threads] for generation, [threadsBatch] for prompt processing; at most 4 on the Pixel 9 (D-033). */
        suspend fun load(
            context: Context,
            model: File,
            nCtx: Int = 2048,
            threads: Int = 4,
            threadsBatch: Int = threads,
            log: (String) -> Unit = {},
        ): LlamaTranslator =
            withContext(Dispatchers.IO) {
                ensureBackend(context)
                val h = LlamaNative.load(model.path, nCtx, threads, threadsBatch)
                if (h == 0L) throw IOException("Could not load ${model.name}")
                LlamaTranslator(h, log)
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
