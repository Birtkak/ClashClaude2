package baker

import com.clashclaude.game.game.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Anim { IDLE, WALK, ATTACK }

/**
 * One animation sample. [phase] runs 0..1 over a looping idle or walk cycle; [attackT] is the
 * time in seconds relative to the moment the attack lands (negative = wind-up).
 */
class Pose(val anim: Anim, val phase: Float = 0f, val attackT: Float = 99f) {
    /** -1..1 walk swing. */
    val swing: Float get() = if (anim == Anim.WALK) sin(phase * 2 * PI).toFloat() else 0f

    /** 0..1 vertical bounce, twice per walk cycle. */
    val bounce: Float get() = if (anim == Anim.WALK) abs(sin(phase * 2 * PI).toFloat()) else 0f

    /** 0..1 slow breathing for idle. */
    val breath: Float get() = if (anim == Anim.IDLE) (sin(phase * 2 * PI).toFloat() + 1f) / 2f else 0.5f

    /** 0 at rest, rising to 1 at the top of the wind-up just before the hit. */
    val windup: Float
        get() = if (anim != Anim.ATTACK || attackT >= 0f) 0f
        else ease(((attackT + View.ATTACK_WINDUP) / (View.ATTACK_WINDUP * 0.85f)).coerceIn(0f, 1f))

    /** 1 right at the hit, falling to 0 as the unit recovers. */
    val strike: Float
        get() = if (anim != Anim.ATTACK || attackT < 0f) 0f
        else 1f - ease((attackT / View.ATTACK_RECOVER).coerceIn(0f, 1f))

    val attacking get() = anim == Anim.ATTACK

    companion object {
        val REST = Pose(Anim.IDLE, 0.25f)
    }
}

fun ease(t: Float) = t * t * (3 - 2 * t)
fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Team colours: main (clothing), dark trim and light accent. */
enum class TeamColor(val main: Int, val dark: Int, val light: Int) {
    BLUE(0x2F8BEB, 0x1A4F9C, 0x8FD0FF),
    RED(0xE53935, 0x962020, 0xFF9E80);

    val mat get() = Mat(main)
    val darkMat get() = Mat(dark)
    val lightMat get() = Mat(light)
}

/** Shared palette. */
object C {
    val skin = Mat(0xF6C49A)
    val skinDark = Mat(0xD99A70)
    val steel = Mat(0xB8C4CF, shiny = 0.25f)
    val darkSteel = Mat(0x5E6B78, shiny = 0.4f)
    val iron = Mat(0x37404A, shiny = 0.5f)
    val gold = Mat(0xFFC93C, shiny = 0.3f)
    val wood = Mat(0x9A6534)
    val woodDark = Mat(0x6B4220)
    val leather = Mat(0x7A4A26)
    val boot = Mat(0x4E3420)
    val bone = Mat(0xF1EEE4)
    val black = Mat(0x1A1A22)
    val white = Mat(0xFFFFFF)
    val eyeWhite = Mat(0xFFFFFF, emissive = true)
    val stone = Mat(0xA9AFB8)
    val stoneDark = Mat(0x7C838E)
    val roof = Mat(0x5D4037)
}

/**
 * A chunky cartoon humanoid. Lengths are in tiles; the feet are at y = 0 and the model
 * faces +Z. Subclasses dress it up with [head], [torsoExtra] and hand-held props.
 */
