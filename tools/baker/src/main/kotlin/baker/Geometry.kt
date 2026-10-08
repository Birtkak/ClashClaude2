package baker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Row-major 4x4 affine matrix. */
class Mat4(val m: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)) {
    operator fun times(o: Mat4): Mat4 {
        val r = FloatArray(16)
        for (i in 0 until 4) for (j in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += m[i * 4 + k] * o.m[k * 4 + j]
            r[i * 4 + j] = s
        }
        return Mat4(r)
    }

    fun px(x: Float, y: Float, z: Float) = m[0] * x + m[1] * y + m[2] * z + m[3]
    fun py(x: Float, y: Float, z: Float) = m[4] * x + m[5] * y + m[6] * z + m[7]
    fun pz(x: Float, y: Float, z: Float) = m[8] * x + m[9] * y + m[10] * z + m[11]

    /** Transforms a normal with the inverse transpose of the upper 3x3, then normalises it. */
    fun normal(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[4]; val e = m[5]; val f = m[6]
        val g = m[8]; val h = m[9]; val i = m[10]
        // Cofactor matrix = inverse transpose * det; the scale is removed by normalising.
        val c00 = e * i - f * h; val c01 = -(d * i - f * g); val c02 = d * h - e * g
        val c10 = -(b * i - c * h); val c11 = a * i - c * g; val c12 = -(a * h - b * g)
        val c20 = b * f - c * e; val c21 = -(a * f - c * d); val c22 = a * e - b * d
        var nx = c00 * x + c01 * y + c02 * z
        var ny = c10 * x + c11 * y + c12 * z
        var nz = c20 * x + c21 * y + c22 * z
        val det = a * c00 + b * c01 + c * c02
        if (det < 0f) { nx = -nx; ny = -ny; nz = -nz }
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-9f)
        out[at] = nx / l; out[at + 1] = ny / l; out[at + 2] = nz / l
    }

    companion object {
        fun translate(x: Float, y: Float, z: Float) = Mat4(floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z, 0f, 0f, 0f, 1f))
        fun scale(x: Float, y: Float = x, z: Float = x) = Mat4(floatArrayOf(x, 0f, 0f, 0f, 0f, y, 0f, 0f, 0f, 0f, z, 0f, 0f, 0f, 0f, 1f))

        /** Rotation about X by [deg]: +Y turns toward +Z. */
        fun rotX(deg: Float): Mat4 {
            val a = deg * PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
            return Mat4(floatArrayOf(1f, 0f, 0f, 0f, 0f, c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f))
        }

        /** Rotation about Y by [deg]: +Z turns toward +X. */
        fun rotY(deg: Float): Mat4 {
            val a = deg * PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
            return Mat4(floatArrayOf(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f, 0f, 0f, 0f, 1f))
        }

        /** Rotation about Z by [deg]: +X turns toward +Y. */
        fun rotZ(deg: Float): Mat4 {
            val a = deg * PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
            return Mat4(floatArrayOf(c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        }

        /** Rotation from a unit quaternion (x, y, z, w), as glTF stores them. */
        fun quat(x: Float, y: Float, z: Float, w: Float) = Mat4(
            floatArrayOf(
                1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0f,
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0f,
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0f,
                0f, 0f, 0f, 1f,
            ),
        )

        /** Maps +Y onto the direction (dx, dy, dz). */
        fun alignY(dx: Float, dy: Float, dz: Float): Mat4 {
            val l = sqrt(dx * dx + dy * dy + dz * dz)
            val yx = dx / l; val yy = dy / l; val yz = dz / l
            // Any vector not parallel to the new Y gives the other two axes.
            var ax = 0f; var ay = 0f; var az = 1f
            if (abs(yz) > 0.9f) { ax = 1f; az = 0f }
            // x = a × y, z = x × y
            var xx = ay * yz - az * yy; var xy = az * yx - ax * yz; var xz = ax * yy - ay * yx
            val xl = sqrt(xx * xx + xy * xy + xz * xz); xx /= xl; xy /= xl; xz /= xl
            val zx = xy * yz - xz * yy; val zy = xz * yx - xx * yz; val zz = xx * yy - xy * yx
            return Mat4(floatArrayOf(xx, yx, zx, 0f, xy, yy, zy, 0f, xz, yz, zz, 0f, 0f, 0f, 0f, 1f))
        }
    }
}

