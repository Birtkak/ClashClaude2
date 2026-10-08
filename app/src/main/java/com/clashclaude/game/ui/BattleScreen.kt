package com.clashclaude.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.Cards
import com.clashclaude.game.game.Arena
import com.clashclaude.game.game.Battle
import com.clashclaude.game.game.Command
import com.clashclaude.game.game.LocalMatch
import com.clashclaude.game.game.Match
import com.clashclaude.game.game.Outcome
import com.clashclaude.game.game.Sfx
import com.clashclaude.game.game.Team
import kotlin.math.ceil

@Composable
fun BattleScreen(
    playerDeck: List<CardDef>,
    /** A pre-built battle to show instead of starting a new one (used by tests and the playtest harness). */
    initialBattle: Battle? = null,
    /** Which side this device plays; it is always drawn at the bottom. */
    viewer: Team = Team.PLAYER,
    opponentName: String = "Claude Bot",
    audio: GameAudio = GameAudio.Silent,
    onFinished: (Outcome) -> Unit,
) {
    val match: Match = remember {
        LocalMatch(initialBattle ?: Battle(playerDeck, Cards.aiDecks.random().mapNotNull { Cards.get(it) }), viewer)
    }
    val battle = match.battle
    val me = battle.side(viewer)
    val foe = battle.side(viewer.opponent)
    var frame by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    var confirmLeave by remember { mutableStateOf(false) }
    // Short message shown over the arena, e.g. "Not enough elixir", until battle time `second`.
    var notice by remember { mutableStateOf<Pair<String, Float>?>(null) }
    val transform = remember { ArenaTransform(viewer) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val cardOrigins = remember { Array(4) { Offset.Zero } }

    LaunchedEffect(match) {
        // Decode every sheet this match can show before the first frame.
        Sprites.preload(
            (battle.player.hand + battle.player.queue + battle.enemy.hand + battle.enemy.queue).map { it.id } +
                listOf("tower_princess", "tower_king", "archers", "kingtop"),
        )
        audio.music(true)
        var fastMusic = false
        var last = withFrameNanos { it }
        while (battle.outcome == null) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                transform.alpha = match.advance(dt)
                battle.drainSounds().forEach(audio::play)
                if (battle.doubleElixir != fastMusic) {
                    fastMusic = battle.doubleElixir
                    audio.music(true, fast = fastMusic)
                }
                frame++
            }
        }
        transform.alpha = 1f
        battle.drainSounds().forEach(audio::play)
        audio.music(false)
        audio.play(if (battle.outcomeFor(viewer) == Outcome.WIN) Sfx.VICTORY else Sfx.DEFEAT)
        frame++
    }
    DisposableEffect(match) {
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
        return battle.snapPlacement(viewer, card, transform.worldX(local.x), transform.worldY(local.y))
    }

    /**
     * Where the card under the finger would land: right under it, snapped to the tile grid and
     * the deploy zone. Off the arena (back over the hand) means "cancel".
     */
    fun dragSpot(card: CardDef): Pair<Float, Float>? = dropSpot(card, dragPos)

    /** Sends a play to the match; it lands on the next tick (or when the server accepts it). */
    fun tryDeploy(index: Int, spot: Pair<Float, Float>?): Boolean {
        val card = me.hand.getOrNull(index) ?: return false
        val (x, y) = spot ?: return false
        if (me.elixir < card.cost) {
            notice = "Not enough elixir!" to battle.time + 1.2f
            audio.play(Sfx.DENY)
            return false
        }
        if (!battle.canPlace(viewer, card, x, y)) {
            notice = "Can't place it there" to battle.time + 1.2f
            audio.play(Sfx.DENY)
            return false
        }
        match.send(Command.PlayCard(viewer, card.id, x, y))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
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
                opponentName = opponentName,
                enemyCrowns = foe.crowns,
                playerCrowns = me.crowns,
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("arena")
                    .onGloballyPositioned {
                        transform.originInRoot = it.positionInRoot()
                        transform.fit(it.size.width.toFloat(), it.size.height.toFloat())
                    }
                    .pointerInput(Unit) {
                        // With a card selected, tap the arena to place it there, or press and slide:
                        // the ghost follows the finger and the card lands where it's released.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val i = selected
                            if (me.hand.getOrNull(i) == null) return@awaitEachGesture
                            down.consume()
                            dragIndex = i
                            dragPos = down.position + transform.originInRoot
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    val card = me.hand.getOrNull(i)
                                    if (card != null && tryDeploy(i, dragSpot(card))) selected = -1
                                    break
                                }
                                dragPos = change.position + transform.originInRoot
                                change.consume()
                            }
                            dragIndex = -1
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (frame < 0) return@Canvas // Read the tick so the arena redraws every frame.
                    val dragged = me.hand.getOrNull(dragIndex)
                    val ghost = dragged?.let { card ->
                        dragSpot(card)?.let { (x, y) ->
                            Ghost(
                                card, x, y,
                                formation = battle.formation(viewer, card, x, y),
                                waitSeconds = battle.secondsUntilAffordable(me, card),
                            )
                        }
                    }
                    drawBattle(battle, transform, ghost, armed = dragged ?: me.hand.getOrNull(selected))
                }
            // While a card is picked up, say what to do with it.
                val picked = me.hand.getOrNull(selected)
                if (picked != null && dragIndex < 0 && battle.outcome == null) {
                    OutlinedText(
                        "Tap the arena to place ${picked.name} · hold to aim",
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xE6142440))
                            .border(2.dp, Palette.Gold, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                notice?.let { (text, until) ->
                    if (battle.time < until) {
                        OutlinedText(
                            text,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Palette.ElixirDark.copy(alpha = 0.9f))
                                .border(2.dp, Color(0xFF0B1324), RoundedCornerShape(50))
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            HandBar(
                hand = me.hand.toList(),
                next = me.next,
                elixir = me.elixir,
                selected = selected,
                dragIndex = dragIndex,
                onSelect = {
                    selected = if (selected == it) -1 else it
                    audio.play(Sfx.CLICK)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onCardPositioned = { i, pos -> cardOrigins[i] = pos },
                onDragStart = { i, offset ->
                    dragIndex = i
                    selected = -1
                    dragPos = cardOrigins[i] + offset
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onDrag = { dragPos += it },
                onDragEnd = {
                    val i = dragIndex
                    val card = me.hand.getOrNull(i)
                    if (card != null) {
                        // Let go back over the hand: keep the card picked up instead of placing it.
                        if (!inArena(dragPos)) selected = i else tryDeploy(i, dragSpot(card))
                    }
                    dragIndex = -1
                },
                onDragCancel = { dragIndex = -1 },
            )
        }

        // While dragging over the hand (not yet over the arena), the card follows the finger.
        val dragged = me.hand.getOrNull(dragIndex)
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

        battle.outcomeFor(viewer)?.let { outcome ->
            ResultOverlay(outcome, myCrowns = me.crowns, theirCrowns = foe.crowns) { onFinished(outcome) }
        }

        if (confirmLeave) {
            LeaveDialog(
                onStay = { confirmLeave = false },
                onSurrender = {
                    confirmLeave = false
                    match.send(Command.Surrender(viewer))
                },
            )
        }
    }
}

// ---------------------------------------------------------------------- HUD

private val Ink = Color(0xFF0B1324)

@Composable
private fun TopBar(
    timeLeft: Float,
    overtime: Boolean,
    doubleElixir: Boolean,
    opponentName: String,
    enemyCrowns: Int,
    playerCrowns: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Palette.Night, Color(0xFF1B3260))))
            .drawBehind { drawLine(Ink, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 3.dp.toPx()) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NamePlate(opponentName, enemyCrowns, Palette.Red, Modifier.weight(1f), Alignment.Start)
        // Timer plaque.
        val secs = ceil(timeLeft).toInt()
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2E3B55), Color(0xFF151D2E))))
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedText(if (overtime) "OVERTIME" else "TIME LEFT", fontSize = 11.sp, color = if (overtime) Palette.Gold else Palette.TextDim)
            OutlinedText(
                "%d:%02d".format(secs / 60, secs % 60),
                fontSize = 26.sp,
                color = if (overtime || secs <= 10) Palette.Gold else Color.White,
            )
            if (doubleElixir) OutlinedText("x2 ELIXIR", fontSize = 11.sp, color = Palette.Elixir)
        }
        NamePlate("You", playerCrowns, Palette.Blue, Modifier.weight(1f), Alignment.End)
    }
}

