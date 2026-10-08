package com.clashclaude.game.ui

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.Cards
import com.clashclaude.game.game.Arena
import com.clashclaude.game.game.Battle
import com.clashclaude.game.game.Outcome
import com.clashclaude.game.game.Sfx
import com.clashclaude.game.game.Team
import kotlin.math.ceil

@Composable
fun BattleScreen(
    playerDeck: List<CardDef>,
    /** A pre-built battle to show instead of starting a new one (used by the playtest harness). */
    initialBattle: Battle? = null,
    audio: GameAudio = GameAudio.Silent,
    onFinished: (Outcome) -> Unit,
) {
    val battle = remember {
        initialBattle ?: Battle(playerDeck, Cards.aiDecks.random().mapNotNull { Cards.get(it) })
    }
    var frame by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var confirmLeave by remember { mutableStateOf(false) }
    // Short message shown over the arena, e.g. "Not enough elixir", until battle time `second`.
    var notice by remember { mutableStateOf<Pair<String, Float>?>(null) }
    val transform = remember { ArenaTransform() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val cardOrigins = remember { Array(4) { Offset.Zero } }

    LaunchedEffect(battle) {
        audio.music(true)
        var fastMusic = false
        var last = withFrameNanos { it }
        while (battle.outcome == null) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                battle.update(dt)
                battle.drainSounds().forEach(audio::play)
                if (battle.doubleElixir != fastMusic) {
                    fastMusic = battle.doubleElixir
                    audio.music(true, fast = fastMusic)
                }
                frame++
            }
        }
        battle.drainSounds().forEach(audio::play)
        audio.music(false)
        audio.play(if (battle.outcome == Outcome.WIN) Sfx.VICTORY else Sfx.DEFEAT)
        frame++
    }
    DisposableEffect(battle) {
        onDispose { audio.music(false) }
    }

    BackHandler(enabled = battle.outcome == null) { confirmLeave = true }

    // Reading the frame counter recomposes this screen every tick. Child HUD composables
    // get plain values (not the mutable Battle) so they aren't skipped as "unchanged".
    if (frame < 0) return

    fun inArena(rootPos: Offset): Boolean {
        val local = rootPos - transform.originInRoot
        val wx = transform.worldX(local.x)
        val wy = transform.worldY(local.y)
        return wx in 0f..Arena.WIDTH && wy in 0f..Arena.HEIGHT
    }

    /** Arena tile under a root-space position, snapped to where [card] can go; null if off the arena. */
    fun dropSpot(card: CardDef, rootPos: Offset): Pair<Float, Float>? {
        if (!inArena(rootPos)) return null
        val local = rootPos - transform.originInRoot
        return battle.snapPlacement(Team.PLAYER, card, transform.worldX(local.x), transform.worldY(local.y))
    }

    /**
     * Where a dragged card would land. The point is lifted above the fingertip so the
     * ghost stays visible; dragging back over the hand means "cancel".
     */
    fun dragSpot(card: CardDef): Pair<Float, Float>? {
        if (!inArena(dragPos)) return null
        val lifted = Offset(dragPos.x, dragPos.y - DRAG_LIFT_TILES * transform.scale)
        return dropSpot(card, if (inArena(lifted)) lifted else dragPos)
    }

    fun tryDeploy(index: Int, spot: Pair<Float, Float>?): Boolean {
        val card = battle.player.hand.getOrNull(index) ?: return false
        val (x, y) = spot ?: return false
        if (battle.player.elixir < card.cost) {
            notice = "Not enough elixir!" to battle.time + 1.2f
            audio.play(Sfx.DENY)
            return false
        }
        val ok = battle.deploy(Team.PLAYER, index, x, y)
        battle.drainSounds().forEach(audio::play)
        return ok
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Night)
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(
                timeLeft = battle.timeLeft,
                overtime = battle.overtime,
                doubleElixir = battle.doubleElixir,
                enemyCrowns = battle.enemy.crowns,
                playerCrowns = battle.player.crowns,
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { transform.originInRoot = it.positionInRoot() }
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val i = selected
                            val card = battle.player.hand.getOrNull(i)
                            if (card != null && tryDeploy(i, dropSpot(card, pos + transform.originInRoot))) selected = -1
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (frame < 0) return@Canvas // Read the tick so the arena redraws every frame.
                    val dragged = battle.player.hand.getOrNull(dragIndex)
                    val ghost = dragged?.let { card ->
                        dragSpot(card)?.let { (x, y) ->
                            Ghost(
                                card, x, y,
                                formation = battle.formation(Team.PLAYER, card, x, y),
                                waitSeconds = battle.secondsUntilAffordable(battle.player, card),
                            )
                        }
                    }
                    drawBattle(battle, transform, ghost, armed = dragged ?: battle.player.hand.getOrNull(selected))
                }
                notice?.let { (text, until) ->
                    if (battle.time < until) {
                        Text(
                            text,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Palette.ElixirDark.copy(alpha = 0.9f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            HandBar(
                hand = battle.player.hand.toList(),
                next = battle.player.next,
                elixir = battle.player.elixir,
                selected = selected,
                dragIndex = dragIndex,
                onSelect = {
                    selected = if (selected == it) -1 else it
                    audio.play(Sfx.CLICK)
                },
                onCardPositioned = { i, pos -> cardOrigins[i] = pos },
                onDragStart = { i, offset ->
                    dragIndex = i
                    selected = -1
                    dragPos = cardOrigins[i] + offset
                },
                onDrag = { dragPos += it },
                onDragEnd = {
                    val i = dragIndex
                    val card = battle.player.hand.getOrNull(i)
                    if (card != null) tryDeploy(i, dragSpot(card))
                    dragIndex = -1
                },
                onDragCancel = { dragIndex = -1 },
            )
        }

        // While dragging over the hand (not yet over the arena), the card follows the finger.
        val dragged = battle.player.hand.getOrNull(dragIndex)
        if (dragged != null && !inArena(dragPos)) {
            val width = 72.dp
            val half = with(LocalDensity.current) { (width / 2).toPx() }
            CardTile(
                dragged,
                showName = false,
                modifier = Modifier
                    .offset { IntOffset((dragPos.x - rootOrigin.x - half).toInt(), (dragPos.y - rootOrigin.y - half * 1.3f).toInt()) }
                    .width(width),
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

/** The drop point floats this many tiles above the fingertip while dragging. */
private const val DRAG_LIFT_TILES = 1.3f

// ---------------------------------------------------------------------- HUD

@Composable
private fun TopBar(
    timeLeft: Float,
    overtime: Boolean,
    doubleElixir: Boolean,
    enemyCrowns: Int,
    playerCrowns: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Navy)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("🤖 Claude Bot", color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Crowns(enemyCrowns)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val secs = ceil(timeLeft).toInt()
            Text(
                if (overtime) "Overtime" else "Time left",
                color = Palette.TextDim,
                fontSize = 11.sp,
            )
            Text(
                "%d:%02d".format(secs / 60, secs % 60),
                color = if (overtime) Palette.Gold else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            if (doubleElixir) {
                Text("x2 Elixir", color = Palette.Elixir, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("You 🙂", color = Palette.Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Crowns(playerCrowns)
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
    hand: List<CardDef>,
    next: CardDef?,
    elixir: Float,
    selected: Int,
    dragIndex: Int,
    onSelect: (Int) -> Unit,
    onCardPositioned: (Int, Offset) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Palette.Panel, Palette.Navy)))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(0.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Next", color = Palette.TextDim, fontSize = 11.sp)
                next?.let { CardTile(it, showName = false, showCost = false, modifier = Modifier.fillMaxWidth()) }
            }
            for (i in 0 until 4) {
                val card = hand[i]
                val affordable = elixir >= card.cost
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
                                onDragCancel = onDragCancel,
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
        ElixirBar(elixir)
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
