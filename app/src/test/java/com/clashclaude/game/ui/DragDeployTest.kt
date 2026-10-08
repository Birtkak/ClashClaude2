package com.clashclaude.game.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
import com.clashclaude.game.game.Battle
import com.clashclaude.game.game.Kind
import com.clashclaude.game.game.Team
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Drives the real battle screen with touch input, on Android's Compose stack (via Robolectric). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DragDeployTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/ui-shots").apply { mkdirs() }

    private fun shot(name: String) {
        // The battle loop never goes idle, so draw the window directly instead of captureToImage().
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun draggingATroopShowsTheGhostAndDeploysOnRelease() {
        val deck = Cards.defaultDecks[0].map { Cards.get(it)!! }
        val battle = Battle(deck, deck, Random(5))
        // Make card 0 a troop and affordable, and keep the AI quiet.
        battle.player.hand[0] = Cards.get("knight")!!
        battle.player.elixir = 10f
        battle.enemy.elixir = 0f
        rule.mainClock.autoAdvance = false
        rule.setContent { ClashTheme { BattleScreen(deck, initialBattle = battle) {} } }
        rule.mainClock.advanceTimeBy(500)
        shot("drag-0-start") // Also runs a draw pass, like the first frame on a device.

        val troopsBefore = battle.entities.count { it.team == Team.PLAYER && it.kind == Kind.TROOP }
        val arena = rule.onNodeWithTag("arena").fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("hand-0").fetchSemanticsNode().boundsInRoot
        // Target: a point on our side, about 3/4 down the arena, converted to the card's local space.
        val target = Offset(arena.center.x - card.left, arena.top + arena.height * 0.72f - card.top)

        rule.onNodeWithTag("hand-0").performTouchInput {
            down(center)
            val steps = 20
            for (s in 1..steps) {
                moveTo(center + (target - center) * (s / steps.toFloat()), delayMillis = 16)
            }
        }
        rule.mainClock.advanceTimeBy(50)
        shot("drag-1-over-arena")
        rule.mainClock.advanceTimeBy(100)
        shot("drag-mid")
        rule.onNodeWithTag("hand-0").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        shot("drag-dropped")

        val troopsAfter = battle.entities.count { it.team == Team.PLAYER && it.kind == Kind.TROOP }
        assertEquals("one Knight should have been deployed", troopsBefore + 1, troopsAfter)
        assertEquals(CardType.TROOP, Cards.get("knight")!!.type)
    }

    /** A battle where card 0 is an affordable Knight and the AI can't play. */
    private fun knightBattle(): Battle {
        val deck = Cards.defaultDecks[0].map { Cards.get(it)!! }
        val battle = Battle(deck, deck, Random(5))
        battle.player.hand[0] = Cards.get("knight")!!
        battle.player.elixir = 10f
        battle.enemy.elixir = 0f
        rule.mainClock.autoAdvance = false
        rule.setContent { ClashTheme { BattleScreen(deck, initialBattle = battle) {} } }
        rule.mainClock.advanceTimeBy(500)
        return battle
    }

    private fun knights(b: Battle) = b.entities.count { it.team == Team.PLAYER && it.kind == Kind.TROOP }

    @Test
    fun tapACardThenTapTheArenaPlacesIt() {
        val battle = knightBattle()
        val before = knights(battle)
        rule.onNodeWithTag("hand-0").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(100)
        shot("tap-1-selected")
        val arena = rule.onNodeWithTag("arena").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("arena").performTouchInput { click(Offset(arena.width * 0.3f, arena.height * 0.75f)) }
        rule.mainClock.advanceTimeBy(300)
        shot("tap-2-placed")
        assertEquals(before + 1, knights(battle))
        // It landed where the finger tapped (left side), not somewhere else.
        val knight = battle.entities.last { it.team == Team.PLAYER && it.kind == Kind.TROOP }
        assertTrue("knight at x=${knight.x}", knight.x < 9f)
    }

    @Test
    fun tapACardThenHoldAndSlideAimsBeforePlacing() {
        val battle = knightBattle()
        val before = knights(battle)
        rule.onNodeWithTag("hand-0").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(100)
        val arena = rule.onNodeWithTag("arena").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("arena").performTouchInput {
            down(Offset(arena.width * 0.3f, arena.height * 0.75f))
            for (s in 1..15) moveTo(Offset(arena.width * (0.3f + 0.04f * s), arena.height * 0.75f), delayMillis = 16)
        }
        rule.mainClock.advanceTimeBy(100)
        shot("hover-1-aiming")
        assertEquals("nothing placed while aiming", before, knights(battle))
        rule.onNodeWithTag("arena").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(before + 1, knights(battle))
        val knight = battle.entities.last { it.team == Team.PLAYER && it.kind == Kind.TROOP }
        assertTrue("knight follows the slide to the right, x=${knight.x}", knight.x > 12f)
    }

    @Test
    fun arenaKeepsItsSizeWhenDoubleElixirStarts() {
        val battle = knightBattle()
        val before = rule.onNodeWithTag("arena").fetchSemanticsNode().boundsInRoot
        // Fast-forward the match into the last minute.
        while (!battle.doubleElixir) {
            battle.enemy.elixir = 0f
            battle.update(0.5f)
        }
        rule.mainClock.advanceTimeBy(200)
        shot("double-elixir")
        val after = rule.onNodeWithTag("arena").fetchSemanticsNode().boundsInRoot
        assertEquals("the arena must not resize (zoom) mid-match", before, after)
    }
}
