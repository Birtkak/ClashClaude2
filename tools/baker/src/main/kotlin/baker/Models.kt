package baker

import com.clashclaude.game.game.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Something that can be baked into a sprite sheet. Built-in models are sculpted in code below;
 * [GltfModel] loads one exported from Blender instead.
 */
abstract class SpriteModel(
    val id: String,
    val idleFrames: Int = View.IDLE_FRAMES,
    val walkFrames: Int = View.WALK_FRAMES,
    val attackFrames: Int = View.ATTACK_FRAMES,
    val directions: Int = View.DIRECTIONS,
) {
    /** For towers: height (tiles) of the platform where the defender stands. */
    open val mount: Float = 0f

    /**
     * Drawn size relative to the arena. Units are drawn larger than their footprint, like in
     * other games of the genre, so they read well from the high camera.
     */
    open val scale: Float = 1.35f

    abstract fun build(s: Sculpt, p: Pose, team: TeamColor)
}

/** Wraps a humanoid so a team colour can be chosen per bake. */
class HumanModel(id: String, private val make: (TeamColor) -> Humanoid) : SpriteModel(id) {
    override fun build(s: Sculpt, p: Pose, team: TeamColor) = make(team).build(s, p)
}

// ------------------------------------------------------------------------ Knight

class Knight(val team: TeamColor) : Humanoid(
    shirt = team.mat, pants = C.darkSteel, sleeve = C.steel,
    torsoW = 0.44f, torsoH = 0.4f, headR = 0.22f, armR = 0.07f,
) {
    override fun rightArm(p: Pose) = when {
        p.attacking && p.attackT < 0f -> Arm(lerp(35f, 165f, p.windup), 18f, lerp(30f, 20f, p.windup))
        p.attacking -> Arm(lerp(35f, 15f, p.strike), 14f, 25f)
        else -> super.rightArm(p).let { if (p.anim == Anim.WALK) it else it.copy(raise = 30f, bend = 35f) }
    }

    override fun twist(p: Pose) = if (p.attacking) lerp(0f, -18f, p.windup) + 22f * p.strike else 0f
    override fun lean(p: Pose) = super.lean(p) + 12f * p.strike

    override fun Sculpt.head(p: Pose) {
        ball(headR, skin)
        face(headR, eyeY = -0.05f)
        // Big blond mustache.
        for (side in listOf(-1f, 1f)) at(side * headR * 0.28f, -headR * 0.38f, headR * 0.86f, rz = side * 18f) {
            blob(headR * 0.32f, headR * 0.12f, headR * 0.14f, Mat(0xE9B44C))
        }
        // Open-faced steel helmet with a team plume.
        at(0f, headR * 0.32f, -headR * 0.12f) {
            with(Mat4.scale(1.08f, 0.85f, 1.08f)) {
                surface(18, 8, true, C.steel) { u, v, o ->
                    val lon = (u * 2 * PI).toFloat(); val lat = (v * PI / 2).toFloat()
                    o[0] = headR * cos(lat) * sin(lon); o[1] = headR * sin(lat); o[2] = headR * cos(lat) * cos(lon)
                }
            }
            ring(headR * 1.05f, headR * 0.07f, C.darkSteel)
            at(0f, headR * 0.82f, -headR * 0.1f, rx = -20f) { blob(headR * 0.16f, headR * 0.4f, headR * 0.5f, team.mat) }
        }
    }

    override fun Sculpt.torsoExtra(p: Pose) {
        // Steel chest plate and belt.
        at(0f, torsoH * 0.55f, torsoD * 0.32f) { box(torsoW * 0.7f, torsoH * 0.55f, 0.08f, C.steel, 0.5f) }
        at(0f, torsoH * 0.15f, 0f) { box(torsoW * 1.04f, 0.07f, torsoD * 1.06f, C.leather, 0.5f) }
        at(0f, torsoH * 0.15f, torsoD * 0.53f) { box(0.08f, 0.07f, 0.03f, C.gold, 0.5f) }
    }

    override fun Sculpt.rightHand(p: Pose) = sword(0.62f, 0.09f)
}

// ------------------------------------------------------------------------ Archer

