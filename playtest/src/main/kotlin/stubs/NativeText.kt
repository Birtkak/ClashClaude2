@file:Suppress("unused", "UNUSED_PARAMETER")
package com.clashclaude.game.ui

// Desktop replacement for android.graphics.Canvas.drawText, drawn with Skia (color emoji via Noto).
import org.jetbrains.skia.*
private val emojiTf = FontMgr.default.matchFamilyStyle("Noto Color Emoji", FontStyle.NORMAL)
private val textTf = FontMgr.default.matchFamilyStyle("DejaVu Sans", FontStyle.BOLD)
fun Canvas.drawText(s: String, x: Float, y: Float, p: android.graphics.Paint) {
    val isEmoji = s.codePointAt(0) > 0x2000
    val font = Font(if (isEmoji) emojiTf else textTf, p.textSize)
    val paint = Paint().apply { color = (p.color and 0x00FFFFFF) or (p.alpha shl 24) }
    val w = font.measureTextWidth(s, paint)
    drawString(s, x - w / 2f, y, font, paint)
}
