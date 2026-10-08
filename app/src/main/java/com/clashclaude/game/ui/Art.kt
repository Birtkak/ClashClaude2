package com.clashclaude.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Small vector drawings for things that aren't baked sprites: spell flight effects, rubble,
// and fallbacks. Units, buildings and towers are 3D models baked to sprite sheets
// (tools/baker); see Sprites.kt.

private val Ink = Color(0xFF1B1B26)
private val Shine = Color(0x88FFFFFF)

/** The shadow-side tone of a colour for cel shading. */
private fun shade(c: Color) = Color(c.red * 0.58f, c.green * 0.56f, c.blue * 0.68f, c.alpha)
private const val OUT = 0.035f

private val Fire = Color(0xFFFF7A1A)
private val FireCore = Color(0xFFFFE082)

class Pen(
    private val ds: DrawScope,
    private val ox: Float,
    private val oy: Float,
    val u: Float,
    private val flip: Float = 1f,
    private val alpha: Float = 1f,
) {
    /** A pen whose origin is at (x, y) in this pen's space, [scale] times as large. */
    fun sub(x: Float, y: Float, scale: Float, flipOverride: Float? = null) =
        Pen(ds, sx(x), sy(y), u * scale, flipOverride ?: flip, alpha)

    fun sx(x: Float) = ox + x * u * flip
    fun sy(y: Float) = oy + y * u
    fun at(x: Float, y: Float) = Offset(sx(x), sy(y))
    private fun c(color: Color) = if (alpha >= 1f) color else color.copy(alpha = color.alpha * alpha)

    // Cel shading: outlined (main) shapes get a shadow side, a lit core shifted toward the light
    // (top-left of the screen, whichever way the unit faces) and a specular highlight, so the
    // flat figures read as solid little 3D toys. Small unoutlined details stay flat.

    fun circle(x: Float, y: Float, r: Float, fill: Color, outline: Boolean = true) {
        val center = at(x, y)
        val rp = r * u
        if (outline) {
            ds.drawCircle(c(shade(fill)), rp, center)
            ds.drawCircle(c(fill), rp * 0.84f, center + Offset(-rp * 0.13f, -rp * 0.13f))
            ds.drawCircle(c(Shine), rp * 0.26f, center + Offset(-rp * 0.4f, -rp * 0.42f))
            ds.drawCircle(c(Ink), rp, center, style = Stroke(OUT * u))
        } else {
            ds.drawCircle(c(fill), rp, center)
        }
    }

    fun oval(x: Float, y: Float, rx: Float, ry: Float, fill: Color, outline: Boolean = true) {
        val tl = Offset(min(sx(x - rx), sx(x + rx)), sy(y - ry))
        val size = Size(2 * rx * u, 2 * ry * u)
        if (outline) {
            ds.drawOval(c(shade(fill)), tl, size)
            ds.drawOval(c(fill), tl + Offset(size.width * 0.02f, size.height * 0.02f), Size(size.width * 0.84f, size.height * 0.82f))
            ds.drawOval(
                c(Shine),
                tl + Offset(size.width * 0.18f, size.height * 0.14f),
                Size(size.width * 0.28f, size.height * 0.22f),
            )
            ds.drawOval(c(Ink), tl, size, style = Stroke(OUT * u))
        } else {
            ds.drawOval(c(fill), tl, size)
        }
    }

    /** Rectangle centered on (x, y). */
    fun box(x: Float, y: Float, w: Float, h: Float, fill: Color, corner: Float = 0.06f, outline: Boolean = true) {
        val tl = Offset(min(sx(x - w / 2), sx(x + w / 2)), sy(y - h / 2))
        val size = Size(w * u, h * u)
        val cr = androidx.compose.ui.geometry.CornerRadius(corner * u)
        if (outline) {
            ds.drawRoundRect(c(shade(fill)), tl, size, cr)
            ds.drawRoundRect(c(fill), tl, Size(size.width * 0.88f, size.height * 0.8f), cr)
            ds.drawRoundRect(
                c(Shine),
                tl + Offset(size.width * 0.08f, size.height * 0.1f),
                Size(size.width * 0.6f, size.height * 0.14f),
                androidx.compose.ui.geometry.CornerRadius(size.height * 0.07f),
            )
            ds.drawRoundRect(c(Ink), tl, size, cr, style = Stroke(OUT * u))
        } else {
            ds.drawRoundRect(c(fill), tl, size, cr)
        }
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Color) {
        ds.drawLine(c(color), at(x1, y1), at(x2, y2), strokeWidth = w * u, cap = StrokeCap.Round)
    }

    /** A thick rounded stroke with an ink outline, shaded like a cylinder: arms, legs, handles. */
    fun limb(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Color) {
        val a = at(x1, y1)
        val b = at(x2, y2)
        val wp = w * u
        ds.drawLine(c(Ink), a, b, strokeWidth = wp + OUT * 2 * u, cap = StrokeCap.Round)
        ds.drawLine(c(shade(color)), a, b, strokeWidth = wp, cap = StrokeCap.Round)
        // Offset the lit band toward the light (screen up-left), across the limb.
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = kotlin.math.max(0.001f, kotlin.math.hypot(dx, dy))
        var nx = -dy / len
        var ny = dx / len
        if (nx + ny > 0f) { nx = -nx; ny = -ny }
        val lit = Offset(nx, ny) * (wp * 0.17f)
        ds.drawLine(c(color), a + lit, b + lit, strokeWidth = wp * 0.55f, cap = StrokeCap.Round)
        val spec = Offset(nx, ny) * (wp * 0.3f)
        ds.drawLine(c(Shine), a + spec, b + spec, strokeWidth = wp * 0.16f, cap = StrokeCap.Round)
    }

    fun poly(vararg pts: Float, fill: Color, outline: Boolean = true) {
        val path = polyPath(pts, 1f, 0f, 0f)
        if (outline) {
            ds.drawPath(path, c(shade(fill)))
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var i = 1
            while (i < pts.size) {
                minY = min(minY, pts[i]); maxY = kotlin.math.max(maxY, pts[i]); i += 2
            }
            ds.drawPath(polyPath(pts, 0.82f, -0.02f, -(maxY - minY) * 0.06f), c(fill))
            ds.drawPath(path, c(Ink), style = Stroke(OUT * u, join = StrokeJoin.Round))
        } else {
            ds.drawPath(path, c(fill))
        }
    }

    /** The polygon scaled about its centroid by [k] and shifted (screen-space x by [dx], sprite y by [dy]). */
    private fun polyPath(pts: FloatArray, k: Float, dx: Float, dy: Float): Path {
        var cx = 0f
        var cy = 0f
        val n = pts.size / 2
        for (i in 0 until n) { cx += pts[i * 2]; cy += pts[i * 2 + 1] }
        cx /= n; cy /= n
        val path = Path()
        for (i in 0 until n) {
            val px = cx + (pts[i * 2] - cx) * k
            val py = cy + (pts[i * 2 + 1] - cy) * k + dy
            val sxp = sx(px) + dx * u
            if (i == 0) path.moveTo(sxp, sy(py)) else path.lineTo(sxp, sy(py))
        }
        path.close()
        return path
    }

    fun arc(x: Float, y: Float, r: Float, startDeg: Float, sweepDeg: Float, w: Float, color: Color) {
        // Mirroring flips the arc's direction too.
        val start = if (flip < 0) 180f - startDeg - sweepDeg else startDeg
        ds.drawArc(
            c(color), start, sweepDeg, false,
            topLeft = Offset(sx(x) - r * u, sy(y) - r * u), size = Size(2 * r * u, 2 * r * u),
            style = Stroke(w * u, cap = StrokeCap.Round),
        )
    }
}

