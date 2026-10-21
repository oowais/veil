package com.owaisahmed.veil.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.system.measureNanoTime

/**
 * Mozilla's Firefox Translations model (Bergamot / Marian, int8) through bergamot-translator
 * (D-062). Small (~37 MB with vocab and shortlist), loads fast, and splits text into sentences
 * itself; a screen's texts go in one call, which Bergamot batches.
 */
class BergamotTranslator private constructor(private val handle: Long) : Translator {

    private val lock = Mutex()
    private var closed = false

    /** Bergamot takes no instructions, so [fromOcr] changes nothing here. */
    override suspend fun translate(text: String, targetLanguage: String, fromOcr: Boolean): Translation {
        var result: Translation? = null
        translateAll(listOf(text), targetLanguage, fromOcr) { _, r -> result = r }
        return result!!
    }

    override suspend fun translateAll(
        texts: List<String>,
        targetLanguage: String,
        fromOcr: Boolean,
        onEach: suspend (index: Int, Translation) -> Unit,
    ) = lock.withLock {
        check(!closed) { "translator closed" }
        if (texts.isEmpty()) return@withLock
        var out: Array<String>? = null
        val ns = withContext(Dispatchers.Default) {
            measureNanoTime { out = BergamotNative.translate(handle, texts.toTypedArray()) }
        }
        val results = out ?: throw IOException("Bergamot translation failed (see logcat tag VeilBergamot)")
        // One call for all texts; its time is shared by length, good enough for the activity log.
        val total = texts.sumOf { it.length }.coerceAtLeast(1).toDouble()
        texts.forEachIndexed { i, t ->
            onEach(i, Translation(results.getOrElse(i) { "" }.trim(), TranslationStats(0, 0.0, 0, ns / 1e6 * t.length / total)))
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            BergamotNative.unload(handle)
        }
    }

    companion object {
        const val MODEL = "model.bin"
        const val VOCAB = "vocab.spm"
        const val SHORTLIST = "lex.bin"

        /**
         * [dir] holds [MODEL], [VOCAB] and [SHORTLIST]. Decoding options as in Firefox's translation
         * worker: greedy (beam 1), int8 gemm with the model's own quantisation, sentences over 128
         * tokens split.
         */
        suspend fun load(dir: File): BergamotTranslator = withContext(Dispatchers.IO) {
            val config = """
                models:
                  - ${File(dir, MODEL).path}
                vocabs:
                  - ${File(dir, VOCAB).path}
                  - ${File(dir, VOCAB).path}
                shortlist:
                  - ${File(dir, SHORTLIST).path}
                  - false
                beam-size: 1
                normalize: 1.0
                word-penalty: 0
                cpu-threads: 0
                gemm-precision: int8shiftAlphaAll
                skip-cost: true
                quiet: true
                quiet-translation: true
                max-length-break: 128
                mini-batch-words: 1024
                workspace: 128
                max-length-factor: 2.0
            """.trimIndent()
            val h = BergamotNative.load(config)
            if (h == 0L) throw IOException("Could not load the Bergamot model (see logcat tag VeilBergamot)")
            BergamotTranslator(h)
        }
    }
}
