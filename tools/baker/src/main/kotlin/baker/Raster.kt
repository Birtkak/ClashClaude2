package baker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orthographic camera looking from the south (+y) and above, tilted down by [pitchDeg].
 * World axes: x east, y south, z up; units are arena tiles; [ppt] output pixels per tile.
 */
class Camera(val pitchDeg: Float, val ppt: Float) {
    val sinP = sin(pitchDeg * PI / 180).toFloat()
    val cosP = cos(pitchDeg * PI / 180).toFloat()

    fun screenX(x: Float) = x * ppt
    fun screenY(y: Float, z: Float) = (y * sinP - z * cosP) * ppt
    /** Larger = closer to the camera. */
    fun depth(y: Float, z: Float) = y * cosP + z * sinP
}

/**
 * Places a model-space soup in the world: the model's +Z (forward) turns to [heading]
 * (radians, 0 = east, PI/2 = south), its +Y becomes up (+z).
 */
fun placeInWorld(heading: Float): Mat4 {
    val c = cos(heading); val s = sin(heading)
    // Columns: model X -> right = (-s, c, 0); model Y -> up (0, 0, 1); model Z -> forward (c, s, 0).
    return Mat4(floatArrayOf(-s, 0f, c, 0f, c, 0f, s, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f))
}

/** Screen-space bounds (output pixels, relative to the model origin) of a soup. */
class Bounds(var minX: Float = Float.MAX_VALUE, var minY: Float = Float.MAX_VALUE, var maxX: Float = -Float.MAX_VALUE, var maxY: Float = -Float.MAX_VALUE) {
    fun add(x: Float, y: Float) { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
    fun add(o: Bounds) { add(o.minX, o.minY); add(o.maxX, o.maxY) }
    val empty get() = minX > maxX
}

fun bounds(soup: Soup, world: Mat4, cam: Camera): Bounds {
    val b = Bounds()
    for (v in 0 until soup.verts) {
        val x = soup.pos[v * 3]; val y = soup.pos[v * 3 + 1]; val z = soup.pos[v * 3 + 2]
        val wx = world.px(x, y, z); val wy = world.py(x, y, z); val wz = world.pz(x, y, z)
        b.add(cam.screenX(wx), cam.screenY(wy, wz))
    }
    return b
}

/** Look of the shading, matched to the game's chunky cartoon style. */
object Style {
    const val OUTLINE = 0x0B1324
    /** Light comes from the upper left, a little toward the camera. */
    private val L = floatArrayOf(-0.5f, 0.45f, 0.75f).let { v ->
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    /** Toon ramp: lit, mid and shadow bands with a blue-ish shadow tint. */
    fun shade(rgb: Int, nx: Float, ny: Float, nz: Float, mat: Mat, toCam: FloatArray): Int {
        if (mat.emissive) return rgb
        val d = nx * L[0] + ny * L[1] + nz * L[2]
        // Narrow smooth steps between bands; supersampling turns them into soft edges.
        val lit = smooth(0.25f, 0.33f, d)
        val mid = smooth(-0.25f, -0.15f, d)
        var r = ((rgb shr 16) and 255) / 255f
        var g = ((rgb shr 8) and 255) / 255f
        var b = (rgb and 255) / 255f
        val kr = 0.56f + 0.2f * mid + 0.24f * lit
        val kg = 0.55f + 0.2f * mid + 0.25f * lit
        val kb = 0.66f + 0.14f * mid + 0.2f * lit
        r *= kr; g *= kg; b *= kb
        // Rim light on edges facing away from the camera, and an optional highlight.
        val facing = nx * toCam[0] + ny * toCam[1] + nz * toCam[2]
        val rim = smooth(0.35f, 0.15f, facing) * 0.18f * lit
        var spec = 0f
        if (mat.shiny > 0f) {
            // Blinn half vector between light and camera.
            val hx = L[0] + toCam[0]; val hy = L[1] + toCam[1]; val hz = L[2] + toCam[2]
            val hl = sqrt(hx * hx + hy * hy + hz * hz)
            val nh = (nx * hx + ny * hy + nz * hz) / hl
            spec = smooth(0.97f, 0.995f, nh) * mat.shiny
        }
        r = r + rim + spec; g = g + rim + spec; b = b + rim + spec
        return (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
    }

    private fun clamp255(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

/**
 * Renders one frame into a [w] x [h] ARGB image (premultiplied alpha is not used: plain ARGB).
 * The model origin lands at output pixel ([ax], [ay]). Renders at [ss]x supersampling, adds a
 * silhouette outline and inner contour lines, then downsamples.
 */
fun render(soup: Soup, world: Mat4, cam: Camera, w: Int, h: Int, ax: Float, ay: Float, ss: Int = 3, outlinePx: Float = 1.6f): IntArray {
    val W = w * ss; val H = h * ss
    val color = IntArray(W * H)
    val depth = FloatArray(W * H) { -Float.MAX_VALUE }
    val toCam = floatArrayOf(0f, cam.cosP, cam.sinP)

    val n = soup.verts
    val sx = FloatArray(n); val sy = FloatArray(n); val sd = FloatArray(n)
    val wn = FloatArray(n * 3)
    for (v in 0 until n) {
        val x = soup.pos[v * 3]; val y = soup.pos[v * 3 + 1]; val z = soup.pos[v * 3 + 2]
        val wx = world.px(x, y, z); val wy = world.py(x, y, z); val wz = world.pz(x, y, z)
        sx[v] = (cam.screenX(wx) + ax) * ss
        sy[v] = (cam.screenY(wy, wz) + ay) * ss
        sd[v] = cam.depth(wy, wz)
        world.normal(soup.nrm[v * 3], soup.nrm[v * 3 + 1], soup.nrm[v * 3 + 2], wn, v * 3)
    }
    val uv = soup.uv
    for (t in 0 until soup.tris) {
        val a = t * 3; val b = a + 1; val c = a + 2
        val area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sx[c] - sx[a]) * (sy[b] - sy[a])
        if (abs(area) < 1e-6f) continue
        val x0 = max(0, floor(min(sx[a], min(sx[b], sx[c]))).toInt())
        val x1 = min(W - 1, ceil(max(sx[a], max(sx[b], sx[c]))).toInt())
        val y0 = max(0, floor(min(sy[a], min(sy[b], sy[c]))).toInt())
        val y1 = min(H - 1, ceil(max(sy[a], max(sy[b], sy[c]))).toInt())
        if (x0 > x1 || y0 > y1) continue
        val mat = soup.mats[t]
        val tex = soup.texs[t]
        val inv = 1f / area
        for (py in y0..y1) {
            val fy = py + 0.5f
            for (px in x0..x1) {
                val fx = px + 0.5f
                val w0 = ((sx[b] - fx) * (sy[c] - fy) - (sx[c] - fx) * (sy[b] - fy)) * inv
                val w1 = ((sx[c] - fx) * (sy[a] - fy) - (sx[a] - fx) * (sy[c] - fy)) * inv
                val w2 = 1f - w0 - w1
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val d = w0 * sd[a] + w1 * sd[b] + w2 * sd[c]
                val i = py * W + px
                if (d <= depth[i]) continue
                depth[i] = d
                var nx = w0 * wn[a * 3] + w1 * wn[b * 3] + w2 * wn[c * 3]
                var ny = w0 * wn[a * 3 + 1] + w1 * wn[b * 3 + 1] + w2 * wn[c * 3 + 1]
                var nz = w0 * wn[a * 3 + 2] + w1 * wn[b * 3 + 2] + w2 * wn[c * 3 + 2]
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                nx /= l; ny /= l; nz /= l
                // Visible surfaces face the camera: this fixes inward-wound or open geometry.
                if (nx * toCam[0] + ny * toCam[1] + nz * toCam[2] < 0f) { nx = -nx; ny = -ny; nz = -nz }
                var base = mat.rgb
                if (tex != null && uv != null) {
                    val u = w0 * uv[a * 2] + w1 * uv[b * 2] + w2 * uv[c * 2]
                    val v = w0 * uv[a * 2 + 1] + w1 * uv[b * 2 + 1] + w2 * uv[c * 2 + 1]
                    val texel = tex.sample(u, v)
                    if ((texel ushr 24) < 128) { depth[i] = -Float.MAX_VALUE; continue }
                    base = multiply(texel and 0xFFFFFF, mat.rgb)
                }
                color[i] = (0xFF shl 24) or Style.shade(base, nx, ny, nz, mat, toCam)
            }
        }
    }

    // Inner contour lines: darken pixels just behind a depth step (an arm in front of a body).
    val step = max(1, (ss * 0.9f).toInt())
    val thr = 0.07f
    val ink = (0xFF shl 24) or Style.OUTLINE
    val lines = BooleanArray(W * H)
    for (py in 0 until H) for (px in 0 until W) {
        val i = py * W + px
        if (color[i] == 0) continue
        val d = depth[i]
        var edge = false
        for ((dx, dy) in NEIGHBOURS) {
            val qx = px + dx * step; val qy = py + dy * step
            if (qx < 0 || qy < 0 || qx >= W || qy >= H) continue
            val q = qy * W + qx
            if (color[q] != 0 && depth[q] > d + thr) { edge = true; break }
        }
        lines[i] = edge
    }
    for (i in lines.indices) if (lines[i]) color[i] = blend(color[i], ink, 0.85f)

    // Silhouette outline: dilate the coverage mask by a disk.
    val r = outlinePx * ss
    val ri = ceil(r).toInt()
    val out = color.copyOf()
    for (py in 0 until H) for (px in 0 until W) {
        val i = py * W + px
        if (color[i] != 0) continue
        var hit = false
        loop@ for (dy in -ri..ri) {
            val qy = py + dy
            if (qy < 0 || qy >= H) continue
            for (dx in -ri..ri) {
                if (dx * dx + dy * dy > r * r) continue
                val qx = px + dx
                if (qx < 0 || qx >= W) continue
                if (color[qy * W + qx] != 0) { hit = true; break@loop }
            }
        }
        if (hit) out[i] = ink
    }
    return downsample(out, W, H, ss)
}

private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

private fun multiply(a: Int, b: Int): Int {
    val r = ((a shr 16) and 255) * ((b shr 16) and 255) / 255
    val g = ((a shr 8) and 255) * ((b shr 8) and 255) / 255
    val bl = (a and 255) * (b and 255) / 255
    return (r shl 16) or (g shl 8) or bl
}

private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt() and 255) shl s
    return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
}

/** Box-filters the supersampled image down, with alpha-weighted colour averaging. */
private fun downsample(src: IntArray, W: Int, H: Int, ss: Int): IntArray {
    val w = W / ss; val h = H / ss
    val out = IntArray(w * h)
    val n = ss * ss
    for (y in 0 until h) for (x in 0 until w) {
        var a = 0; var r = 0; var g = 0; var b = 0
        for (dy in 0 until ss) for (dx in 0 until ss) {
            val p = src[(y * ss + dy) * W + x * ss + dx]
            val pa = p ushr 24
            if (pa == 0) continue
            a += pa; r += ((p shr 16) and 255) * pa; g += ((p shr 8) and 255) * pa; b += (p and 255) * pa
        }
        if (a == 0) continue
        out[y * w + x] = ((a / n) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (b / a)
    }
    return out
}
