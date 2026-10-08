package com.clashclaude.game.game

import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.ProjectileStyle
import com.clashclaude.game.data.TargetType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Team {
    PLAYER, ENEMY;

    val opponent: Team get() = if (this == PLAYER) ENEMY else PLAYER
}

enum class Kind { TROOP, BUILDING, PRINCESS_TOWER, KING_TOWER }

enum class Outcome { WIN, LOSS, DRAW }

/**
 * Arena geometry in tiles. The player defends the bottom half (large y),
 * the enemy defends the top half (small y).
 */
object Arena {
    const val WIDTH = 18f
    const val HEIGHT = 32f
    const val RIVER_TOP = 15f
    const val RIVER_BOTTOM = 17f
    const val RIVER_MID = 16f
    const val BRIDGE_HALF_WIDTH = 1.0f
    val BRIDGES = floatArrayOf(3.5f, 14.5f)

    fun onBridge(x: Float): Boolean = BRIDGES.any { abs(x - it) <= BRIDGE_HALF_WIDTH }
    fun inRiver(y: Float): Boolean = y > RIVER_TOP && y < RIVER_BOTTOM
    fun laneX(x: Float): Float = if (x < WIDTH / 2f) BRIDGES[0] else BRIDGES[1]
}

class Combatant(
    val kind: Kind,
    val team: Team,
    var x: Float,
    var y: Float,
    val card: CardDef?,
    val maxHp: Float,
    val damage: Float,
    val hitSpeed: Float,
    val range: Float,
    val sight: Float,
    val speed: Float,
    val flying: Boolean,
    val targets: TargetType,
    val splash: Float,
    val splashAroundSelf: Boolean,
    val radius: Float,
    val projectile: ProjectileStyle,
    val projectileSpeed: Float,
    val lifetime: Float,
    val jumpsRiver: Boolean,
    val emoji: String,
) {
    val id: Int = nextId++
    var hp: Float = maxHp
    var cooldown = 0f
    var deployTimer = 0f
    var target: Combatant? = null
    var lockedOn = false
    var retargetTimer = 0f
    var stunTimer = 0f
    var active = kind != Kind.KING_TOWER
    var hitFlash = 0f
    var attackAnim = 0f

    val alive: Boolean get() = hp > 0f
    val isBuilding: Boolean get() = kind != Kind.TROOP
    val isTower: Boolean get() = kind == Kind.PRINCESS_TOWER || kind == Kind.KING_TOWER
    val deploying: Boolean get() = deployTimer > 0f

    /** Approximate elixir value of this single body, used by the AI. */
    val value: Float get() = card?.let { it.cost.toFloat() / it.count } ?: 0f

    companion object {
        private var nextId = 1
        const val DEPLOY_TIME = 1f

        fun fromCard(card: CardDef, team: Team, x: Float, y: Float) = Combatant(
            kind = if (card.type == CardType.BUILDING) Kind.BUILDING else Kind.TROOP,
            team = team, x = x, y = y, card = card,
            maxHp = card.hp.toFloat(), damage = card.damage.toFloat(), hitSpeed = card.hitSpeed,
            range = card.range, sight = max(card.sight, card.range), speed = card.speed,
            flying = card.flying, targets = card.targets, splash = card.splash,
            splashAroundSelf = card.splashAroundSelf, radius = card.radius,
            projectile = card.projectile, projectileSpeed = card.projectileSpeed,
            lifetime = card.lifetime, jumpsRiver = card.jumpsRiver, emoji = card.emoji,
        ).apply { deployTimer = DEPLOY_TIME }

        fun tower(king: Boolean, team: Team, x: Float, y: Float) = Combatant(
            kind = if (king) Kind.KING_TOWER else Kind.PRINCESS_TOWER,
            team = team, x = x, y = y, card = null,
            maxHp = if (king) 4000f else 2500f,
            damage = if (king) 110f else 90f,
            hitSpeed = if (king) 1.0f else 0.8f,
            range = if (king) 6.5f else 7f,
            sight = if (king) 6.5f else 7f,
            speed = 0f, flying = false, targets = TargetType.ANY, splash = 0f,
            splashAroundSelf = false, radius = if (king) 2f else 1.5f,
            projectile = ProjectileStyle.ARROW, projectileSpeed = 16f,
            lifetime = 0f, jumpsRiver = false, emoji = if (king) "🤴" else "👸",
        )
    }
}

