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

// Hand-drawn (in code) cartoon sprites for every unit, building and tower. Each sprite is
// drawn in its own coordinate space: feet at (0, 0), up is negative y, a normal-sized
// character is about 1 unit tall and faces right (+x). Pen maps that space to the screen,
// including mirroring for units that face left.

/** One frame of a character's animation. */
class Pose(
    /** Walk-cycle phase in radians. */
    val walk: Float = 0f,
    val moving: Boolean = false,
    /** Melee weapon: 1 = wound up, 0 = rest, -1 = follow-through after a hit. */
    val swing: Float = 0f,
    /** Aim angle for bows/guns in sprite space: 0 = straight ahead, negative = up. */
    val aim: Float = 0f,
    /** 1 right after a ranged shot, fading to 0. */
    val recoil: Float = 0f,
    /** Seconds, for idle loops such as wings and flames. */
    val time: Float = 0f,
    val asleep: Boolean = false,
) {
    companion object {
        val IDLE = Pose()
    }
}

private val Ink = Color(0xFF1B1B26)
private val Shine = Color(0x88FFFFFF)

/** The shadow-side tone of a colour for cel shading. */
private fun shade(c: Color) = Color(c.red * 0.58f, c.green * 0.56f, c.blue * 0.68f, c.alpha)
private const val OUT = 0.035f

private val Skin = Color(0xFFF6C9A0)
private val GoblinSkin = Color(0xFF8BC34A)
private val Bone = Color(0xFFF1EEE4)
private val Steel = Color(0xFFB8C4CE)
private val SteelDark = Color(0xFF7D8B97)
private val Wood = Color(0xFF8D5A2B)
private val WoodDark = Color(0xFF5D3A1A)
private val Leather = Color(0xFF7A4B2A)
private val Boot = Color(0xFF4A3426)
private val Gold = Color(0xFFFFCA28)
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
private fun along(x: Float, y: Float, angle: Float, dist: Float) =
    (x + cos(angle) * dist) to (y + sin(angle) * dist)

// ------------------------------------------------------------------ humanoids

private enum class Hold { MELEE, AIM, RAISE }

private class Body(
    val shirt: Color,
    val pants: Color,
    val skin: Color = Skin,
    val shoes: Color = Boot,
    val headR: Float = 0.2f,
    val torsoW: Float = 0.36f,
    val torsoH: Float = 0.34f,
    val limbW: Float = 0.11f,
    val robe: Boolean = false,
    val bony: Boolean = false,
)

/**
 * Draws a standard character. [head] decorates the head (helmet, hair...), [weapon]
 * draws what's in the front hand given the hand position and weapon angle, and
 * [offHand] draws what's in the back hand (a shield, say).
 */
private fun Pen.humanoid(
    body: Body,
    pose: Pose,
    hold: Hold,
    head: Pen.(hx: Float, hy: Float, r: Float) -> Unit = { _, _, _ -> },
    weapon: Pen.(hx: Float, hy: Float, angle: Float) -> Unit = { _, _, _ -> },
    offHand: (Pen.(hx: Float, hy: Float) -> Unit)? = null,
) {
    val bob = if (pose.moving) -abs(sin(pose.walk)) * 0.05f else 0f
    val stride = if (pose.moving) sin(pose.walk) * 0.13f else 0f
    val limb = if (body.bony) body.limbW * 0.55f else body.limbW
    val shoulderY = -0.6f + bob
    val hipY = -0.3f + bob

    // Back arm, behind everything.
    val backHand = (-0.16f - stride * 0.7f) to (shoulderY + 0.26f)
    limb(-0.1f, shoulderY, backHand.first, backHand.second, limb, if (body.bony) Bone else body.skin)
    offHand?.invoke(this, backHand.first, backHand.second)

    // Legs (hidden under a robe).
    if (!body.robe) {
        val legColor = if (body.bony) Bone else body.pants
        limb(-0.07f, hipY, -0.07f - stride, -0.04f, limb, legColor)
        oval(-0.07f - stride + 0.03f, -0.02f, 0.08f, 0.045f, body.shoes)
        limb(0.07f, hipY, 0.07f + stride, -0.04f, limb, legColor)
        oval(0.07f + stride + 0.03f, -0.02f, 0.08f, 0.045f, body.shoes)
    }

    // Torso.
    if (body.robe) {
        poly(
            -body.torsoW / 2, shoulderY, body.torsoW / 2, shoulderY,
            body.torsoW / 2 + 0.12f, 0f, -body.torsoW / 2 - 0.12f, 0f,
            fill = body.shirt,
        )
    } else if (body.bony) {
        // Spine and ribs.
        line(0f, shoulderY, 0f, hipY, 0.05f, Bone)
        for (i in 0..2) {
            val ry = shoulderY + 0.06f + i * 0.08f
            limb(-0.12f + i * 0.02f, ry, 0.12f - i * 0.02f, ry, 0.035f, Bone)
        }
    } else {
        box(0f, (shoulderY + hipY) / 2f - 0.01f, body.torsoW, body.torsoH, body.shirt, corner = 0.1f)
    }

    // Head.
    val hy = -0.82f + bob
    val r = body.headR
    if (body.bony) {
        circle(0f, hy, r, Bone)
        circle(0.07f, hy - 0.01f, r * 0.28f, Ink, outline = false)
        circle(-0.04f, hy - 0.01f, r * 0.24f, Ink, outline = false)
        line(0.02f, hy + r * 0.55f, 0.12f, hy + r * 0.55f, 0.03f, Ink)
    } else {
        circle(0f, hy, r, body.skin)
        if (pose.asleep) {
            line(0.06f, hy, 0.13f, hy, 0.03f, Ink)
        } else {
            circle(0.1f, hy - 0.02f, r * 0.13f, Ink, outline = false)
        }
    }
    head(0f, hy, r)

    // Front arm and weapon.
    val shoulderX = 0.1f
    val (handAngle, weaponAngle) = when (hold) {
        Hold.MELEE -> (0.75f - pose.swing * 0.95f) to (-1.0f - pose.swing * 1.4f)
        Hold.AIM -> pose.aim.coerceIn(-1.3f, 1.2f).let { it to it }
        // Hand up beside the head, higher on wind-up, flung forward on the throw.
        Hold.RAISE -> (-0.85f - max(0f, pose.swing) * 0.45f + max(0f, -pose.swing) * 1.2f) to -1.4f
    }
    val (hx, hy2) = along(shoulderX, shoulderY, handAngle, 0.26f)
    weapon(hx, hy2, weaponAngle)
    limb(shoulderX, shoulderY, hx, hy2, limb, if (body.bony) Bone else body.skin)
    circle(hx, hy2, limb * 0.6f, if (body.bony) Bone else body.skin)
}