/** An RGB colour plus how it is lit. */
data class Mat(val rgb: Int, val emissive: Boolean = false, val shiny: Float = 0f) {
    fun darker(f: Float) = copy(rgb = scaleRgb(rgb, f))
}

fun scaleRgb(rgb: Int, f: Float): Int {
    val r = (((rgb shr 16) and 255) * f).toInt().coerceIn(0, 255)
    val g = (((rgb shr 8) and 255) * f).toInt().coerceIn(0, 255)
    val b = ((rgb and 255) * f).toInt().coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}

/** A texture for glTF materials: ARGB pixels. */
class Texture(val w: Int, val h: Int, val px: IntArray) {
    fun sample(u: Float, v: Float): Int {
        val x = ((u - kotlin.math.floor(u)) * w).toInt().coerceIn(0, w - 1)
        val y = ((v - kotlin.math.floor(v)) * h).toInt().coerceIn(0, h - 1)
        return px[y * w + x]
    }
}

/**
 * Triangles in model space (X right, Y up, Z forward, units = arena tiles). Per vertex:
 * position, normal; per triangle: material, and optionally texture coordinates.
 */
class Soup {
    var pos = FloatArray(4096); var nrm = FloatArray(4096)
    var uv: FloatArray? = null
    val mats = ArrayList<Mat>()
    val texs = ArrayList<Texture?>()
    var verts = 0
        private set
    val tris get() = verts / 3

    private fun grow(need: Int) {
        if (need * 3 <= pos.size) return
        val n = maxOf(need * 3, pos.size * 2)
        pos = pos.copyOf(n); nrm = nrm.copyOf(n)
        uv = uv?.copyOf(n / 3 * 2)
    }

    fun addVertex(m: Mat4, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, v: Float = 0f) {
        grow(verts + 1)
        val i = verts * 3
        pos[i] = m.px(x, y, z); pos[i + 1] = m.py(x, y, z); pos[i + 2] = m.pz(x, y, z)
        m.normal(nx, ny, nz, nrm, i)
        uv?.let { it[verts * 2] = u; it[verts * 2 + 1] = v }
        verts++
    }

    fun enableUv() { if (uv == null) uv = FloatArray(pos.size / 3 * 2) }

    fun endTriangle(mat: Mat, tex: Texture? = null) { mats += mat; texs += tex }
}

/**
 * Builds model geometry with a transform stack: `at(...) { ... }` nests a local frame,
 * and the primitives add triangles in the current frame.
 */
class Sculpt(val soup: Soup = Soup()) {
    var m = Mat4()
        private set

    inline fun at(
        x: Float = 0f, y: Float = 0f, z: Float = 0f,
        rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, s: Float = 1f,
        block: Sculpt.() -> Unit,
    ) = with(Mat4.translate(x, y, z) * Mat4.rotY(ry) * Mat4.rotX(rx) * Mat4.rotZ(rz) * Mat4.scale(s), block)

    inline fun with(t: Mat4, block: Sculpt.() -> Unit) {
        val saved = m
        set(m * t)
        block()
        set(saved)
    }

    fun set(t: Mat4) { m = t }