class Archer(val team: TeamColor) : Humanoid(
    shirt = team.mat, pants = Mat(0x4E7D3A), sleeve = Mat(0x5E9C46),
    torsoW = 0.36f, torsoH = 0.36f, torsoD = 0.26f, headR = 0.2f, legLen = 0.28f, legR = 0.065f,
    armR = 0.055f, handR = 0.065f,
) {
    private val hair = Mat(0xF0629A)

    /** Left arm holds the bow out front; the right arm draws the string while attacking. */
    override fun leftArm(p: Pose) = if (p.attacking) Arm(88f, -8f, 0f) else super.leftArm(p).copy(bend = 25f)
    override fun rightArm(p: Pose) = when {
        p.attacking && p.attackT < 0f -> Arm(lerp(70f, 88f, p.windup), -20f, lerp(60f, 145f, p.windup))
        p.attacking -> Arm(lerp(88f, 70f, 1f - p.strike), 10f, lerp(150f, 60f, 1f - p.strike))
        else -> super.rightArm(p)
    }

    override fun twist(p: Pose) = if (p.attacking) -25f else 0f

    override fun Sculpt.head(p: Pose) {
        ball(headR, skin)
        face(headR)
        // Hair cap, fringe and a big ponytail.
        at(0f, headR * 0.18f, -headR * 0.1f) { blob(headR * 1.08f, headR * 0.92f, headR * 1.06f, hair) }
        at(0f, headR * 0.55f, headR * 0.6f, rx = 30f) { blob(headR * 0.75f, headR * 0.25f, headR * 0.35f, hair) }
        at(0f, headR * 0.1f, -headR * 1.05f, rx = 35f) {
            ball(headR * 0.22f, team.mat, 10)
            limb(0f, 0f, 0f, 0f, -headR * 1.4f, -headR * 0.3f, headR * 0.33f, headR * 0.12f, hair)
        }
    }

    override fun Sculpt.torsoExtra(p: Pose) {
        // Quiver of arrows on the back.
        at(-0.08f, torsoH * 0.6f, -torsoD * 0.55f, rz = 20f) {
            at(0f, -0.18f, 0f) { tube(0.06f, 0.07f, 0.34f, C.leather, 10) }
            for (i in 0 until 3) at((i - 1) * 0.035f, 0.18f, 0f) { limb(0f, 0f, 0f, 0f, 0.08f, 0f, 0.012f, 0.012f, C.wood); at(0f, 0.1f, 0f) { box(0.05f, 0.06f, 0.012f, C.white, 0.4f) } }
        }
        at(0f, torsoH * 0.15f, 0f) { box(torsoW * 1.04f, 0.06f, torsoD * 1.06f, C.leather, 0.5f) }
    }

    override fun Sculpt.leftHand(p: Pose) {
        // The bow: an arc in the hand's XZ... held upright, string toward the body.
        at(0f, 0f, 0f, rx = 90f) {
            val r = 0.32f
            surface(14, 6, false, C.wood) { u, v, o ->
                val a = (-1.1f + 2.2f * u)
                val b = (v * 2 * PI).toFloat()
                val th = 0.022f * (1.2f - 0.6f * kotlin.math.abs(a))
                val rr = r + th * cos(b)
                o[0] = th * sin(b); o[1] = rr * sin(a); o[2] = rr * cos(a) - r
            }
            val pull = if (p.attacking && p.attackT < 0f) 0.05f + 0.22f * p.windup else 0f
            val tipY = r * sin(1.1f); val tipZ = r * cos(1.1f) - r
            limb(0f, tipY, tipZ, 0f, 0f, -pull, 0.008f, 0.008f, C.white)
            limb(0f, -tipY, tipZ, 0f, 0f, -pull, 0.008f, 0.008f, C.white)
            if (p.attacking && p.attackT < 0f) at(0f, 0f, -pull) {
                limb(0f, 0f, 0f, 0f, 0f, 0.5f, 0.012f, 0.012f, C.wood)
                at(0f, 0f, 0.5f) { at(rx = 90f) { tube(0.03f, 0f, 0.07f, C.steel, 8) } }
            }
        }
    }
}

// ------------------------------------------------------------------------ Giant

