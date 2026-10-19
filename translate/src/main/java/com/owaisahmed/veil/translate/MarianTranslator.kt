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
 * into sentences, which are translated in batches (D-048). Greedy decoding with the KV cache.
 */
class MarianTranslator private constructor(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val tokenizer: SentencePieceTokenizer,
) : Translator {

    private val lock = Mutex()
    private var closed = false

    override suspend fun translate(text: String, targetLanguage: String): Translation {
        var result: Translation? = null
        translateAll(listOf(text), targetLanguage) { _, r -> result = r }
        return result!!
    }

    /**
     * Splits every text into sentences and translates them [BATCH] at a time in reading order, so a
     * screen's encoder and decoder steps run once per batch instead of once per sentence (D-048). A
     * text is reported as soon as its last sentence is done.
     */
    override suspend fun translateAll(
        texts: List<String>,
        targetLanguage: String,
        onEach: suspend (index: Int, Translation) -> Unit,
    ) = lock.withLock {
        check(!closed) { "translator closed" }
        val sentences = texts.map(::splitSentences)
        val pending = sentences.mapTo(ArrayList()) { it.size }
        val outputs = sentences.map { arrayOfNulls<String>(it.size) }
        val stats = texts.map { MutableStats() }
        // Texts without any sentence (only whitespace) are done at once.
        texts.indices.filter { pending[it] == 0 }.forEach { onEach(it, Translation("", TranslationStats(0, 0.0, 0, 0.0))) }

        val jobs = sentences.flatMapIndexed { ti, list -> list.indices.map { si -> ti to si } }
        for (chunk in jobs.chunked(BATCH)) {
            val results = withContext(Dispatchers.Default) {
                translateBatch(chunk.map { (ti, si) -> sentences[ti][si] })
            }
            val chunkTokens = results.sumOf { it.srcTokens + it.genTokens }.coerceAtLeast(1)
            chunk.forEachIndexed { k, (ti, si) ->
                val r = results[k]
                outputs[ti][si] = r.text
                // Batch time is shared by token count; good enough for the activity log.
                val share = (r.srcTokens + r.genTokens).toDouble() / chunkTokens
                val s = stats[ti]
                s.srcTokens += r.srcTokens
                s.genTokens += r.genTokens
                s.encMs += r.encMs * share
                s.decMs += r.decMs * share
                if (--pending[ti] == 0) {
                    onEach(ti, Translation(outputs[ti].joinToString(" "), TranslationStats(s.srcTokens, s.encMs, s.genTokens, s.decMs)))
                }
            }
        }
    }

    private class MutableStats(var srcTokens: Int = 0, var encMs: Double = 0.0, var genTokens: Int = 0, var decMs: Double = 0.0)

    /** [encMs]/[decMs] are for the whole batch the sentence was in. */
    private class SentenceResult(val text: String, val srcTokens: Int, val genTokens: Int, val encMs: Double, val decMs: Double)

    /** Translates [batch] sentences together: padded encoder input, then greedy decoding of all rows at once. */
    private fun translateBatch(batch: List<String>): List<SentenceResult> {
        val srcs = batch.map { s -> tokenizer.encode(s).let { if (it.size > MAX_SRC) it.copyOf(MAX_SRC - 1) + EOS else it } }
        val b = srcs.size
        val len = srcs.maxOf { it.size }
        val ids = LongArray(b * len) { PAD.toLong() }
        val mask = LongArray(b * len)
        srcs.forEachIndexed { row, src ->
            src.forEachIndexed { i, tok -> ids[row * len + i] = tok.toLong(); mask[row * len + i] = 1L }
        }
        val outs = List(b) { ArrayList<Int>() }
        var encMs = 0.0
        var decMs = 0.0

        val shape = longArrayOf(b.toLong(), len.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { idsT ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { maskT ->
                lateinit var hidden: OrtSession.Result
                encMs = measureNanoTime {
                    hidden = encoder.run(mapOf("input_ids" to idsT, "attention_mask" to maskT))
                } / 1e6
                hidden.use { enc ->
                    decMs = measureNanoTime {
                        decode(maskT, enc.get(0) as OnnxTensor, IntArray(b) { srcs[it].size }, outs)
                    } / 1e6
                }
            }
        }
        return List(b) { SentenceResult(tokenizer.decode(outs[it].toIntArray()), srcs[it].size, outs[it].size, encMs, decMs) }
    }

    /**
     * Greedy decoding of all rows with the merged decoder: the first step runs without cache and
     * returns the self- and cross-attention caches; later steps feed only the last token per row plus
     * the caches. The cross-attention cache from the first step is reused unchanged. A row that has
     * finished (EOS or its length limit) keeps being fed PAD and its output is ignored.
     */
    private fun decode(mask: OnnxTensor, hidden: OnnxTensor, srcLens: IntArray, outs: List<MutableList<Int>>) {
        val b = srcLens.size
        val empty = FloatBuffer.allocate(0)
        val emptyPast = pastNames.associateWith { OnnxTensor.createTensor(env, empty, longArrayOf(b.toLong(), HEADS, 0, HEAD_DIM)) }
        val maxLens = IntArray(b) { minOf(MAX_LEN, srcLens[it] * 3 + 10) }
        val done = BooleanArray(b)
        val tokens = LongArray(b) { PAD.toLong() }
        var first: OrtSession.Result? = null
        var prev: OrtSession.Result? = null
        try {
            var step = 0
            while (!done.all { it }) {
                val useCache = step > 0
                OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.copyOf()), longArrayOf(b.toLong(), 1)).use { tokT ->
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
                        val logits = res.get(0) as OnnxTensor
                        for (row in 0 until b) {
                            if (done[row]) continue
                            val token = argmax(logits, row, b)
                            if (token == EOS) {
                                done[row] = true
                            } else {
                                outs[row] += token
                                tokens[row] = token.toLong()
                                if (outs[row].size >= maxLens[row]) done[row] = true
                            }
                            if (done[row]) tokens[row] = PAD.toLong()
                        }
                        // Keep the first result (cross-attention cache) and the latest (self-attention cache).
                        if (first == null) first = res else if (prev !== first) prev?.close()
                        prev = res
                    }
                }
                step++
            }
        } finally {
            if (prev !== first) prev?.close()
            first?.close()
            emptyPast.values.forEach(OnnxTensor::close)
        }
    }

    private fun OrtSession.Result.tensor(name: String): OnnxTensor = get(name).get() as OnnxTensor

    /** Best token of [row] at the last position, never the pad token (bad_words_ids in the model config). */
    private fun argmax(logits: OnnxTensor, row: Int, rows: Int): Int {
        val buf = logits.floatBuffer
        val vocab = logits.info.shape.last().toInt()
        val perRow = buf.limit() / rows
        val base = row * perRow + perRow - vocab
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
        /** Sentences translated together; bigger batches pad more and delay the first result. */
        private const val BATCH = 8
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