// ------------------------------------------------------------------ weapons

private fun Pen.sword(hx: Float, hy: Float, a: Float, length: Float = 0.5f, width: Float = 0.07f) {
    val (tx, ty) = along(hx, hy, a, length)
    val (bx, by) = along(hx, hy, a, -0.08f)
    limb(bx, by, hx, hy, 0.05f, WoodDark)
    limb(hx, hy, tx, ty, width, Steel)
    line(hx, hy, tx, ty, width * 0.3f, Color.White)
    val (g1x, g1y) = along(hx, hy, a + PI.toFloat() / 2, 0.08f)
    val (g2x, g2y) = along(hx, hy, a - PI.toFloat() / 2, 0.08f)
    limb(g1x, g1y, g2x, g2y, 0.04f, Gold)
}

private fun Pen.axe(hx: Float, hy: Float, a: Float) {
    val (tx, ty) = along(hx, hy, a, 0.5f)
    val (bx, by) = along(hx, hy, a, -0.1f)
    limb(bx, by, tx, ty, 0.055f, Wood)
    // Double-bladed head.
    val perp = a + PI.toFloat() / 2
    val (b1x, b1y) = along(tx, ty, perp, 0.2f)
    val (b2x, b2y) = along(tx, ty, perp, -0.2f)
    val (fx, fy) = along(tx, ty, a, 0.1f)
    val (rx, ry) = along(tx, ty, a, -0.1f)
    poly(rx, ry, b1x, b1y, fx, fy, fill = Steel)
    poly(rx, ry, b2x, b2y, fx, fy, fill = Steel)
}

private fun Pen.spear(hx: Float, hy: Float, a: Float, length: Float = 0.75f) {
    val (tx, ty) = along(hx, hy, a, length * 0.75f)
    val (bx, by) = along(hx, hy, a, -length * 0.25f)
    limb(bx, by, tx, ty, 0.045f, Wood)
    val perp = a + PI.toFloat() / 2
    val (px, py) = along(tx, ty, a, 0.16f)
    val (l1x, l1y) = along(tx, ty, perp, 0.06f)
    val (l2x, l2y) = along(tx, ty, perp, -0.06f)
    poly(l1x, l1y, px, py, l2x, l2y, fill = Steel)
}

private fun Pen.bow(hx: Float, hy: Float, a: Float, pull: Float) {
    val perp = a + PI.toFloat() / 2
    val (t1x, t1y) = along(hx, hy, perp, 0.3f)
    val (t2x, t2y) = along(hx, hy, perp, -0.3f)
    val (mx, my) = along(hx, hy, a, 0.1f)
    // Bow limbs as two strokes bent forward.
    limb(t1x, t1y, mx, my, 0.05f, Wood)
    limb(t2x, t2y, mx, my, 0.05f, Wood)
    val (sx, sy) = along(hx, hy, a, -0.05f - pull * 0.15f)
    line(t1x, t1y, sx, sy, 0.015f, Color.White)
    line(t2x, t2y, sx, sy, 0.015f, Color.White)
    if (pull > 0.2f) {
        val (ax, ay) = along(sx, sy, a, 0.45f)
        line(sx, sy, ax, ay, 0.025f, WoodDark)
    }
}

private fun Pen.musket(hx: Float, hy: Float, a: Float, recoil: Float) {
    val back = recoil * 0.08f
    val (sx, sy) = along(hx, hy, a, -0.2f - back)
    val (mx, my) = along(hx, hy, a, 0.6f - back)
    limb(sx, sy, hx, hy, 0.09f, Wood)
    limb(hx, hy, mx, my, 0.05f, SteelDark)
    if (recoil > 0.5f) {
        val (fx, fy) = along(mx, my, a, 0.1f)
        circle(fx, fy, 0.09f * recoil, Gold, outline = false)
        circle(fx, fy, 0.05f * recoil, Color.White, outline = false)
    }
}

