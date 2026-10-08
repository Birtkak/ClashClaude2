package com.clashclaude.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.ProjectileStyle
import com.clashclaude.game.game.Arena
import com.clashclaude.game.game.Battle
import com.clashclaude.game.game.Combatant
import com.clashclaude.game.game.EffectKind
import com.clashclaude.game.game.Kind
import com.clashclaude.game.game.Projectile
import com.clashclaude.game.game.Team
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Maps arena tiles to canvas pixels. Updated every draw. */
class ArenaTransform {
    var scale = 1f
    var ox = 0f
    var oy = 0f
    var originInRoot = Offset.Zero
    fun sx(x: Float) = ox + x * scale
    fun sy(y: Float) = oy + y * scale
    fun worldX(px: Float) = (px - ox) / scale
    fun worldY(py: Float) = (py - oy) / scale
}

/** A card being dragged over the arena: where it would land and how long until it's affordable. */
class Ghost(
    val card: CardDef,
    val x: Float,
    val y: Float,
    val formation: List<Pair<Float, Float>>,
    val waitSeconds: Float,
)

private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
    color = android.graphics.Color.WHITE
}

private val GrassA = Color(0xFF6DBE45)
private val GrassB = Color(0xFF62B03D)
private val River = Color(0xFF3D9BE0)
private val RiverLight = Color(0xFF7CC4F5)
private val Bridge = Color(0xFFA9774A)
private val BridgeDark = Color(0xFF7D532D)
private val PlayerColor = Color(0xFF3FA7FF)
private val EnemyColor = Color(0xFFFF4B4B)
private val Hedge = Color(0xFF3D7A2C)
private val Dashed = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

/** Extra space (tiles) above the arena for the enemy king tower. */
private const val TOP_MARGIN = 1.8f

fun teamColor(team: Team) = if (team == Team.PLAYER) PlayerColor else EnemyColor

private fun DrawScope.label(text: String, cx: Float, cy: Float, size: Float, color: Int = android.graphics.Color.WHITE) {
    labelPaint.textSize = size
    labelPaint.color = color
    labelPaint.setShadowLayer(size * 0.15f, 0f, size * 0.08f, android.graphics.Color.BLACK)
    val baseline = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
    drawIntoCanvas { it.nativeCanvas.drawText(text, cx, baseline, labelPaint) }
}

/** How big a unit's sprite is drawn, in tiles per sprite unit. */
private fun visualScale(c: Combatant): Float = when (c.kind) {
    Kind.BUILDING -> c.radius * 1.9f
    else -> c.radius * 3.0f
}

/** How high (tiles) a deploying unit starts its drop from. */
private const val DROP_HEIGHT = 4f

/** How high flyers hover above their ground position, in tiles. */
private const val FLY_HEIGHT = 0.9f

/** Builds this frame's animation pose from the unit's simulation state. */
private fun poseOf(c: Combatant, time: Float): Pose {
    val t = c.sinceAttack
    val swing = when {
        t < 0.1f -> 1f - 2f * (t / 0.1f)
        t < 0.35f -> -1f + (t - 0.1f) / 0.25f
        c.lockedOn && c.cooldown < 0.35f -> 1f - max(0f, c.cooldown) / 0.35f
        else -> 0f
    }
    val forward = (c.aimX - c.x) * c.faceX
    val aim = if (c.target == null) 0f else atan2(c.aimY - c.y, max(0.3f, forward))
    return Pose(
        walk = c.walkCycle * (2f * PI.toFloat() / 0.7f),
        moving = c.moving,
        swing = swing,
        aim = aim,
        recoil = if (t < 0.25f) 1f - t / 0.25f else 0f,
        time = time + c.id * 0.37f,
        asleep = c.kind == Kind.KING_TOWER && !c.active,
    )
}