@Composable
private fun NamePlate(name: String, crowns: Int, color: Color, modifier: Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align) {
        OutlinedText(name, fontSize = 17.sp, color = color, maxLines = 1)
        Crowns(crowns, color)
    }
}

@Composable
private fun Crowns(count: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val won = i < count
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (won) color else Color(0xFF26324A))
                    .border(2.dp, Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("👑", fontSize = 12.sp, modifier = Modifier.alpha(if (won) 1f else 0.25f))
            }
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
            .background(Brush.verticalGradient(listOf(Color(0xFF2A4C86), Palette.Night)))
            .drawBehind { drawLine(Color(0xFF0B1324), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 3.dp.toPx()) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(0.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText("NEXT", fontSize = 12.sp, color = Palette.TextDim)
                next?.let { CardTile(it, showName = false, showCost = false, modifier = Modifier.fillMaxWidth()) }
            }
            for (i in 0 until 4) {
                val card = hand[i]
                val affordable = elixir >= card.cost
                // The picked-up card pops up out of the hand.
                val lift by animateDpAsState(if (i == selected) (-16).dp else 0.dp, label = "card-lift")
                Box(
                    Modifier
                        .weight(1f)
                        .offset(y = lift)
                        .onGloballyPositioned { onCardPositioned(i, it.positionInRoot()) }
                        .testTag("hand-$i")
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
        // Elixir drop with the whole-number count.
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFF3B6FF), Palette.Elixir, Palette.ElixirDark)))
                .border(2.5.dp, Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedText(elixir.toInt().toString(), fontSize = 19.sp)
        }
        Spacer(Modifier.width(6.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF241534))
                .border(2.5.dp, Ink, RoundedCornerShape(8.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * (elixir / Battle.MAX_ELIXIR))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF08CFF), Palette.Elixir, Palette.ElixirDark))),
            )
            // Gloss and pip dividers.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(horizontal = 4.dp)
                    .offset(y = 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Row(Modifier.fillMaxSize()) {
                repeat(10) { i ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (i > 0) Box(Modifier.width(2.dp).fillMaxHeight().background(Ink.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultOverlay(outcome: Outcome, myCrowns: Int, theirCrowns: Int, onOk: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xC0000000))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        ChunkyPanel(Modifier.width(280.dp), tint = if (outcome == Outcome.WIN) Color(0xFF2A4C86) else Color(0xFF5A2A3A)) {
            val (title, color) = when (outcome) {
                Outcome.WIN -> "VICTORY!" to Palette.Gold
                Outcome.LOSS -> "DEFEAT" to Palette.Red
                Outcome.DRAW -> "DRAW" to Color.White
            }
            OutlinedText(title, fontSize = 44.sp, color = color, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                OutlinedText("$myCrowns", fontSize = 38.sp, color = Palette.Blue)
                Text("  👑  ", fontSize = 28.sp)
                OutlinedText("$theirCrowns", fontSize = 38.sp, color = Palette.Red)
            }
            Spacer(Modifier.height(18.dp))
            ChunkyButton("OK", onOk, Modifier.fillMaxWidth(), color = ChunkyColor.GOLD, fontSize = 24.sp)
        }
    }
}

@Composable
private fun LeaveDialog(onStay: () -> Unit, onSurrender: () -> Unit) {
    BackHandler(onBack = onStay)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xA0000000))
            .pointerInput(Unit) { detectTapGestures { onStay() } },
        contentAlignment = Alignment.Center,
    ) {
        ChunkyPanel(Modifier.width(290.dp).pointerInput(Unit) { detectTapGestures { } }) {
            OutlinedText("Leave battle?", fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(6.dp))
            Text(
                "Leaving now counts as a loss.",
                color = Palette.TextDim,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("Stay", onStay, Modifier.weight(1f), color = ChunkyColor.BLUE, height = 46.dp)
                ChunkyButton("Give up", onSurrender, Modifier.weight(1f), color = ChunkyColor.GREY, height = 46.dp)
            }
        }
    }
}
