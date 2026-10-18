package com.owaisahmed.veil.translate

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.system.measureNanoTime

/**
 * OPUS-MT German→English (Marian, int8 ONNX from Xenova/opus-mt-de-en) on ONNX Runtime (D-036).
 * Small and fast to load; the default engine until the 1.25-bit Hy-MT2 build can run.
 * Marian is trained on sentences and tends to drop sentences from long inputs, so text is split
 * into sentences and each one is translated on its own. Greedy decoding with the KV cache.
 */
class MarianTranslator private constructor(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val tokenizer: SentencePieceTokenizer,
) : Translator {

    private val lock = Mutex()
    private var closed = false

    override suspend fun translate(text: String, targetLanguage: String): Translation = lock.withLock {
        check(!closed) { "translator closed" }
        withContext(Dispatchers.Default) {
            val stats = MutableStats()
            val out = splitSentences(text).joinToString(" ") { translateSentence(it, stats) }
            Translation(out, TranslationStats(stats.srcTokens, stats.encMs, stats.genTokens, stats.decMs))
        }
    }

    private class MutableStats(var srcTokens: Int = 0, var encMs: Double = 0.0, var genTokens: Int = 0, var decMs: Double = 0.0)

    private fun translateSentence(sentence: String, stats: MutableStats): String {
        val src = tokenizer.encode(sentence).let { if (it.size > MAX_SRC) it.copyOf(MAX_SRC - 1) + EOS else it }
        val n = src.size.toLong()
        val ids = LongArray(src.size) { src[it].toLong() }
        val mask = LongArray(src.size) { 1L }
        val out = ArrayList<Int>()

        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, n)).use { idsT ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, n)).use { maskT ->
                lateinit var hidden: OrtSession.Result
                stats.encMs += measureNanoTime {
                    hidden = encoder.run(mapOf("input_ids" to idsT, "attention_mask" to maskT))
                } / 1e6
                hidden.use { enc ->
                    stats.decMs += measureNanoTime { decode(maskT, enc.get(0) as OnnxTensor, src.size, out) } / 1e6
                }
            }
        }
        stats.srcTokens += src.size
        stats.genTokens += out.size
        return tokenizer.decode(out.toIntArray())
    }

    /**
     * Greedy decoding with the merged decoder: the first step runs without cache and returns the
     * self- and cross-attention caches; later steps feed only the last token plus the caches. The
     * cross-attention cache from the first step is reused unchanged.
     */
    private fun decode(mask: OnnxTensor, hidden: OnnxTensor, srcLen: Int, out: MutableList<Int>) {
        val empty = FloatBuffer.allocate(0)
        val emptyPast = pastNames.associateWith { OnnxTensor.createTensor(env, empty, longArrayOf(1, HEADS, 0, HEAD_DIM)) }
        var first: OrtSession.Result? = null
        var prev: OrtSession.Result? = null
        try {
            var token = PAD
            val maxLen = minOf(MAX_LEN, srcLen * 3 + 10)
            for (step in 0 until maxLen) {
                val useCache = step > 0
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token.toLong())), longArrayOf(1, 1)).use { tokT ->
                    OnnxTensor.createTensor(env, booleanArrayOf(useCache)).use { cacheT ->
                        val feeds = HashMap<String, OnnxTensor>()
                        feeds["encoder_attention_mask"] = mask
                        feeds["input_ids"] = tokT
                        feeds["encoder_hidden_states"] = hidden
                        feeds["use_cache_branch"] = cacheT
                        for (name in pastNames) {
                            feeds[name] = when {
                                !useCache -> emptyPast.getValue(name)
                                ".encoder." in name -> first!!.tensor(presentName(name))
                                else -> prev!!.tensor(presentName(name))
                            }
                        }
                        val res = decoder.run(feeds)
                        token = argmax(res.get(0) as OnnxTensor)
                        // Keep the first result (cross-attention cache) and the latest (self-attention cache).
                        if (first == null) first = res else if (prev !== first) prev?.close()
                        prev = res
                    }
                }
                if (token == EOS) break
                out += token
            }
        } finally {
            if (prev !== first) prev?.close()
            first?.close()
            emptyPast.values.forEach(OnnxTensor::close)
        }
    }

    private fun OrtSession.Result.tensor(name: String): OnnxTensor = get(name).get() as OnnxTensor

    /** Best token at the last position, never the pad token (bad_words_ids in the model config). */
    private fun argmax(logits: OnnxTensor): Int {
        val buf = logits.floatBuffer
        val vocab = logits.info.shape.last().toInt()
        val base = buf.limit() - vocab
        var best = -1
        var bestV = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            if (i == PAD) continue
            val v = buf.get(base + i)
            if (v > bestV) { bestV = v; best = i }
        }
        return best
    }

    override fun close() {
        if (!closed) {
            closed = true
            decoder.close()
            encoder.close()
        }
    }

    companion object {
        private const val PAD = 58100
        private const val EOS = 0
        private const val MAX_SRC = 512
        private const val MAX_LEN = 512
        private const val LAYERS = 6
        private const val HEADS = 8L
        private const val HEAD_DIM = 64L

        private val pastNames = (0 until LAYERS).flatMap { i ->
            listOf("decoder", "encoder").flatMap { k -> listOf("key", "value").map { v -> "past_key_values.$i.$k.$v" } }
        }

        private fun presentName(past: String) = past.replace("past_key_values", "present")

        // A sentence ends at . ! ? followed by space and an upper-case letter or opening quote, so
        // lower case ("z. B. die") and digits ("Nr. 5") don't split.
        private val sentenceEnd = Regex("""(?<=[.!?…])\s+(?=[\p{Lu}„"«])""")
        /** "z." in "z. B.": a single letter before the period is an abbreviation, not a sentence end. */
        private val abbreviationEnd = Regex("""(^|\s)\p{L}\.$""")

        fun splitSentences(text: String): List<String> {
            val out = ArrayList<String>()
            for (part in text.split(sentenceEnd).map(String::trim).filter(String::isNotEmpty)) {
                if (out.isNotEmpty() && abbreviationEnd.containsMatchIn(out.last())) out[out.size - 1] += " $part"
                else out += part
            }
            return out
        }

        /**
         * Folder with `encoder.onnx`, `decoder.onnx`, `source.spm`, `vocab.json` (see ModelRegistry).
         * [log] gets one line per step with its time, so a slow step shows up in the app's log.
         * With [cacheDir], ONNX Runtime's optimized graphs are saved there on the first load and
         * reused afterwards (graph optimization was most of the 1.1 s decoder load on the Pixel 9).
         */
        suspend fun load(
            dir: File,
            threads: Int = 4,
            cacheDir: File? = null,
            log: (String) -> Unit = {},
        ): MarianTranslator = withContext(Dispatchers.IO) {
            fun <T> step(name: String, block: () -> T): T {
                log("$name…")
                val t0 = System.nanoTime()
                return block().also { log("$name done in ${(System.nanoTime() - t0) / 1_000_000} ms") }
            }
            val env = OrtEnvironment.getEnvironment()
            cacheDir?.mkdirs()

            fun session(name: String): OrtSession {
                val src = File(dir, "$name.onnx")
                val opt = cacheDir?.let { File(it, "$name.opt.onnx") }
                if (opt != null && opt.exists() && opt.lastModified() >= src.lastModified()) {
                    val cached = runCatching {
                        OrtSession.SessionOptions().use { o ->
                            o.setIntraOpNumThreads(threads)
                            // Already optimized: skip the expensive passes.
                            o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                            env.createSession(opt.path, o)
                        }
                    }
                    cached.getOrNull()?.let { log("$name: optimized graph from cache"); return it }
                    log("$name: cached graph unusable (${cached.exceptionOrNull()?.message}), rebuilding")
                    opt.delete()
                }
                return OrtSession.SessionOptions().use { o ->
                    o.setIntraOpNumThreads(threads)
                    o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    if (opt != null) o.setOptimizedModelFilePath(opt.path)
                    env.createSession(src.path, o)
                }
            }

            val tokenizer = step("tokenizer") { SentencePieceTokenizer.load(File(dir, "source.spm"), File(dir, "vocab.json")) }
            val encoder = step("encoder") { session("encoder") }
            val decoder = step("decoder") { session("decoder") }
            MarianTranslator(env, encoder, decoder, tokenizer)
        }
    }
}
