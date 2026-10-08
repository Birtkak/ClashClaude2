package com.clashclaude.game.game

import com.clashclaude.game.data.Cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The engine guarantees a server-authoritative multiplayer setup relies on. */
class MultiplayerTest {
    private fun deck(i: Int) = Cards.defaultDecks[i].map { Cards.get(it)!! }

    /** Two humans: both sides play a scripted card every few seconds. Returns checksums per second. */
    private fun scriptedMatch(seed: Int, withAi: Boolean, seconds: Int = 90): List<Long> {
        val b = Battle(deck(0), deck(1), Random(seed), withAi = withAi)
        val sums = ArrayList<Long>()
        repeat(seconds * 30) { tick ->
            if (tick % 90 == 0) {
                b.submit(Command.PlayCard(Team.PLAYER, b.player.hand[0].id, 4.5f, 24.5f))
                b.submit(Command.PlayCard(Team.ENEMY, b.enemy.hand[0].id, 13.5f, 7.5f))
            }
            b.step()
            if (tick % 30 == 0) sums += b.checksum()
        }
        return sums
    }

    @Test
    fun sameSeedAndCommandsGiveTheSameMatch() {
        assertEquals(scriptedMatch(3, withAi = false), scriptedMatch(3, withAi = false))
        assertEquals(scriptedMatch(4, withAi = true), scriptedMatch(4, withAi = true))
    }

    @Test
    fun differentSeedsDiverge() {
        assertNotEquals(scriptedMatch(3, withAi = false).last(), scriptedMatch(5, withAi = false).last())
    }

    @Test
    fun commandsApplyOnTheNextStepAndOnlyFromHand() {
        val b = Battle(deck(0), deck(1), Random(1), withAi = false)
        b.player.elixir = 10f
        val card = b.player.hand.first { it.type != com.clashclaude.game.data.CardType.SPELL }
        val before = b.entities.size
        b.submit(Command.PlayCard(Team.PLAYER, card.id, 9.5f, 24.5f))
        assertEquals("nothing happens until the tick", before, b.entities.size)
        b.step()
        assertEquals(before + card.count, b.entities.size)

        // A card that isn't in hand (e.g. a forged message) is ignored.
        val notInHand = Cards.all.first { c -> b.player.hand.none { it.id == c.id } }
        assertFalse(b.apply(Command.PlayCard(Team.PLAYER, notInHand.id, 9.5f, 24.5f)))
    }

    @Test
    fun unitIdsArePerBattle() {
        val a = Battle(deck(0), deck(1), Random(1))
        val b = Battle(deck(0), deck(1), Random(1))
        assertEquals(a.entities.map { it.id }, b.entities.map { it.id })
        assertEquals((1..6).toList(), a.entities.map { it.id })
    }

    @Test
    fun withoutAiTheEnemyNeverPlays() {
        val b = Battle(deck(0), deck(1), Random(1), withAi = false)
        repeat(30 * 30) { b.step() }
        assertTrue(b.entities.none { it.team == Team.ENEMY && !it.isTower })
        assertNull(b.ai)
    }

    @Test
    fun surrenderAndOutcomeAreSeenPerTeam() {
        val b = Battle(deck(0), deck(1), Random(1), withAi = false)
        b.submit(Command.Surrender(Team.ENEMY))
        b.step()
        assertEquals(Outcome.WIN, b.outcomeFor(Team.PLAYER))
        assertEquals(Outcome.LOSS, b.outcomeFor(Team.ENEMY))
        assertEquals(3, b.player.crowns)
    }

    @Test
    fun localMatchRunsFixedTicks() {
        val m = LocalMatch(Battle(deck(0), deck(1), Random(1)))
        var alpha = 0f
        repeat(60) { alpha = m.advance(1f / 60f) }
        assertTrue(m.battle.tick in 29L..30L)
        assertTrue(alpha in 0f..1f)
    }
}

class ViewTest {
    @Test
    fun headingsMapToBakedRows() {
        val pi = Math.PI.toFloat()
        assertEquals(0 to false, View.direction(pi / 2)) // facing the camera
        assertEquals(4 to false, View.direction(0f)) // facing right
        assertEquals(8 to false, View.direction(-pi / 2)) // facing away
        assertEquals(4 to true, View.direction(pi)) // facing left = mirrored right
        assertEquals(2 to true, View.direction(3 * pi / 4)) // down-left = mirrored down-right
        for (row in 0 until View.DIRECTIONS) assertEquals(row to false, View.direction(View.headingOfRow(row)))
    }
}
