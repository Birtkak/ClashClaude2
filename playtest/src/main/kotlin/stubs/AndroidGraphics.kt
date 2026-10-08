@file:Suppress("unused", "UNUSED_PARAMETER")

// Minimal stand-ins for android.graphics used by BattleScreen. Text is drawn by NativeText.kt.
package android.graphics
class Paint(flags: Int = 0) {
    enum class Align { LEFT, CENTER, RIGHT }
    var textAlign: Align = Align.LEFT
    var typeface: Typeface? = null
    var color: Int = -16777216
    var textSize: Float = 12f
    var alpha: Int = 255
    fun descent(): Float = textSize * 0.25f
    fun ascent(): Float = -textSize * 0.9f
    fun setShadowLayer(r: Float, dx: Float, dy: Float, c: Int) {}
    companion object { const val ANTI_ALIAS_FLAG = 1 }
}
class Typeface { companion object { val DEFAULT_BOLD = Typeface() } }
object Color { const val WHITE = -1; const val BLACK = -16777216 }
