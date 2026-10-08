package com.clashclaude.game.game

import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleTest {
    private fun deck(i: Int) = Cards.defaultDecks[i].map { Cards.get(it)!! }

    private fun newBattle(seed: Int = 1) = Battle(deck(0), deck(1), Random(seed))

    @Test
    fun elixirRegeneratesOnePointPerInterval() {
        val b = newBattle()
        val start = b.player.elixir
        repeat((Battle.ELIXIR_SECONDS * 60).toInt()) { b.update(1f / 60f) }
        assertEquals(start + 1f, b.player.elixir, 0.05f)
    }

    @Test
    fun deployingSpendsElixirAndCyclesTheCard() {
        val b = newBattle()
        b.player.elixir = 10f
        val index = b.player.hand.indexOfFirst { it.type != CardType.SPELL }
        val card = b.player.hand[index]
        val next = b.player.next
        assertTrue(b.deploy(Team.PLAYER, index, 9f, 24f))
        assertEquals(10f - card.cost, b.player.elixir, 0.001f)
        assertEquals(next, b.player.hand[index])
        assertEquals(card, b.player.queue.last())
    }

    @Test
    fun cannotDeployTroopsOnEnemySideOrWithoutElixir() {
        val b = newBattle()
        b.player.elixir = 10f
        val index = b.player.hand.indexOfFirst { it.type != CardType.SPELL }
        assertFalse(b.deploy(Team.PLAYER, index, 9f, 8f))
        b.player.elixir = 0f
        assertFalse(b.deploy(Team.PLAYER, index, 9f, 24f))
    }

    @Test
    fun troopDropsOnEnemySideSnapBehindTheRiver() {
        val b = newBattle()
        val knight = Cards.get("knight")!!
        val (x, y) = b.snapPlacement(Team.PLAYER, knight, 14f, 4f)
        assertEquals(14f, x, 0.001f)
        assertTrue("snapped y=$y should be on the player side", y >= Arena.RIVER_BOTTOM)
        assertTrue(b.canPlace(Team.PLAYER, knight, x, y))
    }

    @Test
    fun dropsOnATowerArePushedOffIt() {
        val b = newBattle()
        val knight = Cards.get("knight")!!
        val (x, y) = b.snapPlacement(Team.PLAYER, knight, 3.5f, 26f)
        assertTrue(b.canPlace(Team.PLAYER, knight, x, y))
    }

    @Test
    fun spellsAreNotSnapped() {
        val b = newBattle()
        val fireball = Cards.get("fireball")!!
        assertEquals(5f to 6f, b.snapPlacement(Team.PLAYER, fireball, 5f, 6f))
    }

    @Test
    fun matchesAgainstTheAiAlwaysFinish() {
        repeat(5) { seed ->
            val rng = Random(seed)
            val b = Battle(deck(seed % 3), deck((seed + 1) % 3), rng)
            var next = 2f
            while (b.outcome == null && b.time < Battle.REGULATION + Battle.OVERTIME + 5f) {
                b.update(1f / 30f)
                if (b.time > next) {
                    val i = rng.nextInt(4)
                    val (x, y) = b.snapPlacement(Team.PLAYER, b.player.hand[i], rng.nextFloat() * 18f, rng.nextFloat() * 32f)
                    b.deploy(Team.PLAYER, i, x, y)
                    next = b.time + 1.5f
                }
                for (e in b.entities) assertFalse("NaN position", e.x.isNaN() || e.y.isNaN())
            }
            assertNotNull("seed $seed never finished", b.outcome)
        }
    }
}