private fun Pen.hammer(hx: Float, hy: Float, a: Float) {
    val (tx, ty) = along(hx, hy, a, 0.42f)
    val (bx, by) = along(hx, hy, a, -0.08f)
    limb(bx, by, tx, ty, 0.05f, Wood)
    val perp = a + PI.toFloat() / 2
    val (h1x, h1y) = along(tx, ty, perp, 0.13f)
    val (h2x, h2y) = along(tx, ty, perp, -0.13f)
    limb(h1x, h1y, h2x, h2y, 0.15f, SteelDark)
}

private fun Pen.fireOrb(x: Float, y: Float, r: Float, time: Float) {
    val flicker = 1f + sin(time * 20f) * 0.08f
    circle(x, y, r * 1.5f * flicker, Fire.copy(alpha = 0.35f), outline = false)
    circle(x, y, r * flicker, Fire, outline = false)
    circle(x, y, r * 0.55f, FireCore, outline = false)
}

private fun Pen.bomb(x: Float, y: Float, r: Float, time: Float) {
    circle(x, y, r, Color(0xFF263238))
    circle(x - r * 0.35f, y - r * 0.35f, r * 0.25f, Color(0x88FFFFFF), outline = false)
    line(x + r * 0.5f, y - r * 0.8f, x + r * 0.8f, y - r * 1.3f, 0.025f, WoodDark)
    if (sin(time * 25f) > -0.3f) circle(x + r * 0.85f, y - r * 1.4f, r * 0.3f, Gold, outline = false)
}

// ------------------------------------------------------------------ head pieces

private fun Pen.helmet(hx: Float, hy: Float, r: Float, color: Color, plume: Color?) {
    // Dome over the top half of the head with a nose guard.
    poly(
        -r * 1.05f, hy + r * 0.1f, -r * 0.9f, hy - r * 0.8f, 0f, hy - r * 1.1f,
        r * 0.9f, hy - r * 0.8f, r * 1.05f, hy + r * 0.1f,
        fill = color,
    )
    line(r * 0.55f, hy - r * 0.2f, r * 0.55f, hy + r * 0.45f, 0.04f, color)
    plume?.let {
        poly(-r * 0.2f, hy - r * 1.05f, -r * 1.1f, hy - r * 1.5f, -r * 0.9f, hy - r * 0.9f, fill = it)
    }
}

private fun Pen.hair(hx: Float, hy: Float, r: Float, color: Color) {
    poly(
        -r * 1.05f, hy + r * 0.2f, -r * 0.95f, hy - r * 0.75f, -r * 0.2f, hy - r * 1.1f,
        r * 0.7f, hy - r * 0.95f, r * 0.95f, hy - r * 0.4f, r * 0.3f, hy - r * 0.6f, -r * 0.3f, hy - r * 0.2f,
        fill = color,
    )
}

private fun Pen.mustache(hx: Float, hy: Float, r: Float, color: Color) {
    poly(r * 0.1f, hy + r * 0.35f, r * 1.0f, hy + r * 0.3f, r * 0.85f, hy + r * 0.6f, r * 0.3f, hy + r * 0.5f, fill = color)
}

private fun Pen.goblinEars(hx: Float, hy: Float, r: Float, skin: Color) {
    poly(-r * 0.6f, hy - r * 0.2f, -r * 1.8f, hy - r * 0.6f, -r * 0.8f, hy + r * 0.3f, fill = skin)
    // Big nose.
    oval(r * 0.95f, hy + r * 0.15f, r * 0.35f, r * 0.22f, skin)
}

// ------------------------------------------------------------------ units

/** Rough sprite height (in sprite units) for placing HP bars above a unit. */
fun spriteTop(id: String): Float = when (id) {
    "babydragon", "minions" -> -1.1f
    "hogrider" -> -1.25f
    "pekka", "minipekka" -> -1.15f
    "cannon" -> -0.9f
    "tesla" -> -1.5f
    "infernotower" -> -1.6f
    "tombstone" -> -1.0f
    "megaminion" -> -1.15f
    "royalgiant" -> -1.2f
    "witch" -> -1.35f
    else -> -1.12f
}

