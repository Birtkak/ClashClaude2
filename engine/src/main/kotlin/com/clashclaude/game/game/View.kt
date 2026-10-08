package com.clashclaude.game.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The fixed camera everything is drawn with: an orthographic view from the PLAYER's side,
 * tilted down by [PITCH_DEG]. The simulation stays flat 2D; only drawing uses this.
 *
 * It is shared by the sprite baker (tools/baker), which renders 3D models from exactly this
 * camera, and the arena renderer, which squashes the ground the same way. If the camera
 * changes, re-bake the sprites.
 */
object View {
    const val PITCH_DEG = 50f

    /** Screen-y per tile of ground depth (the ground is foreshortened by this). */
    val DEPTH: Float = sin(PITCH_DEG * PI / 180.0).toFloat()

    /** Screen-y per tile of height above the ground. */
    val HEIGHT: Float = cos(PITCH_DEG * PI / 180.0).toFloat()

    /** Sprite sheets are baked at this many pixels per tile. */
    const val PIXELS_PER_TILE = 50

    /**
     * Facing directions baked per sprite: from facing the camera (south) round through east to
     * facing away (north) in 22.5 degree steps. West-facing sprites are mirrored east-facing ones,
     * which gives 16 directions in total.
     */
    const val DIRECTIONS = 9
    const val DIRECTION_STEP_DEG = 22.5f

    /** Frames per animation, laid out left to right in each sheet row: idle, walk, attack. */
    const val IDLE_FRAMES = 4
    const val WALK_FRAMES = 8
    const val ATTACK_FRAMES = 6

    /** The attack animation spans this wind-up before the hit and recovery after it, in seconds. */
    const val ATTACK_WINDUP = 0.3f
    const val ATTACK_RECOVER = 0.3f

    /** Attack frame index that shows the moment of impact. */
    const val ATTACK_HIT_FRAME = 3

    /** Tiles walked per full walk cycle. */
    const val STRIDE = 1.1f

    /**
     * Which baked direction row shows a unit with this [heading] (radians, 0 = east, PI/2 = south),
     * and whether the sprite must be mirrored horizontally.
     */
    fun direction(heading: Float): Pair<Int, Boolean> {
        var a = heading.toDouble()
        val mirrored = cos(a) < -1e-4
        if (mirrored) a = PI - a
        // Normalise to (-PI, PI], then a is in [-PI/2, PI/2]: south (PI/2) = row 0, north = last row.
        while (a > PI) a -= 2 * PI
        while (a <= -PI) a += 2 * PI
        val deg = 90.0 - a * 180.0 / PI
        val row = Math.round(deg / DIRECTION_STEP_DEG).toInt().coerceIn(0, DIRECTIONS - 1)
        return row to mirrored
    }

    /** Heading (radians) that baked direction row [row] faces, on the east side. */
    fun headingOfRow(row: Int): Float = ((90.0 - row * DIRECTION_STEP_DEG) * PI / 180.0).toFloat()
}
