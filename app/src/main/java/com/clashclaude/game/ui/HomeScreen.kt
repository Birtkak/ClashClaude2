package com.clashclaude.game.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
import com.clashclaude.game.data.DeckBuilder
import com.clashclaude.game.data.DeckRepository
import com.clashclaude.game.data.TargetType
import com.clashclaude.game.game.Sfx
import java.util.Locale

private enum class HomeTab(val label: String, val icon: String) {
    BATTLE("Battle", "⚔️"),
    DECK("Deck", "🃏"),
    MORE("More", "⚙️"),
}

private enum class CardFilter(val label: String) { ALL("All"), TROOPS("Troops"), SPELLS("Spells"), BUILDINGS("Buildings") }

private const val CARD_FORGE_URL = "https://claude.ai/artifact/BUm139CyGUeFVq3daoNyJw"

@Composable
fun HomeScreen(
    repo: DeckRepository,
    audio: GameAudio = GameAudio.Silent,
    /** Called with the new (sound effects, music) settings after a toggle. */
    onAudioSettings: (Boolean, Boolean) -> Unit = { _, _ -> },
    /** Which tab to open on (0 battle, 1 deck, 2 more); used by the playtest harness. */
    initialTab: Int = 0,
    onBattle: (List<CardDef>) -> Unit,
) {
    var tab by remember { mutableStateOf(HomeTab.entries[initialTab.coerceIn(0, 2)]) }
    var decks by remember { mutableStateOf(repo.loadDecks()) }
    var selected by remember { mutableIntStateOf(repo.selectedDeck) }

    fun selectDeck(i: Int) {
        selected = i
        repo.selectedDeck = i
        audio.play(Sfx.CLICK)
    }

    fun updateDeck(newDeck: List<String>) {
        decks = decks.toMutableList().also { it[selected] = newDeck }
        repo.saveDeck(selected, newDeck)
    }

    ChunkyBackdrop(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
        TopBar(repo.wins, repo.losses)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = tab, label = "home-tab") { current ->
                when (current) {
                    HomeTab.BATTLE -> BattleTab(
                        decks = decks,
                        selected = selected,
                        wins = repo.wins,
                        losses = repo.losses,
                        onSelectDeck = ::selectDeck,
                        onEditDeck = { tab = HomeTab.DECK },
                        onMagic = {
                            updateDeck(DeckBuilder.random())
                            audio.play(Sfx.DEPLOY)
                        },
                        onBattle = { cards ->
                            audio.play(Sfx.DEPLOY)
                            onBattle(cards)
                        },
                    )
                    HomeTab.DECK -> DeckTab(
                        decks = decks,
                        selected = selected,
                        audio = audio,
                        onSelectDeck = ::selectDeck,
                        onUpdateDeck = ::updateDeck,
                    )
                    HomeTab.MORE -> MoreTab(repo, audio, onAudioSettings)
                }
            }
        }
        BottomNav(tab) {
            if (it != tab) audio.play(Sfx.CLICK)
            tab = it
        }
    } }
}

// ---------------------------------------------------------------- chrome

@Composable
private fun TopBar(wins: Int, losses: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedText("Clash Claude", fontSize = 30.sp, color = Palette.Gold, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A4C86), Color(0xFF16294D))))
                .border(2.5.dp, Color(0xFF0B1324), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🏆", fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            OutlinedText("$wins", fontSize = 18.sp, color = Palette.Gold)
        }
    }
}

@Composable
private fun BottomNav(current: HomeTab, onSelect: (HomeTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2F57), Color(0xFF0B1528))))
            .border(width = 2.5.dp, color = Color(0xFF0B1324), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (t in HomeTab.entries) {
            val active = t == current
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(if (active) 1.25f else 1f)
                    .clip(shape)
                    .background(
                        if (active) Brush.verticalGradient(listOf(ChunkyColor.GOLD.top, ChunkyColor.GOLD.bottom))
                        else Brush.verticalGradient(listOf(Color(0xFF223A66), Color(0xFF172947))),
                    )
                    .border(2.5.dp, Color(0xFF0B1324), shape)
                    .clickable { onSelect(t) }
                    .padding(vertical = if (active) 10.dp else 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.icon, fontSize = if (active) 28.sp else 22.sp, modifier = Modifier.alpha(if (active) 1f else 0.7f))
                OutlinedText(t.label, fontSize = if (active) 16.sp else 13.sp, color = if (active) Color.White else Color(0xFFB9C7E6))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedText(text, fontSize = 19.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun DeckChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until DeckRepository.DECK_COUNT) {
            ChunkyChip("Deck ${i + 1}", i == selected, { onSelect(i) })
        }
    }
}

