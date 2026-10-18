package com.owaisahmed.veil.translate

import java.io.File
import java.text.Normalizer

/**
 * Minimal SentencePiece unigram encoder for Marian (OPUS-MT) source text: reads the pieces and
 * scores from `source.spm`, segments with Viterbi, and maps pieces to model ids with `vocab.json`.
 * Normalization approximates `nmt_nfkc` with Java NFKC plus whitespace cleanup. Pure Kotlin, so it
 * is unit-tested against the real SentencePiece output.
 */
class SentencePieceTokenizer(
    pieceScores: Map<String, Float>,
    private val vocab: Map<String, Int>,
) {
    private val scores = pieceScores
    private val maxPieceLen = pieceScores.keys.maxOf { it.length }
    private val unkScore = pieceScores.values.min() - UNK_PENALTY
    private val unkId = vocab.getValue("<unk>")
    private val eosId = vocab.getValue("</s>")
    private val pieces: Array<String?> = arrayOfNulls<String>(vocab.values.max() + 1).also { arr ->
        vocab.forEach { (p, id) -> arr[id] = p }
    }

    /** Ids for the encoder, ending with `</s>`. */
    fun encode(text: String): IntArray {
        val norm = normalize(text)
        if (norm.isEmpty()) return intArrayOf(eosId)
        val s = SPACE + norm.replace(' ', SPACE)
        val n = s.length
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }.also { it[0] = 0f }
        val from = IntArray(n + 1)
        val unk = BooleanArray(n + 1)
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            var matched = false
            for (len in 1..minOf(maxPieceLen, n - i)) {
                val score = scores[s.substring(i, i + len)] ?: continue
                matched = matched || len == 1
                val v = best[i] + score
                if (v > best[i + len]) {
                    best[i + len] = v; from[i + len] = i; unk[i + len] = false
                }
            }
            // A character no piece covers becomes <unk>, as in SentencePiece.
            if (!matched) {
                val step = if (Character.isHighSurrogate(s[i]) && i + 1 < n) 2 else 1
                val v = best[i] + unkScore
                if (v > best[i + step]) {
                    best[i + step] = v; from[i + step] = i; unk[i + step] = true
                }
            }
        }
        val ids = ArrayList<Int>()
        var end = n
        while (end > 0) {
            val start = from[end]
            val id = if (unk[end]) unkId else vocab[s.substring(start, end)] ?: unkId
            // SentencePiece merges runs of unknown characters into one <unk>.
            if (!(id == unkId && ids.lastOrNull() == unkId)) ids += id
            end = start
        }
        ids.reverse()
        ids += eosId
        return ids.toIntArray()
    }

    /** Target ids back to text (shared vocabulary), skipping special tokens. */
    fun decode(ids: IntArray): String = buildString {
        for (id in ids) {
            val p = pieces.getOrNull(id) ?: continue
            if (p.startsWith("<") && p.endsWith(">")) continue
            append(p)
        }
    }.replace(SPACE, ' ').trim()

    companion object {
        private const val SPACE = '▁'
        private const val UNK_PENALTY = 10f
        private val whitespace = Regex("""\s+""")

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .filter { !Character.isISOControl(it) || it.isWhitespace() }
                .replace(whitespace, " ")
                .trim()

        fun load(spm: File, vocabJson: File): SentencePieceTokenizer =
            SentencePieceTokenizer(readSpmPieces(spm.readBytes()), readVocab(vocabJson.readText()))

        /** NORMAL and USER_DEFINED pieces with scores from a SentencePiece `ModelProto`. */
        fun readSpmPieces(bytes: ByteArray): Map<String, Float> {
            val out = HashMap<String, Float>()
            val r = ProtoReader(bytes, 0, bytes.size)
            while (r.hasMore()) {
                val (field, wire) = r.tag()
                if (field == 1 && wire == 2) {
                    val len = r.varint().toInt()
                    val p = ProtoReader(bytes, r.pos, r.pos + len)
                    var piece = ""; var score = 0f; var type = 1
                    while (p.hasMore()) {
                        val (f, w) = p.tag()
                        when {
                            f == 1 && w == 2 -> piece = p.string()
                            f == 2 && w == 5 -> score = p.float()
                            f == 3 && w == 0 -> type = p.varint().toInt()
                            else -> p.skip(w)
                        }
                    }
                    if (type == 1 || type == 4) out[piece] = score
                    r.pos += len
                } else {
                    r.skip(wire)
                }
            }
            return out
        }

        /**
         * `vocab.json` is one flat JSON object of piece → id. Scanned by hand in one pass: Kotlin's
         * `Regex.findAll` creates a new matcher per match, and on Android each one copies the whole
         * 1.4 MB input into the native regex engine, which took minutes (v0.4.0 hung on load).
         */
        fun readVocab(json: String): Map<String, Int> {
            val out = HashMap<String, Int>(65_536)
            var i = 0
            val n = json.length
            while (i < n) {
                if (json[i] != '"') { i++; continue }
                // Key: up to the closing quote, skipping escaped characters.
                val start = ++i
                while (i < n && json[i] != '"') i += if (json[i] == '\\') 2 else 1
                val key = unescape(json.substring(start, i))
                i++
                while (i < n && (json[i] == ':' || json[i].isWhitespace())) i++
                val numStart = i
                while (i < n && json[i].isDigit()) i++
                if (i > numStart) out[key] = json.substring(numStart, i).toInt()
            }
            return out
        }

        private fun unescape(s: String): String {
            if ('\\' !in s) return s
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (val e = s[i + 1]) {
                        'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        else -> sb.append(e)
                    }
                    i += 2
                } else {
                    sb.append(c); i++
                }
            }
            return sb.toString()
        }
    }

    private class ProtoReader(val buf: ByteArray, var pos: Int, val end: Int) {
        fun hasMore() = pos < end
        fun tag(): Pair<Int, Int> = varint().toInt().let { (it ushr 3) to (it and 7) }
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        fun string(): String {
            val len = varint().toInt()
            return String(buf, pos, len, Charsets.UTF_8).also { pos += len }
        }
        fun float(): Float {
            val bits = (buf[pos].toInt() and 0xff) or ((buf[pos + 1].toInt() and 0xff) shl 8) or
                ((buf[pos + 2].toInt() and 0xff) shl 16) or ((buf[pos + 3].toInt() and 0xff) shl 24)
            pos += 4
            return Float.fromBits(bits)
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> { val len = varint().toInt(); pos += len } // read the length before moving pos
                5 -> pos += 4
                else -> error("unsupported protobuf wire type $wire")
            }
        }
    }
}
