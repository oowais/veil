package com.owaisahmed.veil.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class OcrLine(val text: String, val score: Float, val quad: Quad)

/** Wall-clock milliseconds per stage of one [OcrEngine.recognize] call. */
data class OcrTimings(
    val detPre: Long,
    val detInfer: Long,
    val detPost: Long,
    val recPre: Long,
    val recInfer: Long,
    val recPost: Long,
) {
    val det: Long get() = detPre + detInfer + detPost
    val rec: Long get() = recPre + recInfer + recPost
    val total: Long get() = det + rec
}

data class OcrResult(
    val lines: List<OcrLine>,
    val timings: OcrTimings,
    val detInput: Pair<Int, Int>,
    val boxes: Int,
    /** Lines recognised but dropped by the score or script filter. */
    val dropped: Int,
)

data class OcrConfig(
    /** Longest side of the detector input. Larger finds smaller text but costs time quadratically. */
    val detMaxSide: Int = 1280,
    val threads: Int = 4,
    /**
     * XNNPACK for the detector only. The recognizer always uses the default CPU provider: with XNNPACK
     * it crashed natively on a Pixel 9 (ORT 1.31.0, varying input widths) — see docs/STATUS.md.
     */
    val detXnnpack: Boolean = false,
    val recBatch: Int = 8,
    val minScore: Float = 0.5f,
    /** Drop lines containing letters from non-Latin scripts (status-bar icons read as CJK, etc.). */
    val latinOnly: Boolean = true,
)