class Giant(val team: TeamColor) : Humanoid(
    legLen = 0.5f, legR = 0.14f, hipW = 0.19f, torsoW = 0.86f, torsoH = 0.78f, torsoD = 0.6f,
    headR = 0.3f, armLen = 0.72f, armR = 0.13f, handR = 0.17f,
    shirt = Mat(0x8D5A2B), pants = Mat(0x5D4037), sleeve = C.skin, boots = Mat(0x3E2A1A),
) {
    private val hair = Mat(0xE0782E)

    override fun rightArm(p: Pose) = when {
        p.attacking && p.attackT < 0f -> Arm(lerp(15f, 60f, p.windup), 20f, lerp(20f, 120f, p.windup))
        p.attacking -> Arm(lerp(40f, 92f, p.strike), 8f, lerp(40f, 5f, p.strike))
        else -> super.rightArm(p).copy(out = 18f)
    }

    override fun leftArm(p: Pose) = super.leftArm(p).copy(out = 18f)
    override fun twist(p: Pose) = if (p.attacking) -20f * p.windup + 15f * p.strike else 0f
    override fun lean(p: Pose) = 6f + super.lean(p) + 10f * p.strike

    override fun Sculpt.head(p: Pose) {
        ball(headR, skin)
        face(headR, eyeY = 0.08f, eyeGap = 0.35f)
        // Bushy eyebrows, a big beard and side tufts on a bald head.
        for (side in listOf(-1f, 1f)) {
            at(side * headR * 0.36f, headR * 0.32f, headR * 0.88f, rz = side * -12f) { blob(headR * 0.24f, headR * 0.08f, headR * 0.1f, hair) }
            at(side * headR * 0.85f, headR * 0.05f, -headR * 0.1f) { blob(headR * 0.25f, headR * 0.35f, headR * 0.4f, hair) }
        }
        at(0f, -headR * 0.55f, headR * 0.55f) { blob(headR * 0.75f, headR * 0.6f, headR * 0.45f, hair) }
    }

    override fun Sculpt.torsoExtra(p: Pose) {
        // Team sash and a belt.
        at(0f, torsoH * 0.55f, 0f, rz = -35f) { box(0.16f, torsoH * 1.35f, torsoD * 1.08f, team.mat, 0.5f) }
        at(0f, torsoH * 0.12f, 0f) { box(torsoW * 1.05f, 0.12f, torsoD * 1.06f, C.leather, 0.5f) }
        at(0f, torsoH * 0.12f, torsoD * 0.53f) { box(0.14f, 0.12f, 0.04f, C.gold, 0.4f) }
    }
}

// ------------------------------------------------------------------------ Wizard

class Wizard(val team: TeamColor) : Humanoid(
    shirt = team.mat, pants = team.darkMat, sleeve = team.mat, torsoW = 0.4f, torsoH = 0.42f,
    headR = 0.21f, legLen = 0.3f,
) {
    override fun rightArm(p: Pose) = when {
        p.attacking && p.attackT < 0f -> Arm(lerp(30f, 150f, p.windup), 25f, lerp(30f, 70f, p.windup))
        p.attacking -> Arm(lerp(60f, 95f, p.strike), 6f, 5f)
        else -> super.rightArm(p).let { if (p.anim == Anim.WALK) it else it.copy(raise = 45f, bend = 60f) }
    }

    override fun leftArm(p: Pose) = if (p.attacking) Arm(lerp(20f, 80f, p.strike), 10f, 20f) else super.leftArm(p)
    override fun twist(p: Pose) = if (p.attacking) -15f * p.windup + 12f * p.strike else 0f

    override fun Sculpt.head(p: Pose) {
        ball(headR, skin)
        face(headR)
        // Brown beard and a pointy hat with a gold band.
        at(0f, -headR * 0.55f, headR * 0.45f) { blob(headR * 0.62f, headR * 0.65f, headR * 0.5f, Mat(0x6D4C33)) }
        at(0f, headR * 0.35f, -headR * 0.05f) {
            ring(headR * 1.15f, headR * 0.12f, team.darkMat)
            at(0f, 0f, 0f, rx = -14f) { tube(headR * 1.05f, 0f, headR * 2.1f, team.mat, 16) }
            ring(headR * 1.06f, headR * 0.07f, C.gold)
        }
    }

    override fun Sculpt.torsoExtra(p: Pose) {
        // A long robe skirt down to the ankles hides the legs.
        at(0f, -legLen * 0.9f, 0f) { tube(torsoW * 0.75f, torsoW * 0.52f, legLen + 0.12f, team.mat, 16) }
        at(0f, torsoH * 0.18f, 0f) { box(torsoW * 1.05f, 0.06f, torsoD * 1.06f, C.gold, 0.5f) }
    }

    override fun Sculpt.rightHand(p: Pose) {
        // A fireball that grows during the wind-up.
        val size = if (p.attacking && p.attackT < 0f) lerp(0.07f, 0.16f, p.windup) else if (p.attacking) 0.05f else 0.06f
        at(0f, -0.05f, 0.08f) {
            ball(size, Mat(0xFF8A1E, emissive = true), 12)
            ball(size * 0.6f, Mat(0xFFE082, emissive = true), 10)
        }
    }
}