/** Draws the whole arena. [armed] is a card being placed (shows where troops can't go). */
fun DrawScope.drawBattle(battle: Battle, t: ArenaTransform, ghost: Ghost?, armed: CardDef?) {
    // Leave room above the arena for the enemy king tower, which is drawn taller than its footprint.
    t.scale = min(size.width / Arena.WIDTH, size.height / (Arena.HEIGHT + TOP_MARGIN))
    t.ox = (size.width - Arena.WIDTH * t.scale) / 2f
    t.oy = (size.height - (Arena.HEIGHT + TOP_MARGIN) * t.scale) / 2f + TOP_MARGIN * t.scale
    val s = t.scale
    drawRect(Hedge, Offset.Zero, size)

    drawGround(battle, t)

    // Red overlay where troops can't go while a troop/building card is being placed.
    if (armed != null && armed.type != CardType.SPELL) {
        val red = Color(0x55FF2020)
        for (ty in 0 until Arena.HEIGHT.toInt()) {
            for (tx in 0 until Arena.WIDTH.toInt()) {
                val cx = tx + 0.5f
                val cy = ty + 0.5f
                if (!battle.inDeployZone(Team.PLAYER, cx, cy) || Arena.inRiver(cy)) {
                    drawRect(red, Offset(t.sx(tx.toFloat()), t.sy(ty.toFloat())), Size(s + 0.5f, s + 0.5f))
                }
            }
        }
    }

    for (r in battle.rubble) {
        Pen(this, t.sx(r.x), t.sy(r.y), s).rubble(r.kind == Kind.KING_TOWER)
    }

    // Ground layer, back to front so nearer things overlap farther ones.
    val ground = battle.entities.filter { !it.flying }.sortedBy { it.y }
    val air = battle.entities.filter { it.flying }.sortedBy { it.y }
    for (c in air) {
        drawOval(
            Color(0x40000000),
            topLeft = Offset(t.sx(c.x - c.radius), t.sy(c.y - c.radius * 0.35f)),
            size = Size(c.radius * 2 * s, c.radius * 0.7f * s),
        )
    }
    for (c in ground) {
        if (c.isTower) drawTowerBody(c, t, battle.time) else drawUnit(c, t, battle.time)
    }

    for (p in battle.projectiles) drawProjectile(p, t, battle.time)
    for (c in air) drawUnit(c, t, battle.time)
    drawSpells(battle, t)
    drawEffects(battle, t)

    // Health bars on top of everything so they're always readable.
    for (c in battle.entities) drawHealth(c, t)

    ghost?.let { drawGhost(battle, it, t) }
}

private fun DrawScope.drawGround(battle: Battle, t: ArenaTransform) {
    val s = t.scale
    for (ty in 0 until Arena.HEIGHT.toInt()) {
        for (tx in 0 until Arena.WIDTH.toInt()) {
            drawRect(
                if ((tx + ty) % 2 == 0) GrassA else GrassB,
                topLeft = Offset(t.sx(tx.toFloat()), t.sy(ty.toFloat())),
                size = Size(s + 0.5f, s + 0.5f),
            )
        }
    }
    drawRect(
        River,
        topLeft = Offset(t.sx(0f), t.sy(Arena.RIVER_TOP)),
        size = Size(Arena.WIDTH * s, (Arena.RIVER_BOTTOM - Arena.RIVER_TOP) * s),
    )
    val wave = (battle.time * 0.6f) % 2f
    for (i in 0 until 10) {
        val wx = (i * 2f + wave) % (Arena.WIDTH - 0.8f)
        val wy = Arena.RIVER_MID - 0.3f + (i % 2) * 0.6f
        drawLine(RiverLight, Offset(t.sx(wx), t.sy(wy)), Offset(t.sx(wx + 0.8f), t.sy(wy)), strokeWidth = s * 0.08f)
    }
    for (bx in Arena.BRIDGES) {
        val left = t.sx(bx - Arena.BRIDGE_HALF_WIDTH)
        val top = t.sy(Arena.RIVER_TOP - 0.3f)
        val w = Arena.BRIDGE_HALF_WIDTH * 2 * s
        val h = (Arena.RIVER_BOTTOM - Arena.RIVER_TOP + 0.6f) * s
        drawRect(Bridge, Offset(left, top), Size(w, h))
        var py = Arena.RIVER_TOP - 0.3f
        while (py < Arena.RIVER_BOTTOM + 0.3f) {
            drawLine(BridgeDark, Offset(left, t.sy(py)), Offset(left + w, t.sy(py)), strokeWidth = s * 0.05f)
            py += 0.5f
        }
        drawRect(BridgeDark, Offset(left, top), Size(w, h), style = Stroke(s * 0.08f))
    }
}

