package com.clashclaude.game.game

import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.TargetType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Simple heuristic opponent that plays the top half of the arena. It defends
 * against incoming troops, punishes clumps with spells, and builds pushes
 * (tank first, support behind) when it has enough elixir.
 */
class AiController(private val battle: Battle, private val side: Side, private val rng: Random) {
    private val team = side.team
    private val foe = team.opponent

    private var thinkTimer = 2.5f
    private var pushThreshold = 8f

    private var supportLane: Float? = null
    private var supportDelay = 0f

    fun update(dt: Float) {
        supportDelay -= dt
        thinkTimer -= dt
        if (thinkTimer > 0f) return
        thinkTimer = 0.35f + rng.nextFloat() * 0.55f
        think()
    }

    private fun think() {
        val threats = battle.entities.filter { it.team == foe && it.kind == Kind.TROOP && it.y < 21f }
        if (threats.isNotEmpty() && trySpell(threats, minRatio = 1.2f)) return
        if (tryFinisher()) return
        if (threats.isNotEmpty() && defend(threats)) return
        if (trySupport()) return
        if (threats.isEmpty()) tryPush()
    }

    private fun affordable(): List<IndexedValue<CardDef>> =
        side.hand.withIndex().filter { it.value.cost <= side.elixir }

    private fun canHit(card: CardDef, t: Combatant): Boolean = when (card.targets) {
        TargetType.GROUND -> !t.flying
        TargetType.ANY -> true
        TargetType.BUILDINGS -> t.isBuilding
    }

    // ------------------------------------------------------------------ spells

    private fun trySpell(units: List<Combatant>, minRatio: Float): Boolean {
        val all = battle.entities.filter { it.team == foe && it.kind == Kind.TROOP }
        for ((index, card) in affordable()) {
            if (card.type != CardType.SPELL) continue
            var bestValue = 0f
            var bx = 0f
            var by = 0f
            for (center in units) {
                // Lead moving troops a little so slow spells still land.
                val lead = if (card.spellSpeed > 0f && !center.deploying) center.speed * 0.6f else 0f
                val cx = center.x
                val cy = center.y - lead
                var v = 0f
                for (e in all) {
                    if (hypot(e.x - cx, e.y - cy) - e.radius > card.spellRadius) continue
                    v += if (e.hp <= card.damage) e.value else e.value * 0.3f
                }
                if (v > bestValue) {
                    bestValue = v; bx = cx; by = cy
                }
            }
            if (bestValue >= card.cost * minRatio && battle.deploy(team, index, bx, by)) return true
        }
        return false
    }

    private fun tryFinisher(): Boolean {
        for ((index, card) in affordable()) {
            if (card.type != CardType.SPELL) continue
            val towerDamage = card.damage * card.towerDamagePct
            val tower = battle.towers(foe).firstOrNull { it.hp <= towerDamage } ?: continue
            if (battle.deploy(team, index, tower.x, tower.y)) return true
        }
        // Don't sit on full elixir with a hand full of spells.
        if (side.elixir >= Battle.MAX_ELIXIR - 0.1f) {
            val spells = affordable().filter { it.value.type == CardType.SPELL }
            if (spells.size == side.hand.size) {
                val tower = battle.towers(foe).minByOrNull { it.hp } ?: return false
                return battle.deploy(team, spells.first().index, tower.x, tower.y)
            }
        }
        return false
    }

    // ----------------------------------------------------------------- defense