/** PP-OCR detector + recognizer on ONNX Runtime. Not thread-safe; call [recognize] from one coroutine at a time. */
class OcrEngine(detModel: File, recModel: File, charset: List<String>, private val config: OcrConfig = OcrConfig()) : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val det: OrtSession = env.createSession(detModel.path, sessionOptions(xnnpack = config.detXnnpack))
    private val rec: OrtSession = env.createSession(recModel.path, sessionOptions(xnnpack = false))
    private val post = DbPostProcess()

    // Paddle appends a space class after the dictionary (use_space_char). If the model has no such
    // class, the extra entry is simply never indexed.
    private val decoder = CtcDecoder(if (" " in charset) charset else charset + " ")

    private fun sessionOptions(xnnpack: Boolean) = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        if (xnnpack) {
            // XNNPACK manages its own pool; keep ORT's pool at 1 to avoid oversubscription.
            addXnnpack(mapOf("intra_op_num_threads" to config.threads.toString()))
            setIntraOpNumThreads(1)
            addConfigEntry("session.intra_op.allow_spinning", "0")
        } else {
            setIntraOpNumThreads(config.threads)
        }
    }

    /** Accumulates stage timings while one [recognize] call runs. */
    private class Clock {
        val ms = LongArray(6)
        inline fun <T> time(stage: Int, block: () -> T): T {
            val t = SystemClock.elapsedRealtime()
            return block().also { ms[stage] += SystemClock.elapsedRealtime() - t }
        }
        fun timings() = OcrTimings(ms[0], ms[1], ms[2], ms[3], ms[4], ms[5])
    }

    fun recognize(src: Bitmap): OcrResult {
        val bmp = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val clock = Clock()

        val (dw, dh) = detSize(bmp.width, bmp.height)
        val boxes = detect(bmp, dw, dh, clock)

        val sx = bmp.width.toFloat() / dw
        val sy = bmp.height.toFloat() / dh
        val quads = boxes.map { it.quad.scale(sx, sy) }
        val all = recognizeCrops(bmp, quads, clock)
        val lines = clock.time(REC_POST) {
            all.filter { it.text.isNotBlank() && it.score >= config.minScore && (!config.latinOnly || isLatinText(it.text)) }
                .sortedWith(compareBy<OcrLine> { it.quad.top }.thenBy { it.quad.left })
        }
        return OcrResult(lines, clock.timings(), dw to dh, boxes.size, all.size - lines.size)
    }

    private fun detSize(w: Int, h: Int): Pair<Int, Int> {
        val ratio = minOf(1f, config.detMaxSide.toFloat() / max(w, h))
        fun r32(v: Float) = max(32, (v / 32f).roundToInt() * 32)
        return r32(w * ratio) to r32(h * ratio)
    }

    private fun detect(bmp: Bitmap, dw: Int, dh: Int, clock: Clock): List<DbPostProcess.Box> {
        val buf = clock.time(DET_PRE) {
            val scaled = Bitmap.createScaledBitmap(bmp, dw, dh, true)
            val px = IntArray(dw * dh)
            scaled.getPixels(px, 0, dw, 0, 0, dw, dh)
            if (scaled !== bmp) scaled.recycle()

            // Paddle feeds BGR; mean/std are given in that channel order.
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            val plane = dw * dh
            val buf = FloatBuffer.allocate(3 * plane)
            val arr = buf.array()
            for (i in 0 until plane) {
                val c = px[i]
                arr[i] = ((c and 0xFF) / 255f - mean[0]) / std[0]
                arr[plane + i] = (((c shr 8) and 0xFF) / 255f - mean[1]) / std[1]
                arr[2 * plane + i] = (((c shr 16) and 0xFF) / 255f - mean[2]) / std[2]
            }
            buf
        }

        val map = clock.time(DET_INFER) {
            OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, dh.toLong(), dw.toLong())).use { input ->
                det.run(mapOf(det.inputNames.first() to input)).use { out ->
                    val prob = (out[0] as OnnxTensor).floatBuffer
                    FloatArray(prob.remaining()).also { prob.get(it) }
                }
            }
        }
        return clock.time(DET_POST) { post.run(map, dw, dh) }
    }

    private fun recognizeCrops(bmp: Bitmap, quads: List<Quad>, clock: Clock): List<OcrLine> {
        val recH = 48
        // Sorting by aspect ratio keeps padding small inside each batch.
        val order = quads.indices.sortedBy { quads[it].width / max(1f, quads[it].height) }
        val results = arrayOfNulls<OcrLine>(quads.size)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (chunk in order.chunked(config.recBatch)) {
            val (buf, batchW) = clock.time(REC_PRE) {
                val widths = chunk.map { i ->
                    val q = quads[i]
                    ceil(recH * q.width / max(1f, q.height)).toInt().coerceIn(16, 3200)
                }
                val batchW = max(320, widths.max())
                val plane = recH * batchW
                val buf = FloatBuffer.allocate(chunk.size * 3 * plane)
                val arr = buf.array()
                val px = IntArray(plane)

                chunk.forEachIndexed { b, i ->
                    val q = quads[i]
                    val cw = widths[b]
                    val crop = Bitmap.createBitmap(cw, recH, Bitmap.Config.ARGB_8888)
                    val m = Matrix()
                    m.setPolyToPoly(
                        floatArrayOf(q.tl.x, q.tl.y, q.tr.x, q.tr.y, q.br.x, q.br.y, q.bl.x, q.bl.y), 0,
                        floatArrayOf(0f, 0f, cw.toFloat(), 0f, cw.toFloat(), recH.toFloat(), 0f, recH.toFloat()), 0,
                        4,
                    )
                    Canvas(crop).drawBitmap(bmp, m, paint)
                    crop.getPixels(px, 0, cw, 0, 0, cw, recH)
                    crop.recycle()

                    // Normalise to [-1, 1]; the padded area stays 0 like Paddle's zero padding.
                    val base = b * 3 * plane
                    for (y in 0 until recH) for (x in 0 until cw) {
                        val c = px[y * cw + x]
                        val o = y * batchW + x
                        arr[base + o] = ((c and 0xFF) / 255f - 0.5f) / 0.5f
                        arr[base + plane + o] = (((c shr 8) and 0xFF) / 255f - 0.5f) / 0.5f
                        arr[base + 2 * plane + o] = (((c shr 16) and 0xFF) / 255f - 0.5f) / 0.5f
                    }
                }
                buf to batchW
            }

            val (probs, shape) = clock.time(REC_INFER) {
                OnnxTensor.createTensor(env, buf, longArrayOf(chunk.size.toLong(), 3, recH.toLong(), batchW.toLong())).use { input ->
                    rec.run(mapOf(rec.inputNames.first() to input)).use { out ->
                        val t = out[0] as OnnxTensor
                        val fb = t.floatBuffer
                        FloatArray(fb.remaining()).also { fb.get(it) } to t.info.shape // [N, T, C]
                    }
                }
            }

            clock.time(REC_POST) {
                val steps = shape[1].toInt()
                val classes = shape[2].toInt()
                chunk.forEachIndexed { b, i ->
                    val r = decoder.decode(probs, steps, classes, b * steps * classes)
                    results[i] = OcrLine(r.text, r.score, quads[i])
                }
            }
        }
        return results.filterNotNull()
    }

    override fun close() {
        det.close()
        rec.close()
    }

    companion object {
        private const val DET_PRE = 0
        private const val DET_INFER = 1
        private const val DET_POST = 2
        private const val REC_PRE = 3
        private const val REC_INFER = 4
        private const val REC_POST = 5

        /** True when every letter is Latin script; digits, punctuation and symbols don't count. */
        fun isLatinText(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (Character.isLetter(cp) && Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN) return false
                i += Character.charCount(cp)
            }
            return true
        }
    }
}