    /** A surface from a parametric function over a (u, v) grid with averaged smooth normals. */
    fun surface(nu: Int, nv: Int, wrapU: Boolean, mat: Mat, f: (u: Float, v: Float, out: FloatArray) -> Unit) {
        val cols = nu + 1; val rows = nv + 1
        val p = FloatArray(cols * rows * 3)
        val tmp = FloatArray(3)
        for (j in 0 until rows) for (i in 0 until cols) {
            f(i / nu.toFloat(), j / nv.toFloat(), tmp)
            val k = (j * cols + i) * 3
            p[k] = tmp[0]; p[k + 1] = tmp[1]; p[k + 2] = tmp[2]
        }
        // Accumulate face normals per grid vertex.
        val n = FloatArray(p.size)
        fun idx(i: Int, j: Int) = (j * cols + i) * 3
        for (j in 0 until nv) for (i in 0 until nu) {
            val a = idx(i, j); val b = idx(i + 1, j); val c = idx(i + 1, j + 1); val d = idx(i, j + 1)
            for ((x, y, z) in listOf(Triple(a, b, c), Triple(a, c, d))) {
                val ux = p[y] - p[x]; val uy = p[y + 1] - p[x + 1]; val uz = p[y + 2] - p[x + 2]
                val vx = p[z] - p[x]; val vy = p[z + 1] - p[x + 1]; val vz = p[z + 2] - p[x + 2]
                val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
                for (q in intArrayOf(x, y, z)) { n[q] += nx; n[q + 1] += ny; n[q + 2] += nz }
            }
        }
        // Weld the seam and the poles: vertices at the same position share a normal.
        val byPos = HashMap<Long, FloatArray>()
        fun key(k: Int): Long {
            val qx = Math.round(p[k] * 2000f).toLong(); val qy = Math.round(p[k + 1] * 2000f).toLong(); val qz = Math.round(p[k + 2] * 2000f).toLong()
            // 21 bits per axis: exact for any position within +-500 tiles.
            return ((qx + (1L shl 20)) shl 42) or ((qy + (1L shl 20)) shl 21) or (qz + (1L shl 20))
        }
        for (k in 0 until cols * rows) {
            val acc = byPos.getOrPut(key(k * 3)) { FloatArray(3) }
            acc[0] += n[k * 3]; acc[1] += n[k * 3 + 1]; acc[2] += n[k * 3 + 2]
        }
        for (k in 0 until cols * rows) {
            val acc = byPos[key(k * 3)]!!
            n[k * 3] = acc[0]; n[k * 3 + 1] = acc[1]; n[k * 3 + 2] = acc[2]
        }
        for (j in 0 until nv) for (i in 0 until nu) {
            val a = idx(i, j); val b = idx(i + 1, j); val c = idx(i + 1, j + 1); val d = idx(i, j + 1)
            tri(p, n, a, b, c, mat)
            tri(p, n, a, c, d, mat)
        }
    }

    private fun tri(p: FloatArray, n: FloatArray, a: Int, b: Int, c: Int, mat: Mat) {
        // Skip degenerate triangles (at poles).
        val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
        val vx = p[c] - p[a]; val vy = p[c + 1] - p[a + 1]; val vz = p[c + 2] - p[a + 2]
        val cx = uy * vz - uz * vy; val cy = uz * vx - ux * vz; val cz = ux * vy - uy * vx
        if (cx * cx + cy * cy + cz * cz < 1e-14f) return
        for (q in intArrayOf(a, b, c)) soup.addVertex(m, p[q], p[q + 1], p[q + 2], n[q], n[q + 1], n[q + 2])
        soup.endTriangle(mat)
    }

