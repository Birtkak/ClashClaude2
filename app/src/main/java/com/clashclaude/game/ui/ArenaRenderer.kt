package com.clashclaude.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.clashclaude.game.game.View
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maps the flat 2D arena (tiles) to canvas pixels through the fixed tilted camera ([View]):
 * ground depth is foreshortened by [View.DEPTH] and heights rise by [View.HEIGHT] per tile.
 * [viewer]'s side is always at the bottom; for [Team.ENEMY] the arena is turned 180 degrees.
 */
class ArenaTransform(val viewer: Team = Team.PLAYER) {
    /** Pixels per tile across the screen. */
    var scale = 1f
    var ox = 0f
    var oy = 0f
    var originInRoot = Offset.Zero

    /** How far (0..1) this frame is from the previous simulation tick to the current one. */
    var alpha = 1f

    val flipped: Boolean get() = viewer == Team.ENEMY

    /** +1, or -1 when flipped: multiply world-space directions by this to get screen directions. */
    val dir: Float get() = if (flipped) -1f else 1f

    /** Screen pixels per tile of ground depth, and per tile of height. */
    val depth: Float get() = scale * View.DEPTH
    val rise: Float get() = scale * View.HEIGHT

    /** Fits the arena into a canvas of this size. Called at layout time, so touch input never
     *  depends on whether a frame has been drawn yet. */
    fun fit(width: Float, height: Float) {
        val tall = Arena.HEIGHT * View.DEPTH + TOP_MARGIN + BOTTOM_MARGIN
        scale = min(width / Arena.WIDTH, height / tall)
        ox = (width - Arena.WIDTH * scale) / 2f
        oy = (height - tall * scale) / 2f + TOP_MARGIN * scale
    }

    fun sx(x: Float) = ox + (if (flipped) Arena.WIDTH - x else x) * scale
    fun sy(y: Float) = oy + (if (flipped) Arena.HEIGHT - y else y) * depth
    fun worldX(px: Float) = ((px - ox) / scale).let { if (flipped) Arena.WIDTH - it else it }
    fun worldY(py: Float) = ((py - oy) / depth).let { if (flipped) Arena.HEIGHT - it else it }

    /** Screen top-left of the ground rectangle [x0, x0 + w] x [y0, y0 + h]. */
    fun topLeft(x0: Float, y0: Float, w: Float, h: Float) =
        Offset(min(sx(x0), sx(x0 + w)), min(sy(y0), sy(y0 + h)))

    /** Blue for the viewer's units, red for the opponent's. */
    fun colorOf(team: Team) = if (team == viewer) PlayerColor else EnemyColor
    fun isBlue(team: Team) = team == viewer

    /** Where to draw a unit this frame, between its last two simulated positions. */
    fun ix(c: Combatant) = sx(c.prevX + (c.x - c.prevX) * alpha)
    fun iy(c: Combatant) = sy(c.prevY + (c.y - c.prevY) * alpha)

