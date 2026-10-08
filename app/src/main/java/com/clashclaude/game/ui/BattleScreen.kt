package com.clashclaude.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
import com.clashclaude.game.data.ProjectileStyle
import com.clashclaude.game.game.Arena
import com.clashclaude.game.game.Battle
import com.clashclaude.game.game.Combatant
import com.clashclaude.game.game.EffectKind
import com.clashclaude.game.game.Kind
import com.clashclaude.game.game.Outcome
import com.clashclaude.game.game.Team
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Maps arena tiles to canvas pixels. Updated every draw. */
private class ArenaTransform {
    var scale = 1f
    var ox = 0f
    var oy = 0f
    var originInRoot = Offset.Zero
    fun sx(x: Float) = ox + x * scale
    fun sy(y: Float) = oy + y * scale
    fun worldX(px: Float) = (px - ox) / scale
    fun worldY(py: Float) = (py - oy) / scale
}

@Composable
fun BattleScreen(playerDeck: List<CardDef>, onFinished: (Outcome) -> Unit) {
    val battle = remember {
        Battle(playerDeck, Cards.aiDecks.random().mapNotNull { Cards.get(it) })
    }
    var frame by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var confirmLeave by remember { mutableStateOf(false) }
    val transform = remember { ArenaTransform() }
    val cardOrigins = remember { Array(4) { Offset.Zero } }

    LaunchedEffect(battle) {
        var last = withFrameNanos { it }
        while (battle.outcome == null) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                battle.update(dt)
                frame++
            }
        }
        frame++
    }

    BackHandler(enabled = battle.outcome == null) { confirmLeave = true }

    // Reading the frame counter recomposes the HUD every tick.
    @Suppress("UNUSED_VARIABLE") val tick = frame

    fun tryDeploy(index: Int, rootPos: Offset): Boolean {
        val local = rootPos - transform.originInRoot
        val wx = transform.worldX(local.x)
        val wy = transform.worldY(local.y)
        return battle.deploy(Team.PLAYER, index, wx, wy)
    }

    Box(Modifier.fillMaxSize().background(Palette.Night)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(battle)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { transform.originInRoot = it.positionInRoot() }
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val i = selected
                            if (i >= 0 && tryDeploy(i, pos + transform.originInRoot)) selected = -1
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (frame < 0) return@Canvas // Read the tick so the arena redraws every frame.
                    val ghostCard = when {
                        dragIndex >= 0 -> battle.player.hand.getOrNull(dragIndex)
                        selected >= 0 -> battle.player.hand.getOrNull(selected)
                        else -> null
                    }
                    val ghostPos = if (dragIndex >= 0) dragPos - transform.originInRoot else null
                    drawBattle(battle, transform, ghostCard, ghostPos)
                }
            }
            HandBar(
                battle = battle,
                selected = selected,
                dragIndex = dragIndex,
                onSelect = { selected = if (selected == it) -1 else it },
                onCardPositioned = { i, pos -> cardOrigins[i] = pos },
                onDragStart = { i, offset ->
                    dragIndex = i
                    selected = -1
                    dragPos = cardOrigins[i] + offset
                },
                onDrag = { dragPos += it },
                onDragEnd = {
                    val i = dragIndex
                    dragIndex = -1
                    if (i >= 0) tryDeploy(i, dragPos)
                },
            )
        }

        battle.outcome?.let { outcome ->
            ResultOverlay(battle, outcome) { onFinished(outcome) }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Palette.Navy,
            title = { Text("Leave battle?") },
            text = { Text("Leaving now counts as a loss.") },
            confirmButton = {
                Button(onClick = {
                    confirmLeave = false
                    battle.surrender()
                }) { Text("Surrender") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep fighting") } },
        )
    }
}

// ---------------------------------------------------------------------- HUD