// ------------------------------------------------------------------------ Skeleton

class Skeleton(val team: TeamColor) : Humanoid(
    legLen = 0.24f, legR = 0.035f, hipW = 0.08f, torsoW = 0.24f, torsoH = 0.28f, torsoD = 0.16f,
    headR = 0.17f, armLen = 0.28f, armR = 0.03f, handR = 0.045f,
    shirt = C.bone, pants = C.bone, sleeve = C.bone, skin = C.bone, boots = C.bone,
) {
    override fun rightArm(p: Pose) = when {
        p.attacking && p.attackT < 0f -> Arm(lerp(35f, 160f, p.windup), 18f, 25f)
        p.attacking -> Arm(lerp(35f, 15f, p.strike), 14f, 20f)
        else -> super.rightArm(p).let { if (p.anim == Anim.WALK) it else it.copy(raise = 30f, bend = 35f) }
    }

    override fun lean(p: Pose) = super.lean(p) + 10f * p.strike

    override fun Sculpt.head(p: Pose) {
        // Skull with dark sockets and teeth.
        blob(headR, headR * 0.95f, headR, C.bone)
        at(0f, -headR * 0.55f, headR * 0.35f) { blob(headR * 0.62f, headR * 0.35f, headR * 0.55f, C.bone) }
        for (side in listOf(-1f, 1f)) at(side * headR * 0.38f, 0f, headR * 0.82f) { blob(headR * 0.24f, headR * 0.27f, headR * 0.15f, C.black) }
        at(0f, -headR * 0.25f, headR * 0.95f) { blob(headR * 0.08f, headR * 0.1f, headR * 0.05f, C.black) }
        at(0f, headR * 0.55f, -headR * 0.1f, rz = 8f) { blob(headR * 0.95f, headR * 0.35f, headR * 0.95f, team.mat) }
    }

    override fun Sculpt.torsoExtra(p: Pose) {
        // Ribs over the bony torso, plus a little team loincloth.
        for (i in 0 until 3) at(0f, torsoH * (0.35f + i * 0.2f), 0f) { ring(torsoW * 0.48f, 0.022f, C.bone, 14) }
        at(0f, 0.0f, torsoD * 0.4f) { box(torsoW * 0.7f, 0.12f, 0.04f, team.mat, 0.5f) }
    }

    override fun Sculpt.rightHand(p: Pose) = sword(0.36f, 0.06f, C.steel, C.darkSteel)
}

// ------------------------------------------------------------------------ Minion

