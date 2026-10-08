package com.clashclaude.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A small "toy box" UI kit in the style of mobile card battlers: thick dark outlines, glossy
// gradient faces, a solid darker base that the face presses into, and outlined display text.

/** Colour sets for chunky buttons: glossy top, face bottom, and the darker base edge. */
enum class ChunkyColor(val top: Color, val bottom: Color, val base: Color, val text: Color) {
    GOLD(Color(0xFFFFE27A), Color(0xFFF5A623), Color(0xFFA35F00), Color.White),
    BLUE(Color(0xFF6EC6FF), Color(0xFF2B7BE4), Color(0xFF14468F), Color.White),
    PURPLE(Color(0xFFD48CFF), Color(0xFF8E3BE0), Color(0xFF4F1A8A), Color.White),
    GREEN(Color(0xFF8DF07A), Color(0xFF3DB93A), Color(0xFF1D6B1E), Color.White),
    GREY(Color(0xFF8C97AD), Color(0xFF5E6A82), Color(0xFF353D4F), Color(0xFFE3E8F2)),
}

private val Outline = Color(0xFF0B1324)

/** Text with a thick dark outline, the signature look of the genre. */
@Composable
fun OutlinedText(
    text: String,
    fontSize: TextUnit,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    outline: Color = Outline,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val font = LocalDisplayFont.current
    val stroke = with(androidx.compose.ui.platform.LocalDensity.current) { (fontSize.toPx() * 0.16f) }
    Box(modifier) {
        Text(
            text, maxLines = maxLines, textAlign = textAlign,
            style = TextStyle(fontFamily = font, fontSize = fontSize, color = outline, drawStyle = Stroke(width = stroke * 2f)),
        )
        // A drop shadow under the fill makes letters look raised.
        Text(
            text, maxLines = maxLines, textAlign = textAlign,
            style = TextStyle(fontFamily = font, fontSize = fontSize, color = color),
        )
    }
}

/** A raised, pressable button: glossy face sitting on a darker base, with a thick outline. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: ChunkyColor = ChunkyColor.GOLD,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    fontSize: TextUnit = 20.sp,
    leading: String? = null,
) {
    val colors = if (enabled) color else ChunkyColor.GREY
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 5.dp
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(height + depth)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        // Base (the "thickness" of the button).
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = depth)
                .clip(shape)
                .background(colors.base)
                .border(2.5.dp, Outline, shape),
        )
        // Face, pushed down into the base while pressed.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = if (pressed) depth - 1.dp else 0.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
                .border(2.5.dp, Outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            // Gloss stripe across the top half.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .fillMaxWidth()
                    .height(height * 0.28f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f)),
            )
            OutlinedText(
                (leading?.let { "$it " } ?: "") + text,
                fontSize = fontSize,
                color = colors.text,
                maxLines = 1,
            )
        }
    }
}

/** A framed panel: deep gradient, thick outline and a thin inner highlight. */
@Composable
fun ChunkyPanel(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF2A4C86),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint, lerp(tint, Color(0xFF0E1A33), 0.55f))))
            .border(2.5.dp, Outline, shape)
            .padding(2.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content,
    )
}

/** A small pill toggle (deck number, filter) that looks pressed-in when selected. */
@Composable
fun ChunkyChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(ChunkyColor.GOLD.top, ChunkyColor.GOLD.bottom))
                else Brush.verticalGradient(listOf(Color(0xFF223A66), Color(0xFF172947))),
            )
            .border(2.dp, Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedText(text, fontSize = 15.sp, color = if (selected) Color.White else Color(0xFFB9C7E6))
    }
}

/** Screen backdrop: deep blue with soft diagonal stripes and a vignette. */
@Composable
fun ChunkyBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Color(0xFF13254A))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 46.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    Color.White.copy(alpha = 0.035f),
                    Offset(x, size.height),
                    Offset(x + size.height, 0f),
                    strokeWidth = step * 0.45f,
                )
                x += step
            }
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color(0xAA050B18)),
                    center = Offset(size.width / 2, size.height * 0.4f),
                    radius = size.maxDimension * 0.75f,
                ),
            )
        }
        content()
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