/** Draws the unit for card [id] with its feet at the pen origin. [team] tints capes, plumes and flags. */
fun Pen.unit(id: String, team: Color, pose: Pose) {
    when (id) {
        "knight" -> humanoid(
            Body(shirt = Steel, pants = SteelDark), pose, Hold.MELEE,
            head = { x, y, r ->
                mustache(x, y, r, Color(0xFF6D4C41))
                helmet(x, y, r, Steel, team)
            },
            weapon = { x, y, a -> sword(x, y, a) },
            offHand = { x, y ->
                circle(x - 0.04f, y - 0.08f, 0.17f, team)
                circle(x - 0.04f, y - 0.08f, 0.07f, Steel)
            },
        )
        "archers" -> humanoid(
            Body(shirt = Color(0xFF43A047), pants = Color(0xFF2E7D32)), pose, Hold.AIM,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFEC407A))
                // Ponytail.
                oval(x - r * 1.2f, y + r * 0.2f, r * 0.35f, r * 0.6f, Color(0xFFEC407A))
                box(x, y - r * 0.85f, r * 1.6f, r * 0.3f, team, corner = 0.03f)
            },
            weapon = { x, y, a -> bow(x, y, a, pull = max(0f, pose.swing)) },
        )
        "giant" -> humanoid(
            Body(shirt = Color(0xFFE08A3C), pants = Color(0xFF6D4C41), torsoW = 0.44f, torsoH = 0.38f, limbW = 0.14f),
            pose, Hold.MELEE,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFD84315))
                // Beard.
                poly(-r * 0.3f, y + r * 0.2f, r * 0.95f, y + r * 0.2f, r * 0.5f, y + r * 1.2f, -r * 0.1f, y + r * 0.9f, fill = Color(0xFFD84315))
                line(-r * 0.9f, y + r * 1.3f, r * 0.9f, y + r * 1.3f, 0.05f, team)
            },
            weapon = { x, y, _ ->
                // A big fist that punches forward on the hit.
                val punch = if (pose.swing < 0f) -pose.swing * 0.15f else 0f
                circle(x + punch, y, 0.14f, Skin)
            },
        )
        "musketeer" -> humanoid(
            Body(shirt = Color(0xFF5E35B1), pants = Color(0xFF311B92)), pose, Hold.AIM,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFF3E2723))
                // Morion helmet with a brim.
                poly(-r * 0.8f, y - r * 0.5f, 0f, y - r * 1.3f, r * 0.8f, y - r * 0.5f, fill = Steel)
                line(-r * 1.3f, y - r * 0.5f, r * 1.3f, y - r * 0.5f, 0.05f, Steel)
                circle(x, y - r * 1.3f, 0.04f, team)
            },
            weapon = { x, y, a -> musket(x, y, a, pose.recoil) },
        )
        "minipekka" -> armored(pose, team, big = false)
        "pekka" -> armored(pose, team, big = true)
        "goblins" -> humanoid(
            Body(shirt = Color(0xFF8D6E63), pants = Color(0xFF5D4037), skin = GoblinSkin, headR = 0.22f),
            pose, Hold.MELEE,
            head = { x, y, r ->
                goblinEars(x, y, r, GoblinSkin)
                poly(-r * 0.9f, y - r * 0.5f, 0f, y - r * 1.2f, r * 0.9f, y - r * 0.5f, fill = team)
            },
            weapon = { x, y, a -> sword(x, y, a, length = 0.25f, width = 0.05f) },
        )
        "speargoblins" -> humanoid(
            Body(shirt = Color(0xFF9CCC65), pants = Color(0xFF558B2F), skin = GoblinSkin, headR = 0.22f),
            pose, Hold.AIM,
            head = { x, y, r ->
                goblinEars(x, y, r, GoblinSkin)
                box(x, y - r * 0.9f, r * 1.7f, r * 0.35f, team, corner = 0.03f)
            },
            weapon = { x, y, a -> if (pose.recoil < 0.3f) spear(x, y, a - 0.6f, length = 0.6f) },
        )
        "skeletons" -> humanoid(
            Body(shirt = Bone, pants = Bone, bony = true, headR = 0.21f), pose, Hold.MELEE,
            head = { x, y, r -> line(-r * 0.8f, y - r * 0.85f, r * 0.8f, y - r * 0.85f, 0.04f, team) },
            weapon = { x, y, a -> sword(x, y, a, length = 0.3f, width = 0.05f) },
        )
        "bomber" -> humanoid(
            Body(shirt = Bone, pants = Bone, bony = true, headR = 0.21f), pose, Hold.RAISE,
            head = { x, y, r ->
                // Bandana.
                poly(-r, y - r * 0.4f, r, y - r * 0.4f, r * 0.9f, y - r * 1.0f, -r * 0.9f, y - r * 1.0f, fill = team)
            },
            weapon = { x, y, _ -> if (pose.recoil < 0.3f) bomb(x, y - 0.1f, 0.13f, pose.time) },
        )
        "valkyrie" -> humanoid(
            Body(shirt = Color(0xFF8D6E63), pants = Color(0xFF5D4037), torsoW = 0.38f), pose, Hold.MELEE,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFFF7043))
                // Braids.
                oval(x - r * 0.9f, y + r * 0.9f, r * 0.25f, r * 0.6f, Color(0xFFFF7043))
                oval(x + r * 0.2f, y + r * 1.0f, r * 0.22f, r * 0.5f, Color(0xFFFF7043))
                line(-r * 0.9f, y - r * 0.75f, r * 0.9f, y - r * 0.75f, 0.05f, team)
            },
            weapon = { x, y, a ->
                // Spin attack: the axe sweeps all the way around right after a hit.
                val spin = if (pose.swing < 0f) (1f + pose.swing) * 2f * PI.toFloat() else 0f
                axe(x, y, a + spin)
            },
        )
        "barbarians" -> humanoid(
            Body(shirt = Color(0xFF795548), pants = Color(0xFF4E342E), torsoW = 0.38f), pose, Hold.MELEE,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFFFD54F))
                mustache(x, y, r * 1.2f, Color(0xFFFFD54F))
                helmet(x, y, r, SteelDark, null)
                // Horns.
                poly(-r * 0.9f, y - r * 0.6f, -r * 1.5f, y - r * 1.4f, -r * 0.5f, y - r * 0.9f, fill = Bone)
                poly(r * 0.9f, y - r * 0.6f, r * 1.4f, y - r * 1.4f, r * 0.5f, y - r * 0.9f, fill = Bone)
                line(-r, y - r * 0.15f, r, y - r * 0.15f, 0.04f, team)
            },
            weapon = { x, y, a -> sword(x, y, a, length = 0.45f) },
        )
        "wizard" -> humanoid(
            Body(shirt = Color(0xFF1E88E5), pants = Color(0xFF1565C0), robe = true), pose, Hold.AIM,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFF5D4037))
                // Pointy hat with a team band.
                poly(-r * 1.3f, y - r * 0.5f, r * 1.3f, y - r * 0.5f, -r * 0.5f, y - r * 2.6f, fill = Color(0xFF1565C0))
                line(-r * 0.9f, y - r * 0.75f, r * 0.9f, y - r * 0.75f, 0.06f, team)
            },
            weapon = { x, y, _ ->
                val size = if (pose.recoil > 0f) 0.08f + pose.recoil * 0.06f else 0.08f + max(0f, pose.swing) * 0.06f
                fireOrb(x, y - 0.05f, size, pose.time)
            },
        )
        "hogrider" -> hogRider(pose, team)
        "babydragon" -> babyDragon(pose, team)
        "minions" -> minion(pose, team)
        "megaminion" -> megaMinion(pose, team)
        "cannon" -> cannon(team, pose)
        "tesla" -> tesla(team, pose)
        "infernotower" -> infernoTower(team, pose)
        "tombstone" -> tombstone(team)
        "witch" -> humanoid(
            Body(shirt = Color(0xFF6A1B9A), pants = Color(0xFF4A148C), skin = Color(0xFFE0C3A8), robe = true), pose, Hold.AIM,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFAB47BC))
                // Long hair down the back and a crooked dark hat.
                oval(x - r * 0.9f, y + r * 0.9f, r * 0.35f, r * 0.9f, Color(0xFFAB47BC))
                poly(-r * 1.5f, y - r * 0.5f, r * 1.4f, y - r * 0.5f, r * 0.2f, y - r * 1.4f, -r * 0.9f, y - r * 2.6f, -r * 0.3f, y - r * 1.2f, fill = Color(0xFF311B42))
                line(-r * 1.0f, y - r * 0.75f, r * 1.0f, y - r * 0.75f, 0.06f, team)
            },
            weapon = { x, y, a ->
                // Bone staff topped with a glowing skull.
                val (tx, ty) = along(x, y, -1.45f, 0.55f)
                val (bx, by) = along(x, y, -1.45f, -0.35f)
                limb(bx, by, tx, ty, 0.05f, Bone)
                val glow = 0.1f + pose.recoil * 0.06f
                circle(tx, ty - 0.05f, glow * 1.8f, Color(0x66CE93D8), outline = false)
                circle(tx, ty - 0.05f, 0.09f, Bone)
                circle(tx + 0.03f, ty - 0.06f, 0.02f, Color(0xFFE040FB), outline = false)
                circle(tx - 0.03f, ty - 0.06f, 0.02f, Color(0xFFE040FB), outline = false)
            },
        )
        "royalgiant" -> humanoid(
            Body(shirt = Color(0xFF1565C0), pants = Color(0xFF5D4037), torsoW = 0.44f, torsoH = 0.38f, limbW = 0.14f),
            pose, Hold.AIM,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFFFB300))
                poly(-r * 0.3f, y + r * 0.2f, r * 0.95f, y + r * 0.2f, r * 0.5f, y + r * 1.2f, -r * 0.1f, y + r * 0.9f, fill = Color(0xFFFFB300))
                // Gold crown.
                poly(
                    -r * 0.8f, y - r * 0.7f, -r * 0.8f, y - r * 1.5f, -r * 0.4f, y - r * 1.05f, 0f, y - r * 1.6f,
                    r * 0.4f, y - r * 1.05f, r * 0.8f, y - r * 1.5f, r * 0.8f, y - r * 0.7f, fill = Gold,
                )
                line(-r * 0.9f, y + r * 1.3f, r * 0.9f, y + r * 1.3f, 0.05f, team)
            },
            weapon = { x, y, a ->
                // Hand cannon.
                val back = pose.recoil * 0.1f
                val (sx, sy) = along(x, y, a, -0.15f - back)
                val (mx, my) = along(x, y, a, 0.45f - back)
                limb(sx, sy, mx, my, 0.18f, Color(0xFF37474F))
                circle(mx, my, 0.08f, Color(0xFF263238))
                if (pose.recoil > 0.5f) {
                    val (fx, fy) = along(mx, my, a, 0.12f)
                    circle(fx, fy, 0.12f * pose.recoil, Gold, outline = false)
                }
            },
        )
        else -> humanoid(Body(shirt = team, pants = SteelDark), pose, Hold.MELEE)
    }
}