open class Humanoid(
    val legLen: Float = 0.3f,
    val legR: Float = 0.075f,
    val hipW: Float = 0.1f,
    val torsoW: Float = 0.42f,
    val torsoH: Float = 0.4f,
    val torsoD: Float = 0.3f,
    val headR: Float = 0.21f,
    val armLen: Float = 0.34f,
    val armR: Float = 0.065f,
    val handR: Float = 0.075f,
    val shirt: Mat = Mat(0x777777),
    val pants: Mat = Mat(0x555555),
    val boots: Mat = C.boot,
    val skin: Mat = C.skin,
    val sleeve: Mat = shirt,
) {
    /** Arm angles: forward raise (deg, 0 = hanging, 90 = pointing ahead, 180 = overhead), outward roll, elbow bend. */
    data class Arm(val raise: Float = 8f, val out: Float = 10f, val bend: Float = 15f)

    open fun rightArm(p: Pose): Arm = Arm(8f - 22f * p.swing, 10f, 15f + 4f * p.breath)
    open fun leftArm(p: Pose): Arm = Arm(8f + 22f * p.swing, 10f, 15f + 4f * p.breath)

    /** Body lean forward in degrees, and twist around Y. */
    open fun lean(p: Pose) = 4f * p.bounce
    open fun twist(p: Pose) = 0f

    open fun Sculpt.head(p: Pose) {
        ball(headR, skin)
        face(headR)
    }

    open fun Sculpt.torsoExtra(p: Pose) {}
    open fun Sculpt.rightHand(p: Pose) {}
    open fun Sculpt.leftHand(p: Pose) {}

    /** Default cartoon face on a head of radius r centred at the origin. */
    fun Sculpt.face(r: Float, eyeY: Float = 0.02f, eyeGap: Float = 0.38f) {
        for (side in listOf(-1f, 1f)) {
            at(side * r * eyeGap, r * eyeY, r * 0.9f) {
                blob(r * 0.13f, r * 0.17f, r * 0.08f, C.black)
                at(r * 0.03f, r * 0.05f, r * 0.05f) { ball(r * 0.045f, C.eyeWhite, 8) }
            }
        }
        at(0f, -r * 0.18f, r * 0.97f) { ball(r * 0.11f, skin.darker(0.92f), 10) }
    }

    fun build(s: Sculpt, p: Pose) = with(s) {
        val bob = 0.035f * p.bounce + 0.008f * p.breath
        val hipY = legLen + legR + bob
        // Legs swing opposite each other while walking.
        for (side in listOf(-1f, 1f)) {
            val swing = 28f * p.swing * side
            at(side * hipW, hipY, 0f, rx = -swing) {
                limb(0f, 0f, 0f, 0f, -legLen, 0f, legR * 1.1f, legR, pants)
                at(0f, -legLen - legR * 0.3f, legR * 0.4f) { box(legR * 2.3f, legR * 1.5f, legR * 3.2f, boots, 0.45f) }
            }
        }
        at(0f, hipY, 0f, rx = lean(p), ry = twist(p)) {
            val breathe = 1f + 0.03f * (p.breath - 0.5f)
            at(0f, torsoH * 0.45f, 0f) {
                with(Mat4.scale(1f, breathe, 1f)) { box(torsoW, torsoH * 1.05f, torsoD, shirt, 0.55f) }
            }
            at(0f, 0.02f, 0f) { box(torsoW * 0.95f, torsoH * 0.35f, torsoD * 0.95f, pants, 0.6f) }
            torsoExtra(p)
            val shoulderY = torsoH * 0.82f
            arm(this@Humanoid.rightArm(p), 1f, shoulderY) { rightHand(p) }
            arm(this@Humanoid.leftArm(p), -1f, shoulderY) { leftHand(p) }
            // Heads tip up toward the high camera so faces stay readable.
            at(0f, torsoH + headR * 0.75f, 0.02f, rx = -HEAD_TILT) { head(p) }
        }
    }

    /** side = +1 for the right arm (on the model's +X... which is its right). */
    private fun Sculpt.arm(a: Arm, side: Float, shoulderY: Float, hand: Sculpt.() -> Unit) {
        val upper = armLen * 0.5f
        val fore = armLen * 0.5f
        at(side * (torsoW / 2 + armR * 0.6f), shoulderY, 0f, rx = -a.raise, rz = side * a.out) {
            ball(armR * 1.35f, sleeve, 12)
            limb(0f, 0f, 0f, 0f, -upper, 0f, armR * 1.1f, armR, sleeve)
            at(0f, -upper, 0f, rx = -a.bend) {
                limb(0f, 0f, 0f, 0f, -fore, 0f, armR, armR * 0.95f, skin)
                at(0f, -fore - handR * 0.5f, 0f) {
                    ball(handR, skin, 12)
                    hand()
                }
            }
        }
    }
}

/** Degrees every head is tipped back so the face shows from the tilted camera. */
const val HEAD_TILT = 22f

/** A sword held in a fist: the blade points along the hand's +Z. */
fun Sculpt.sword(len: Float, width: Float, blade: Mat = C.steel, guard: Mat = C.gold) {
    at(0f, 0f, 0.02f) {
        limb(0f, 0f, -0.08f, 0f, 0f, 0.04f, 0.028f, 0.028f, C.leather)
        at(0f, 0f, 0.06f) { box(width * 3.2f, 0.05f, 0.05f, guard, 0.4f) }
        at(0f, 0f, 0.08f, rx = 90f) {
            slab(listOf(-width / 2 to 0f, width / 2 to 0f, width / 2 to len * 0.85f, 0f to len, -width / 2 to len * 0.85f), 0.03f, blade)
        }
    }
}