class Projectile(
    val team: Team,
    var x: Float,
    var y: Float,
    val target: Combatant,
    val damage: Float,
    val splash: Float,
    val hitsAir: Boolean,
    val speed: Float,
    val style: ProjectileStyle,
) {
    var tx = target.x
    var ty = target.y
    var done = false
}

class SpellCast(
    val team: Team,
    val card: CardDef,
    var x: Float,
    var y: Float,
    val tx: Float,
    val ty: Float,
) {
    var done = false
}

enum class EffectKind { RING, FLASH, LINE, PUFF }

class Effect(
    val kind: EffectKind,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Long,
    val duration: Float,
    val x2: Float = 0f,
    val y2: Float = 0f,
) {
    var age = 0f
    val progress: Float get() = (age / duration).coerceIn(0f, 1f)
}

/** Cards, elixir and crowns for one participant. */
class Side(val team: Team, deck: List<CardDef>, rng: Random) {
    var elixir = 5f
    var crowns = 0
    val hand: MutableList<CardDef>
    val queue: ArrayDeque<CardDef>

    init {
        val shuffled = deck.shuffled(rng)
        hand = shuffled.take(4).toMutableList()
        queue = ArrayDeque(shuffled.drop(4))
    }

    val next: CardDef? get() = queue.firstOrNull()

    fun cycle(handIndex: Int): CardDef {
        val played = hand[handIndex]
        val replacement = queue.removeFirstOrNull()
        if (replacement != null) {
            hand[handIndex] = replacement
            queue.addLast(played)
        }
        return played
    }
}

class Battle(playerDeck: List<CardDef>, enemyDeck: List<CardDef>, val rng: Random = Random.Default) {
    val player = Side(Team.PLAYER, playerDeck, rng)
    val enemy = Side(Team.ENEMY, enemyDeck, rng)
    val entities = mutableListOf<Combatant>()
    val projectiles = mutableListOf<Projectile>()
    val spells = mutableListOf<SpellCast>()
    val effects = mutableListOf<Effect>()

    /** Positions of destroyed towers, drawn as rubble. */
    val rubble = mutableListOf<Combatant>()

    var time = 0f
        private set
    var overtime = false
        private set
    var outcome: Outcome? = null
        private set

    val ai = AiController(this, enemy, rng)

    init {
        entities += Combatant.tower(true, Team.PLAYER, 9f, 29.5f)
        entities += Combatant.tower(false, Team.PLAYER, 3.5f, 26f)
        entities += Combatant.tower(false, Team.PLAYER, 14.5f, 26f)
        entities += Combatant.tower(true, Team.ENEMY, 9f, 2.5f)
        entities += Combatant.tower(false, Team.ENEMY, 3.5f, 6f)
        entities += Combatant.tower(false, Team.ENEMY, 14.5f, 6f)
    }

    fun side(team: Team): Side = if (team == Team.PLAYER) player else enemy

    val doubleElixir: Boolean get() = overtime || time >= REGULATION - 60f

    /** Seconds remaining in the current period. */
    val timeLeft: Float
        get() = if (overtime) max(0f, REGULATION + OVERTIME - time) else max(0f, REGULATION - time)

    fun towers(team: Team): List<Combatant> = entities.filter { it.team == team && it.isTower }

    fun princessAlive(team: Team, leftSide: Boolean): Boolean =
        entities.any { it.team == team && it.kind == Kind.PRINCESS_TOWER && (it.x < Arena.WIDTH / 2f) == leftSide }

    // ---------------------------------------------------------------- deploying

    fun canPlace(team: Team, card: CardDef, x: Float, y: Float): Boolean {
        if (x < 0.5f || x > Arena.WIDTH - 0.5f || y < 0.5f || y > Arena.HEIGHT - 0.5f) return false
        if (card.type == CardType.SPELL) return true
        if (Arena.inRiver(y)) return false
        if (!inDeployZone(team, x, y)) return false
        // Can't drop things on top of towers.
        return entities.none { it.isTower && hypot(it.x - x, it.y - y) < it.radius }
    }

