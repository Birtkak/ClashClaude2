import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.clashclaude.game.data.Cards
import com.clashclaude.game.data.DeckRepository
import com.clashclaude.game.ui.BattleScreen
import com.clashclaude.game.ui.ClashTheme
import com.clashclaude.game.ui.HomeScreen
import com.clashclaude.game.ui.Pen
import com.clashclaude.game.ui.Pose
import com.clashclaude.game.ui.spellIcon
import com.clashclaude.game.ui.tower
import com.clashclaude.game.ui.unit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.clashclaude.game.data.CardType
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

// Scripted, headless playtest of the real Compose screens at a typical phone size
// (1080x2340 px, 2.75x density). Screenshots land in the directory given as args[0].

/** The app's display font, loaded from the Android resources folder. */
private val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.platform.Font(File("../app/src/main/res/font/lilita_one.ttf")),
)

private const val W = 1080
private const val H = 2340

/** Screen-space centers of the four hand cards and arena helpers for this phone size. */
private val handX = listOf(360f, 545f, 730f, 915f)
private const val HAND_Y = 2050f

private class InMemoryPrefs : android.content.SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getString(k: String, d: String?) = values[k] as String? ?: d
    override fun getInt(k: String, d: Int) = values[k] as Int? ?: d
    override fun getBoolean(k: String, d: Boolean) = values[k] as Boolean? ?: d
    override fun edit() = object : android.content.SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { values[k] = v }
        override fun putInt(k: String, v: Int) = apply { values[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { values[k] = v }
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

    for ((i, name) in listOf("battle", "deck", "more").withIndex()) {
        val home = ImageComposeScene(W, H, density) {
            ClashTheme(DisplayFont) { HomeScreen(DeckRepository(FakeContext()), initialTab = i) {} }
        }
        Driver(home, out).apply { wait(0.5f) }.screenshot("01-home-$name")
        home.close()
    }

    // Sprite sheet: every card in idle, walk and attack poses, plus both towers.
    val gallery = ImageComposeScene(W, H, density) {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF6DBE45))) {
            val cell = size.width / 6f
            Cards.all.forEachIndexed { i, card ->
                val col = (i % 2) * 3
                val row = i / 2
                val y = cell * 0.6f * (row + 1) + 10f
                for (p in 0 until 3) {
                    val pose = when (p) {
                        0 -> Pose(time = 0.2f)
                        1 -> Pose(walk = 1.2f, moving = true, time = 0.5f)
                        else -> Pose(swing = -0.6f, recoil = 0.8f, aim = -0.3f, time = 0.8f)
                    }
                    val x = cell * (col + p + 0.5f)
                    if (card.type == CardType.SPELL) {
                        Pen(this, x, y - cell * 0.3f, cell * 0.45f).spellIcon(card.id, p * 0.3f)
                    } else {
                        Pen(this, x, y, cell * 0.5f).unit(card.id, if (p == 2) Color(0xFFFF4B4B) else Color(0xFF3FA7FF), pose)
                    }
                }
            }
            Pen(this, cell * 1.5f, size.height - cell * 1.6f, cell * 0.5f).tower(false, Color(0xFF3FA7FF), 1f, -1f, Pose())
            Pen(this, cell * 4.5f, size.height - cell * 1.6f, cell * 0.5f).tower(true, Color(0xFFFF4B4B), -1f, 1f, Pose(recoil = 1f))
        }
    }
    Driver(gallery, out).screenshot("00-gallery")
    gallery.close()

    stagedFight(out, density)
    // The same fight seen by the other side (as a multiplayer opponent would): their units at the bottom, in blue.
    stagedFight(out, density, com.clashclaude.game.game.Team.ENEMY, "07b-flipped")
    stagedNewCards(out, density)

    val deck = Cards.defaultDecks[0].mapNotNull { Cards.get(it) }
    val battle = ImageComposeScene(W, H, density) { ClashTheme(DisplayFont) { BattleScreen(deck) {} } }
    val d = Driver(battle, out)
    d.wait(1f)
    d.screenshot("02-battle-start")

    // Tap-to-place: select a card, then tap on our side of the arena.
    d.wait(4f)
    d.tap(handX[0], HAND_Y)
    d.screenshot("03-card-selected")
    d.tap(250f, 1500f)
    d.wait(0.35f)
    d.screenshot("03b-dropping")

    // Drag a card deep into enemy territory: troops should snap back to our side of the river.
    for (round in 0 until 4) {
        d.wait(5f)
        val card = round % 4
        d.drag(handX[card], HAND_Y, if (round % 2 == 0) 830f else 250f, if (round < 2) 1500f else 500f, shot = if (round == 0) "04-dragging" else null)
    }
    d.wait(3f)
    d.screenshot("05-mid-battle")
    d.wait(30f)
    d.screenshot("06-later")
    battle.close()
}

