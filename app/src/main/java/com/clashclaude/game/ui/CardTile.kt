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

/** The card's portrait, baked from its 3D model (tools/baker); a vector icon if it isn't available. */
@Composable
fun CardArt(card: CardDef, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val img = Sprites.image("card_${card.id}")
        if (img != null) {
            // Fill the width, keeping the aspect ratio, anchored to the bottom like a card illustration.
            val w = size.width
            val h = w * img.height / img.width
            drawImage(
                img,
                dstOffset = androidx.compose.ui.unit.IntOffset(0, (size.height - h).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
            )
            return@Canvas
        }
        val w = size.width
        val h = size.height
        if (card.type == CardType.SPELL) {
            Pen(this, w * 0.5f, h * 0.42f, w * 0.62f).spellIcon(card.id, 0f)
        } else {
            Pen(this, w * 0.5f, h * 0.45f, w * 0.3f).circle(0f, 0f, 1f, Color(0xFF3FA7FF))
        }
    }
}
