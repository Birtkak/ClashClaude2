// Headless balance/sanity check: the real AI plays against a bot that drops random cards.
// Fails loudly if a match produces NaN positions or ground troops standing in the river.

import com.clashclaude.game.data.*
import com.clashclaude.game.game.*
import kotlin.random.Random

fun main() {
    val results = mutableMapOf<Outcome, Int>()
    var totalTime = 0f
    var maxEntities = 0
    repeat(40) { seed ->
        val rng = Random(seed)
        val pDeck = Cards.defaultDecks[seed % 3].map { Cards.get(it)!! }
        val eDeck = Cards.aiDecks[seed % 4].map { Cards.get(it)!! }
        val b = Battle(pDeck, eDeck, rng)
        var next = 3f
        var plays = 0
        while (b.outcome == null && b.time < 400f) {
            b.update(1f / 60f)
            maxEntities = maxOf(maxEntities, b.entities.size)
            // naive player bot: random card at a random own-side spot
            if (b.time > next) {
                val i = rng.nextInt(4)
                val c = b.player.hand[i]
                val x = 1f + rng.nextFloat() * 16f
                val y = if (c.type == CardType.SPELL) 4f + rng.nextFloat() * 6f else 18f + rng.nextFloat() * 12f
                if (b.deploy(Team.PLAYER, i, x, y)) plays++
                next = b.time + 1f + rng.nextFloat() * 3f
            }
            // sanity: no ground non-jumper in river off-bridge
            for (e in b.entities) {
                check(!e.x.isNaN() && !e.y.isNaN()) { "NaN pos" }
                if (!e.flying && !e.jumpsRiver && e.kind == Kind.TROOP && Arena.inRiver(e.y) && !Arena.onBridge(e.x)) error("in river ${e.card?.id} ${e.x},${e.y}")
            }
        }
        results.merge(b.outcome ?: Outcome.DRAW, 1, Int::plus)
        totalTime += b.time
        println("seed=$seed outcome=${b.outcome} time=${"%.0f".format(b.time)} crowns P${b.player.crowns}-E${b.enemy.crowns} plays=$plays towersP=${b.towers(Team.PLAYER).map{it.hp.toInt()}} towersE=${b.towers(Team.ENEMY).map{it.hp.toInt()}}")
    }
    println("results (player=random bot): $results avgTime=${totalTime / 40} maxEntities=$maxEntities")
}