/** P.E.K.K.A and Mini P.E.K.K.A: blocky armor, horns, one glowing eye, a big sword. */
private fun Pen.armored(pose: Pose, team: Color, big: Boolean) {
    // Mini P.E.K.K.A is bright blue; the full-size P.E.K.K.A is dark purple.
    val armor = if (big) Color(0xFF311B5E) else Color(0xFF1E6FD9)
    val armorLight = if (big) Color(0xFF4A2C8A) else Color(0xFF42A5F5)
    val eye = if (big) Color(0xFFE040FB) else Color(0xFF18FFFF)
    humanoid(
        Body(shirt = armor, pants = armorLight, shoes = armor, skin = armorLight, headR = 0.22f, torsoW = 0.42f, limbW = 0.13f),
        pose, Hold.MELEE,
        head = { x, y, r ->
            circle(x, y, r * 1.05f, armor)
            // Horns.
            poly(-r * 0.6f, y - r * 0.7f, -r * 1.2f, y - r * 1.7f, -r * 0.1f, y - r * 0.95f, fill = Steel)
            poly(r * 0.4f, y - r * 0.8f, r * 0.9f, y - r * 1.7f, r * 0.8f, y - r * 0.5f, fill = Steel)
            box(x + r * 0.45f, y, r * 0.8f, r * 0.35f, Ink, corner = 0.02f, outline = false)
            circle(x + r * 0.55f, y, r * 0.16f, eye, outline = false)
            line(-r * 0.9f, y + r * 0.6f, r * 0.9f, y + r * 0.6f, 0.04f, team)
        },
        weapon = { x, y, a -> sword(x, y, a, length = if (big) 0.75f else 0.55f, width = if (big) 0.12f else 0.09f) },
    )
}

