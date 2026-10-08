package com.clashclaude.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType

/** A card face: emoji art, elixir cost badge and name. Scales with its width. */
@Composable
fun CardTile(
    card: CardDef,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    showName: Boolean = true,
    showCost: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier
            .aspectRatio(0.78f)
            .alpha(if (dimmed) 0.4f else 1f)
            .clip(shape)
            .background(Brush.verticalGradient(Palette.rarity(card.rarity)))
            .border(if (highlighted) 3.dp else 2.5.dp, if (highlighted) Palette.Gold else Color(0xFF0B1324), shape)
            .padding(2.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
    ) {
        val density = LocalDensity.current
        val w = maxWidth
        val nameSize = with(density) { (w * 0.13f).toSp() }
        val badge = w * 0.3f
        CardArt(card, Modifier.fillMaxSize())
        if (showCost) Box(
            Modifier
                .padding(3.dp)
                .size(badge)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Palette.Elixir, Palette.ElixirDark)))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                card.cost.toString(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = with(density) { (badge * 0.62f).toSp() },
            )
        }
        if (showName) {
            Text(
                card.name,
                color = Color.White,
                fontSize = nameSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(vertical = 2.dp, horizontal = 2.dp),
            )
        }
    }
}

/** The unit (or spell) drawn as the card's portrait; multi-unit cards show a small group. */
@Composable
fun CardArt(card: CardDef, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (card.type == CardType.SPELL) {
            Pen(this, w * 0.5f, h * 0.42f, w * 0.62f).spellIcon(card.id, 0f)
            return@Canvas
        }
        val tint = Color(0xFF3FA7FF)
        val big = card.id == "giant" || card.id == "pekka"
        val feetY = h * 0.74f
        when {
            card.count >= 3 -> {
                val u = h * 0.36f
                Pen(this, w * 0.3f, feetY - h * 0.1f, u * 0.9f).unit(card.id, tint, Pose.IDLE)
                Pen(this, w * 0.72f, feetY - h * 0.1f, u * 0.9f).unit(card.id, tint, Pose.IDLE)
                Pen(this, w * 0.5f, feetY + h * 0.02f, u).unit(card.id, tint, Pose.IDLE)
            }
            card.count == 2 -> {
                val u = h * 0.42f
                Pen(this, w * 0.33f, feetY - h * 0.04f, u * 0.92f).unit(card.id, tint, Pose.IDLE)
                Pen(this, w * 0.64f, feetY + h * 0.02f, u).unit(card.id, tint, Pose.IDLE)
            }
            else -> {
                val u = h * if (big) 0.6f else if (card.type == CardType.BUILDING) 0.48f else 0.54f
                val x = w * if (card.id == "hogrider") 0.45f else 0.5f
                Pen(this, x, feetY + h * 0.02f, u).unit(card.id, tint, Pose.IDLE)
            }
        }
    }
}
