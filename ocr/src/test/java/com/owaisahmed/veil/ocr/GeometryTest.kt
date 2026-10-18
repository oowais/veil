package com.owaisahmed.veil.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {

    @Test
    fun axisAlignedRectangle() {
        val pts = listOf(Pt(10f, 20f), Pt(110f, 20f), Pt(110f, 50f), Pt(10f, 50f), Pt(60f, 35f))
        val r = minAreaRect(pts)
        assertEquals(60f, r.cx, 1e-3f)
        assertEquals(35f, r.cy, 1e-3f)
        assertEquals(100f, r.w, 1e-3f)
        assertEquals(30f, r.h, 1e-3f)
        assertEquals(0f, r.angle, 1e-3f)

        val q = r.toQuad()
        assertEquals(Pt(10f, 20f).x, q.tl.x, 1e-3f)
        assertEquals(20f, q.tl.y, 1e-3f)
        assertEquals(110f, q.br.x, 1e-3f)
        assertEquals(50f, q.br.y, 1e-3f)
    }

    @Test
    fun dbFindsSingleTextBlob() {
        val w = 64
        val h = 32
        val prob = FloatArray(w * h)
        for (y in 10 until 20) for (x in 8 until 56) prob[y * w + x] = 0.9f
        val boxes = DbPostProcess().run(prob, w, h)
        assertEquals(1, boxes.size)
        val q = boxes[0].quad
        // Unclip grows the 48×10 kernel outward on every side.
        assert(q.left < 8f && q.right > 56f && q.top < 10f && q.bottom > 20f) { q.toString() }
    }
}