@Composable
private fun TopBar(battle: Battle) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Navy)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("🤖 Claude Bot", color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Crowns(battle.enemy.crowns)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val secs = ceil(battle.timeLeft).toInt()
            Text(
                if (battle.overtime) "Overtime" else "Time left",
                color = Palette.TextDim,
                fontSize = 11.sp,
            )
            Text(
                "%d:%02d".format(secs / 60, secs % 60),
                color = if (battle.overtime) Palette.Gold else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            if (battle.doubleElixir) {
                Text("x2 Elixir", color = Palette.Elixir, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("You 🙂", color = Palette.Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Crowns(battle.player.crowns)
        }
    }
}

@Composable
private fun Crowns(count: Int) {
    Row {
        repeat(3) { i ->
            Text(
                "👑",
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .alpha(if (i < count) 1f else 0.2f),
            )
        }
    }
}

@Composable
private fun HandBar(
    battle: Battle,
    selected: Int,
    dragIndex: Int,
    onSelect: (Int) -> Unit,
    onCardPositioned: (Int, Offset) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val side = battle.player
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Palette.Panel, Palette.Navy)))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(0.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Next", color = Palette.TextDim, fontSize = 11.sp)
                side.next?.let { CardTile(it, showName = false, modifier = Modifier.fillMaxWidth()) }
            }
            for (i in 0 until 4) {
                val card = side.hand[i]
                val affordable = side.elixir >= card.cost
                Box(
                    Modifier
                        .weight(1f)
                        .offset(y = if (i == selected) (-8).dp else 0.dp)
                        .onGloballyPositioned { onCardPositioned(i, it.positionInRoot()) }
                        .pointerInput(i) {
                            detectDragGestures(
                                onDragStart = { onDragStart(i, it) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        }
                        .pointerInput(i) { detectTapGestures { onSelect(i) } },
                ) {
                    CardTile(
                        card,
                        dimmed = !affordable || i == dragIndex,
                        highlighted = i == selected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ElixirBar(side.elixir)
    }
}

@Composable
private fun ElixirBar(elixir: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Palette.Elixir, Palette.ElixirDark)))
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(elixir.toInt().toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.width(6.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A1840)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * (elixir / Battle.MAX_ELIXIR))
                    .background(Brush.horizontalGradient(listOf(Palette.ElixirDark, Palette.Elixir))),
            )
            Row(Modifier.fillMaxSize()) {
                repeat(10) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, Color.Black.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultOverlay(battle: Battle, outcome: Outcome, onOk: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB0000000))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Navy)
                .border(2.dp, Palette.Gold, RoundedCornerShape(20.dp))
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (title, color) = when (outcome) {
                Outcome.WIN -> "VICTORY!" to Palette.Gold
                Outcome.LOSS -> "DEFEAT" to Palette.Red
                Outcome.DRAW -> "DRAW" to Color.White
            }
            Text(title, color = color, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${battle.player.crowns}", color = Palette.Blue, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("  👑  ", fontSize = 28.sp)
                Text("${battle.enemy.crowns}", color = Palette.Red, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOk,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Color(0xFF3A2600)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("OK", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ drawing

private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
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
private val Stone = Color(0xFF9AA0AE)
private val StoneDark = Color(0xFF6A7080)
private val PlayerColor = Color(0xFF3FA7FF)
private val EnemyColor = Color(0xFFFF4B4B)

private fun teamColor(team: Team) = if (team == Team.PLAYER) PlayerColor else EnemyColor

private fun DrawScope.emoji(text: String, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
    emojiPaint.textSize = size
    emojiPaint.alpha = (alpha * 255).toInt()
    val baseline = cy - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
    drawIntoCanvas { it.nativeCanvas.drawText(text, cx, baseline, emojiPaint) }
}

private fun DrawScope.label(text: String, cx: Float, cy: Float, size: Float) {
    labelPaint.textSize = size
    labelPaint.setShadowLayer(size * 0.15f, 0f, size * 0.08f, android.graphics.Color.BLACK)
    val baseline = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
    drawIntoCanvas { it.nativeCanvas.drawText(text, cx, baseline, labelPaint) }
}

private fun DrawScope.drawBattle(battle: Battle, t: ArenaTransform, ghost: CardDef?, ghostPos: Offset?) {
    t.scale = min(size.width / Arena.WIDTH, size.height / Arena.HEIGHT)
    t.ox = (size.width - Arena.WIDTH * t.scale) / 2f
    t.oy = (size.height - Arena.HEIGHT * t.scale) / 2f
    val s = t.scale

    // Grass checkerboard
    for (ty in 0 until Arena.HEIGHT.toInt()) {
        for (tx in 0 until Arena.WIDTH.toInt()) {
            drawRect(
                if ((tx + ty) % 2 == 0) GrassA else GrassB,
                topLeft = Offset(t.sx(tx.toFloat()), t.sy(ty.toFloat())),
                size = Size(s + 0.5f, s + 0.5f),
            )
        }
    }
    // River with a couple of wave lines
    drawRect(
        River,
        topLeft = Offset(t.sx(0f), t.sy(Arena.RIVER_TOP)),
        size = Size(Arena.WIDTH * s, (Arena.RIVER_BOTTOM - Arena.RIVER_TOP) * s),
    )
    val wave = (battle.time * 0.6f) % 2f
    for (i in 0 until 10) {
        val wx = (i * 2f + wave) % Arena.WIDTH
        drawLine(
            RiverLight,
            Offset(t.sx(wx), t.sy(Arena.RIVER_MID - 0.3f + (i % 2) * 0.6f)),
            Offset(t.sx(wx + 0.8f), t.sy(Arena.RIVER_MID - 0.3f + (i % 2) * 0.6f)),
            strokeWidth = s * 0.08f,
        )
    }
    // Bridges
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

    // Forbidden placement area while a troop/building card is armed.
    if (ghost != null && ghost.type != CardType.SPELL) {
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

    // Rubble of destroyed towers
    for (r in battle.rubble) {
        drawCircle(StoneDark.copy(alpha = 0.7f), r.radius * s * 0.9f, Offset(t.sx(r.x), t.sy(r.y)))
        emoji("🪨", t.sx(r.x), t.sy(r.y), r.radius * s)
    }

    // Shadows for flyers, then ground bodies sorted by depth, then flyers.
    val ground = battle.entities.filter { !it.flying }.sortedBy { it.y }
    val air = battle.entities.filter { it.flying }.sortedBy { it.y }
    for (c in air) {
        drawOval(
            Color(0x44000000),
            topLeft = Offset(t.sx(c.x - c.radius), t.sy(c.y - c.radius * 0.4f)),
            size = Size(c.radius * 2 * s, c.radius * 0.8f * s),
        )
    }
    for (c in ground) {
        if (c.isTower) drawTower(c, t) else drawUnit(c, t, lift = 0f)
    }

    // Projectiles
    for (p in battle.projectiles) {
        val px = t.sx(p.x)
        val py = t.sy(p.y)
        when (p.style) {
            ProjectileStyle.ARROW -> {
                val ang = atan2(p.ty - p.y, p.tx - p.x)
                val len = 0.45f * s
                drawLine(
                    Color(0xFF5D4037),
                    Offset(px - kotlin.math.cos(ang) * len, py - kotlin.math.sin(ang) * len),
                    Offset(px, py),
                    strokeWidth = s * 0.08f,
                )
            }
            ProjectileStyle.BULLET -> drawCircle(Color(0xFF263238), s * 0.12f, Offset(px, py))
            ProjectileStyle.FIRE -> {
                drawCircle(Color(0xFFFF6D00), s * 0.22f, Offset(px, py))
                drawCircle(Color(0xFFFFD180), s * 0.11f, Offset(px, py))
            }
            ProjectileStyle.BOMB -> drawCircle(Color(0xFF212121), s * 0.2f, Offset(px, py))
            ProjectileStyle.ORB -> drawCircle(Color(0xFF7E57C2), s * 0.14f, Offset(px, py))
            else -> drawCircle(Color.White, s * 0.1f, Offset(px, py))
        }
    }

    for (c in air) drawUnit(c, t, lift = 0.6f)

    // Spells in flight
    for (sp in battle.spells) {
        emoji(sp.card.emoji, t.sx(sp.x), t.sy(sp.y), s * 1.2f)
        drawCircle(
            Color.White.copy(alpha = 0.35f),
            sp.card.spellRadius * s,
            Offset(t.sx(sp.tx), t.sy(sp.ty)),
            style = Stroke(s * 0.06f),
        )
    }

    // Effects
    for (e in battle.effects) {
        val color = Color(e.color.toInt())
        val p = e.progress
        val center = Offset(t.sx(e.x), t.sy(e.y))
        when (e.kind) {
            EffectKind.RING -> drawCircle(
                color.copy(alpha = color.alpha * (1f - p)),
                e.radius * s * (0.4f + 0.6f * p),
                center,
                style = Stroke(s * 0.15f),
            )
            EffectKind.FLASH -> drawCircle(color.copy(alpha = color.alpha * (1f - p) * 0.6f), e.radius * s, center)
            EffectKind.PUFF -> drawCircle(color.copy(alpha = color.alpha * (1f - p)), e.radius * s * (0.6f + p), center)
            EffectKind.LINE -> drawLine(
                color.copy(alpha = 1f - p),
                center,
                Offset(t.sx(e.x2), t.sy(e.y2)),
                strokeWidth = s * 0.12f,
            )
        }
    }

    // Drag ghost
    if (ghost != null && ghostPos != null) {
        val wx = t.worldX(ghostPos.x)
        val wy = t.worldY(ghostPos.y)
        val valid = battle.canPlace(Team.PLAYER, ghost, wx, wy)
        val ring = if (ghost.type == CardType.SPELL) ghost.spellRadius else max(0.6f, ghost.radius + 0.3f)
        drawCircle(
            (if (valid) Color.White else Color.Red).copy(alpha = 0.3f),
            ring * s,
            ghostPos,
        )
        drawCircle(if (valid) Color.White else Color.Red, ring * s, ghostPos, style = Stroke(s * 0.06f))
        emoji(ghost.emoji, ghostPos.x, ghostPos.y, s * 1.1f, alpha = 0.8f)
    }
}

private fun DrawScope.drawHpBar(c: Combatant, t: ArenaTransform, cx: Float, top: Float, width: Float, showNumber: Boolean) {
    val s = t.scale
    val h = max(4f, s * 0.16f)
    val frac = (c.hp / c.maxHp).coerceIn(0f, 1f)
    drawRoundRect(Color(0xCC000000), Offset(cx - width / 2, top), Size(width, h), CornerRadius(h / 2))
    drawRoundRect(teamColor(c.team), Offset(cx - width / 2, top), Size(width * frac, h), CornerRadius(h / 2))
    if (showNumber) label(c.hp.toInt().toString(), cx, top - s * 0.25f, s * 0.38f)
}

private fun DrawScope.drawTower(c: Combatant, t: ArenaTransform) {
    val s = t.scale
    val cx = t.sx(c.x)
    val cy = t.sy(c.y)
    val half = c.radius * s * 0.95f
    val color = teamColor(c.team)
    // Base
    drawRoundRect(StoneDark, Offset(cx - half, cy - half + s * 0.15f), Size(half * 2, half * 2), CornerRadius(s * 0.3f))
    drawRoundRect(Stone, Offset(cx - half, cy - half), Size(half * 2, half * 2), CornerRadius(s * 0.3f))
    // Roof
    val roof = half * 0.62f
    drawRoundRect(color, Offset(cx - roof, cy - roof), Size(roof * 2, roof * 2), CornerRadius(s * 0.2f))
    if (c.hitFlash > 0f) {
        drawRoundRect(Color(0x88FFFFFF), Offset(cx - half, cy - half), Size(half * 2, half * 2), CornerRadius(s * 0.3f))
    }
    emoji(c.emoji, cx, cy, roof * 1.6f)
    if (c.kind == Kind.KING_TOWER && !c.active) emoji("💤", cx + half * 0.7f, cy - half * 0.7f, s * 0.6f)
    if (c.stunTimer > 0f) emoji("⚡", cx + half * 0.7f, cy - half * 0.7f, s * 0.6f)
    drawHpBar(c, t, cx, cy - half - s * 0.45f, half * 2, showNumber = true)
}

private fun DrawScope.drawUnit(c: Combatant, t: ArenaTransform, lift: Float) {
    val s = t.scale
    val cx = t.sx(c.x)
    val cy = t.sy(c.y - lift)
    val r = c.radius * s
    val alpha = if (c.deploying) 0.5f else 1f
    val color = teamColor(c.team)
    val bump = if (c.attackAnim > 0f) 1.12f else 1f

    if (lift == 0f) {
        drawOval(Color(0x33000000), Offset(cx - r, cy + r * 0.4f), Size(r * 2, r * 0.8f))
    }
    if (c.kind == Kind.BUILDING) {
        drawRoundRect(StoneDark.copy(alpha = alpha), Offset(cx - r, cy - r), Size(r * 2, r * 2), CornerRadius(s * 0.25f))
        drawRoundRect(color.copy(alpha = alpha), Offset(cx - r, cy - r), Size(r * 2, r * 2), CornerRadius(s * 0.25f), style = Stroke(s * 0.12f))
    } else {
        drawCircle(color.copy(alpha = 0.85f * alpha), r * bump, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.8f * alpha), r * bump, Offset(cx, cy), style = Stroke(s * 0.06f))
    }
    if (c.hitFlash > 0f) drawCircle(Color(0x99FFFFFF), r, Offset(cx, cy))
    emoji(c.emoji, cx, cy, r * 1.5f * bump, alpha)

    if (c.deploying) {
        drawArc(
            Color.White,
            startAngle = -90f,
            sweepAngle = 360f * (c.deployTimer / Combatant.DEPLOY_TIME),
            useCenter = false,
            topLeft = Offset(cx - r * 1.2f, cy - r * 1.2f),
            size = Size(r * 2.4f, r * 2.4f),
            style = Stroke(s * 0.08f),
        )
    }
    if (c.stunTimer > 0f) emoji("⚡", cx + r * 0.8f, cy - r * 0.8f, s * 0.45f)
    if (c.hp < c.maxHp || c.kind == Kind.BUILDING) {
        drawHpBar(c, t, cx, cy - r - s * 0.3f, max(r * 2, s * 0.8f), showNumber = false)
    }
}