private fun DrawScope.drawTowerBody(c: Combatant, t: ArenaTransform, time: Float) {
    val king = c.kind == Kind.KING_TOWER
    // Idle defenders look toward the enemy side.
    val forward = if (c.team == Team.PLAYER) -1f else 1f
    val dx = if (c.target != null) c.aimX - c.x else 0.4f * forward
    val dy = if (c.target != null) c.aimY - c.y else forward
    Pen(this, t.sx(c.x), t.sy(c.y), t.scale).tower(king, teamColor(c.team), dx, dy, poseOf(c, time))
    if (c.hitFlash > 0f) {
        drawCircle(Color(0x55FFFFFF), c.radius * t.scale, Offset(t.sx(c.x), t.sy(c.y)))
    }
    if (king && !c.active) {
        val bob = sin(time * 2f) * 0.1f
        label("z Z", t.sx(c.x + 1.5f), t.sy(c.y - 1.9f + bob), t.scale * 0.55f)
    }
    if (c.stunTimer > 0f) Pen(this, t.sx(c.x + 1f), t.sy(c.y - 2.3f), t.scale * 0.5f).spellIcon("zap", time)
}

private fun DrawScope.drawUnit(c: Combatant, t: ArenaTransform, time: Float) {
    val s = t.scale
    val id = c.card?.id ?: return
    val u = s * visualScale(c)
    val fx = t.sx(c.x)
    val groundY = t.sy(c.y) + c.radius * 0.45f * s
    // While deploying the unit drops in from above, speeding up as it falls.
    val fall = if (c.deploying) (c.deployTimer / Combatant.DEPLOY_TIME).coerceIn(0f, 1f) else 0f
    val dropHeight = fall * fall * DROP_HEIGHT
    val feetY = (if (c.flying) groundY - FLY_HEIGHT * s else groundY) - dropHeight * s
    val color = teamColor(c.team)

    // Team-colored base so friend/foe reads at a glance; while dropping it's the landing shadow.
    val baseR = c.radius * s * 1.05f * (1f - fall * 0.5f)
    val baseTl = Offset(fx - baseR, groundY - baseR * 0.42f)
    val baseSize = Size(baseR * 2, baseR * 0.84f)
    if (c.deploying) drawOval(Color(0x55000000), baseTl, baseSize)
    drawOval(color.copy(alpha = 0.35f), baseTl, baseSize)
    drawOval(color, baseTl, baseSize, style = Stroke(s * 0.06f))

    Pen(this, fx, feetY, u, c.faceX).unit(id, color, poseOf(c, time))

    if (c.hitFlash > 0f) {
        drawCircle(Color(0x66FFFFFF), u * 0.4f, Offset(fx, feetY - u * 0.5f))
    }
    if (c.deploying) {
        // Countdown ring on the landing spot.
        val ringR = c.radius * s * 1.3f
        drawArc(
            Color.White,
            startAngle = -90f,
            sweepAngle = 360f * fall,
            useCenter = false,
            topLeft = Offset(fx - ringR, groundY - ringR * 0.42f),
            size = Size(ringR * 2, ringR * 0.84f),
            style = Stroke(s * 0.07f),
        )
    }
    if (c.stunTimer > 0f) {
        Pen(this, fx + u * 0.3f, feetY + spriteTop(id) * u, s * 0.4f).spellIcon("zap", time)
    }
}

private fun DrawScope.drawHealth(c: Combatant, t: ArenaTransform) {
    val s = t.scale
    if (c.isTower) {
        val top = if (c.kind == Kind.KING_TOWER) -1.7f else -1.35f
        val y = t.sy(c.y + top) - s * 1.15f
        drawBar(c, t.sx(c.x), y, c.radius * 1.5f * s)
        label(c.hp.toInt().toString(), t.sx(c.x), y - s * 0.3f, s * 0.4f)
        return
    }
    if (c.deploying || (c.hp >= c.maxHp && c.kind != Kind.BUILDING)) return
    val id = c.card?.id ?: return
    val u = s * visualScale(c)
    val groundY = t.sy(c.y) + c.radius * 0.45f * s
    val feetY = if (c.flying) groundY - FLY_HEIGHT * s else groundY
    drawBar(c, t.sx(c.x), feetY + spriteTop(id) * u - s * 0.2f, max(c.radius * 2 * s, s * 0.8f))
}

private fun DrawScope.drawBar(c: Combatant, cx: Float, top: Float, width: Float) {
    val h = max(4f, width * 0.12f).coerceAtMost(12f)
    val frac = (c.hp / c.maxHp).coerceIn(0f, 1f)
    drawRoundRect(Color(0xCC000000), Offset(cx - width / 2 - 1f, top - 1f), Size(width + 2f, h + 2f), CornerRadius(h / 2))
    drawRoundRect(teamColor(c.team), Offset(cx - width / 2, top), Size(width * frac, h), CornerRadius(h / 2))
}

