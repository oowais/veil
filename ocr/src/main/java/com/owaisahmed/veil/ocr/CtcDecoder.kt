package com.owaisahmed.veil.ocr

/**
 * Greedy CTC decoding. Class 0 is the blank; class i (i ≥ 1) maps to [charset][i - 1].
 */
class CtcDecoder(private val charset: List<String>) {

    data class Result(val text: String, val score: Float)

    /** [probs] holds [steps]×[classes] values for one sample, row-major, starting at [offset]. */
    fun decode(probs: FloatArray, steps: Int, classes: Int, offset: Int = 0): Result {
        val sb = StringBuilder()
        var prev = -1
        var scoreSum = 0f
        var kept = 0
        for (t in 0 until steps) {
            val base = offset + t * classes
            var best = 0
            var bestP = probs[base]
            for (c in 1 until classes) {
                val p = probs[base + c]
                if (p > bestP) { bestP = p; best = c }
            }
            if (best != 0 && best != prev) {
                val idx = best - 1
                if (idx < charset.size) {
                    sb.append(charset[idx])
                    scoreSum += bestP
                    kept++
                }
            }
            prev = best
        }
        return Result(sb.toString(), if (kept == 0) 0f else scoreSum / kept)
    }
}