private fun avgElixir(cards: List<CardDef>): String =
    if (cards.isEmpty()) "–" else String.format(Locale.US, "%.1f", cards.sumOf { it.cost }.toDouble() / cards.size)

/** Plain-language problems with a deck, so new players know what to fix. */
private fun deckWarnings(cards: List<CardDef>): List<String> {
    val out = mutableListOf<String>()
    if (cards.size < DeckRepository.DECK_SIZE) out += "Add ${DeckRepository.DECK_SIZE - cards.size} more card${if (DeckRepository.DECK_SIZE - cards.size > 1) "s" else ""} to battle."
    if (cards.isEmpty()) return out
    val hitsAir = cards.any { it.type != CardType.SPELL && it.targets == TargetType.ANY }
    if (!hitsAir) out += "Nothing in this deck can hit flying troops."
    if (cards.none { it.type == CardType.SPELL }) out += "No spell: add one to finish towers or clear swarms."
    val avg = cards.sumOf { it.cost }.toDouble() / cards.size
    if (avg > 4.6) out += "This deck is expensive; you'll often wait for elixir."
    return out
}

// ---------------------------------------------------------------- battle tab

@Composable
private fun BattleTab(
    decks: List<List<String>>,
    selected: Int,
    wins: Int,
    losses: Int,
    onSelectDeck: (Int) -> Unit,
    onEditDeck: () -> Unit,
    onMagic: () -> Unit,
    onBattle: (List<CardDef>) -> Unit,
) {
    val deckCards = decks[selected].mapNotNull { Cards.get(it) }
    val ready = deckCards.size == DeckRepository.DECK_SIZE
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArenaBanner()

            // Record
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val games = wins + losses
                StatTile("Wins", wins.toString(), Modifier.weight(1f))
                StatTile("Losses", losses.toString(), Modifier.weight(1f))
                StatTile("Win rate", if (games == 0) "–" else "${wins * 100 / games}%", Modifier.weight(1f))
            }

            // Deck preview
            ChunkyPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Battle deck") {
                    Text("💧 ${avgElixir(deckCards)} avg", color = Palette.Elixir, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                DeckChips(selected, onSelectDeck)
                Spacer(Modifier.height(10.dp))
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        for (col in 0 until 4) {
                            val card = deckCards.getOrNull(row * 4 + col)
                            Box(Modifier.weight(1f)) {
                                if (card != null) CardTile(card, showName = false, modifier = Modifier.fillMaxWidth())
                                else EmptySlot(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                for (w in deckWarnings(deckCards)) WarningLine(w)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChunkyButton("Edit deck", onEditDeck, Modifier.weight(1f), color = ChunkyColor.BLUE, fontSize = 18.sp)
                    ChunkyButton("Magic", onMagic, Modifier.weight(1f), color = ChunkyColor.PURPLE, fontSize = 18.sp, leading = "✨")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        ChunkyButton(
            if (ready) "BATTLE" else "Finish your deck first",
            onClick = { onBattle(deckCards) },
            enabled = ready,
            height = 72.dp,
            fontSize = if (ready) 36.sp else 20.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
    }
}

/** A little scene: our princess tower facing theirs across the river. */
@Composable
private fun ArenaBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(2.5.dp, Color(0xFF0B1324), RoundedCornerShape(18.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tile = h / 4.2f
            for (gx in 0..(w / tile).toInt()) for (gy in 0..4) {
                drawRect(
                    if ((gx + gy) % 2 == 0) Color(0xFF6DBE45) else Color(0xFF62B03D),
                    Offset(gx * tile, gy * tile),
                    Size(tile + 1f, tile + 1f),
                )
            }
            // River with a bridge down the middle.
            drawRect(Color(0xFF3D9BE0), Offset(w / 2 - tile * 0.7f, 0f), Size(tile * 1.4f, h))
            drawRect(Color(0xFFA9774A), Offset(w / 2 - tile * 0.9f, h * 0.55f), Size(tile * 1.8f, tile * 1.2f))
            drawSprite("tower_princess", true, 1.57f, 0, w * 0.2f, h * 0.92f, tile * 0.95f)
            drawSprite("tower_princess", false, 1.57f, 0, w * 0.8f, h * 0.92f, tile * 0.95f)
        }
        OutlinedText(
            "Training Camp",
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    ChunkyPanel(modifier) {
        OutlinedText(value, fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            label,
            color = Color(0xFFB9C7E6),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("⚠️", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color(0xFFFFB74D), fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- deck tab

@Composable
private fun DeckTab(
    decks: List<List<String>>,
    selected: Int,
    audio: GameAudio,
    onSelectDeck: (Int) -> Unit,
    onUpdateDeck: (List<String>) -> Unit,
) {
    val deck = decks[selected]
    val deckCards = deck.mapNotNull { Cards.get(it) }
    var infoCard by remember { mutableStateOf<CardDef?>(null) }
    // A collection card waiting to replace one in a full deck.
    var swapIn by remember { mutableStateOf<CardDef?>(null) }
    var filter by remember { mutableStateOf(CardFilter.ALL) }
    var sortByCost by remember { mutableStateOf(true) }

    val collection = Cards.all
        .filter {
            when (filter) {
                CardFilter.ALL -> true
                CardFilter.TROOPS -> it.type == CardType.TROOP
                CardFilter.SPELLS -> it.type == CardType.SPELL
                CardFilter.BUILDINGS -> it.type == CardType.BUILDING
            }
        }
        .let { list -> if (sortByCost) list.sortedWith(compareBy({ it.cost }, { it.name })) else list.sortedBy { it.name } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Your decks") {
                    Text("💧 ${avgElixir(deckCards)} avg", color = Palette.Elixir, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                DeckChips(selected) {
                    swapIn = null
                    onSelectDeck(it)
                }
                ChunkyButton(
                    "Magic deck",
                    onClick = {
                        swapIn = null
                        onUpdateDeck(DeckBuilder.random())
                        audio.play(Sfx.DEPLOY)
                    },
                    color = ChunkyColor.PURPLE,
                    leading = "✨",
                    height = 46.dp,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                swapIn?.let { incoming ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4A3A0A))
                            .border(1.5.dp, Palette.Gold, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Tap a card in your deck to swap it for ${incoming.name}",
                            color = Palette.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Cancel",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { swapIn = null }.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        // The 8 deck slots.
        items(DeckRepository.DECK_SIZE) { i ->
            val card = deckCards.getOrNull(i)
            if (card == null) {
                EmptySlot(Modifier.fillMaxWidth())
            } else {
                CardTile(
                    card,
                    highlighted = swapIn != null,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val incoming = swapIn
                        if (incoming != null) {
                            onUpdateDeck(deck.map { if (it == card.id) incoming.id else it })
                            swapIn = null
                            audio.play(Sfx.DEPLOY)
                        } else {
                            infoCard = card
                        }
                    },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                for (w in deckWarnings(deckCards)) WarningLine(w)
            }
        }
        // Collection header with filters.
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SectionTitle("All cards (${Cards.all.size})") {
                    Text(
                        if (sortByCost) "Sort: elixir" else "Sort: name",
                        color = Palette.TextDim,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sortByCost = !sortByCost }
                            .padding(6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in CardFilter.entries) ChunkyChip(f.label, f == filter, { filter = f })
                }
                Text("Tap a card to see its stats and add it to your deck.", color = Palette.TextDim, fontSize = 12.sp)
            }
        }
        items(collection, key = { "c-" + it.id }) { card ->
            val inDeck = card.id in deck
            Box {
                CardTile(
                    card,
                    dimmed = inDeck,
                    highlighted = swapIn?.id == card.id,
                    modifier = Modifier.fillMaxWidth().clickable { infoCard = card },
                )
                if (inDeck) {
                    Text(
                        "IN DECK",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E7D32))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }

    infoCard?.let { card ->
        val inDeck = card.id in deck
        val full = deck.size >= DeckRepository.DECK_SIZE
        CardSheet(
            card = card,
            action = when {
                inDeck -> "Remove from deck"
                !full -> "Add to deck"
                else -> "Swap into deck"
            },
            onAction = {
                when {
                    inDeck -> onUpdateDeck(deck - card.id)
                    !full -> onUpdateDeck(deck + card.id)
                    else -> swapIn = card
                }
                audio.play(Sfx.CLICK)
                infoCard = null
            },
            onDismiss = { infoCard = null },
        )
    }
}

@Composable
private fun EmptySlot(modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22FFFFFF))
            .border(1.5.dp, Color(0x44FFFFFF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color(0x88FFFFFF), fontSize = 28.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardSheet(card: CardDef, action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Navy,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTile(card, modifier = Modifier.width(96.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    OutlinedText(card.name, fontSize = 26.sp)
                    Text(
                        "${card.rarity.name.lowercase().replaceFirstChar { it.uppercase() }} ${Palette.typeLabel(card.type)}",
                        fontSize = 14.sp,
                        color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(card.description, color = Color.White, fontSize = 14.sp)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Panel)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                for ((label, value) in statLines(card)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(label, color = Palette.TextDim, modifier = Modifier.weight(1f))
                        Text(value, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                }
            }
            ChunkyButton(action, onAction, Modifier.fillMaxWidth(), height = 56.dp, fontSize = 22.sp)
        }
    }
}

// ---------------------------------------------------------------- more tab

@Composable
private fun MoreTab(repo: DeckRepository, audio: GameAudio, onAudioSettings: (Boolean, Boolean) -> Unit) {
    var soundOn by remember { mutableStateOf(repo.soundOn) }
    var musicOn by remember { mutableStateOf(repo.musicOn) }
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Panel {
            SectionTitle("Sound")
            SettingRow("Sound effects", soundOn) {
                soundOn = it
                repo.soundOn = it
                onAudioSettings(soundOn, musicOn)
                audio.play(Sfx.CLICK)
            }
            SettingRow("Music", musicOn) {
                musicOn = it
                repo.musicOn = it
                onAudioSettings(soundOn, musicOn)
            }
        }
        Panel {
            SectionTitle("How to play")
            val tips = listOf(
                "Drag a card onto your half of the arena (or tap a card, then tap the arena). The highlighted tile shows exactly where it lands.",
                "Cards cost elixir. It refills over time, twice as fast in the last minute.",
                "Destroy princess towers for crowns. Taking the king tower wins instantly.",
                "Once you destroy a princess tower, you can place troops further forward on that side.",
                "Ranged troops and buildings show their attack range while you drag them.",
            )
            for (t in tips) {
                Row {
                    Text("•", color = Palette.Gold, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 8.dp))
                    Text(t, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        Panel {
            SectionTitle("Design your own cards")
            Text(
                "Use the Card Forge to send Claude new card ideas or rebalance existing ones.",
                color = Palette.TextDim,
                fontSize = 14.sp,
            )
            ChunkyButton(
                "Open the Card Forge",
                onClick = { runCatching { uri.openUri(CARD_FORGE_URL) } },
                color = ChunkyColor.BLUE,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    ChunkyPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Gold, checkedThumbColor = Color(0xFF3A2600)),
        )
    }
}

private fun statLines(card: CardDef): List<Pair<String, String>> {
    val lines = mutableListOf("Elixir" to card.cost.toString())
    if (card.type == CardType.SPELL) {
        if (card.damage > 0) {
            lines += "Damage" to card.damage.toString()
            lines += "Tower damage" to (card.damage * card.towerDamagePct).toInt().toString()
        }
        lines += "Radius" to "${card.spellRadius} tiles"
        if (card.stun > 0f) lines += (if (card.stun >= 2f) "Freeze" else "Stun") to "${card.stun}s"
        return lines
    }
    lines += "Hitpoints" to card.hp.toString() + if (card.count > 1) " ×${card.count}" else ""
    lines += "Damage" to card.damage.toString() + if (card.splash > 0f) " (splash)" else ""
    lines += "Hit speed" to "${card.hitSpeed}s"
    lines += "Range" to if (card.range < 1.5f) "Melee" else "${card.range} tiles"
    lines += "Targets" to when (card.targets) {
        TargetType.GROUND -> "Ground"
        TargetType.ANY -> "Air & Ground"
        TargetType.BUILDINGS -> "Buildings"
    }
    if (card.type == CardType.BUILDING) {
        lines += "Lifetime" to "${card.lifetime.toInt()}s"
    } else {
        lines += "Speed" to when {
            card.speed >= 1.9f -> "Very fast"
            card.speed >= 1.4f -> "Fast"
            card.speed >= 0.9f -> "Medium"
            else -> "Slow"
        }
        if (card.flying) lines += "Type" to "Flying"
    }
    return lines
}