    /**
     * Snaps a drop point to the closest spot where [card] may be placed: troops dropped
     * on the enemy side or in the river slide back to the edge of the deploy zone, and
     * drops on top of a tower are pushed off it.
     */
    fun snapPlacement(team: Team, card: CardDef, x: Float, y: Float): Pair<Float, Float> {
        var px = x.coerceIn(0.5f, Arena.WIDTH - 0.5f)
        var py = y.coerceIn(0.5f, Arena.HEIGHT - 0.5f)
        if (card.type == CardType.SPELL) return px to py

        val left = px < Arena.WIDTH / 2f
        py = if (team == Team.PLAYER) {
            max(py, if (princessAlive(Team.ENEMY, left)) Arena.RIVER_BOTTOM + 0.5f else 10f)
        } else {
            min(py, if (princessAlive(Team.PLAYER, left)) Arena.RIVER_TOP - 0.5f else 22f)
        }
        if (Arena.inRiver(py)) py = if (py < Arena.RIVER_MID) Arena.RIVER_TOP else Arena.RIVER_BOTTOM
        for (t in entities) {
            if (!t.isTower) continue
            val dx = px - t.x
            val dy = py - t.y
            val d = hypot(dx, dy)
            if (d >= t.radius) continue
            val push = t.radius + 0.05f
            if (d < 0.01f) {
                py = t.y + if (team == Team.PLAYER) -push else push
            } else {
                px = t.x + dx / d * push
                py = t.y + dy / d * push
            }
        }
        return px.coerceIn(0.5f, Arena.WIDTH - 0.5f) to py.coerceIn(0.5f, Arena.HEIGHT - 0.5f)
    }

    fun inDeployZone(team: Team, x: Float, y: Float): Boolean {
        val left = x < Arena.WIDTH / 2f
        return if (team == Team.PLAYER) {
            y >= Arena.RIVER_BOTTOM + 0.5f || (!princessAlive(Team.ENEMY, left) && y >= 10f)
        } else {
            y <= Arena.RIVER_TOP - 0.5f || (!princessAlive(Team.PLAYER, left) && y <= 22f)
        }
    }

    fun deploy(team: Team, handIndex: Int, x: Float, y: Float): Boolean {
        if (outcome != null) return false
        val side = side(team)
        val card = side.hand.getOrNull(handIndex) ?: return false
        if (side.elixir < card.cost) return false
        if (!canPlace(team, card, x, y)) return false
        side.elixir -= card.cost
        side.cycle(handIndex)

        if (card.type == CardType.SPELL) {
            if (card.spellSpeed <= 0f) {
                spells += SpellCast(team, card, x, y, x, y)
            } else {
                val king = entities.firstOrNull { it.team == team && it.kind == Kind.KING_TOWER }
                val sx = king?.x ?: Arena.WIDTH / 2f
                val sy = king?.y ?: if (team == Team.PLAYER) Arena.HEIGHT else 0f
                spells += SpellCast(team, card, sx, sy, x, y)
            }
            return true
        }

        val n = card.count
        val ring = if (n <= 1) 0f else 0.35f + 0.15f * n
        for (i in 0 until n) {
            val a = (2.0 * PI * i / n + PI / 2).toFloat()
            var ux = (x + ring * cos(a)).coerceIn(0.5f, Arena.WIDTH - 0.5f)
            var uy = (y + ring * sin(a)).coerceIn(0.5f, Arena.HEIGHT - 0.5f)
            if (!card.flying && Arena.inRiver(uy)) {
                uy = if (team == Team.PLAYER) Arena.RIVER_BOTTOM else Arena.RIVER_TOP
            }
            ux = ux.coerceIn(0.5f, Arena.WIDTH - 0.5f)
            entities += Combatant.fromCard(card, team, ux, uy)
        }
        effects += Effect(EffectKind.RING, x, y, 1.2f, teamColor(team), 0.5f)
        return true
    }

    // ------------------------------------------------------------------ update

    fun update(dt: Float) {
        if (outcome != null) return
        time += dt
        val regen = dt / ELIXIR_SECONDS * if (doubleElixir) 2f else 1f
        player.elixir = min(MAX_ELIXIR, player.elixir + regen)
        enemy.elixir = min(MAX_ELIXIR, enemy.elixir + regen)

        ai.update(dt)

        // Iterate over a snapshot so deploys/kills during the loop are safe.
        for (c in entities.toList()) if (c.alive) updateCombatant(c, dt)
        resolveCollisions()
        updateProjectiles(dt)
        updateSpells(dt)
        updateEffects(dt)
        removeDead()
        updateClock()
    }