    /** Screen-space heading of a world heading. */
    fun heading(world: Float) = if (flipped) world + PI.toFloat() else world
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

private val GrassA = Color(0xFF79C24E)
private val GrassB = Color(0xFF6DB646)
private val Dirt = Color(0xFFD9C08A)
private val River = Color(0xFF3D9BE0)
private val RiverDeep = Color(0xFF2C7BC0)
private val RiverLight = Color(0xFF8FD0F8)
private val Bank = Color(0xFF7A5A36)
private val Bridge = Color(0xFFB07C4C)
private val BridgeDark = Color(0xFF7D532D)
internal val PlayerColor = Color(0xFF3FA7FF)
internal val EnemyColor = Color(0xFFFF4B4B)
private val Hedge = Color(0xFF3D7A2C)
private val HedgeDark = Color(0xFF2B5A1F)
private val Ink = Color(0xFF0B1324)
private val Dashed = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

/** Extra space (tiles of screen height) above the arena for the far king tower, and below for the near one. */
private const val TOP_MARGIN = 3.2f
private const val BOTTOM_MARGIN = 0.6f

/** How high (tiles) a deploying unit starts its drop from. */
private const val DROP_HEIGHT = 4f

/** How high flyers hover above their ground position, in tiles. */
private const val FLY_HEIGHT = 1.4f

private fun DrawScope.label(text: String, cx: Float, cy: Float, size: Float, color: Int = android.graphics.Color.WHITE) {
    labelPaint.textSize = size
    labelPaint.color = color
    labelPaint.setShadowLayer(size * 0.15f, 0f, size * 0.08f, android.graphics.Color.BLACK)
    val baseline = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
    drawIntoCanvas { it.nativeCanvas.drawText(text, cx, baseline, labelPaint) }
}

/** A circle lying on the ground, seen through the tilted camera: an ellipse. */
private fun DrawScope.groundCircle(t: ArenaTransform, c: Offset, r: Float, color: Color, stroke: Stroke? = null) {
    val rx = r * t.scale
    val ry = r * t.depth
    if (stroke == null) drawOval(color, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    else drawOval(color, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = stroke)
}

/** Draws an image centred at ([cx], [cy]) and [w] pixels wide. */
private fun DrawScope.drawPicture(name: String, cx: Float, cy: Float, w: Float, alpha: Float = 1f): Boolean {
    val img = Sprites.image(name) ?: return false
    val h = w * img.height / img.width
    drawImage(img, dstOffset = IntOffset((cx - w / 2).toInt(), (cy - h / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), alpha = alpha)
    return true
}

/** Draws the whole arena. [armed] is a card being placed (shows where troops can't go). */
fun DrawScope.drawBattle(battle: Battle, t: ArenaTransform, ghost: Ghost?, armed: CardDef?) {
    t.fit(size.width, size.height)
    val s = t.scale
    drawRect(Brush.verticalGradient(listOf(HedgeDark, Hedge, HedgeDark)), Offset.Zero, size)

    drawGround(battle, t)

    // Red overlay where troops can't go while a troop/building card is being placed.
    if (armed != null && armed.type != CardType.SPELL) {
        val red = Color(0x55FF2020)
        for (ty in 0 until Arena.HEIGHT.toInt()) {
            for (tx in 0 until Arena.WIDTH.toInt()) {
                val cx = tx + 0.5f
                val cy = ty + 0.5f
                if (!battle.inDeployZone(t.viewer, cx, cy) || Arena.inRiver(cy)) {
                    drawRect(red, t.topLeft(tx.toFloat(), ty.toFloat(), 1f, 1f), Size(s + 0.5f, t.depth + 0.5f))
                }
            }
        }
    }

    for (r in battle.rubble) {
        val king = r.kind == Kind.KING_TOWER
        if (!drawSprite(if (king) "rubble_king" else "rubble_princess", t.isBlue(r.team), PI.toFloat() / 2, 0, t.sx(r.x), t.sy(r.y), s)) {
            Pen(this, t.sx(r.x), t.sy(r.y) - t.depth * 0.4f, s * 0.9f).rubble(king)
        }
    }

    // Ground shadows and team rings first, so every body stands on top of them.
    for (c in battle.entities) drawFootprint(c, t)

    // Bodies back to front by their ground position on screen; flyers above everything on the ground.
    val ground = battle.entities.filter { !it.flying }.sortedBy { t.iy(it) }
    val air = battle.entities.filter { it.flying }.sortedBy { t.iy(it) }
    for (c in ground) if (c.isTower) drawTower(c, t, battle.time) else drawUnit(c, t, battle.time)
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
    val d = t.depth
    // Earth edge along the near side gives the arena some thickness.
    drawRect(Bank, Offset(t.ox, t.oy + Arena.HEIGHT * d), Size(Arena.WIDTH * s, t.rise * 0.45f))
    for (ty in 0 until Arena.HEIGHT.toInt()) {
        for (tx in 0 until Arena.WIDTH.toInt()) {
            drawRect(
                if ((tx + ty) % 2 == 0) GrassA else GrassB,
                topLeft = t.topLeft(tx.toFloat(), ty.toFloat(), 1f, 1f),
                size = Size(s + 0.5f, d + 0.5f),
            )
        }
    }
    // Dirt paths down each lane, from the princess towers to the bridges.
    for (bx in Arena.BRIDGES) {
        for ((y0, y1) in listOf(6f to Arena.RIVER_TOP, Arena.RIVER_BOTTOM to 26f)) {
            drawRoundRect(
                Dirt.copy(alpha = 0.5f), t.topLeft(bx - 0.9f, y0, 1.8f, y1 - y0),
                Size(1.8f * s, (y1 - y0) * d), CornerRadius(s * 0.5f, d * 0.5f),
            )
        }
    }
    // Tile grid, so placements can be lined up exactly.
    val gridColor = Color(0x1F000000)
    for (gx in 1 until Arena.WIDTH.toInt()) {
        drawLine(gridColor, Offset(t.sx(gx.toFloat()), t.sy(0f)), Offset(t.sx(gx.toFloat()), t.sy(Arena.HEIGHT)), strokeWidth = 1.5f)
    }
    for (gy in 1 until Arena.HEIGHT.toInt()) {
        drawLine(gridColor, Offset(t.sx(0f), t.sy(gy.toFloat())), Offset(t.sx(Arena.WIDTH), t.sy(gy.toFloat())), strokeWidth = 1.5f)
    }

    // River, sunk below the grass: the far bank shows a strip of earth wall.
    val riverTl = t.topLeft(0f, Arena.RIVER_TOP, Arena.WIDTH, Arena.RIVER_BOTTOM - Arena.RIVER_TOP)
    val riverH = (Arena.RIVER_BOTTOM - Arena.RIVER_TOP) * d
    drawRect(Brush.verticalGradient(listOf(RiverDeep, River), riverTl.y, riverTl.y + riverH), riverTl, Size(Arena.WIDTH * s, riverH))
    drawRect(Bank, riverTl, Size(Arena.WIDTH * s, t.rise * 0.28f))
    val wave = (battle.time * 0.6f) % 2f
    for (i in 0 until 10) {
        val wx = (i * 2f + wave) % (Arena.WIDTH - 0.8f)
        val wy = Arena.RIVER_MID - 0.15f + (i % 2) * 0.5f
        drawLine(RiverLight, Offset(t.sx(wx), t.sy(wy)), Offset(t.sx(wx + 0.8f), t.sy(wy)), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }

    drawScenery(t)

    // Bridges: plank decks with a little thickness and side rails.
    for (bx in Arena.BRIDGES) {
        val y0 = Arena.RIVER_TOP - 0.35f
        val len = Arena.RIVER_BOTTOM - Arena.RIVER_TOP + 0.7f
        val w = Arena.BRIDGE_HALF_WIDTH * 2 * s
        val h = len * d
        val tl = t.topLeft(bx - Arena.BRIDGE_HALF_WIDTH, y0, Arena.BRIDGE_HALF_WIDTH * 2, len)
        drawRect(BridgeDark, Offset(tl.x, tl.y + t.rise * 0.18f), Size(w, h))
        drawRect(Bridge, tl, Size(w, h))
        var py = y0 + 0.5f
        while (py < y0 + len) {
            drawLine(BridgeDark, Offset(tl.x, t.sy(py)), Offset(tl.x + w, t.sy(py)), strokeWidth = s * 0.04f)
            py += 0.5f
        }
        drawRect(Ink, tl, Size(w, h), style = Stroke(s * 0.05f))
        for (edge in listOf(tl.x + s * 0.06f, tl.x + w - s * 0.06f)) {
            drawLine(BridgeDark, Offset(edge, tl.y - t.rise * 0.25f), Offset(edge, tl.y + h - t.rise * 0.25f), strokeWidth = s * 0.12f, cap = StrokeCap.Round)
        }
    }
}

/**
 * Trees, rocks and bushes around the arena. Positions are in screen terms (the far edge is
 * always at the top), so they stay put whichever side the viewer plays.
 */
private val Scenery = listOf(
    Triple("prop_pine", 0.6f, -1.1f), Triple("prop_tree", 2.6f, -0.7f), Triple("prop_bush", 4.6f, -0.35f),
    Triple("prop_tree", 6.2f, -1.2f), Triple("prop_pine", 12.1f, -1.25f), Triple("prop_bush", 13.5f, -0.4f),
    Triple("prop_tree", 15.4f, -0.8f), Triple("prop_pine", 17.4f, -1.0f), Triple("prop_rock", 8.0f, -0.25f),
    Triple("prop_bush", 0.25f, 14.4f), Triple("prop_rock", 17.75f, 14.5f),
    Triple("prop_rock", 0.3f, 32.55f), Triple("prop_bush", 17.6f, 32.5f),
)

private fun DrawScope.drawScenery(t: ArenaTransform) {
    for ((id, x, y) in Scenery.sortedBy { it.third }) {
        val wx = if (t.flipped) Arena.WIDTH - x else x
        val wy = if (t.flipped) Arena.HEIGHT - y else y
        drawSprite(id, true, PI.toFloat() / 2, 0, t.sx(wx), t.sy(wy), t.scale)
    }
}

/** Ground shadow and team ring under a body. */
private fun DrawScope.drawFootprint(c: Combatant, t: ArenaTransform) {
    val center = Offset(t.ix(c), t.iy(c))
    if (c.isTower) {
        groundCircle(t, center, c.radius * 1.05f, Color(0x40000000))
        return
    }
    val fall = if (c.deploying) (c.deployTimer / Combatant.DEPLOY_TIME).coerceIn(0f, 1f) else 0f
    val shrink = 1f - 0.5f * fall
    groundCircle(t, center, c.radius * (if (c.flying) 0.8f else 1.1f) * shrink, Color(0x48000000))
    if (!c.flying) {
        val color = t.colorOf(c.team)
        groundCircle(t, center, c.radius * 1.05f * shrink, color.copy(alpha = 0.3f))
        groundCircle(t, center, c.radius * 1.05f * shrink, color, Stroke(t.scale * 0.05f))
    }
    if (c.deploying) {
        // Countdown ring on the landing spot.
        val r = c.radius * 1.35f
        drawArc(
            Color.White, -90f, 360f * fall, false,
            Offset(center.x - r * t.scale, center.y - r * t.depth), Size(r * 2 * t.scale, r * 2 * t.depth),
            style = Stroke(t.scale * 0.07f),
        )
    }
}

private fun DrawScope.drawTower(c: Combatant, t: ArenaTransform, time: Float) {
    val king = c.kind == Kind.KING_TOWER
    val id = if (king) "tower_king" else "tower_princess"
    val x = t.sx(c.x)
    val y = t.sy(c.y)
    val blue = t.isBlue(c.team)
    val frozen = c.frozenTimer > 0f
    if (!drawSprite(id, blue, PI.toFloat() / 2, 0, x, y, t.scale, frozen = frozen, flash = c.hitFlash > 0f)) {
        drawCircle(t.colorOf(c.team), c.radius * t.scale, Offset(x, y - t.rise))
    }
    // The defender on top: an archer on princess towers, the king and his cannon on the king tower.
    val mount = Sprites.sheet(id)?.mount ?: 2.4f
    val topY = y - mount * t.rise
    val defender = if (king) "kingtop" else "archers"
    Sprites.sheet(defender)?.let { sheet ->
        drawSprite(defender, blue, t.heading(c.heading), frameColumn(c, sheet, time), x, topY, t.scale * if (king) 1f else 0.8f, frozen = frozen)
    }
    if (king && !c.active) {
        val bob = sin(time * 2f) * 0.1f
        label("z Z", x + 1.3f * t.scale, topY + (bob - 2.2f) * t.rise, t.scale * 0.55f)
    }
    if (c.stunTimer > 0f && !frozen) Pen(this, x + t.scale, topY - 1.6f * t.rise, t.scale * 0.5f).spellIcon("zap", time)
}

/** Screen y of a unit's feet, including flying height and the deploy drop. */
private fun feetY(c: Combatant, t: ArenaTransform): Float {
    val fall = if (c.deploying) (c.deployTimer / Combatant.DEPLOY_TIME).coerceIn(0f, 1f) else 0f
    val drop = fall * sqrt(fall) * DROP_HEIGHT
    return t.iy(c) - ((if (c.flying) FLY_HEIGHT else 0f) + drop) * t.rise
}

private fun DrawScope.drawUnit(c: Combatant, t: ArenaTransform, time: Float) {
    val id = c.card?.id ?: return
    val x = t.ix(c)
    val y = feetY(c, t)
    val frozen = c.frozenTimer > 0f
    val sheet = Sprites.sheet(id)
    val drawn = sheet != null && drawSprite(
        id, t.isBlue(c.team), t.heading(c.heading), frameColumn(c, sheet, time), x, y, t.scale,
        frozen = frozen, flash = c.hitFlash > 0f,
    )
    if (!drawn) {
        // No baked sprite (e.g. in a unit test): a simple team-coloured token.
        val r = c.radius * t.scale
        drawCircle(t.colorOf(c.team), r, Offset(x, y - r))
        drawCircle(Ink, r, Offset(x, y - r), style = Stroke(t.scale * 0.05f))
    }
    val h = spriteHeight(id, t.scale) ?: t.scale
    if (frozen) {
        // A few ice glints over the frozen unit.
        for (i in 0 until 3) {
            val gx = x + (i - 1) * t.scale * 0.3f
            val gy = y - h * (0.3f + 0.25f * i)
            drawLine(Color.White, Offset(gx - 4f, gy), Offset(gx + 4f, gy), strokeWidth = 2.5f)
            drawLine(Color.White, Offset(gx, gy - 4f), Offset(gx, gy + 4f), strokeWidth = 2.5f)
        }
    }
    if (c.stunTimer > 0f && !frozen) {
        Pen(this, x + t.scale * 0.3f, y - h * 0.9f, t.scale * 0.4f).spellIcon("zap", time)
    }
}

private fun DrawScope.drawHealth(c: Combatant, t: ArenaTransform) {
    val s = t.scale
    if (c.isTower) {
        val mount = Sprites.sheet(if (c.kind == Kind.KING_TOWER) "tower_king" else "tower_princess")?.mount ?: 2.4f
        val y = t.sy(c.y) - (mount + 1.9f) * t.rise
        drawBar(c, t, t.sx(c.x), y, c.radius * 1.4f * s)
        label(c.hp.toInt().toString(), t.sx(c.x), y - s * 0.32f, s * 0.42f)
        return
    }
    if (c.deploying || (c.hp >= c.maxHp && c.kind != Kind.BUILDING)) return
    val id = c.card?.id ?: return
    val top = feetY(c, t) - (spriteHeight(id, s) ?: s) + s * 0.1f
    drawBar(c, t, t.ix(c), top, max(c.radius * 2 * s, s * 0.8f))
}

private fun DrawScope.drawBar(c: Combatant, t: ArenaTransform, cx: Float, top: Float, width: Float) {
    val h = max(5f, width * 0.12f).coerceAtMost(13f)
    val frac = (c.hp / c.maxHp).coerceIn(0f, 1f)
    drawRoundRect(Color(0xCC000000), Offset(cx - width / 2 - 1.5f, top - 1.5f), Size(width + 3f, h + 3f), CornerRadius(h / 2))
    drawRoundRect(t.colorOf(c.team), Offset(cx - width / 2, top), Size(width * frac, h), CornerRadius(h / 2))
}

// ------------------------------------------------------------------ projectiles

private fun DrawScope.drawProjectile(p: Projectile, t: ArenaTransform, time: Float) {
    val s = t.scale
    val prog = p.progress
    val targetHeight = if (p.target.flying) FLY_HEIGHT + 0.5f else 0.5f
    val arc = when (p.style) {
        ProjectileStyle.BOMB -> 1.6f
        ProjectileStyle.CANNONBALL -> 0.6f
        ProjectileStyle.ARROW, ProjectileStyle.SPEAR -> 0.5f
        else -> 0.2f
    }
    fun height(pr: Float) = p.launchHeight + (targetHeight - p.launchHeight) * pr + sin(PI.toFloat() * pr) * arc
    val gx = t.sx(p.prevX + (p.x - p.prevX) * t.alpha)
    val gy = t.sy(p.prevY + (p.y - p.prevY) * t.alpha)
    val px = gx
    val py = gy - height(prog) * t.rise

    // Screen-space flight direction, including the arc, for orienting arrows and trails.
    val dist = max(0.01f, hypot(p.tx - p.startX, p.ty - p.startY))
    val dh = (height(min(1f, prog + 0.05f)) - height(max(0f, prog - 0.05f))) / 0.1f
    var vx = (p.tx - p.startX) * t.dir / dist * s
    var vy = (p.ty - p.startY) * t.dir / dist * t.depth - dh / dist * t.rise
    val vl = max(0.01f, hypot(vx, vy))
    vx /= vl
    vy /= vl

    // Ground shadow helps judge where it lands.
    drawOval(Color(0x33000000), Offset(gx - s * 0.15f, gy - t.depth * 0.08f), Size(s * 0.3f, t.depth * 0.16f))

    when (p.style) {
        ProjectileStyle.ARROW, ProjectileStyle.SPEAR -> {
            val len = s * if (p.style == ProjectileStyle.SPEAR) 0.85f else 0.6f
            val tail = Offset(px - vx * len, py - vy * len)
            val tip = Offset(px, py)
            drawLine(Ink, tail, tip, strokeWidth = s * 0.11f, cap = StrokeCap.Round)
            drawLine(Color(0xFF8D5A2B), tail, tip, strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            val hx = -vy * s * 0.09f
            val hy = vx * s * 0.09f
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(px + vx * s * 0.18f, py + vy * s * 0.18f)
                lineTo(px + hx, py + hy)
                lineTo(px - hx, py - hy)
                close()
            }
            drawPath(head, Color(0xFFCFD8DC))
            drawPath(head, Ink, style = Stroke(s * 0.025f))
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
            drawCircle(Ink, s * 0.2f, Offset(px, py))
            drawCircle(Color(0xFF455A64), s * 0.16f, Offset(px, py))
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
        groundCircle(t, target, sp.card.spellRadius, Color(0x33FFFFFF))
        groundCircle(t, target, sp.card.spellRadius, Color.White, Stroke(s * 0.06f, pathEffect = Dashed))

        // A flaming comet arcing in from the king tower.
        val prog = sp.progress
        val h = sin(PI.toFloat() * prog) * 4f + (1f - prog) * 2f
        val pos = Offset(t.sx(sp.x), t.sy(sp.y) - h * t.rise)
        val back = Offset(t.sx(sp.startX) - pos.x, t.sy(sp.startY) - pos.y)
        val bl = max(0.01f, hypot(back.x, back.y))
        for (i in 5 downTo 1) {
            drawCircle(
                Color(0xFFFF7A1A).copy(alpha = 0.55f - i * 0.09f),
                s * (0.6f - i * 0.06f),
                Offset(pos.x + back.x / bl * s * 0.32f * i, pos.y + back.y / bl * s * 0.32f * i),
            )
        }
        val heading = t.heading(atan2(sp.ty - sp.startY, sp.tx - sp.startX))
        val frame = (battle.time * 12f).toInt() % 4
        if (sp.card.id != "fireball" || !drawSprite("fireball_fly", true, heading, frame, pos.x, pos.y + 0.6f * 1.2f * t.rise, s)) {
            Pen(this, pos.x, pos.y, s * 1.5f).spellIcon(sp.card.id, battle.time)
        }
    }
}

private fun DrawScope.drawEffects(battle: Battle, t: ArenaTransform) {
    val s = t.scale
    for (e in battle.effects) {
        // Effects are tinted in absolute team colors (blue = PLAYER); swap them when viewing as ENEMY.
        val color = when {
            !t.flipped -> Color(e.color.toInt())
            e.color == Battle.teamColor(Team.PLAYER) -> EnemyColor
            e.color == Battle.teamColor(Team.ENEMY) -> PlayerColor
            else -> Color(e.color.toInt())
        }
        val p = e.progress
        val center = Offset(t.sx(e.x), t.sy(e.y))
        when (e.kind) {
            EffectKind.RING -> groundCircle(
                t, center, e.radius * (0.5f + 0.5f * p), color.copy(alpha = color.alpha * (1f - p)), Stroke(s * 0.14f),
            )
            EffectKind.FLASH -> groundCircle(t, center, e.radius, color.copy(alpha = color.alpha * (1f - p) * 0.6f))
            EffectKind.PUFF -> for (i in 0 until 3) {
                val a = i * 2.1f
                drawCircle(
                    Color(0xFFE0E0E0).copy(alpha = 0.7f * (1f - p)),
                    e.radius * s * (0.35f + 0.4f * p),
                    Offset(center.x + cos(a) * e.radius * s * 0.4f * p, center.y + sin(a) * e.radius * t.depth * 0.4f * p - p * t.rise * 0.6f),
                )
            }
            EffectKind.SPARK -> for (i in 0 until 6) {
                val a = i * PI.toFloat() / 3f + 0.3f
                val r0 = e.radius * s * (0.2f + 0.6f * p)
                val r1 = e.radius * s * (0.6f + 0.8f * p)
                val cy = center.y - t.rise * 0.5f
                drawLine(
                    color.copy(alpha = 1f - p),
                    Offset(center.x + cos(a) * r0, cy + sin(a) * r0),
                    Offset(center.x + cos(a) * r1, cy + sin(a) * r1),
                    strokeWidth = s * 0.07f,
                    cap = StrokeCap.Round,
                )
            }
            EffectKind.EXPLOSION -> {
                // The filled area is the real splash radius; a burst rises from it.
                groundCircle(t, center, e.radius, color.copy(alpha = 0.45f * (1f - p)))
                groundCircle(t, center, e.radius, Color.White.copy(alpha = 0.9f * (1f - p)), Stroke(s * 0.08f))
                drawCircle(Color(0xFFFFF8E1).copy(alpha = 1f - p), e.radius * s * 0.45f * (1f - p), Offset(center.x, center.y - p * t.rise * 0.8f))
            }
            EffectKind.SLASH -> {
                val r = e.radius * s * 1.3f
                val from = atan2((e.y - e.y2) * t.dir * View.DEPTH, (e.x - e.x2) * t.dir) * 180f / PI.toFloat()
                drawArc(
                    Color.White.copy(alpha = 1f - p),
                    startAngle = from + 120f - p * 60f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - t.rise * 0.6f - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(s * 0.12f * (1f - p * 0.5f), cap = StrokeCap.Round),
                )
            }
            EffectKind.FREEZE -> {
                // Icy zone for the whole freeze, with snow drifting down and the ice crystal overhead.
                val fade = ((e.duration - e.age) / 0.5f).coerceIn(0f, 1f) * (e.age / 0.2f).coerceIn(0f, 1f)
                groundCircle(t, center, e.radius, Color(0xFF81D4FA).copy(alpha = 0.3f * fade))
                groundCircle(t, center, e.radius, Color.White.copy(alpha = 0.8f * fade), Stroke(s * 0.07f, pathEffect = Dashed))
                for (i in 0 until 16) {
                    val ang = i * 2.4f
                    val dist = (i % 5 + 1) / 6f * e.radius
                    val drift = (e.age * 0.7f + i * 0.13f) % 1f
                    val fx = center.x + cos(ang) * dist * s
                    val fy = center.y + sin(ang) * dist * t.depth - (1f - drift) * t.rise * 2.5f
                    drawCircle(Color.White.copy(alpha = 0.85f * fade), s * 0.07f, Offset(fx, fy))
                }
                val bob = sin(e.age * 3f) * t.rise * 0.15f
                if (!drawPicture("card_freeze", center.x, center.y - t.rise * 3.6f + bob, s * 2.2f, fade)) {
                    Pen(this, center.x, center.y - t.rise * 3.6f + bob, s * 1.8f, alpha = fade).spellIcon("freeze", e.age)
                }
            }
            EffectKind.DEATH -> e.body?.let { b ->
                // The unit keels over sideways and fades out; flyers drop to the ground first.
                val heading = t.heading(b.heading)
                val side = if (cos(heading) >= 0f) 1f else -1f
                val fall = if (b.flying) FLY_HEIGHT * (1f - min(1f, p * 1.6f)).let { it * it } else 0f
                val feet = Offset(center.x, center.y - fall * t.rise)
                val sheet = Sprites.sheet(b.cardId)
                if (sheet != null) {
                    withTransform({ rotate(side * 75f * min(1f, p * 1.8f), feet) }) {
                        drawSprite(b.cardId, t.isBlue(b.team), heading, 0, feet.x, feet.y, s, alpha = (1f - p) * 0.9f, flash = p < 0.12f)
                    }
                }
            }
            EffectKind.LINE -> {
                // Jagged lightning, re-jittered every frame so it crackles.
                val a = Offset(t.sx(e.x), t.sy(e.y) - e.lift * t.rise)
                val b = Offset(t.sx(e.x2), t.sy(e.y2) - 0.5f * t.rise)
                var prev = a
                val segs = 6
                val w = if (e.radius > 0f) e.radius else 1f
                for (i in 1..segs) {
                    val f = i / segs.toFloat()
                    val jitter = if (i == segs) 0f else (kotlin.random.Random.nextFloat() - 0.5f) * s * 0.6f
                    val next = Offset(a.x + (b.x - a.x) * f + jitter, a.y + (b.y - a.y) * f)
                    drawLine(color.copy(alpha = 0.5f * (1f - p)), prev, next, strokeWidth = s * 0.22f * w, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 1f - p), prev, next, strokeWidth = s * 0.08f * w, cap = StrokeCap.Round)
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
        groundCircle(t, center, g.card.spellRadius, tint.copy(alpha = 0.25f))
        groundCircle(t, center, g.card.spellRadius, tint, Stroke(s * 0.07f, pathEffect = Dashed))
        if (!drawPicture("card_${g.card.id}", center.x, center.y - t.rise * 0.9f, s * 1.8f, 0.85f)) {
            Pen(this, center.x, center.y, s * 1.2f, alpha = 0.85f).spellIcon(g.card.id, battle.time)
        }
    } else {
        // Attack range of ranged troops and buildings, so you can see what they'll reach.
        if (g.card.range >= 2f) {
            val reach = g.card.range + g.card.radius
            groundCircle(t, center, reach, tint.copy(alpha = 0.08f))
            groundCircle(t, center, reach, tint.copy(alpha = 0.6f), Stroke(s * 0.05f, pathEffect = Dashed))
        }
        // Highlight the exact tiles the card will occupy.
        val tiles = if (g.card.type == CardType.BUILDING) 2 else 1
        val left = if (tiles == 2) g.x - 1f else kotlin.math.floor(g.x)
        val top = if (tiles == 2) g.y - 1f else kotlin.math.floor(g.y)
        val corner = t.topLeft(left, top, tiles.toFloat(), tiles.toFloat())
        drawRect(tint.copy(alpha = 0.35f), corner, Size(tiles * s, tiles * t.depth))
        drawRect(tint, corner, Size(tiles * s, tiles * t.depth), style = Stroke(s * 0.06f))
        // The units themselves, translucent, facing the enemy.
        val facing = -PI.toFloat() / 2
        for ((x, y) in g.formation) {
            val fx = t.sx(x)
            val fy = t.sy(y) - (if (g.card.flying) FLY_HEIGHT else 0f) * t.rise
            groundCircle(t, Offset(fx, t.sy(y)), g.card.radius * 1.1f, tint.copy(alpha = 0.5f))
            if (!drawSprite(g.card.id, true, facing, 0, fx, fy, s, alpha = 0.7f)) {
                drawCircle(tint.copy(alpha = 0.7f), g.card.radius * s, Offset(fx, fy - g.card.radius * s))
            }
        }
    }
    if (waiting) {
        label(
            String.format(Locale.US, "%.1fs", g.waitSeconds),
            center.x,
            center.y - s * 2.4f,
            s * 0.6f,
            0xFFF3B6FF.toInt(),
        )
    }
}
