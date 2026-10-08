package com.clashclaude.game.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
import com.clashclaude.game.data.DeckRepository
import com.clashclaude.game.data.TargetType
import com.clashclaude.game.game.Sfx
import java.util.Locale

@Composable
fun HomeScreen(
    repo: DeckRepository,
    audio: GameAudio = GameAudio.Silent,
    /** Called with the new (sound effects, music) settings after a toggle. */
    onAudioSettings: (Boolean, Boolean) -> Unit = { _, _ -> },
    onBattle: (List<CardDef>) -> Unit,
) {
    var soundOn by remember { mutableStateOf(repo.soundOn) }
    var musicOn by remember { mutableStateOf(repo.musicOn) }
    var decks by remember { mutableStateOf(repo.loadDecks()) }
    var selected by remember { mutableStateOf(repo.selectedDeck) }
    var infoCard by remember { mutableStateOf<CardDef?>(null) }
    // Card from the collection waiting to replace a card in a full deck.
    var swapIn by remember { mutableStateOf<CardDef?>(null) }

    val deck = decks[selected]
    val deckCards = deck.mapNotNull { Cards.get(it) }

    fun updateDeck(newDeck: List<String>) {
        decks = decks.toMutableList().also { it[selected] = newDeck }
        repo.saveDeck(selected, newDeck)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Navy, Palette.Night)))
            .systemBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⚔️ Clash Claude",
                color = Palette.Gold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            ToggleChip(if (soundOn) "🔊" else "🔇") {
                soundOn = !soundOn
                repo.soundOn = soundOn
                onAudioSettings(soundOn, musicOn)
                audio.play(Sfx.CLICK)
            }
            ToggleChip("🎵", dim = !musicOn) {
                musicOn = !musicOn
                repo.musicOn = musicOn
                onAudioSettings(soundOn, musicOn)
                audio.play(Sfx.CLICK)
            }
            Text(
                "🏆 ${repo.wins}W · ${repo.losses}L",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Panel)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Deck tabs
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until DeckRepository.DECK_COUNT) {
                val active = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Palette.Gold else Palette.Panel)
                        .clickable {
                            selected = i
                            repo.selectedDeck = i
                            swapIn = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Deck ${i + 1}",
                        color = if (active) Color(0xFF3A2600) else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Deck
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Panel)
                .padding(8.dp),
        ) {
            val avg = if (deckCards.isEmpty()) 0.0 else deckCards.sumOf { it.cost }.toDouble() / deckCards.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Battle Deck  ${deckCards.size}/${DeckRepository.DECK_SIZE}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "💧 Avg elixir ${String.format(Locale.US, "%.1f", avg)}",
                    color = Palette.Elixir,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            if (swapIn != null) {
                Text(
                    "Tap a card in your deck to swap it for ${swapIn!!.name}",
                    color = Palette.Gold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { swapIn = null },
                )
            }
            Spacer(Modifier.height(6.dp))
            for (row in 0 until 2) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (col in 0 until 4) {
                        val card = deckCards.getOrNull(row * 4 + col)
                        Box(Modifier.weight(1f)) {
                            if (card != null) {
                                CardTile(
                                    card,
                                    highlighted = swapIn != null,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val incoming = swapIn
                                        if (incoming != null) {
                                            updateDeck(deck.map { if (it == card.id) incoming.id else it })
                                            swapIn = null
                                        } else {
                                            infoCard = card
                                        }
                                    },
                                )
                            } else {
                                EmptySlot(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Collection · tap a card for details",
            color = Palette.TextDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(Cards.all, key = { it.id }) { card ->
                val inDeck = card.id in deck
                CardTile(
                    card,
                    dimmed = inDeck,
                    highlighted = swapIn?.id == card.id,
                    modifier = Modifier.clickable { infoCard = card },
                )
            }
        }

        val ready = deckCards.size == DeckRepository.DECK_SIZE
        Button(
            onClick = {
                audio.play(Sfx.DEPLOY)
                onBattle(deckCards)
            },
            enabled = ready,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Gold,
                contentColor = Color(0xFF3A2600),
                disabledContainerColor = Color(0xFF4A5570),
                disabledContentColor = Color(0xFFB0B8C8),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .height(64.dp),
        ) {
            Text(
                if (ready) "⚔️  BATTLE" else "Deck needs ${DeckRepository.DECK_SIZE} cards",
                fontSize = if (ready) 26.sp else 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }

    infoCard?.let { card ->
        val inDeck = card.id in deck
        val full = deck.size >= DeckRepository.DECK_SIZE
        CardInfoDialog(
            card = card,
            actionLabel = when {
                inDeck -> "Remove"
                !full -> "Add to deck"
                else -> "Swap in"
            },
            onAction = {
                when {
                    inDeck -> updateDeck(deck - card.id)
                    !full -> updateDeck(deck + card.id)
                    else -> swapIn = card
                }
                infoCard = null
            },
            onDismiss = { infoCard = null },
        )
    }
}

@Composable
private fun ToggleChip(icon: String, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(Palette.Panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 17.sp, modifier = Modifier.alpha(if (dim) 0.35f else 1f))
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

@Composable
private fun CardInfoDialog(card: CardDef, actionLabel: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Navy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTile(card, showName = false, modifier = Modifier.width(64.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(card.name, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        "${card.rarity.name.lowercase().replaceFirstChar { it.uppercase() }} ${Palette.typeLabel(card.type)}",
                        fontSize = 13.sp,
                        color = Palette.TextDim,
                    )
                }
            }
        },
        text = {
            Column {
                Text(card.description, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
                for ((label, value) in statLines(card)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(label, color = Palette.TextDim, modifier = Modifier.weight(1f))
                        Text(value, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAction) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun statLines(card: CardDef): List<Pair<String, String>> {
    val lines = mutableListOf("Elixir" to card.cost.toString())
    if (card.type == CardType.SPELL) {
        lines += "Damage" to card.damage.toString()
        lines += "Tower damage" to (card.damage * card.towerDamagePct).toInt().toString()
        lines += "Radius" to "${card.spellRadius} tiles"
        if (card.stun > 0f) lines += "Stun" to "${card.stun}s"
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