class Minion(val team: TeamColor) : SpriteModel("minions") {
    private val body = Mat(0x4F6E9E)

    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        val flap = when (p.anim) {
            Anim.IDLE -> sin(p.phase * 2 * PI).toFloat()
            Anim.WALK -> sin(p.phase * 4 * PI).toFloat()
            Anim.ATTACK -> sin((p.attackT + 0.3f) / 0.6f * 4 * PI).toFloat()
        }
        val hover = 0.04f * flap
        val punch = p.strike
        at(0f, 0.45f + hover, 0f, rx = 15f + 15f * punch) {
            // Body and belly.
            blob(0.24f, 0.22f, 0.2f, body)
            at(0f, -0.2f, 0f) { box(0.3f, 0.1f, 0.24f, team.mat, 0.5f) }
            // Legs dangling.
            for (side in listOf(-1f, 1f)) limb(side * 0.08f, -0.2f, 0f, side * 0.1f, -0.38f, 0.04f, 0.045f, 0.05f, body)
            // Arms reaching forward to punch.
            for (side in listOf(-1f, 1f)) {
                val reach = if (side > 0) punch else 0f
                limb(side * 0.2f, 0.06f, 0.02f, side * 0.24f, -0.06f + 0.08f * reach, 0.12f + 0.2f * reach, 0.05f, 0.04f, body)
                at(side * 0.24f, -0.06f + 0.08f * reach, 0.12f + 0.2f * reach) { ball(0.06f, body.darker(0.85f), 10) }
            }
            // Head with big ears and little horns.
            at(0f, 0.3f, 0.06f) {
                ball(0.2f, body)
                at(0f, -0.04f, 0.17f) { ball(0.06f, body.darker(0.92f), 10) }
                for (side in listOf(-1f, 1f)) {
                    at(side * 0.08f, 0.04f, 0.17f) { blob(0.035f, 0.045f, 0.02f, Mat(0xFFF176, emissive = true)) }
                    at(side * 0.2f, 0.05f, 0f, rz = side * -55f) { slab(listOf(0f to 0f, 0.08f to 0.16f, -0.06f to 0.12f), 0.025f, body.darker(0.85f)) }
                    at(side * 0.09f, 0.17f, 0f, rz = side * -25f) { tube(0.035f, 0f, 0.1f, C.bone, 8) }
                }
            }
            // Bat wings.
            for (side in listOf(-1f, 1f)) {
                at(side * 0.12f, 0.1f, -0.14f, ry = side * 20f, rz = side * (15f + 35f * flap)) {
                    slab(
                        listOf(0f to 0f, side * 0.55f to 0.22f, side * 0.5f to 0.02f, side * 0.38f to -0.08f, side * 0.24f to -0.02f, side * 0.12f to -0.1f),
                        0.02f, team.darkMat,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ Cannon (building) and king's cannon

/** The cannon card: wooden platform with an iron barrel. The whole thing turns to aim. */
class CannonModel : SpriteModel("cannon", idleFrames = 1, walkFrames = 0) {
    override val scale = 1.1f
    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        // Octagonal wooden deck with a team-coloured trim.
        at(0f, 0f, 0f) { tube(0.95f, 0.9f, 0.22f, C.wood, 8) }
        at(0f, 0.22f, 0f) { ring(0.86f, 0.05f, team.mat, 8) }
        for (i in 0 until 8) {
            val a = (i * PI / 4).toFloat()
            at(0.92f * sin(a), 0.11f, 0.92f * cos(a)) { ball(0.05f, C.iron, 8) }
        }
        cannonBarrel(p, 0.25f, 1f)
    }
}

fun Sculpt.cannonBarrel(p: Pose, baseY: Float, size: Float) {
    val recoil = 0.18f * p.strike * size
    // Swivel mount.
    at(0f, baseY, 0f) { tube(0.4f * size, 0.34f * size, 0.18f * size, C.woodDark, 10) }
    for (side in listOf(-1f, 1f)) at(side * 0.28f * size, baseY + 0.32f * size, -0.05f * size) { box(0.1f * size, 0.36f * size, 0.4f * size, C.woodDark, 0.4f) }
    at(0f, baseY + 0.4f * size, -recoil, rx = 80f) {
        at(0f, -0.35f * size, 0f) {
            tube(0.24f * size, 0.18f * size, 0.95f * size, C.iron, 16)
            at(0f, 0.95f * size, 0f) { ring(0.19f * size, 0.05f * size, C.iron, 14) }
            at(0f, 0.15f * size, 0f) { ring(0.235f * size, 0.04f * size, C.gold, 14) }
            at(0f, -0.02f * size, 0f) { ball(0.22f * size, C.iron, 12) }
            if (p.attacking && p.attackT >= 0f && p.attackT < 0.12f) {
                at(0f, 1.1f * size, 0f) {
                    ball(0.26f * size, Mat(0xFFB74D, emissive = true), 12)
                    ball(0.15f * size, Mat(0xFFF8E1, emissive = true), 10)
                }
            }
        }
    }
}

/** The king on top of the king tower, behind his cannon. */
class KingTop : SpriteModel("kingtop", walkFrames = 0) {
    override val scale = 1.2f
    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        // The king: a round body in a team robe, white beard and a gold crown.
        at(0f, 0f, -0.55f) {
            val bob = 0.02f * p.breath
            at(0f, 0.32f + bob, 0f) { blob(0.34f, 0.34f, 0.3f, team.mat) }
            at(0f, 0.32f + bob, 0.05f) { ring(0.3f, 0.04f, C.white, 14) }
            at(0f, 0.78f + bob, 0.02f) {
                ball(0.24f, C.skin)
                at(0f, -0.1f, 0.15f) { blob(0.2f, 0.2f, 0.14f, C.white) }
                for (side in listOf(-1f, 1f)) at(side * 0.09f, 0.03f, 0.21f) { blob(0.03f, 0.04f, 0.02f, C.black) }
                at(0f, 0.16f, -0.02f) {
                    tube(0.2f, 0.22f, 0.14f, C.gold, 10)
                    for (i in 0 until 5) {
                        val a = (i * 2 * PI / 5).toFloat()
                        at(0.2f * sin(a), 0.14f, 0.2f * cos(a)) { tube(0.05f, 0f, 0.1f, C.gold, 6) }
                    }
                }
            }
        }
        cannonBarrel(p, 0f, 0.75f)
    }
}

// ------------------------------------------------------------------------ Towers

private fun Sculpt.crenellations(r: Float, y: Float, n: Int, mat: Mat) {
    for (i in 0 until n) {
        val a = (i * 2 * PI / n).toFloat()
        at(r * sin(a), y, r * cos(a), ry = a * 180f / PI.toFloat()) { box(0.3f, 0.32f, 0.22f, mat, 0.35f) }
    }
}

class PrincessTower : SpriteModel("tower_princess", idleFrames = 1, walkFrames = 0, attackFrames = 0, directions = 1) {
    override val mount = 2.35f
    override val scale = 1f

    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        at(0f, 0f, 0f) { tube(1.45f, 1.3f, 0.3f, C.stoneDark, 12) }
        at(0f, 0.3f, 0f) { tube(1.2f, 1.1f, 1.7f, C.stone, 14) }
        // Stone courses.
        for (i in 0 until 3) at(0f, 0.65f + i * 0.5f, 0f) { ring(1.17f - i * 0.03f, 0.04f, C.stoneDark, 18) }
        at(0f, 2.0f, 0f) { tube(1.15f, 1.32f, 0.35f, C.stone, 14) }
        at(0f, 2.35f, 0f) { tube(1.32f, 1.32f, 0.02f, C.stoneDark, 14) }
        crenellations(1.18f, 2.5f, 10, C.stone)
        // Wooden door and team banners on the front.
        at(0f, 0.3f, 1.12f) { box(0.55f, 0.75f, 0.12f, C.woodDark, 0.3f) }
        at(0f, 0.9f, 1.12f) { ball(0.28f, C.woodDark, 10) }
        for (side in listOf(-1f, 1f)) at(side * 0.62f, 1.55f, 0.97f, ry = side * 32f) {
            slab(listOf(-0.22f to 0.35f, 0.22f to 0.35f, 0.22f to -0.35f, 0f to -0.5f, -0.22f to -0.35f), 0.04f, team.mat)
            at(0f, 0.36f, 0.04f) { box(0.5f, 0.06f, 0.06f, C.gold, 0.4f) }
        }
    }
}

class KingTower : SpriteModel("tower_king", idleFrames = 1, walkFrames = 0, attackFrames = 0, directions = 1) {
    override val mount = 2.55f
    override val scale = 1f

    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        at(0f, 0.15f, 0f) { box(4f, 0.3f, 4f, C.stoneDark, 0.25f) }
        at(0f, 1.25f, 0f) { box(3.5f, 2.2f, 3.5f, C.stone, 0.18f) }
        for (i in 0 until 3) at(0f, 0.7f + i * 0.6f, 0f) { box(3.56f, 0.07f, 3.56f, C.stoneDark, 0.2f) }
        at(0f, 2.45f, 0f) { box(3.8f, 0.3f, 3.8f, C.stone, 0.25f) }
        at(0f, 2.55f, 0f) { box(3.5f, 0.06f, 3.5f, team.darkMat, 0.3f) }
        // Corner turrets with team roofs.
        for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) at(sx * 1.7f, 2.3f, sz * 1.7f) {
            tube(0.42f, 0.42f, 0.6f, C.stone, 12)
            at(0f, 0.6f, 0f) { tube(0.55f, 0f, 0.7f, team.mat, 12) }
            at(0f, 1.3f, 0f) { ball(0.08f, C.gold, 8) }
        }
        for (i in -2..2) for ((x, z) in listOf(i * 0.6f to 1.78f, i * 0.6f to -1.78f, 1.78f to i * 0.6f, -1.78f to i * 0.6f)) {
            if (kotlin.math.abs(i) == 2) continue
            at(x, 2.75f, z) { box(0.32f, 0.3f, 0.32f, C.stone, 0.35f) }
        }
        // Big front gate and a team crest.
        at(0f, 0.75f, 1.76f) { box(1.0f, 1.3f, 0.12f, C.woodDark, 0.25f) }
        at(0f, 1.4f, 1.76f) { ball(0.5f, C.woodDark, 12) }
        at(0f, 1.95f, 1.78f) {
            slab(listOf(-0.35f to 0.3f, 0.35f to 0.3f, 0.35f to -0.1f, 0f to -0.4f, -0.35f to -0.1f), 0.06f, team.mat)
            at(0f, 0f, 0.04f) { slab(listOf(-0.18f to 0.12f, 0f to -0.05f, 0.18f to 0.12f, 0.12f to -0.15f, -0.12f to -0.15f), 0.04f, C.gold) }
        }
    }
}

