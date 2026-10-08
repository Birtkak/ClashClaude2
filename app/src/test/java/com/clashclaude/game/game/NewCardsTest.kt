package com.clashclaude.game.game

import com.clashclaude.game.data.Cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Mechanics introduced by Freeze, Tombstone, Inferno Tower and Lightning. */
class NewCardsTest {
    private val deck = Cards.defaultDecks[0].map { Cards.get(it)!! }

    /** A battle where the AI never has elixir, so only what the test places is on the field. */
    private fun quietBattle() = Battle(deck, deck, Random(1)).also { it.enemy.elixir = 0f }

    private fun place(b: Battle, team: Team, id: String, x: Float, y: Float): Combatant {
        val side = b.side(team)
        side.elixir = 10f
        side.hand[0] = Cards.get(id)!!
        assertTrue("deploy $id", b.deploy(team, 0, x, y))
        if (team == Team.ENEMY) side.elixir = 0f
        return b.entities.last()
    }

    private fun run(b: Battle, seconds: Float) = repeat((seconds * 30).toInt()) {
        b.enemy.elixir = 0f
        b.update(1f / 30f)
    }

    @Test
    fun freezeStopsEnemyTroopsForItsDuration() {
        val b = quietBattle()
        val giant = place(b, Team.ENEMY, "giant", 9f, 10f)
        run(b, 1.5f)
        place(b, Team.PLAYER, "freeze", giant.x, giant.y)
        run(b, 0.1f)
        val x0 = giant.x
        val y0 = giant.y
        run(b, 3f)
        assertEquals("a frozen giant shouldn't move", 0f, kotlin.math.hypot(giant.x - x0, giant.y - y0), 0.01f)
        run(b, 1.5f)
        assertTrue("the giant moves again after the freeze", kotlin.math.hypot(giant.x - x0, giant.y - y0) > 0.3f)
    }

    @Test
    fun tombstoneSpawnsSkeletonsAndMoreOnDeath() {
        val b = quietBattle()
        val stone = place(b, Team.PLAYER, "tombstone", 9f, 24f)
        val skeletons = { b.entities.count { it.team == Team.PLAYER && it.card?.id == "skeletons" } }
        run(b, Combatant.DEPLOY_TIME + 3.2f)
        assertTrue("spawned at least one skeleton", skeletons() >= 1)
        val before = skeletons()
        stone.hp = 0f
        run(b, 0.05f)
        assertEquals("four skeletons pop out when it's destroyed", before + 4, skeletons())
    }

    @Test
    fun infernoDamageRampsUpOnTheSameTarget() {
        val b = quietBattle()
        place(b, Team.PLAYER, "infernotower", 9f, 21f)
        val pekka = place(b, Team.ENEMY, "pekka", 9f, 12f)
        run(b, Combatant.DEPLOY_TIME + 0.1f)
        // Pin the P.E.K.K.A inside the beam's range and out of every crown tower's reach.
        pekka.x = 9f
        pekka.y = 17.6f
        pekka.stunTimer = 999f
        pekka.hp = 1_000_000f
        run(b, 0.6f) // beam acquires the target
        val hp1 = pekka.hp
        run(b, 1f)
        val early = hp1 - pekka.hp
        run(b, 3f)
        val hp2 = pekka.hp
        run(b, 1f)
        val late = hp2 - pekka.hp
        assertTrue("late beam ($late/s) should be much hotter than early ($early/s)", late > early * 2f)
    }

    @Test
    fun lightningHitsOnlyTheThreeToughestEnemies() {
        val b = quietBattle()
        for ((i, id) in listOf("giant", "knight", "musketeer", "minipekka").withIndex()) {
            place(b, Team.ENEMY, id, 7.5f + i, 10f)
        }
        run(b, 1.5f)
        val before = b.entities.filter { it.team == Team.ENEMY && it.kind == Kind.TROOP }.associateWith { it.hp }
        place(b, Team.PLAYER, "lightning", 9f, 10f)
        run(b, 0.05f)
        val struck = before.filter { (e, hp) -> e.hp < hp - 400f }.keys.map { it.card!!.id }.toSet()
        assertEquals(setOf("giant", "knight", "minipekka"), struck)
    }
}

class DeckBuilderTest {
    @Test
    fun magicDecksAreAlwaysSensible() {
        val rng = Random(42)
        val seen = mutableSetOf<List<String>>()
        repeat(300) {
            val ids = com.clashclaude.game.data.DeckBuilder.random(rng)
            val cards = ids.map { Cards.get(it)!! }
            assertTrue("not sensible: $ids", com.clashclaude.game.data.DeckBuilder.isSensible(cards))
            seen += ids.sorted()
        }
        assertTrue("decks should vary (got ${seen.size} distinct)", seen.size > 100)
    }
}