// ------------------------------------------------------------------ projectiles

private fun DrawScope.drawProjectile(p: Projectile, t: ArenaTransform, time: Float) {
    val s = t.scale
    val prog = p.progress
    val targetHeight = if (p.target.flying) FLY_HEIGHT + 0.4f else 0.4f
    val arc = when (p.style) {
        ProjectileStyle.BOMB -> 1.6f
        ProjectileStyle.CANNONBALL -> 0.7f
        ProjectileStyle.ARROW, ProjectileStyle.SPEAR -> 0.5f
        else -> 0.2f
    }
    fun height(pr: Float) = p.launchHeight + (targetHeight - p.launchHeight) * pr + sin(PI.toFloat() * pr) * arc
    val gx = t.sx(p.x)
    val gy = t.sy(p.y)
    val px = gx
    val py = gy - height(prog) * s

    // Screen-space flight direction, including the arc, for orienting arrows and trails.
    val dirGX = p.tx - p.startX
    val dirGY = p.ty - p.startY
    val dist = max(0.01f, hypot(dirGX, dirGY))
    val dh = (height(min(1f, prog + 0.05f)) - height(max(0f, prog - 0.05f))) / 0.1f
    var vx = dirGX / dist * s
    var vy = dirGY / dist * s - dh / dist * s
    val vl = max(0.01f, hypot(vx, vy))
    vx /= vl
    vy /= vl

    // Ground shadow helps judge where it lands.
    drawOval(Color(0x33000000), Offset(gx - s * 0.15f, gy - s * 0.06f), Size(s * 0.3f, s * 0.12f))

    when (p.style) {
        ProjectileStyle.ARROW, ProjectileStyle.SPEAR -> {
            val len = s * if (p.style == ProjectileStyle.SPEAR) 0.85f else 0.6f
            val tail = Offset(px - vx * len, py - vy * len)
            val tip = Offset(px, py)
            drawLine(Color(0xFF1B1B26), tail, tip, strokeWidth = s * 0.11f, cap = StrokeCap.Round)
            drawLine(Color(0xFF8D5A2B), tail, tip, strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            // Head.
            val hx = -vy * s * 0.09f
            val hy = vx * s * 0.09f
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(px + vx * s * 0.18f, py + vy * s * 0.18f)
                lineTo(px + hx, py + hy)
                lineTo(px - hx, py - hy)
                close()
            }
            drawPath(head, Color(0xFFCFD8DC))
            drawPath(head, Color(0xFF1B1B26), style = Stroke(s * 0.025f))
            // Fletching.
            drawLine(Color.White, tail, Offset(tail.x + hx * 1.2f - vx * s * 0.05f, tail.y + hy * 1.2f - vy * s * 0.05f), strokeWidth = s * 0.05f)
            drawLine(Color.White, tail, Offset(tail.x - hx * 1.2f - vx * s * 0.05f, tail.y - hy * 1.2f - vy * s * 0.05f), strokeWidth = s * 0.05f)
        }
        ProjectileStyle.BULLET -> {
            drawLine(Color(0x88FFFFFF), Offset(px - vx * s * 0.5f, py - vy * s * 0.5f), Offset(px, py), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF263238), s * 0.09f, Offset(px, py))
        }
        ProjectileStyle.CANNONBALL -> {
            for (i in 1..3) {
                drawCircle(Color(0x33FFFFFF), s * (0.08f + i * 0.03f), Offset(px - vx * s * 0.25f * i, py - vy * s * 0.25f * i))
            }
            drawCircle(Color(0xFF1B1B26), s * 0.19f, Offset(px, py))
            drawCircle(Color(0xFF455A64), s * 0.15f, Offset(px, py))
            drawCircle(Color(0x66FFFFFF), s * 0.05f, Offset(px - s * 0.05f, py - s * 0.05f))
        }
        ProjectileStyle.FIRE -> {
            for (i in 4 downTo 1) {
                drawCircle(
                    Color(0xFFFF7A1A).copy(alpha = 0.5f - i * 0.1f),
                    s * (0.25f - i * 0.03f),
                    Offset(px - vx * s * 0.18f * i, py - vy * s * 0.18f * i),
                )
            }
            drawCircle(Color(0x66FF7A1A), s * 0.36f, Offset(px, py))
            drawCircle(Color(0xFFFF7A1A), s * 0.24f, Offset(px, py))
            drawCircle(Color(0xFFFFE082), s * 0.12f, Offset(px, py))
        }
        ProjectileStyle.BOMB -> {
            Pen(this, px, py, s).apply {
                circle(0f, 0f, 0.2f, Color(0xFF263238))
                if (sin(time * 25f) > -0.3f) circle(0.15f, -0.25f, 0.07f, Color(0xFFFFCA28), outline = false)
            }
        }
        ProjectileStyle.ORB -> {
            drawCircle(Color(0x667E57C2), s * 0.22f, Offset(px, py))
            drawCircle(Color(0xFF7E57C2), s * 0.13f, Offset(px, py))
            drawCircle(Color(0xFFD1C4E9), s * 0.05f, Offset(px, py))
        }
        else -> drawCircle(Color.White, s * 0.1f, Offset(px, py))
    }
}