/** A staged clash in the player's left lane so projectiles, splash and melee are all on screen. */
private fun stagedFight(
    out: File,
    density: Density,
    viewer: com.clashclaude.game.game.Team = com.clashclaude.game.game.Team.PLAYER,
    name: String = "07-fight",
) {
    fun deck(vararg ids: String) = ids.map { Cards.get(it)!! }
    val player = deck("wizard", "archers", "knight", "valkyrie", "musketeer", "minions", "bomber", "babydragon")
    val enemy = deck("giant", "barbarians", "minions", "goblins", "hogrider", "pekka", "speargoblins", "skeletons")
    val battle = com.clashclaude.game.game.Battle(player, enemy, kotlin.random.Random(7))
    fun put(team: com.clashclaude.game.game.Team, id: String, x: Float, y: Float) {
        val side = battle.side(team)
        side.elixir = 10f
        // Put the wanted card in hand slot 0, then deploy it.
        val card = Cards.get(id)!!
        side.hand[0] = card
        check(battle.deploy(team, 0, x, y)) { "couldn't deploy $id at $x,$y" }
    }
    val P = com.clashclaude.game.game.Team.PLAYER
    val E = com.clashclaude.game.game.Team.ENEMY
    put(E, "giant", 3.5f, 11f)
    put(E, "barbarians", 4.5f, 13f)
    put(E, "minions", 6f, 12f)
    put(P, "wizard", 4f, 24f)
    put(P, "valkyrie", 3.5f, 20.5f)
    put(P, "archers", 6f, 23f)
    put(P, "bomber", 2f, 23f)
    put(P, "babydragon", 6.5f, 21f)
    battle.enemy.elixir = 0f
    battle.player.elixir = 0f

    val scene = ImageComposeScene(W, H, density) {
        ClashTheme(DisplayFont) { BattleScreen(player, initialBattle = battle, viewer = viewer) {} }
    }
    val d = Driver(scene, out)
    d.wait(0.45f)
    d.screenshot("$name-0-dropping")
    d.wait(5.5f)
    for (i in 1..4) {
        d.wait(0.4f)
        d.screenshot("$name-$i")
        if (viewer != com.clashclaude.game.game.Team.PLAYER) break
    }
    scene.close()
}

/** The newer cards together: Inferno, Tombstone, Witch vs Giant, Mega Minion, Royal Giant, then a Freeze. */
private fun stagedNewCards(out: File, density: Density) {
    fun deck(vararg ids: String) = ids.map { Cards.get(it)!! }
    val player = deck("infernotower", "tombstone", "witch", "freeze", "lightning", "knight", "archers", "zap")
    val enemy = deck("giant", "megaminion", "royalgiant", "goblins", "hogrider", "pekka", "speargoblins", "skeletons")
    val battle = com.clashclaude.game.game.Battle(player, enemy, kotlin.random.Random(9))
    val P = com.clashclaude.game.game.Team.PLAYER
    val E = com.clashclaude.game.game.Team.ENEMY
    fun put(team: com.clashclaude.game.game.Team, id: String, x: Float, y: Float) {
        val side = battle.side(team)
        side.elixir = 10f
        side.hand[0] = Cards.get(id)!!
        check(battle.deploy(team, 0, x, y)) { "couldn't deploy $id" }
        side.elixir = 0f
    }
    put(E, "giant", 4f, 12f)
    put(E, "megaminion", 6f, 12f)
    put(E, "royalgiant", 3f, 9f)
    put(P, "infernotower", 6f, 22f)
    put(P, "tombstone", 9f, 24f)
    put(P, "witch", 6.5f, 25.5f)
    val scene = ImageComposeScene(W, H, density) { ClashTheme(DisplayFont) { BattleScreen(player, initialBattle = battle) {} } }
    val d = Driver(scene, out)
    d.wait(9f)
    d.screenshot("08-new-cards-1")
    put(P, "freeze", 4.5f, 17.5f)
    d.wait(1f)
    d.screenshot("08-new-cards-2-freeze")
    d.wait(4f)
    d.screenshot("08-new-cards-3")
    scene.close()
}
