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

data class OcrResult(
    val lines: List<OcrLine>,
    val detMs: Long,
    val recMs: Long,
    val detInput: Pair<Int, Int>,
    val boxes: Int,
)

data class OcrConfig(
    /** Longest side of the detector input. Larger finds smaller text but costs time quadratically. */
    val detMaxSide: Int = 1280,
    val threads: Int = 4,
    val useXnnpack: Boolean = true,
    val recBatch: Int = 8,
    val minScore: Float = 0.5f,
)

/** PP-OCR detector + recognizer on ONNX Runtime. Not thread-safe; call [recognize] from one coroutine at a time. */
class OcrEngine(detModel: File, recModel: File, charset: List<String>, private val config: OcrConfig = OcrConfig()) : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val det: OrtSession
    private val rec: OrtSession
    private val post = DbPostProcess()
    private val decoder: CtcDecoder
    private val charset: List<String>

    init {
        det = env.createSession(detModel.path, sessionOptions())
        rec = env.createSession(recModel.path, sessionOptions())
        // Paddle appends a space class after the dictionary (use_space_char). If the model has no such
        // class, the extra entry is simply never indexed.
        this.charset = if (" " in charset) charset else charset + " "
        decoder = CtcDecoder(this.charset)
    }

    private fun sessionOptions() = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        if (config.useXnnpack) {
            // XNNPACK manages its own pool; keep ORT's pool at 1 to avoid oversubscription.
            addXnnpack(mapOf("intra_op_num_threads" to config.threads.toString()))
            setIntraOpNumThreads(1)
            addConfigEntry("session.intra_op.allow_spinning", "0")
        } else {
            setIntraOpNumThreads(config.threads)
        }
    }

    fun recognize(src: Bitmap): OcrResult {
        val bmp = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)

        val t0 = SystemClock.elapsedRealtime()
        val (dw, dh) = detSize(bmp.width, bmp.height)
        val boxes = detect(bmp, dw, dh)
        val t1 = SystemClock.elapsedRealtime()

        val sx = bmp.width.toFloat() / dw
        val sy = bmp.height.toFloat() / dh
        val quads = boxes.map { it.quad.scale(sx, sy) }
        val lines = recognizeCrops(bmp, quads)
            .filter { it.text.isNotBlank() && it.score >= config.minScore }
            .sortedWith(compareBy<OcrLine> { it.quad.top }.thenBy { it.quad.left })
        val t2 = SystemClock.elapsedRealtime()

        return OcrResult(lines, t1 - t0, t2 - t1, dw to dh, boxes.size)
    }

    private fun detSize(w: Int, h: Int): Pair<Int, Int> {
        val ratio = minOf(1f, config.detMaxSide.toFloat() / max(w, h))
        fun r32(v: Float) = max(32, (v / 32f).roundToInt() * 32)
        return r32(w * ratio) to r32(h * ratio)
    }

    private fun detect(bmp: Bitmap, dw: Int, dh: Int): List<DbPostProcess.Box> {
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
            val b = (c and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val r = ((c shr 16) and 0xFF) / 255f
            arr[i] = (b - mean[0]) / std[0]
            arr[plane + i] = (g - mean[1]) / std[1]
            arr[2 * plane + i] = (r - mean[2]) / std[2]
        }

        OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, dh.toLong(), dw.toLong())).use { input ->
            det.run(mapOf(det.inputNames.first() to input)).use { out ->
                val prob = (out[0] as OnnxTensor).floatBuffer
                val map = FloatArray(prob.remaining()).also { prob.get(it) }
                return post.run(map, dw, dh)
            }
        }
    }

    private fun recognizeCrops(bmp: Bitmap, quads: List<Quad>): List<OcrLine> {
        val recH = 48
        // Sorting by aspect ratio keeps padding small inside each batch.
        val order = quads.indices.sortedBy { quads[it].width / max(1f, quads[it].height) }
        val results = arrayOfNulls<OcrLine>(quads.size)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (chunk in order.chunked(config.recBatch)) {
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

            OnnxTensor.createTensor(env, buf, longArrayOf(chunk.size.toLong(), 3, recH.toLong(), batchW.toLong())).use { input ->
                rec.run(mapOf(rec.inputNames.first() to input)).use { out ->
                    val t = out[0] as OnnxTensor
                    val shape = t.info.shape // [N, T, C]
                    val steps = shape[1].toInt()
                    val classes = shape[2].toInt()
                    val fb = t.floatBuffer
                    val probs = FloatArray(fb.remaining()).also { fb.get(it) }
                    chunk.forEachIndexed { b, i ->
                        val r = decoder.decode(probs, steps, classes, b * steps * classes)
                        results[i] = OcrLine(r.text, r.score, quads[i])
                    }
                }
            }
        }
        return results.filterNotNull()
    }

    override fun close() {
        det.close()
        rec.close()
    }
}