    /** One flat-shaded triangle. */
    fun flat(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, mat: Mat) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        soup.addVertex(m, ax, ay, az, nx, ny, nz)
        soup.addVertex(m, bx, by, bz, nx, ny, nz)
        soup.addVertex(m, cx, cy, cz, nx, ny, nz)
        soup.endTriangle(mat)
    }

    /**
     * Superellipsoid centred here with radii (rx, ry, rz). [round] = 1 is an ellipsoid; smaller
     * values give a rounded box (0.25 is boxy), 2 gives a diamond.
     */
    fun blob(rx: Float, ry: Float, rz: Float, mat: Mat, round: Float = 1f, seg: Int = 18) {
        val e = round
        surface(seg, seg / 2 + 2, true, mat) { u, v, o ->
            val lon = (u * 2 * PI - PI).toFloat()
            val lat = (v * PI - PI / 2).toFloat()
            val cl = spow(cos(lat), e); val sl = spow(sin(lat), e)
            o[0] = rx * cl * spow(sin(lon), e)
            o[1] = ry * sl
            o[2] = rz * cl * spow(cos(lon), e)
        }
    }

    /** Sphere of radius [r]. */
    fun ball(r: Float, mat: Mat, seg: Int = 18) = blob(r, r, r, mat, 1f, seg)

    /** Rounded box of size (w, h, d), centred here. */
    fun box(w: Float, h: Float, d: Float, mat: Mat, round: Float = 0.3f) = blob(w / 2, h / 2, d / 2, mat, round, 16)

    /** Frustum along +Y from 0 to [h], radii [r0] at the bottom and [r1] at the top, with caps. */
    fun tube(r0: Float, r1: Float, h: Float, mat: Mat, seg: Int = 16, caps: Boolean = true) {
        surface(seg, 1, true, mat) { u, v, o ->
            val a = (u * 2 * PI).toFloat()
            val r = r0 + (r1 - r0) * v
            o[0] = r * sin(a); o[1] = h * v; o[2] = r * cos(a)
        }
        if (caps) {
            for (i in 0 until seg) {
                val a0 = (i * 2 * PI / seg).toFloat(); val a1 = ((i + 1) * 2 * PI / seg).toFloat()
                if (r1 > 0f) flat(0f, h, 0f, r1 * sin(a0), h, r1 * cos(a0), r1 * sin(a1), h, r1 * cos(a1), mat)
                if (r0 > 0f) flat(0f, 0f, 0f, r0 * sin(a1), 0f, r0 * cos(a1), r0 * sin(a0), 0f, r0 * cos(a0), mat)
            }
        }
    }

    /** A rounded limb from point a to point b, radius [ra] at a and [rb] at b. */
    fun limb(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, ra: Float, rb: Float, mat: Mat) {
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        with(Mat4.translate(ax, ay, az) * Mat4.alignY(dx, dy, dz)) {
            tube(ra, rb, len, mat, 12, caps = false)
            ball(ra, mat, 12)
            at(y = len) { ball(rb, mat, 12) }
        }
    }

    /** A flat shape from a 2D outline in the XY plane, extruded [depth] along Z (centred). */
    fun slab(points: List<Pair<Float, Float>>, depth: Float, mat: Mat) {
        val n = points.size
        val h = depth / 2
        for (t in earClip(points)) {
            val (a, b, c) = t
            flat(points[a].first, points[a].second, h, points[b].first, points[b].second, h, points[c].first, points[c].second, h, mat)
            flat(points[a].first, points[a].second, -h, points[c].first, points[c].second, -h, points[b].first, points[b].second, -h, mat)
        }
        val ccw = signedArea(points) > 0
        for (i in 0 until n) {
            val p = points[i]; val q = points[(i + 1) % n]
            if (ccw) {
                flat(p.first, p.second, h, p.first, p.second, -h, q.first, q.second, -h, mat)
                flat(p.first, p.second, h, q.first, q.second, -h, q.first, q.second, h, mat)
            } else {
                flat(p.first, p.second, h, q.first, q.second, -h, p.first, p.second, -h, mat)
                flat(p.first, p.second, h, q.first, q.second, h, q.first, q.second, -h, mat)
            }
        }
    }

    /** Torus around the Y axis, for rings and crowns. */
    fun ring(r: Float, thick: Float, mat: Mat, seg: Int = 20) {
        surface(seg, 8, true, mat) { u, v, o ->
            val a = (u * 2 * PI).toFloat(); val b = (v * 2 * PI).toFloat()
            val rr = r + thick * cos(b)
            o[0] = rr * sin(a); o[1] = thick * sin(b); o[2] = rr * cos(a)
        }
    }
}

private fun spow(x: Float, e: Float): Float = sign(x) * abs(x).pow(e)

private fun signedArea(p: List<Pair<Float, Float>>): Float {
    var a = 0f
    for (i in p.indices) {
        val q = p[(i + 1) % p.size]
        a += p[i].first * q.second - q.first * p[i].second
    }
    return a / 2
}

/** Triangulates a simple polygon; returned triangles are counter-clockwise. */
private fun earClip(pts: List<Pair<Float, Float>>): List<Triple<Int, Int, Int>> {
    val idx = pts.indices.toMutableList()
    if (signedArea(pts) < 0) idx.reverse()
    val out = ArrayList<Triple<Int, Int, Int>>()
    fun cross(o: Int, a: Int, b: Int) =
        (pts[a].first - pts[o].first) * (pts[b].second - pts[o].second) - (pts[a].second - pts[o].second) * (pts[b].first - pts[o].first)
    fun inside(p: Int, a: Int, b: Int, c: Int) = cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
    var guard = 0
    while (idx.size > 3 && guard++ < 10000) {
        var cut = false
        for (i in idx.indices) {
            val a = idx[(i + idx.size - 1) % idx.size]; val b = idx[i]; val c = idx[(i + 1) % idx.size]
            if (cross(a, b, c) <= 0) continue
            if (idx.any { it != a && it != b && it != c && inside(it, a, b, c) }) continue
            out += Triple(a, b, c)
            idx.removeAt(i)
            cut = true
            break
        }
        if (!cut) break
    }
    if (idx.size == 3) out += Triple(idx[0], idx[1], idx[2])
    return out
}