// ------------------------------------------------------------------------ Spell icons (card art only)

class FireballIcon : SpriteModel("fireball", 1, 0, 0, 1) {
    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        at(0f, 0.6f, 0f) {
            ball(0.42f, Mat(0xFF7A1A, emissive = true))
            at(0.06f, 0.08f, 0.18f) { ball(0.3f, Mat(0xFFB74D, emissive = true)) }
            at(0.1f, 0.14f, 0.3f) { ball(0.16f, Mat(0xFFF3C4, emissive = true)) }
            // Flame licks trailing up and back.
            for (i in 0 until 7) {
                val a = i * 0.9f
                at(0.3f * sin(a), 0.1f + 0.25f * cos(a * 1.3f), -0.2f, rx = -50f - 10f * i % 3, rz = 20f * sin(a)) {
                    tube(0.16f, 0f, 0.55f + 0.1f * (i % 3), Mat(if (i % 2 == 0) 0xFF5722 else 0xFF9800, emissive = true), 10)
                }
            }
        }
    }
}

class ZapIcon : SpriteModel("zap", 1, 0, 0, 1) {
    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        val bolt = listOf(0.05f to 1.2f, 0.38f to 1.2f, 0.18f to 0.72f, 0.42f to 0.72f, -0.18f to -0.1f, 0.02f to 0.5f, -0.24f to 0.5f)
        at(0f, 0f, 0f, rz = -8f) { slab(bolt, 0.16f, Mat(0xFFE34D, emissive = false, shiny = 0.6f)) }
        at(0.03f, 0.05f, 0.1f, rz = -8f, s = 0.7f) { slab(bolt, 0.05f, Mat(0xFFFBE0, emissive = true)) }
    }
}