    private fun defend(threats: List<Combatant>): Boolean {
        val main = threats.minBy { it.y }
        val group = threats.filter { hypot(it.x - main.x, it.y - main.y) < 5f }
        val threatValue = group.sumOf { it.value.toDouble() }.toFloat()
        val defenders = battle.entities.filter {
            it.team == team && it.kind != Kind.KING_TOWER && it.kind != Kind.PRINCESS_TOWER &&
                hypot(it.x - main.x, it.y - main.y) < 7f
        }
        val defenseValue = defenders.sumOf { it.value.toDouble() }.toFloat()
        if (defenseValue >= threatValue * 1.1f) return false

        val anyFlying = group.any { it.flying }
        var best: IndexedValue<CardDef>? = null
        var bestScore = 0f
        for (iv in affordable()) {
            val card = iv.value
            if (card.type == CardType.SPELL) continue
            if (!canHit(card, main)) continue
            var score = 10f
            if (main.hp > 1500f) score += card.dps * card.count / 60f
            if (group.size >= 3 && card.splash > 0f) score += 6f
            if (group.size >= 3 && card.count >= 3) score += 2f
            if (main.targets == TargetType.BUILDINGS && card.type == CardType.BUILDING) score += 4f
            if (anyFlying && card.targets == TargetType.ANY) score += 3f
            score -= abs(card.cost - threatValue) * 0.8f
            score += rng.nextFloat() * 2f
            if (score > bestScore) {
                bestScore = score; best = iv
            }
        }
        val pick = best ?: return false
        val card = pick.value
        val lane = Arena.laneX(main.x)

        val (px, py) = when {
            card.type == CardType.BUILDING -> Arena.WIDTH / 2f to 10f
            main.y > Arena.RIVER_TOP -> lane to if (card.isRanged) 10f else 13.5f
            card.isRanged -> main.x to main.y - 5f
            else -> main.x to main.y - 2f
        }
        return deployNear(pick.index, card, px, py)
    }

    // ----------------------------------------------------------------- offense

    private fun chooseLane(): Float {
        val towers = battle.towers(foe).filter { it.kind == Kind.PRINCESS_TOWER }
        if (towers.isEmpty()) return Arena.BRIDGES[rng.nextInt(2)]
        val weakest = towers.minBy { it.hp }
        return if (rng.nextFloat() < 0.75f) Arena.laneX(weakest.x) else Arena.BRIDGES[rng.nextInt(2)]
    }

    private fun tryPush(): Boolean {
        if (side.elixir < pushThreshold) return false
        val lane = chooseLane()
        val troops = affordable().filter { it.value.type == CardType.TROOP }
        if (troops.isEmpty()) return false
        val tank = troops.filter { it.value.hp * it.value.count >= 1200 }.maxByOrNull { it.value.hp }
        val pick = tank ?: troops.minBy { it.value.cost }
        val card = pick.value
        val y = when {
            card.speed >= 1.8f && card.targets == TargetType.BUILDINGS -> 13.5f // Hog: straight to the bridge.
            tank != null -> 3.5f // Slow tank: start at the back to build elixir.
            else -> 11f
        }
        if (deployNear(pick.index, card, lane, y)) {
            pushThreshold = 7f + rng.nextInt(4)
            if (tank != null) {
                supportLane = lane
                supportDelay = if (card.speed >= 1.8f) 0.5f else 3f
            }
            return true
        }
        return false
    }

    private fun trySupport(): Boolean {
        val lane = supportLane ?: return false
        if (supportDelay > 0f) return false
        if (supportDelay < -12f) {
            supportLane = null
            return false
        }
        val options = affordable().filter { it.value.type == CardType.TROOP }
        val pick = options.filter { it.value.isRanged || it.value.splash > 0f }.randomOrNull(rng)
            ?: options.randomOrNull(rng)
            ?: return false
        val tank = battle.entities
            .filter { it.team == team && it.kind == Kind.TROOP && abs(Arena.laneX(it.x) - lane) < 1f }
            .maxByOrNull { it.maxHp }
        val (x, y) = if (tank != null && tank.y < Arena.RIVER_TOP - 2.5f) {
            tank.x to tank.y - 2.5f
        } else {
            lane to 12f
        }
        supportLane = null
        return deployNear(pick.index, pick.value, x, y)
    }

    /** Deploys at the closest valid spot to (x, y), searching back toward our side. */
    private fun deployNear(index: Int, card: CardDef, x: Float, y: Float): Boolean {
        var cx = x.coerceIn(0.6f, Arena.WIDTH - 0.6f)
        var cy = y.coerceIn(0.6f, Arena.RIVER_TOP - 0.6f)
        repeat(12) {
            if (battle.canPlace(team, card, cx, cy)) return battle.deploy(team, index, cx, cy)
            cy = (cy - 1f).coerceAtLeast(0.6f)
            if (it % 3 == 2) cx += if (cx < Arena.WIDTH / 2f) 1f else -1f
        }
        return false
    }
}
