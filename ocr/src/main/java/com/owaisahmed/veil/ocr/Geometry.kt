package com.owaisahmed.veil.ocr

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Pt(val x: Float, val y: Float)

/** Four corners in order top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val width: Float get() = hypot(tr.x - tl.x, tr.y - tl.y)
    val height: Float get() = hypot(bl.x - tl.x, bl.y - tl.y)
    val left: Float get() = minOf(tl.x, tr.x, br.x, bl.x)
    val top: Float get() = minOf(tl.y, tr.y, br.y, bl.y)
    val right: Float get() = maxOf(tl.x, tr.x, br.x, bl.x)
    val bottom: Float get() = maxOf(tl.y, tr.y, br.y, bl.y)

    fun scale(sx: Float, sy: Float) = Quad(
        Pt(tl.x * sx, tl.y * sy), Pt(tr.x * sx, tr.y * sy),
        Pt(br.x * sx, br.y * sy), Pt(bl.x * sx, bl.y * sy),
    )
}

/** Rotated rectangle: center, side lengths along/against [angle] (radians). */
data class RotRect(val cx: Float, val cy: Float, val w: Float, val h: Float, val angle: Float) {

    fun expand(d: Float) = copy(w = w + 2 * d, h = h + 2 * d)

    /** Corners ordered so the quad reads left→right, top→bottom. */
    fun toQuad(): Quad {
        val c = cos(angle)
        val s = sin(angle)
        val hw = w / 2
        val hh = h / 2
        val pts = listOf(
            Pt(cx - hw * c + hh * s, cy - hw * s - hh * c),
            Pt(cx + hw * c + hh * s, cy + hw * s - hh * c),
            Pt(cx + hw * c - hh * s, cy + hw * s + hh * c),
            Pt(cx - hw * c - hh * s, cy - hw * s + hh * c),
        )
        return orderQuad(pts)
    }
}

/** Same ordering rule as PaddleOCR: two left-most points form the left edge, then split each by y. */
fun orderQuad(pts: List<Pt>): Quad {
    val byX = pts.sortedBy { it.x }
    val (tl, bl) = byX.take(2).sortedBy { it.y }.let { it[0] to it[1] }
    val (tr, br) = byX.drop(2).sortedBy { it.y }.let { it[0] to it[1] }
    return Quad(tl, tr, br, bl)
}

/** Andrew's monotone chain. Returns hull counter-clockwise, without the repeated first point. */
fun convexHull(points: List<Pt>): List<Pt> {
    if (points.size < 3) return points
    val p = points.sortedWith(compareBy<Pt> { it.x }.thenBy { it.y })
    fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val hull = ArrayList<Pt>(p.size * 2)
    for (pt in p) {
        while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) hull.removeAt(hull.size - 1)
        hull.add(pt)
    }
    val lower = hull.size + 1
    for (i in p.size - 2 downTo 0) {
        val pt = p[i]
        while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) hull.removeAt(hull.size - 1)
        hull.add(pt)
    }
    hull.removeAt(hull.size - 1)
    return hull
}

/** Minimum-area enclosing rectangle via rotating each hull edge onto the x-axis. */
fun minAreaRect(points: List<Pt>): RotRect {
    val hull = convexHull(points)
    if (hull.size == 1) return RotRect(hull[0].x, hull[0].y, 0f, 0f, 0f)
    var best: RotRect? = null
    var bestArea = Float.MAX_VALUE
    for (i in hull.indices) {
        val a = hull[i]
        val b = hull[(i + 1) % hull.size]
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len == 0f) continue
        val ux = (b.x - a.x) / len
        val uy = (b.y - a.y) / len
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        for (p in hull) {
            val u = p.x * ux + p.y * uy
            val v = -p.x * uy + p.y * ux
            if (u < minU) minU = u; if (u > maxU) maxU = u
            if (v < minV) minV = v; if (v > maxV) maxV = v
        }
        val area = (maxU - minU) * (maxV - minV)
        if (area < bestArea) {
            bestArea = area
            val cu = (minU + maxU) / 2
            val cv = (minV + maxV) / 2
            var w = maxU - minU
            var h = maxV - minV
            var angle = kotlin.math.atan2(uy, ux)
            // Normalise so the long side is "width" and the angle stays within ±45°.
            val quarter = (Math.PI / 2).toFloat()
            while (angle > quarter / 2) { angle -= quarter; val t = w; w = h; h = t }
            while (angle <= -quarter / 2) { angle += quarter; val t = w; w = h; h = t }
            best = RotRect(cu * ux - cv * uy, cu * uy + cv * ux, w, h, angle)
        }
    }
    return best!!
}
