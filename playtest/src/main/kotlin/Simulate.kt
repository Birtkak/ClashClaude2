// Headless balance/sanity check: the real AI plays against a bot that drops random cards.
// Fails loudly if a match produces NaN positions or ground troops standing in the river,
// and reports troops that stop moving for 3s while they have nothing to attack (stuck).

import com.clashclaude.game.data.*
import com.clashclaude.game.game.*
import kotlin.random.Random

fun main() {
    val results = mutableMapOf<Outcome, Int>()
    var totalTime = 0f
    var maxEntities = 0
    var stuckReports = 0
    repeat(40) { seed ->
        val rng = Random(seed)
        val pDeck = Cards.defaultDecks[seed % 3].map { Cards.get(it)!! }
        val eDeck = Cards.aiDecks[seed % 4].map { Cards.get(it)!! }
        val b = Battle(pDeck, eDeck, rng)
        var next = 3f
        var plays = 0
        // id -> (x, y, time) of the last moment this troop was seen making progress.
        val lastProgress = HashMap<Int, Triple<Float, Float, Float>>()
        val reported = HashSet<Int>()
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
            for (e in b.entities) {
                if (e.kind != Kind.TROOP || e.deploying || e.stunTimer > 0f || e.lockedOn) {
                    lastProgress[e.id] = Triple(e.x, e.y, b.time)
                    continue
                }
                val (px, py, pt) = lastProgress.getOrPut(e.id) { Triple(e.x, e.y, b.time) }
                if (kotlin.math.hypot(e.x - px, e.y - py) > 0.3f) {
                    lastProgress[e.id] = Triple(e.x, e.y, b.time)
                } else if (b.time - pt > 3f && reported.add(e.id)) {
                    stuckReports++
                    println("  STUCK seed=$seed t=${"%.0f".format(b.time)} ${e.team} ${e.card?.id} at (${"%.1f".format(e.x)}, ${"%.1f".format(e.y)}) target=${e.target?.let { it.card?.id ?: it.kind.name }}@(${e.target?.x},${e.target?.y}) path=${e.path?.size}/${e.pathIndex} near=" + b.entities.filter { it !== e && kotlin.math.hypot(it.x-e.x,it.y-e.y) < 2.5f }.map { "${it.team.name[0]}:${it.card?.id ?: it.kind.name}@(${"%.1f".format(it.x)},${"%.1f".format(it.y)})" })
                }
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
    println("results (player=random bot): $results avgTime=${totalTime / 40} maxEntities=$maxEntities stuckTroops=$stuckReports")
}