    private fun updateClock() {
        if (outcome != null) return
        if (!overtime && time >= REGULATION) {
            if (player.crowns != enemy.crowns) finishByCrowns() else overtime = true
        } else if (overtime) {
            if (player.crowns != enemy.crowns) {
                finishByCrowns()
            } else if (time >= REGULATION + OVERTIME) {
                // Tiebreaker: the side whose weakest tower has less HP loses.
                val p = towers(Team.PLAYER).minOfOrNull { it.hp } ?: 0f
                val e = towers(Team.ENEMY).minOfOrNull { it.hp } ?: 0f
                outcome = when {
                    p > e -> Outcome.WIN
                    p < e -> Outcome.LOSS
                    else -> Outcome.DRAW
                }
            }
        }
    }

    private fun finishByCrowns() {
        outcome = if (player.crowns > enemy.crowns) Outcome.WIN else Outcome.LOSS
    }

    /** Gives up the match (counts as a loss). */
    fun surrender() {
        if (outcome == null) {
            enemy.crowns = 3
            outcome = Outcome.LOSS
        }
    }

    private fun updateCombatant(c: Combatant, dt: Float) {
        c.hitFlash -= dt
        c.attackAnim -= dt
        if (c.deployTimer > 0f) {
            c.deployTimer -= dt
            return
        }
        if (c.kind == Kind.BUILDING && c.lifetime > 0f) c.hp -= c.maxHp / c.lifetime * dt
        if (c.stunTimer > 0f) {
            c.stunTimer -= dt
            return
        }
        if (!c.active || c.damage <= 0f) return
        c.cooldown -= dt

        // Drop targets that are dead, invalid, or (when not locked) out of sight.
        c.target?.let { t ->
            val d = edgeDist(c, t)
            val lost = !t.alive || !canTarget(c, t) ||
                (c.lockedOn && d > c.range + 0.3f) ||
                (!c.lockedOn && d > c.sight + 1f)
            if (lost) {
                c.target = null
                c.lockedOn = false
            }
        }
        c.retargetTimer -= dt
        if (!c.lockedOn && c.retargetTimer <= 0f) {
            c.retargetTimer = 0.25f
            c.target = findTarget(c)
        }

        val t = c.target
        if (t == null) {
            if (c.kind == Kind.TROOP) {
                marchTarget(c)?.let { moveToward(c, it.x, it.y, dt) }
            }
            return
        }
        if (edgeDist(c, t) <= c.range) {
            c.lockedOn = true
            if (c.cooldown <= 0f) {
                attack(c, t)
                c.cooldown = c.hitSpeed
            }
        } else {
            c.lockedOn = false
            // First hit after arriving takes a moment to wind up.
            c.cooldown = max(c.cooldown, c.hitSpeed * 0.35f)
            if (c.kind == Kind.TROOP) moveToward(c, t.x, t.y, dt)
        }
    }

    fun canTarget(c: Combatant, t: Combatant): Boolean {
        if (t.team == c.team || !t.alive) return false
        return when (c.targets) {
            TargetType.GROUND -> !t.flying
            TargetType.ANY -> true
            TargetType.BUILDINGS -> t.isBuilding
        }
    }

    private fun findTarget(c: Combatant): Combatant? {
        var best: Combatant? = null
        var bestD = Float.MAX_VALUE
        for (e in entities) {
            if (!canTarget(c, e)) continue
            val d = edgeDist(c, e)
            if (d <= c.sight && d < bestD) {
                best = e
                bestD = d
            }
        }
        return best
    }

    /** Where a troop walks when nothing is in sight. */
    private fun marchTarget(c: Combatant): Combatant? {
        val candidates = entities.filter {
            it.team != c.team && it.alive &&
                (it.isTower || (c.targets == TargetType.BUILDINGS && it.kind == Kind.BUILDING))
        }
        return candidates.minByOrNull { hypot(it.x - c.x, it.y - c.y) }
    }