private fun Pen.hogRider(pose: Pose, team: Color) {
    val trot = if (pose.moving) sin(pose.walk * 1.5f) * 0.1f else 0f
    val hog = Color(0xFF8D6E63)
    // Hog legs.
    for (lx in floatArrayOf(-0.32f, -0.14f, 0.16f, 0.34f)) {
        val swing = if ((lx * 10).toInt() % 2 == 0) trot else -trot
        limb(lx, -0.25f, lx + swing, -0.02f, 0.1f, Color(0xFF6D4C41))
    }
    oval(0f, -0.36f, 0.48f, 0.22f, hog)
    // Head with snout and tusks.
    circle(0.44f, -0.42f, 0.17f, hog)
    oval(0.6f, -0.38f, 0.08f, 0.07f, Color(0xFFF8BBD0))
    poly(0.5f, -0.3f, 0.6f, -0.22f, 0.54f, -0.32f, fill = Bone)
    circle(0.47f, -0.5f, 0.03f, Ink, outline = false)
    poly(0.36f, -0.55f, 0.32f, -0.68f, 0.45f, -0.58f, fill = hog)
    // Saddle blanket in team color.
    box(-0.05f, -0.55f, 0.36f, 0.08f, team, corner = 0.03f)
    // Rider sits on the hog.
    val riderPose = Pose(swing = pose.swing, time = pose.time)
    val rider = sub(-0.05f, -0.42f, 0.85f)
    rider.humanoid(
        Body(shirt = Color(0xFFBCAAA4), pants = Color(0xFF3E2723)), riderPose, Hold.MELEE,
        head = { x, y, r ->
            // Mohawk.
            poly(-r * 0.6f, y - r * 0.7f, -r * 0.2f, y - r * 1.7f, r * 0.4f, y - r * 0.9f, fill = Ink)
            mustache(x, y, r, Ink)
        },
        weapon = { x, y, a -> hammer(x, y, a) },
    )
}

private fun Pen.babyDragon(pose: Pose, team: Color) {
    val green = Color(0xFF66BB6A)
    val belly = Color(0xFFFFF59D)
    val flap = sin(pose.time * 10f) * 0.18f
    // Wings.
    poly(-0.05f, -0.55f, -0.55f, -0.95f + flap, -0.45f, -0.5f, fill = Color(0xFF388E3C))
    poly(0.05f, -0.55f, 0.35f, -1.0f + flap, 0.3f, -0.55f, fill = Color(0xFF43A047))
    // Tail.
    poly(-0.25f, -0.35f, -0.62f, -0.2f, -0.2f, -0.22f, fill = green)
    oval(0f, -0.38f, 0.3f, 0.24f, green)
    oval(0.06f, -0.32f, 0.17f, 0.14f, belly, outline = false)
    // Head and snout.
    circle(0.28f, -0.62f, 0.17f, green)
    oval(0.43f, -0.58f, 0.1f, 0.07f, green)
    circle(0.31f, -0.67f, 0.035f, Ink, outline = false)
    poly(0.18f, -0.75f, 0.12f, -0.88f, 0.26f, -0.78f, fill = belly)
    line(0.05f, -0.6f, 0.2f, -0.6f, 0.04f, team)
    // Fire breath right after an attack.
    if (pose.recoil > 0f) {
        circle(0.58f, -0.56f, 0.1f * pose.recoil + 0.04f, Fire, outline = false)
        circle(0.58f, -0.56f, 0.05f * pose.recoil + 0.02f, FireCore, outline = false)
    }
}

private fun Pen.minion(pose: Pose, team: Color) {
    val blue = Color(0xFF42A5F5)
    val flap = sin(pose.time * 14f) * 0.15f
    poly(-0.08f, -0.55f, -0.5f, -0.85f + flap, -0.38f, -0.45f, fill = Color(0xFF1E88E5))
    poly(0.08f, -0.55f, 0.42f, -0.88f + flap, 0.34f, -0.48f, fill = Color(0xFF1976D2))
    oval(0f, -0.45f, 0.17f, 0.18f, blue)
    circle(0.04f, -0.7f, 0.15f, blue)
    // Horns and eyes.
    poly(-0.08f, -0.8f, -0.14f, -0.95f, -0.02f, -0.84f, fill = Bone)
    poly(0.1f, -0.82f, 0.14f, -0.97f, 0.16f, -0.8f, fill = Bone)
    circle(0.1f, -0.71f, 0.03f, Ink, outline = false)
    line(-0.12f, -0.36f, 0.12f, -0.36f, 0.04f, team)
    if (pose.recoil > 0.3f) circle(0.25f, -0.55f, 0.06f, Color(0xFF7E57C2), outline = false)
}

