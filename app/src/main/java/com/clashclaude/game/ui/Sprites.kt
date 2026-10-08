package com.clashclaude.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.clashclaude.game.game.Combatant
import com.clashclaude.game.game.View
import kotlin.math.floor

/**
 * Layout of one baked sprite sheet (see tools/baker). Frames are trimmed and packed; they are
 * numbered row by row: direction (0 = facing the camera ... last = facing away), then column
 * (idle frames, walk frames, attack frames).
 */
class SpriteSheet(
    val directions: Int,
    val idle: Int,
    val walk: Int,
    val attack: Int,
    /** Towers: height in tiles of the platform the defender stands on. */
    val mount: Float,
    /** Height in sheet pixels of the tallest frame above the feet. */
    val top: Int,
    frameTable: String,
) {
    val columns get() = idle + walk + attack

    /** Per frame: atlas x, y, width, height, offset from the feet dx, dy. Parsed on first use. */
    val frames: IntArray by lazy { frameTable.split(' ').map { it.toInt() }.toIntArray() }
}

/**
 * Loads baked images by name ("knight_blue", "card_knight") through a platform [loader]
 * set at startup (Android assets in the app, files in the desktop playtest).
 */
object Sprites {
    var loader: ((String) -> ImageBitmap?)? = null
    private val cache = HashMap<String, ImageBitmap?>()

    fun image(name: String): ImageBitmap? {
        val load = loader ?: return null
        return cache.getOrPut(name) { runCatching { load(name) }.getOrNull() }
    }

    fun sheet(id: String): SpriteSheet? = SpriteManifest[id]

    /** Loads the sheets for these ids ahead of time so the first frame doesn't stall. */
    fun preload(ids: Collection<String>) {
        for (id in ids) for (team in listOf("blue", "red")) image("${id}_$team")
    }
}

/** Frozen units are tinted icy blue; hit units flash white. */
private val FrozenFilter = ColorFilter.tint(Color(0xFFA8E6FF), BlendMode.Modulate)
private val FlashFilter = ColorFilter.tint(Color(0x99FFFFFF), BlendMode.SrcAtop)

/**
 * Draws one frame of sheet [id] with its feet at screen ([x], [y]), where one tile is
 * [tile] pixels. [heading] is the screen-space facing (radians, 0 = right, PI/2 = down).
 * Returns false if the sheet isn't available.
 */
fun DrawScope.drawSprite(
    id: String,
    blue: Boolean,
    heading: Float,
    column: Int,
    x: Float,
    y: Float,
    tile: Float,
    alpha: Float = 1f,
    frozen: Boolean = false,
    flash: Boolean = false,
): Boolean {
    val sheet = Sprites.sheet(id) ?: return false
    val img = Sprites.image("${id}_${if (blue) "blue" else "red"}") ?: return false
    val (row0, mirrored) = View.direction(heading)
    val row = if (sheet.directions == 1) 0 else row0.coerceIn(0, sheet.directions - 1)
    val col = column.coerceIn(0, sheet.columns - 1)
    val f = sheet.frames
    val i = (row * sheet.columns + col) * 6
    if (i + 5 >= f.size) return false
    val k = tile / View.PIXELS_PER_TILE
    val src = IntOffset(f[i], f[i + 1])
    val size = IntSize(f[i + 2], f[i + 3])
    val dstSize = IntSize((f[i + 2] * k).toInt().coerceAtLeast(1), (f[i + 3] * k).toInt().coerceAtLeast(1))
    val flip = mirrored && sheet.directions > 1
    // Mirrored frames hang the other way from the feet.
    val left = if (flip) x - (f[i + 4] + f[i + 2]) * k else x + f[i + 4] * k
    val dst = IntOffset(left.toInt(), (y + f[i + 5] * k).toInt())
    fun draw(filter: ColorFilter?) {
        if (flip) {
            withTransform({ scale(-1f, 1f, Offset(dst.x + dstSize.width / 2f, dst.y + dstSize.height / 2f)) }) {
                drawImage(img, src, size, dst, dstSize, alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.Low)
            }
        } else {
            drawImage(img, src, size, dst, dstSize, alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.Low)
        }
    }
    draw(if (frozen) FrozenFilter else null)
    if (flash) draw(FlashFilter)
    return true
}

/** Pixels from the feet to the top of the sprite, at [tile] pixels per tile. */
fun spriteHeight(id: String, tile: Float): Float? =
    Sprites.sheet(id)?.let { it.top * tile / View.PIXELS_PER_TILE }

/** Which sheet column (animation frame) shows [c] right now. */
fun frameColumn(c: Combatant, sheet: SpriteSheet, time: Float): Int {
    if (sheet.attack > 0 && c.frozenTimer <= 0f) {
        val t = when {
            c.sinceAttack < View.ATTACK_RECOVER -> c.sinceAttack
            c.lockedOn && c.target != null && c.cooldown in 0f..View.ATTACK_WINDUP -> -c.cooldown
            else -> null
        }
        if (t != null) {
            val k = floor((t + View.ATTACK_WINDUP) / (View.ATTACK_WINDUP + View.ATTACK_RECOVER) * sheet.attack).toInt()
            return sheet.idle + sheet.walk + k.coerceIn(0, sheet.attack - 1)
        }
    }
    if (c.moving && sheet.walk > 0 && c.frozenTimer <= 0f) {
        val k = floor(c.walkCycle / View.STRIDE * sheet.walk).toInt()
        return sheet.idle + Math.floorMod(k, sheet.walk)
    }
    if (sheet.idle <= 1 || c.frozenTimer > 0f) return 0
    return Math.floorMod(floor(time * IDLE_FPS + c.id * 0.37f).toInt(), sheet.idle)
}

private const val IDLE_FPS = 2.5f
