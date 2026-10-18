package com.owaisahmed.veil.ocr

/**
 * DB (differentiable binarization) post-processing without OpenCV:
 * threshold → 8-connected components → min-area rotated rect → unclip.
 */
class DbPostProcess(
    private val thresh: Float = 0.2f,
    private val boxThresh: Float = 0.45f,
    private val unclipRatio: Float = 1.4f,
    private val minSize: Float = 3f,
    private val maxCandidates: Int = 3000,
) {

    data class Box(val quad: Quad, val score: Float)

    /** [prob] is row-major [h]×[w]. Returned quads are in probability-map pixel coordinates. */
    fun run(prob: FloatArray, w: Int, h: Int): List<Box> {
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val boxes = ArrayList<Box>()

        for (start in 0 until w * h) {
            if (visited[start] || prob[start] <= thresh) continue
            if (boxes.size >= maxCandidates) break

            // Flood-fill one component; keep only each row's left/right extreme for the hull.
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var sum = 0.0
            var count = 0
            val rowMin = HashMap<Int, Int>()
            val rowMax = HashMap<Int, Int>()
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                sum += prob[idx]
                count++
                rowMin[y] = minOf(rowMin[y] ?: x, x)
                rowMax[y] = maxOf(rowMax[y] ?: x, x)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val n = ny * w + nx
                    if (!visited[n] && prob[n] > thresh) {
                        visited[n] = true
                        stack[sp++] = n
                    }
                }
            }
            if (count < 4) continue
            val score = (sum / count).toFloat()
            if (score < boxThresh) continue

            // Pixel centres → pixel edges so a 1-px line still has area.
            val pts = ArrayList<Pt>(rowMin.size * 4)
            for ((y, x0) in rowMin) {
                val x1 = rowMax.getValue(y)
                pts += Pt(x0.toFloat(), y.toFloat()); pts += Pt(x0.toFloat(), y + 1f)
                pts += Pt(x1 + 1f, y.toFloat()); pts += Pt(x1 + 1f, y + 1f)
            }
            val rect = minAreaRect(pts)
            if (minOf(rect.w, rect.h) < minSize) continue

            // Unclip: offset the shrunk text kernel outward by area·ratio / perimeter.
            val area = rect.w * rect.h
            val perimeter = 2 * (rect.w + rect.h)
            val grown = rect.expand(area * unclipRatio / perimeter)
            if (minOf(grown.w, grown.h) < minSize + 2) continue

            boxes += Box(clamp(grown.toQuad(), w, h), score)
        }
        return boxes
    }

    private fun clamp(q: Quad, w: Int, h: Int): Quad {
        fun c(p: Pt) = Pt(p.x.coerceIn(0f, w.toFloat()), p.y.coerceIn(0f, h.toFloat()))
        return Quad(c(q.tl), c(q.tr), c(q.br), c(q.bl))
    }
}
