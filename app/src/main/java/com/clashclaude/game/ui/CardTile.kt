package com.clashclaude.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import com.clashclaude.game.data.CardDef

/** A card face: emoji art, elixir cost badge and name. Scales with its width. */
@Composable
fun CardTile(
    card: CardDef,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    showName: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier
            .aspectRatio(0.78f)
            .alpha(if (dimmed) 0.4f else 1f)
            .clip(shape)
            .background(Brush.verticalGradient(Palette.rarity(card.rarity)))
            .border(if (highlighted) 3.dp else 1.5.dp, if (highlighted) Palette.Gold else Color(0x55FFFFFF), shape),
    ) {
        val density = LocalDensity.current
        val w = maxWidth
        val emojiSize = with(density) { (w * 0.5f).toSp() }
        val nameSize = with(density) { (w * 0.13f).toSp() }
        val badge = w * 0.3f
        Text(
            card.emoji,
            fontSize = emojiSize,
            modifier = Modifier.align(Alignment.Center).offset(y = -w * 0.04f),
        )
        Box(
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