class FreezeIcon : SpriteModel("freeze", 1, 0, 0, 1) {
    override fun build(s: Sculpt, p: Pose, team: TeamColor) = with(s) {
        val ice = Mat(0x7FD3FF, shiny = 0.8f)
        val iceLight = Mat(0xD7F3FF, shiny = 0.9f)
        at(0f, 0.55f, 0f) { blob(0.22f, 0.6f, 0.22f, ice, 2.2f) }
        at(-0.32f, 0.35f, 0.05f, rz = 28f) { blob(0.15f, 0.42f, 0.15f, iceLight, 2.2f) }
        at(0.3f, 0.32f, -0.05f, rz = -30f) { blob(0.16f, 0.45f, 0.16f, ice, 2.2f) }
        at(0.1f, 0.2f, 0.25f, rx = 25f, rz = -10f) { blob(0.11f, 0.3f, 0.11f, iceLight, 2.2f) }
        at(0f, 0.04f, 0f) { blob(0.55f, 0.08f, 0.45f, Mat(0xFFFFFF), 0.6f) }
    }
}

/** Everything the game draws from a sprite sheet, plus portrait-only spell icons. */
object Models {
    val sprites: List<SpriteModel> = listOf(
        HumanModel("knight") { Knight(it) },
        HumanModel("archers") { Archer(it) },
        HumanModel("giant") { Giant(it) },
        HumanModel("wizard") { Wizard(it) },
        HumanModel("skeletons") { Skeleton(it) },
        Minion(TeamColor.BLUE),
        CannonModel(),
        KingTop(),
        PrincessTower(),
        KingTower(),
    )

    val spellIcons: List<SpriteModel> = listOf(FireballIcon(), ZapIcon(), FreezeIcon())

    fun byId(id: String) = (sprites + spellIcons).firstOrNull { it.id == id }
}