    private fun moveToward(c: Combatant, tx: Float, ty: Float, dt: Float) {
        var wx = tx
        var wy = ty
        if (!c.flying && !c.jumpsRiver) {
            val targetTop = ty < Arena.RIVER_MID
            val needsCross = if (targetTop) c.y > Arena.RIVER_TOP else c.y < Arena.RIVER_BOTTOM
            if (needsCross) {
                val bx = Arena.BRIDGES.minByOrNull { abs(c.x - it) + abs(tx - it) * 0.5f }!!
                val entryY = if (targetTop) Arena.RIVER_BOTTOM + 0.3f else Arena.RIVER_TOP - 0.3f
                val exitY = if (targetTop) Arena.RIVER_TOP - 0.4f else Arena.RIVER_BOTTOM + 0.4f
                val pastEntry = if (targetTop) c.y <= entryY + 0.05f else c.y >= entryY - 0.05f
                wx = bx
                wy = if (abs(c.x - bx) < 0.6f && pastEntry) exitY else entryY
            }
        }
        val dx = wx - c.x
        val dy = wy - c.y
        val dist = hypot(dx, dy)
        if (dist < 0.001f) return
        val step = min(dist, c.speed * dt)
        c.x += dx / dist * step
        c.y += dy / dist * step
    }

    private fun attack(c: Combatant, t: Combatant) {
        c.attackAnim = 0.2f
        val hitsAir = c.targets != TargetType.GROUND
        when {
            c.projectile == ProjectileStyle.ZAP -> {
                damage(t, c.damage)
                effects += Effect(EffectKind.LINE, c.x, c.y, 0f, 0xFF9FE8FF, 0.15f, t.x, t.y)
            }
            c.projectileSpeed > 0f -> {
                projectiles += Projectile(
                    c.team, c.x, c.y, t, c.damage, c.splash, hitsAir, c.projectileSpeed, c.projectile,
                )
            }
            c.splashAroundSelf -> {
                splashDamage(c.team, c.x, c.y, c.splash + c.radius, c.damage, hitsAir)
                effects += Effect(EffectKind.RING, c.x, c.y, c.splash + c.radius, 0xCCFFFFFF, 0.25f)
            }
            c.splash > 0f -> splashDamage(c.team, t.x, t.y, c.splash, c.damage, hitsAir)
            else -> {
                damage(t, c.damage)
                effects += Effect(EffectKind.FLASH, t.x, t.y, 0.4f, 0xCCFFFFFF, 0.12f)
            }
        }
    }

    private fun damage(t: Combatant, amount: Float) {
        t.hp -= amount
        t.hitFlash = 0.1f
        if (t.kind == Kind.KING_TOWER) t.active = true
    }

    private fun splashDamage(
        team: Team, x: Float, y: Float, r: Float, amount: Float, hitsAir: Boolean,
        towerPct: Float = 1f, stun: Float = 0f,
    ) {
        for (e in entities) {
            if (e.team == team || !e.alive) continue
            if (e.flying && !hitsAir) continue
            if (hypot(e.x - x, e.y - y) - e.radius > r) continue
            damage(e, if (e.isTower) amount * towerPct else amount)
            if (stun > 0f) {
                e.stunTimer = max(e.stunTimer, stun)
                e.lockedOn = false
                e.target = null
                e.cooldown = max(e.cooldown, e.hitSpeed * 0.35f)
            }
        }
    }