private fun Pen.cannon(team: Color, pose: Pose) {
    // Wooden platform.
    box(0f, -0.2f, 0.9f, 0.4f, Wood, corner = 0.08f)
    line(-0.4f, -0.2f, 0.4f, -0.2f, 0.03f, WoodDark)
    circle(-0.3f, -0.05f, 0.12f, WoodDark)
    circle(0.3f, -0.05f, 0.12f, WoodDark)
    // Barrel turns toward its target and kicks back when it fires.
    val a = pose.aim.coerceIn(-1.6f, 1.6f)
    val back = pose.recoil * 0.1f
    val (bx, by) = along(0f, -0.45f, a, -back)
    val (mx, my) = along(bx, by, a, 0.5f)
    limb(bx, by, mx, my, 0.24f, Color(0xFF37474F))
    circle(mx, my, 0.1f, Color(0xFF263238))
    circle(bx, by, 0.18f, Color(0xFF455A64))
    box(0f, -0.02f, 0.5f, 0.06f, team, corner = 0.02f, outline = false)
}

private fun Pen.megaMinion(pose: Pose, team: Color) {
    val blue = Color(0xFF1E5AA8)
    val armor = Color(0xFF78909C)
    val flap = sin(pose.time * 10f) * 0.15f
    poly(-0.1f, -0.6f, -0.62f, -0.95f + flap, -0.45f, -0.45f, fill = Color(0xFF0D47A1))
    poly(0.1f, -0.6f, 0.55f, -0.98f + flap, 0.42f, -0.5f, fill = Color(0xFF1565C0))
    oval(0f, -0.48f, 0.22f, 0.22f, blue)
    box(0f, -0.5f, 0.32f, 0.22f, armor, corner = 0.06f)
    circle(0.04f, -0.78f, 0.17f, blue)
    // Helmet with a visor slit.
    poly(-0.15f, -0.8f, -0.12f, -0.97f, 0.06f, -1.0f, 0.2f, -0.88f, 0.2f, -0.78f, fill = armor)
    line(0.06f, -0.82f, 0.18f, -0.82f, 0.035f, Color(0xFFFF5252))
    line(-0.16f, -0.36f, 0.16f, -0.36f, 0.05f, team)
    if (pose.recoil > 0.3f) circle(0.3f, -0.6f, 0.08f, Color(0xFF7E57C2), outline = false)
}

private fun Pen.infernoTower(team: Color, pose: Pose) {
    val metal = Color(0xFF4E342E)
    box(0f, -0.15f, 0.8f, 0.3f, Color(0xFF3E2723), corner = 0.06f)
    poly(-0.32f, -0.25f, 0.32f, -0.25f, 0.18f, -1.15f, -0.18f, -1.15f, fill = metal)
    line(-0.25f, -0.55f, 0.25f, -0.55f, 0.04f, Color(0xFF6D4C41))
    line(-0.21f, -0.85f, 0.21f, -0.85f, 0.04f, Color(0xFF6D4C41))
    // Burning core, brighter as it fires.
    val heat = 0.12f + pose.recoil * 0.06f + sin(pose.time * 9f) * 0.012f
    circle(0f, -1.28f, heat * 1.9f, Color(0x55FF6D00), outline = false)
    circle(0f, -1.28f, heat, Color(0xFFFF6D00))
    circle(0f, -1.28f, heat * 0.5f, Color(0xFFFFE082), outline = false)
    box(0f, -0.06f, 0.6f, 0.06f, team, corner = 0.02f, outline = false)
}

private fun Pen.tombstone(team: Color) {
    oval(0f, -0.08f, 0.5f, 0.16f, Color(0xFF6D4C41))
    poly(-0.32f, -0.12f, -0.32f, -0.7f, -0.2f, -0.88f, 0f, -0.94f, 0.2f, -0.88f, 0.32f, -0.7f, 0.32f, -0.12f, fill = Color(0xFF90A4AE))
    // Cross and cracks.
    line(0f, -0.78f, 0f, -0.42f, 0.06f, Color(0xFF546E7A))
    line(-0.13f, -0.66f, 0.13f, -0.66f, 0.06f, Color(0xFF546E7A))
    line(0.15f, -0.3f, 0.24f, -0.42f, 0.025f, Color(0xFF546E7A))
    box(0f, -0.16f, 0.5f, 0.06f, team, corner = 0.02f, outline = false)
}

private fun Pen.tesla(team: Color, pose: Pose) {
    box(0f, -0.12f, 0.7f, 0.24f, SteelDark, corner = 0.06f)
    box(0f, -0.65f, 0.18f, 0.9f, Steel, corner = 0.05f)
    for (i in 0..2) oval(0f, -0.4f - i * 0.22f, 0.2f, 0.06f, Color(0xFFFFB300))
    val glow = 0.13f + if (pose.recoil > 0f) pose.recoil * 0.08f else sin(pose.time * 8f) * 0.015f
    circle(0f, -1.18f, glow * 1.6f, Color(0x5580D8FF), outline = false)
    circle(0f, -1.18f, glow, Color(0xFF80D8FF))
    box(0f, -0.06f, 0.5f, 0.06f, team, corner = 0.02f, outline = false)
}

// ------------------------------------------------------------------ towers

/**
 * Princess or king tower centered at the pen origin, in tile units (the pen's u is one
 * tile). [aim] is the screen-space angle toward the current target, used to turn the
 * archer/cannon.
 */
