import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.clashclaude.game.data.Cards
import com.clashclaude.game.data.DeckRepository
import com.clashclaude.game.ui.BattleScreen
import com.clashclaude.game.ui.ClashTheme
import com.clashclaude.game.ui.HomeScreen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

// Scripted, headless playtest of the real Compose screens at a typical phone size
// (1080x2340 px, 2.75x density). Screenshots land in the directory given as args[0].

private const val W = 1080
private const val H = 2340

/** Screen-space centers of the four hand cards and arena helpers for this phone size. */
private val handX = listOf(360f, 545f, 730f, 915f)
private const val HAND_Y = 2050f

private class InMemoryPrefs : android.content.SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getString(k: String, d: String?) = values[k] as String? ?: d
    override fun getInt(k: String, d: Int) = values[k] as Int? ?: d
    override fun edit() = object : android.content.SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { values[k] = v }
        override fun putInt(k: String, v: Int) = apply { values[k] = v }
        override fun apply() {}
    }
}

private class FakeContext : android.content.Context() {
    private val prefs = InMemoryPrefs()
    override fun getSharedPreferences(n: String, m: Int) = prefs
}

/** Drives a scene with a virtual clock at 30 fps. */
private class Driver(val scene: ImageComposeScene, val outDir: File) {
    var nanos = 0L
    private val ms get() = nanos / 1_000_000

    fun wait(seconds: Float) = repeat((seconds * 30).toInt()) { frame() }

    fun frame() {
        nanos += 33_333_333L
        scene.render(nanos).close() // Frames hold native memory; free them right away.
    }

    fun tap(x: Float, y: Float) {
        scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = ms)
        frame()
        scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), timeMillis = ms)
        frame()
    }

    /** Drags from (x0, y0) to (x1, y1) over [steps] frames; screenshots just before release if [shot] is set. */
    fun drag(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 20, shot: String? = null) {
        scene.sendPointerEvent(PointerEventType.Press, Offset(x0, y0), timeMillis = ms)
        for (i in 1..steps) {
            val f = i / steps.toFloat()
            frame()
            scene.sendPointerEvent(PointerEventType.Move, Offset(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f), timeMillis = ms)
        }
        frame()
        shot?.let { screenshot(it) }
        scene.sendPointerEvent(PointerEventType.Release, Offset(x1, y1), timeMillis = ms)
        frame()
    }

    fun screenshot(name: String) {
        scene.render(nanos).use { img ->
            File(outDir, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        println("saved ${File(outDir, "$name.png")}")
    }
}

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/playtest" }).apply { mkdirs() }
    val density = Density(2.75f)

    val home = ImageComposeScene(W, H, density) {
        ClashTheme { HomeScreen(DeckRepository(FakeContext())) {} }
    }
    Driver(home, out).screenshot("01-home")
    home.close()

    val deck = Cards.defaultDecks[0].mapNotNull { Cards.get(it) }
    val battle = ImageComposeScene(W, H, density) { ClashTheme { BattleScreen(deck) {} } }
    val d = Driver(battle, out)
    d.wait(1f)
    d.screenshot("02-battle-start")

    // Tap-to-place: select a card, then tap on our side of the arena.
    d.wait(4f)
    d.tap(handX[0], HAND_Y)
    d.screenshot("03-card-selected")
    d.tap(250f, 1500f)

    // Drag a card deep into enemy territory: troops should snap back to our side of the river.
    for (round in 0 until 4) {
        d.wait(5f)
        val card = round % 4
        d.drag(handX[card], HAND_Y, if (round % 2 == 0) 830f else 250f, 500f, shot = if (round == 0) "04-dragging" else null)
    }
    d.wait(3f)
    d.screenshot("05-mid-battle")
    d.wait(30f)
    d.screenshot("06-later")
    battle.close()
}
