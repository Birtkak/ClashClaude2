package com.clashclaude.game.data

import kotlin.random.Random

/**
 * Builds a random but sensible deck for the "Magic" button: one win condition, one or two
 * spells, anti-air, at most one building, some cheap cycle cards and a playable elixir average.
 */
object DeckBuilder {
    const val MIN_AVG = 2.9
    const val MAX_AVG = 4.2

    private val all get() = Cards.all
    private val winConditions get() = all.filter { it.type == CardType.TROOP && (it.targets == TargetType.BUILDINGS || it.id == "pekka") }
    private val smallSpells get() = all.filter { it.type == CardType.SPELL && it.cost <= 4 && it.id != "fireball" }
    private val bigSpells get() = all.filter { it.type == CardType.SPELL && it !in smallSpells }

    fun random(rng: Random = Random.Default): List<String> {
        repeat(500) {
            val deck = attempt(rng)
            if (deck != null && isSensible(deck)) return deck.map { it.id }
        }
        return Cards.defaultDecks[0]
    }

    private fun attempt(rng: Random): List<CardDef>? {
        val deck = mutableListOf<CardDef>()
        deck += winConditions.random(rng)
        deck += smallSpells.random(rng)
        if (rng.nextFloat() < 0.6f) deck += bigSpells.random(rng)
        if (rng.nextFloat() < 0.6f) deck += all.filter { it.type == CardType.BUILDING }.random(rng)
        // Fill the rest with troops (no second win condition), favouring anti-air until there's enough.
        val troops = all.filter { it.type == CardType.TROOP && it !in winConditions }
        while (deck.size < 8) {
            val needAir = deck.count(::hitsAir) < 2
            val pool = troops.filter { it !in deck && (!needAir || hitsAir(it)) }
            if (pool.isEmpty()) return null
            deck += pool.random(rng)
        }
        return deck.shuffled(rng)
    }

    private fun hitsAir(c: CardDef) = c.type != CardType.SPELL && c.targets == TargetType.ANY

    /** The rules a generated deck must satisfy; also used by tests. */
    fun isSensible(deck: List<CardDef>): Boolean {
        if (deck.size != 8 || deck.distinct().size != 8) return false
        val spells = deck.count { it.type == CardType.SPELL }
        val avg = deck.sumOf { it.cost }.toDouble() / deck.size
        return spells in 1..2 &&
            deck.count { it in winConditions } == 1 &&
            deck.count(::hitsAir) >= 2 &&
            deck.count { it.type == CardType.BUILDING } <= 1 &&
            deck.count { it.cost <= 2 } >= 2 &&
            avg in MIN_AVG..MAX_AVG
    }
}