fun Pen.tower(king: Boolean, team: Color, aimDx: Float, aimDy: Float, pose: Pose) {
    val w = if (king) 3.3f else 2.6f
    val top = if (king) -1.7f else -1.35f
    val bottom = if (king) 1.6f else 1.25f
    val stone = Color(0xFFB0B4BE)
    val stoneDark = Color(0xFF7C818E)
    // Shadow, walls, darker base band.
    oval(0f, bottom, w * 0.55f, 0.35f, Color(0x33000000), outline = false)
    box(0f, (top + bottom) / 2f, w, bottom - top, stone, corner = 0.15f)
    box(0f, bottom - 0.3f, w, 0.6f, stoneDark, corner = 0.12f)
    // Brick lines.
    var row = top + 0.55f
    var odd = false
    while (row < bottom - 0.35f) {
        line(-w / 2 + 0.1f, row, w / 2 - 0.1f, row, 0.03f, stoneDark)
        var bx = -w / 2 + if (odd) 0.35f else 0.6f
        while (bx < w / 2 - 0.1f) {
            line(bx, row, bx, row + 0.35f, 0.03f, stoneDark)
            bx += 0.6f
        }
        row += 0.35f
        odd = !odd
    }
    // Battlements and team-colored trim.
    box(0f, top + 0.15f, w + 0.1f, 0.3f, team, corner = 0.06f)
    val merlons = if (king) 5 else 4
    for (i in 0 until merlons) {
        val mx = -w / 2 + 0.25f + i * (w - 0.5f) / (merlons - 1)
        box(mx, top - 0.05f, 0.32f, 0.3f, stone, corner = 0.05f)
    }
    // Banner on the front.
    poly(-0.25f, top + 0.35f, 0.25f, top + 0.35f, 0.25f, top + 1.0f, 0f, top + 0.85f, -0.25f, top + 1.0f, fill = team)

    // Defender on top, turned toward the target.
    val faceRight = aimDx >= 0f
    val aimAngle = kotlin.math.atan2(aimDy, abs(aimDx).coerceAtLeast(0.001f))
    val pen = sub(0f, top + 0.1f, if (king) 1.15f else 0.95f, flipOverride = if (faceRight) 1f else -1f)
    if (king) {
        pen.humanoid(
            Body(shirt = team, pants = Color(0xFF4E342E), torsoW = 0.42f), pose.copyAim(aimAngle), Hold.AIM,
            head = { x, y, r ->
                // Beard and crown.
                poly(-r * 0.4f, y + r * 0.2f, r * 0.95f, y + r * 0.2f, r * 0.6f, y + r * 1.1f, 0f, y + r * 0.9f, fill = Color.White)
                poly(
                    -r * 0.9f, y - r * 0.6f, -r * 0.9f, y - r * 1.5f, -r * 0.45f, y - r * 1.05f, 0f, y - r * 1.6f,
                    r * 0.45f, y - r * 1.05f, r * 0.9f, y - r * 1.5f, r * 0.9f, y - r * 0.6f,
                    fill = Gold,
                )
            },
        )
        // The king tower's cannon sits in front of the king.
        val a = aimAngle.coerceIn(-1.3f, 1.3f)
        val back = pose.recoil * 0.12f
        val (bx, by) = along(0.7f, -0.1f, a, -back)
        val (mx, my) = along(bx, by, a, 0.5f)
        pen.limb(bx, by, mx, my, 0.22f, Color(0xFF37474F))
        pen.circle(mx, my, 0.09f, Color(0xFF263238))
        pen.circle(bx, by, 0.14f, Color(0xFF455A64))
    } else {
        pen.humanoid(
            Body(shirt = Color(0xFFF06292), pants = Color(0xFFF06292), robe = true), pose.copyAim(aimAngle), Hold.AIM,
            head = { x, y, r ->
                hair(x, y, r, Color(0xFFFFD54F))
                poly(-r * 0.6f, y - r * 0.85f, -r * 0.3f, y - r * 1.35f, 0f, y - r * 0.95f, r * 0.3f, y - r * 1.35f, r * 0.6f, y - r * 0.85f, fill = Gold)
            },
            weapon = { x, y, a -> bow(x, y, a, pull = max(0f, pose.swing)) },
        )
    }
}

private fun Pose.copyAim(aim: Float) = Pose(walk, moving, swing, aim, recoil, time, asleep)

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
        "arrows" -> {
            for (i in -1..1) {
                val ox = i * 0.25f
                val oy = -i * 0.12f
                line(ox - 0.35f, oy - 0.35f, ox + 0.3f, oy + 0.3f, 0.06f, Wood)
                poly(ox + 0.42f, oy + 0.42f, ox + 0.22f, oy + 0.34f, ox + 0.34f, oy + 0.22f, fill = Steel)
                line(ox - 0.35f, oy - 0.35f, ox - 0.25f, oy - 0.45f, 0.08f, Color.White)
            }
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
        "lightning" -> {
            oval(0f, -0.3f, 0.45f, 0.2f, Color(0xFF546E7A))
            oval(-0.2f, -0.38f, 0.25f, 0.17f, Color(0xFF607D8B))
            oval(0.2f, -0.4f, 0.22f, 0.15f, Color(0xFF607D8B))
            poly(0.05f, -0.18f, -0.15f, 0.12f, 0.02f, 0.12f, -0.1f, 0.48f, 0.22f, 0.02f, 0.06f, 0.02f, 0.18f, -0.18f, fill = Color(0xFFFFEE58))
        }
        "zap" -> poly(
            0.1f, -0.5f, -0.25f, 0.05f, 0f, 0.05f, -0.15f, 0.5f, 0.3f, -0.1f, 0.05f, -0.1f, 0.2f, -0.5f,
            fill = Color(0xFF80D8FF),
        )
    }
}
