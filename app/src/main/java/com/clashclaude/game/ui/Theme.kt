package com.clashclaude.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Rarity

object Palette {
    val Night = Color(0xFF0E1A2F)
    val Navy = Color(0xFF16284A)
    val Panel = Color(0xFF1F3863)
    val Gold = Color(0xFFFFC93C)
    val GoldDark = Color(0xFFE09A00)
    val Elixir = Color(0xFFD43BFF)
    val ElixirDark = Color(0xFF7B1FA2)
    val Blue = Color(0xFF3FA7FF)
    val Red = Color(0xFFFF4B4B)
    val TextDim = Color(0xFFB0BEDA)

    fun rarity(r: Rarity): List<Color> = when (r) {
        Rarity.COMMON -> listOf(Color(0xFF5C7AA8), Color(0xFF34507C))
        Rarity.RARE -> listOf(Color(0xFFF0A040), Color(0xFFB0601A))
        Rarity.EPIC -> listOf(Color(0xFFB05CE0), Color(0xFF6A2A9A))
    }

    fun typeLabel(t: CardType): String = when (t) {
        CardType.TROOP -> "Troop"
        CardType.SPELL -> "Spell"
        CardType.BUILDING -> "Building"
    }
}

/** The chunky display face for titles, buttons and numbers (Lilita One in the app). */
val LocalDisplayFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun ClashTheme(displayFont: FontFamily = FontFamily.Default, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Color(0xFF3A2600),
            secondary = Palette.Blue,
            background = Palette.Night,
            surface = Palette.Navy,
            surfaceContainerHigh = Palette.Navy,
            onSurface = Color.White,
            onBackground = Color.White,
        ),
    ) {
        CompositionLocalProvider(LocalDisplayFont provides displayFont, content = content)
    }
}