/** Point at [dist] along [angle] from (x, y), in sprite space. */
private fun Pen.fireOrb(x: Float, y: Float, r: Float, time: Float) {
    val flicker = 1f + sin(time * 20f) * 0.08f
    circle(x, y, r * 1.5f * flicker, Fire.copy(alpha = 0.35f), outline = false)
    circle(x, y, r * flicker, Fire, outline = false)
    circle(x, y, r * 0.55f, FireCore, outline = false)
}

/** Pile of stones where a tower used to be. */
fun Pen.rubble(king: Boolean) {
    val w = if (king) 3.0f else 2.4f
    oval(0f, 0.6f, w * 0.55f, 0.5f, Color(0xFF6D6F78))
    val stones = listOf(-0.7f to 0.3f, 0.2f to 0.15f, 0.8f to 0.45f, -0.2f to 0.6f, 0.5f to 0.75f, -0.85f to 0.75f)
    for ((x, y) in stones) circle(x * w / 2.4f, y, 0.28f, Color(0xFF9EA2AC))
}

// ------------------------------------------------------------------ spells

/** Spell art for cards and the drag ghost, centered on the origin, about 1 unit across. */
fun Pen.spellIcon(id: String, time: Float) {
    when (id) {
        "fireball" -> {
            for (i in 1..3) circle(-0.18f * i, 0.18f * i, 0.3f - i * 0.07f, Fire.copy(alpha = 0.6f - i * 0.15f), outline = false)
            fireOrb(0.05f, -0.05f, 0.32f, time)
        }
        "freeze" -> {
            // A giant air conditioner blowing freezing air.
            box(0f, -0.1f, 0.95f, 0.55f, Color(0xFFECEFF1), corner = 0.08f)
            for (i in 0..3) line(-0.38f, -0.26f + i * 0.08f, 0.1f, -0.26f + i * 0.08f, 0.03f, Color(0xFF90A4AE))
            circle(0.27f, -0.12f, 0.13f, Color(0xFFCFD8DC))
            circle(0.27f, -0.12f, 0.04f, Color(0xFF4FC3F7), outline = false)
            box(0f, 0.12f, 0.8f, 0.05f, Color(0xFF263238), corner = 0.02f, outline = false)
            for (i in -1..1) {
                val wave = sin(time * 8f + i) * 0.04f
                line(i * 0.25f, 0.22f, i * 0.28f + wave, 0.48f, 0.04f, Color(0xFF81D4FA))
            }
            // Snowflake.
            for (k in 0..2) {
                val a = k * PI.toFloat() / 3f
                line(-0.42f - cos(a) * 0.1f, 0.38f - sin(a) * 0.1f, -0.42f + cos(a) * 0.1f, 0.38f + sin(a) * 0.1f, 0.03f, Color.White)
            }
        }
        "zap" -> poly(
            0.1f, -0.5f, -0.25f, 0.05f, 0f, 0.05f, -0.15f, 0.5f, 0.3f, -0.1f, 0.05f, -0.1f, 0.2f, -0.5f,
            fill = Color(0xFF80D8FF),
        )
    }
}