    private fun resolveCollisions() {
        val list = entities
        for (i in list.indices) {
            val a = list[i]
            for (j in i + 1 until list.size) {
                val b = list[j]
                if (a.flying != b.flying) continue
                val aStatic = a.kind != Kind.TROOP
                val bStatic = b.kind != Kind.TROOP
                if (aStatic && bStatic) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx, dy)
                val overlap = a.radius + b.radius - dist
                if (overlap <= 0f) continue
                val nx: Float
                val ny: Float
                if (dist < 0.0001f) {
                    nx = 1f; ny = 0f
                } else {
                    nx = dx / dist; ny = dy / dist
                }
                val ma = a.radius * a.radius
                val mb = b.radius * b.radius
                val shareA = when {
                    aStatic -> 0f
                    bStatic -> 1f
                    else -> mb / (ma + mb)
                }
                val push = overlap * 0.5f
                a.x -= nx * push * shareA * 2f
                a.y -= ny * push * shareA * 2f
                b.x += nx * push * (1f - shareA) * 2f
                b.y += ny * push * (1f - shareA) * 2f
            }
        }
        for (c in list) {
            if (c.kind != Kind.TROOP) continue
            c.x = c.x.coerceIn(0.4f, Arena.WIDTH - 0.4f)
            c.y = c.y.coerceIn(0.4f, Arena.HEIGHT - 0.4f)
            if (!c.flying && !c.jumpsRiver && Arena.inRiver(c.y) && !Arena.onBridge(c.x)) {
                // Pushed off a bridge: step back onto the nearest bank, or the bridge.
                val bx = Arena.BRIDGES.minByOrNull { abs(it - c.x) }!!
                if (abs(bx - c.x) < Arena.BRIDGE_HALF_WIDTH + 0.4f) {
                    c.x = if (c.x < bx) bx - Arena.BRIDGE_HALF_WIDTH else bx + Arena.BRIDGE_HALF_WIDTH
                } else {
                    c.y = if (c.y < Arena.RIVER_MID) Arena.RIVER_TOP else Arena.RIVER_BOTTOM
                }
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        for (p in projectiles) {
            if (p.target.alive) {
                p.tx = p.target.x
                p.ty = p.target.y
            }
            val dx = p.tx - p.x
            val dy = p.ty - p.y
            val dist = hypot(dx, dy)
            val step = p.speed * dt
            if (dist <= step + 0.05f) {
                p.x = p.tx
                p.y = p.ty
                p.done = true
                if (p.splash > 0f) {
                    splashDamage(p.team, p.tx, p.ty, p.splash, p.damage, p.hitsAir)
                    val color = if (p.style == ProjectileStyle.BOMB) 0xCCFFD54F else 0xCCFF7043
                    effects += Effect(EffectKind.RING, p.tx, p.ty, p.splash, color, 0.3f)
                } else if (p.target.alive) {
                    damage(p.target, p.damage)
                }
            } else {
                p.x += dx / dist * step
                p.y += dy / dist * step
            }
        }
        projectiles.removeAll { it.done }
    }

    private fun updateSpells(dt: Float) {
        for (s in spells) {
            val dx = s.tx - s.x
            val dy = s.ty - s.y
            val dist = hypot(dx, dy)
            val step = s.card.spellSpeed * dt
            if (s.card.spellSpeed <= 0f || dist <= step) {
                s.x = s.tx
                s.y = s.ty
                s.done = true
                splashDamage(
                    s.team, s.tx, s.ty, s.card.spellRadius, s.card.damage.toFloat(), hitsAir = true,
                    towerPct = s.card.towerDamagePct, stun = s.card.stun,
                )
                val color = when (s.card.id) {
                    "fireball" -> 0xDDFF6D00
                    "zap" -> 0xDD80D8FF
                    else -> 0xDDFFF59D
                }
                effects += Effect(EffectKind.RING, s.tx, s.ty, s.card.spellRadius, color, 0.45f)
                effects += Effect(EffectKind.FLASH, s.tx, s.ty, s.card.spellRadius, color, 0.25f)
            } else {
                s.x += dx / dist * step
                s.y += dy / dist * step
            }
        }
        spells.removeAll { it.done }
    }

    private fun updateEffects(dt: Float) {
        for (e in effects) e.age += dt
        effects.removeAll { it.age >= it.duration }
    }

    private fun removeDead() {
        val dead = entities.filter { !it.alive }
        if (dead.isEmpty()) return
        for (d in dead) {
            effects += Effect(EffectKind.PUFF, d.x, d.y, d.radius * 1.6f, 0xAAFFFFFF, 0.4f)
            if (!d.isTower) continue
            rubble += d
            val scorer = side(d.team.opponent)
            if (d.kind == Kind.KING_TOWER) {
                scorer.crowns = 3
                for (t in entities) if (t.team == d.team && t.isTower && t.alive) {
                    t.hp = 0f
                    rubble += t
                }
                outcome = if (d.team == Team.ENEMY) Outcome.WIN else Outcome.LOSS
            } else {
                scorer.crowns = min(3, scorer.crowns + 1)
                // Losing a princess tower wakes up the king.
                entities.firstOrNull { it.team == d.team && it.kind == Kind.KING_TOWER }?.active = true
            }
        }
        entities.removeAll { !it.alive }
    }

    companion object {
        const val REGULATION = 180f
        const val OVERTIME = 60f
        const val MAX_ELIXIR = 10f
        const val ELIXIR_SECONDS = 2.8f

        fun edgeDist(a: Combatant, b: Combatant): Float =
            hypot(a.x - b.x, a.y - b.y) - a.radius - b.radius

        fun teamColor(team: Team): Long = if (team == Team.PLAYER) 0xFF3FA7FF else 0xFFFF4B4B
    }
}