private fun DrawScope.drawSpells(battle: Battle, t: ArenaTransform) {
    val s = t.scale
    for (sp in battle.spells) {
        // Landing zone, so you can see exactly what it will hit.
        val target = Offset(t.sx(sp.tx), t.sy(sp.ty))
        drawCircle(Color(0x33FFFFFF), sp.card.spellRadius * s, target)
        drawCircle(Color.White, sp.card.spellRadius * s, target, style = Stroke(s * 0.06f, pathEffect = Dashed))

        val prog = sp.progress
        val h = sin(PI.toFloat() * prog) * 3f + (1f - prog) * 1.5f
        val pos = Offset(t.sx(sp.x), t.sy(sp.y) - h * s)
        when (sp.card.id) {
            "arrows" -> {
                // A volley: several arrows flying in a loose cluster.
                val dirX = sp.tx - sp.startX
                val dirY = sp.ty - sp.startY
                val d = max(0.01f, hypot(dirX, dirY))
                val ang = atan2(dirY / d - (if (prog < 0.5f) 0.6f else -0.6f), dirX / d)
                for (i in 0 until 9) {
                    val ox = ((i % 3) - 1) * s * 0.8f
                    val oy = ((i / 3) - 1) * s * 0.6f
                    val a = Offset(pos.x + ox, pos.y + oy)
                    val b = Offset(a.x - cos(ang) * s * 0.6f, a.y - sin(ang) * s * 0.6f)
                    drawLine(Color(0xFF1B1B26), b, a, strokeWidth = s * 0.1f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF8D5A2B), b, a, strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFFCFD8DC), s * 0.07f, a)
                }
            }
            else -> {
                // Fireball: flaming comet with a trail.
                val back = Offset(t.sx(sp.startX) - pos.x, t.sy(sp.startY) - pos.y)
                val bl = max(0.01f, hypot(back.x, back.y))
                for (i in 5 downTo 1) {
                    drawCircle(
                        Color(0xFFFF7A1A).copy(alpha = 0.55f - i * 0.09f),
                        s * (0.55f - i * 0.06f),
                        Offset(pos.x + back.x / bl * s * 0.3f * i, pos.y + back.y / bl * s * 0.3f * i),
                    )
                }
                Pen(this, pos.x, pos.y, s * 1.4f).spellIcon(sp.card.id, battle.time)
            }
        }
    }
}

private fun DrawScope.drawEffects(battle: Battle, t: ArenaTransform) {
    val s = t.scale
    for (e in battle.effects) {
        val color = Color(e.color.toInt())
        val p = e.progress
        val center = Offset(t.sx(e.x), t.sy(e.y))
        when (e.kind) {
            EffectKind.RING -> drawCircle(
                color.copy(alpha = color.alpha * (1f - p)),
                e.radius * s * (0.5f + 0.5f * p),
                center,
                style = Stroke(s * 0.14f),
            )
            EffectKind.FLASH -> drawCircle(color.copy(alpha = color.alpha * (1f - p) * 0.6f), e.radius * s, center)
            EffectKind.PUFF -> for (i in 0 until 3) {
                val a = i * 2.1f
                drawCircle(
                    Color(0xFFE0E0E0).copy(alpha = 0.7f * (1f - p)),
                    e.radius * s * (0.35f + 0.4f * p),
                    Offset(center.x + cos(a) * e.radius * s * 0.4f * p, center.y + sin(a) * e.radius * s * 0.4f * p - p * s * 0.5f),
                )
            }
            EffectKind.SPARK -> for (i in 0 until 6) {
                val a = i * PI.toFloat() / 3f + 0.3f
                val r0 = e.radius * s * (0.2f + 0.6f * p)
                val r1 = e.radius * s * (0.6f + 0.8f * p)
                drawLine(
                    color.copy(alpha = 1f - p),
                    Offset(center.x + cos(a) * r0, center.y - s * 0.4f + sin(a) * r0),
                    Offset(center.x + cos(a) * r1, center.y - s * 0.4f + sin(a) * r1),
                    strokeWidth = s * 0.07f,
                    cap = StrokeCap.Round,
                )
            }
            EffectKind.EXPLOSION -> {
                // The filled area is the real splash radius.
                val r = e.radius * s
                drawCircle(color.copy(alpha = 0.45f * (1f - p)), r, center)
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - p)), r, center, style = Stroke(s * 0.08f))
                drawCircle(Color(0xFFFFF8E1).copy(alpha = 1f - p), r * 0.45f * (1f - p), center)
            }
            EffectKind.SLASH -> {
                val r = e.radius * s * 1.3f
                val from = atan2(e.y - e.y2, e.x - e.x2) * 180f / PI.toFloat()
                drawArc(
                    Color.White.copy(alpha = 1f - p),
                    startAngle = from + 120f - p * 60f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - s * 0.4f - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(s * 0.12f * (1f - p * 0.5f), cap = StrokeCap.Round),
                )
            }
            EffectKind.LINE -> {
                // Jagged lightning, re-jittered every frame so it crackles.
                val a = Offset(t.sx(e.x), t.sy(e.y) - e.lift * s)
                val b = Offset(t.sx(e.x2), t.sy(e.y2) - 0.4f * s)
                var prev = a
                val segs = 6
                for (i in 1..segs) {
                    val f = i / segs.toFloat()
                    val jitter = if (i == segs) 0f else (kotlin.random.Random.nextFloat() - 0.5f) * s * 0.6f
                    val next = Offset(a.x + (b.x - a.x) * f + jitter, a.y + (b.y - a.y) * f)
                    drawLine(color.copy(alpha = 0.5f * (1f - p)), prev, next, strokeWidth = s * 0.22f, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 1f - p), prev, next, strokeWidth = s * 0.08f, cap = StrokeCap.Round)
                    prev = next
                }
            }
        }
    }
}

// ------------------------------------------------------------------ placement ghost

private fun DrawScope.drawGhost(battle: Battle, g: Ghost, t: ArenaTransform) {
    val s = t.scale
    val waiting = g.waitSeconds > 0f
    val tint = if (waiting) Color(0xFFD43BFF) else Color.White
    val center = Offset(t.sx(g.x), t.sy(g.y))
    if (g.card.type == CardType.SPELL) {
        drawCircle(tint.copy(alpha = 0.25f), g.card.spellRadius * s, center)
        drawCircle(tint, g.card.spellRadius * s, center, style = Stroke(s * 0.07f, pathEffect = Dashed))
        Pen(this, center.x, center.y, s * 1.2f, alpha = 0.85f).spellIcon(g.card.id, battle.time)
    } else {
        // Attack range of ranged troops and buildings, so you can see what they'll reach.
        if (g.card.range >= 2f) {
            val reach = (g.card.range + g.card.radius) * s
            drawCircle(tint.copy(alpha = 0.08f), reach, center)
            drawCircle(tint.copy(alpha = 0.6f), reach, center, style = Stroke(s * 0.05f, pathEffect = Dashed))
        }
        val proto = Combatant.fromCard(g.card, Team.PLAYER, g.x, g.y)
        val u = s * visualScale(proto)
        for ((x, y) in g.formation) {
            val fx = t.sx(x)
            val groundY = t.sy(y) + g.card.radius * 0.45f * s
            val feetY = if (g.card.flying) groundY - FLY_HEIGHT * s else groundY
            val baseR = g.card.radius * s * 1.15f
            val baseTl = Offset(fx - baseR, groundY - baseR * 0.42f)
            val baseSize = Size(baseR * 2, baseR * 0.84f)
            drawOval(tint.copy(alpha = 0.5f), baseTl, baseSize)
            drawOval(tint, baseTl, baseSize, style = Stroke(s * 0.08f))
            Pen(this, fx, feetY, u, 1f, alpha = 0.75f).unit(g.card.id, PlayerColor, Pose(time = battle.time))
        }
    }
    if (waiting) {
        label(
            String.format(Locale.US, "%.1fs", g.waitSeconds),
            center.x,
            center.y - s * 2.2f,
            s * 0.6f,
            0xFFF3B6FF.toInt(),
        )
    }
}
