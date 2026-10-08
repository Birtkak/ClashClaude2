package com.clashclaude.game.game

import com.clashclaude.game.data.CardDef
import com.clashclaude.game.data.CardType
import com.clashclaude.game.data.Cards
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
) {
    /** Unique within its battle; assigned by [Battle] when the unit enters the arena. */
    var id: Int = 0
        internal set
    var hp: Float = maxHp
    var cooldown = 0f
    var deployTimer = 0f
    var target: Combatant? = null
    var lockedOn = false
    var retargetTimer = 0f
    var stunTimer = 0f
    /** Seconds left frozen by a Freeze spell (drawn icy blue). */
    var frozenTimer = 0f
    /** Spawners: seconds until the next spawn. */
    var spawnTimer = card?.spawnEvery?.times(0.4f) ?: 0f
    /** Inferno: how long the beam has stayed on [rampTargetId]. */
    var rampTime = 0f
    var rampTargetId = -1
    var active = kind != Kind.KING_TOWER
    var hitFlash = 0f

    // Animation state, read by the renderer.
    /** +1 when facing right, -1 when facing left. */
    var faceX = if (team == Team.PLAYER) 1f else -1f
    var moving = false
    /** Distance walked so far, drives the walk cycle. */
    var walkCycle = 0f
    /** Seconds since the last attack landed (large when idle). */
    var sinceAttack = 99f
    /** Point the unit/tower is aiming at, e.g. for turning a cannon or a tower archer. */
    var aimX = x
    var aimY = y

    /** Position at the start of the current tick, so renderers can interpolate between ticks. */
    var prevX = x
    var prevY = y

    // Ground pathing: current A* route toward the goal with this id, replanned periodically.
    var path: List<Pair<Float, Float>>? = null
    var pathIndex = 0
    var pathGoalId = -1
    var pathTimer = 0f

    val alive: Boolean get() = hp > 0f
    val isBuilding: Boolean get() = kind != Kind.TROOP
    val isTower: Boolean get() = kind == Kind.PRINCESS_TOWER || kind == Kind.KING_TOWER
    val deploying: Boolean get() = deployTimer > 0f

    /** Approximate elixir value of this single body, used by the AI. */
    val value: Float get() = card?.let { it.cost.toFloat() / it.count } ?: 0f

    companion object {
        const val DEPLOY_TIME = 1.3f

        fun fromCard(card: CardDef, team: Team, x: Float, y: Float) = Combatant(
            kind = if (card.type == CardType.BUILDING) Kind.BUILDING else Kind.TROOP,
            team = team, x = x, y = y, card = card,
            maxHp = card.hp.toFloat(), damage = card.damage.toFloat(), hitSpeed = card.hitSpeed,
            range = card.range, sight = max(card.sight, card.range), speed = card.speed,
            flying = card.flying, targets = card.targets, splash = card.splash,
            splashAroundSelf = card.splashAroundSelf, radius = card.radius,
            projectile = card.projectile, projectileSpeed = card.projectileSpeed,
            lifetime = card.lifetime, jumpsRiver = card.jumpsRiver,
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
            projectile = if (king) ProjectileStyle.CANNONBALL else ProjectileStyle.ARROW,
            projectileSpeed = if (king) 13f else 16f,
            lifetime = 0f, jumpsRiver = false,
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
    /** Height above the ground (tiles) it was fired from, e.g. the top of a tower. */
    val launchHeight: Float = 0.5f,
) {
    val startX = x
    val startY = y
    var tx = target.x
    var ty = target.y
    var done = false
    var prevX = x
    var prevY = y

    /** 0 at launch, 1 on impact. */
    val progress: Float
        get() {
            val total = hypot(tx - startX, ty - startY)
            return if (total < 0.01f) 1f else (1f - hypot(tx - x, ty - y) / total).coerceIn(0f, 1f)
        }
}

class SpellCast(
    val team: Team,
    val card: CardDef,
    var x: Float,
    var y: Float,
    val tx: Float,
    val ty: Float,
) {
    val startX = x
    val startY = y
    var done = false

    /** 0 at cast, 1 on impact. */
    val progress: Float
        get() {
            val total = hypot(tx - startX, ty - startY)
            return if (total < 0.01f) 1f else (1f - hypot(tx - x, ty - y) / total).coerceIn(0f, 1f)
        }
}

enum class EffectKind {
    /** Expanding outline, e.g. a deploy marker or Valkyrie's spin. */
    RING,
    /** Filled flash that fades. */
    FLASH,
    /** Lightning bolt from (x, y) to (x2, y2). */
    LINE,
    /** Death puff. */
    PUFF,
    /** Small impact spark where a projectile hit. */
    SPARK,
    /** Splash damage area: filled blast plus ring, sized to the real radius. */
    EXPLOSION,
    /** Melee weapon slash on the target. */
    SLASH,
    /** Freeze spell zone: an icy area with a giant AC unit blowing cold air, for the whole freeze. */
    FREEZE,
}

class Effect(
    val kind: EffectKind,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Long,
    val duration: Float,
    val x2: Float = 0f,
    val y2: Float = 0f,
    /** Height above the ground (tiles) of the effect's start point, e.g. the top of a Tesla. */
    val lift: Float = 0f,
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

/**
 * The whole match simulation. It is deterministic for a given [rng] seed and sequence of
 * [Command]s when advanced with [step], so a server can run it authoritatively and clients
 * only need to render it.
 *
 * @param withAi when true the [Team.ENEMY] side is played by [AiController]; pass false
 *   when both sides are people (multiplayer).
 */
class Battle(
    playerDeck: List<CardDef>,
    enemyDeck: List<CardDef>,
    val rng: Random = Random.Default,
    withAi: Boolean = true,
) {
    val player = Side(Team.PLAYER, playerDeck, rng)
    val enemy = Side(Team.ENEMY, enemyDeck, rng)
    val entities = mutableListOf<Combatant>()
    val projectiles = mutableListOf<Projectile>()
    val spells = mutableListOf<SpellCast>()
    val effects = mutableListOf<Effect>()

    private val pendingSounds = ArrayList<Sfx>()

    /** Returns and clears the sounds triggered since the last call. */
    fun drainSounds(): List<Sfx> {
        val out = pendingSounds.toList()
        pendingSounds.clear()
        return out
    }

    private fun sound(sfx: Sfx) {
        // Bounded so a headless simulation that never drains doesn't grow forever.
        if (pendingSounds.size >= 64) pendingSounds.removeAt(0)
        pendingSounds += sfx
    }

    /** Positions of destroyed towers, drawn as rubble. */
    val rubble = mutableListOf<Combatant>()

    var time = 0f
        private set
    var overtime = false
        private set
    var outcome: Outcome? = null
        private set

    val ai: AiController? = if (withAi) AiController(this, enemy, rng) else null
    private val pathfinder = Pathfinder(entities)

    /** Number of fixed [step]s simulated so far. */
    var tick = 0L
        private set

    private var lastId = 0
    private val commands = ArrayList<Command>()

    init {
        add(Combatant.tower(true, Team.PLAYER, 9f, 29.5f))
        add(Combatant.tower(false, Team.PLAYER, 3.5f, 26f))
        add(Combatant.tower(false, Team.PLAYER, 14.5f, 26f))
        add(Combatant.tower(true, Team.ENEMY, 9f, 2.5f))
        add(Combatant.tower(false, Team.ENEMY, 3.5f, 6f))
        add(Combatant.tower(false, Team.ENEMY, 14.5f, 6f))
    }

    /** Puts a unit into the arena with the next id of this battle. */
    private fun add(c: Combatant): Combatant {
        c.id = ++lastId
        entities += c
        return c
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
        px = px.coerceIn(0.5f, Arena.WIDTH - 0.5f)
        py = py.coerceIn(0.5f, Arena.HEIGHT - 0.5f)
        // Snap to the tile grid: troops to a tile's center, 2x2 buildings to a tile corner.
        val (gx, gy) = if (card.type == CardType.BUILDING) {
            Math.round(px).toFloat().coerceIn(1f, Arena.WIDTH - 1f) to Math.round(py).toFloat()
        } else {
            (kotlin.math.floor(px) + 0.5f) to (kotlin.math.floor(py) + 0.5f)
        }
        return if (canPlace(team, card, gx, gy)) gx to gy else px to py
    }

    /** Where each unit of [card] spawns when dropped at (x, y). */
    fun formation(team: Team, card: CardDef, x: Float, y: Float): List<Pair<Float, Float>> {
        val n = card.count
        val ring = if (n <= 1) 0f else 0.35f + 0.15f * n
        return List(n) { i ->
            val a = (2.0 * PI * i / n + PI / 2).toFloat()
            val ux = (x + ring * cos(a)).coerceIn(0.5f, Arena.WIDTH - 0.5f)
            var uy = (y + ring * sin(a)).coerceIn(0.5f, Arena.HEIGHT - 0.5f)
            if (!card.flying && Arena.inRiver(uy)) {
                uy = if (team == Team.PLAYER) Arena.RIVER_BOTTOM else Arena.RIVER_TOP
            }
            ux to uy
        }
    }

    /** Seconds until [side] can afford [card], or 0 if it already can. */
    fun secondsUntilAffordable(side: Side, card: CardDef): Float {
        val missing = card.cost - side.elixir
        if (missing <= 0f) return 0f
        return missing * ELIXIR_SECONDS / if (doubleElixir) 2f else 1f
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
            when (card.id) {
                "fireball" -> sound(Sfx.FIRE)
                "arrows" -> sound(Sfx.VOLLEY)
            }
            return true
        }

        for ((ux, uy) in formation(team, card, x, y)) {
            add(Combatant.fromCard(card, team, ux, uy).apply { faceX = if (x < Arena.WIDTH / 2f) 1f else -1f })
        }
        effects += Effect(EffectKind.RING, x, y, 1.2f, teamColor(team), 0.5f)
        sound(Sfx.DEPLOY)
        return true
    }

    // ------------------------------------------------------------------ update

    /** Queues a player's action; it takes effect at the start of the next [step]. */
    fun submit(command: Command) {
        commands += command
    }

    /**
     * Advances the match by exactly one fixed tick of [TICK_SECONDS], after applying the
     * queued commands in the order they were submitted. This is the only way a networked
     * match should move forward, so every machine runs the same simulation.
     */
    fun step() {
        val queued = commands.toList()
        commands.clear()
        for (cmd in queued) apply(cmd)
        update(TICK_SECONDS)
        tick++
    }

    /** Applies one command now. Returns false if it was rejected (e.g. not enough elixir). */
    fun apply(command: Command): Boolean = when (command) {
        is Command.PlayCard -> {
            val index = side(command.team).hand.indexOfFirst { it.id == command.cardId }
            index >= 0 && deploy(command.team, index, command.x, command.y)
        }
        is Command.Surrender -> {
            surrender(command.team)
            true
        }
    }

    /**
     * Advances the simulation by [dt] seconds. Tests and tools may call it directly; real
     * matches go through [step] so the tick length is always the same.
     */
    fun update(dt: Float) {
        if (outcome != null) return
        for (c in entities) {
            c.prevX = c.x
            c.prevY = c.y
        }
        for (p in projectiles) {
            p.prevX = p.x
            p.prevY = p.y
        }
        time += dt
        val regen = dt / ELIXIR_SECONDS * if (doubleElixir) 2f else 1f
        player.elixir = min(MAX_ELIXIR, player.elixir + regen)
        enemy.elixir = min(MAX_ELIXIR, enemy.elixir + regen)

        ai?.update(dt)

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

    /** [team] gives up the match: the other side gets 3 crowns and the win. */
    fun surrender(team: Team = Team.PLAYER) {
        if (outcome != null) return
        side(team.opponent).crowns = 3
        outcome = if (team == Team.PLAYER) Outcome.LOSS else Outcome.WIN
    }

    /** The result as seen by [team] ([outcome] is always from [Team.PLAYER]'s point of view). */
    fun outcomeFor(team: Team): Outcome? = when {
        team == Team.PLAYER -> outcome
        outcome == Outcome.WIN -> Outcome.LOSS
        outcome == Outcome.LOSS -> Outcome.WIN
        else -> outcome
    }

    /**
     * Hash of the game state that matters for the result. Two machines running the same
     * match should produce the same value at the same [tick]; a mismatch means a desync.
     */
    fun checksum(): Long {
        var h = 1125899906842597L
        fun mix(v: Long) { h = 31 * h + v }
        fun mix(f: Float) = mix(f.toRawBits().toLong())
        mix(tick)
        for (s in listOf(player, enemy)) {
            mix(s.elixir)
            mix(s.crowns.toLong())
            for (c in s.hand) mix(c.id.hashCode().toLong())
        }
        for (c in entities) {
            mix(c.id.toLong())
            mix(c.x)
            mix(c.y)
            mix(c.hp)
        }
        for (p in projectiles) {
            mix(p.x)
            mix(p.y)
        }
        mix(spells.size.toLong())
        return h
    }

    private fun updateCombatant(c: Combatant, dt: Float) {
        c.hitFlash -= dt
        c.sinceAttack += dt
        c.moving = false
        if (c.deployTimer > 0f) {
            // Still dropping in from above; it lands with a puff of dust.
            c.deployTimer -= dt
            if (c.deployTimer <= 0f) {
                effects += Effect(EffectKind.PUFF, c.x, c.y + c.radius * 0.4f, c.radius * 1.5f, 0xAAD7CCC8, 0.4f)
                sound(if (c.maxHp >= 2500f || c.kind == Kind.BUILDING) Sfx.LAND_HEAVY else Sfx.LAND)
            }
            return
        }
        if (c.kind == Kind.BUILDING && c.lifetime > 0f) c.hp -= c.maxHp / c.lifetime * dt
        if (c.frozenTimer > 0f) c.frozenTimer -= dt
        if (c.stunTimer > 0f) {
            c.stunTimer -= dt
            return
        }
        c.rampTime += dt
        c.card?.let { card ->
            if (card.spawnEvery > 0f && card.spawnId != null) {
                c.spawnTimer -= dt
                if (c.spawnTimer <= 0f) {
                    c.spawnTimer = card.spawnEvery
                    spawnAround(c, card.spawnId, card.spawnCount)
                }
            }
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
                marchTarget(c)?.let { moveToward(c, it, dt) }
            }
            return
        }
        c.aimX = t.x
        c.aimY = t.y
        if (edgeDist(c, t) <= c.range) {
            if (abs(t.x - c.x) > 0.05f) c.faceX = if (t.x > c.x) 1f else -1f
            c.lockedOn = true
            if (c.cooldown <= 0f) {
                attack(c, t)
                c.cooldown = c.hitSpeed
            }
        } else {
            c.lockedOn = false
            // First hit after arriving takes a moment to wind up.
            c.cooldown = max(c.cooldown, c.hitSpeed * 0.35f)
            if (c.kind == Kind.TROOP) moveToward(c, t, dt)
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

    private fun moveToward(c: Combatant, goal: Combatant, dt: Float) {
        var wx = goal.x
        var wy = goal.y
        if (!c.flying) {
            c.pathTimer -= dt
            val stale = c.path == null || c.pathGoalId != goal.id || c.pathTimer <= 0f ||
                c.pathIndex >= (c.path?.size ?: 0)
            if (stale) {
                c.path = pathfinder.findPath(c, goal.x, goal.y, goal)
                c.pathIndex = 0
                c.pathGoalId = goal.id
                c.pathTimer = 0.5f + rng.nextFloat() * 0.3f
            }
            val path = c.path
            if (path != null) {
                while (c.pathIndex < path.lastIndex &&
                    hypot(path[c.pathIndex].first - c.x, path[c.pathIndex].second - c.y) < 0.2f
                ) {
                    c.pathIndex++
                }
                // On the final leg, chase the goal's live position (it may be moving).
                if (c.pathIndex < path.lastIndex) {
                    wx = path[c.pathIndex].first
                    wy = path[c.pathIndex].second
                }
            }
        }
        val dx = wx - c.x
        val dy = wy - c.y
        val dist = hypot(dx, dy)
        if (dist < 0.001f) return
        val step = min(dist, c.speed * dt)
        c.x += dx / dist * step
        c.y += dy / dist * step
        c.moving = true
        c.walkCycle += step
        if (abs(dx) > 0.02f) c.faceX = if (dx > 0f) 1f else -1f
    }

    private fun attack(c: Combatant, t: Combatant) {
        c.sinceAttack = 0f
        sound(
            when {
                c.projectile == ProjectileStyle.ZAP -> Sfx.ZAP
                c.projectile == ProjectileStyle.BEAM -> Sfx.INFERNO
                c.projectile == ProjectileStyle.ARROW || c.projectile == ProjectileStyle.SPEAR -> Sfx.BOW
                c.projectile == ProjectileStyle.BULLET -> Sfx.GUN
                c.projectile == ProjectileStyle.CANNONBALL -> Sfx.CANNON
                c.projectile == ProjectileStyle.FIRE -> Sfx.FIRE
                c.projectile == ProjectileStyle.BOMB -> Sfx.THROW
                c.projectile == ProjectileStyle.ORB -> Sfx.BLIP
                c.splashAroundSelf -> Sfx.SPIN
                c.card?.id == "giant" || c.card?.id == "hogrider" -> Sfx.PUNCH
                else -> Sfx.SWORD
            },
        )
        val hitsAir = c.targets != TargetType.GROUND
        when {
            c.projectile == ProjectileStyle.BEAM -> {
                // Ramps up while the beam stays on the same target; switching resets it.
                if (c.rampTargetId != t.id) {
                    c.rampTargetId = t.id
                    c.rampTime = 0f
                }
                val ramp = min(1f, c.rampTime / 4f)
                damage(t, c.damage * (1f + (c.card?.rampDamage ?: 0f) * ramp))
                effects += Effect(EffectKind.LINE, c.x, c.y, 0.4f + ramp, 0xFFFF6D00, 0.12f, t.x, t.y, lift = 1.4f)
            }
            c.projectile == ProjectileStyle.ZAP -> {
                damage(t, c.damage)
                effects += Effect(EffectKind.LINE, c.x, c.y, 0f, 0xFF9FE8FF, 0.18f, t.x, t.y, lift = 1.5f)
                effects += Effect(EffectKind.SPARK, t.x, t.y, 0.4f, 0xFF9FE8FF, 0.2f)
            }
            c.projectileSpeed > 0f -> {
                val launch = when {
                    c.kind == Kind.PRINCESS_TOWER -> 2.2f
                    c.kind == Kind.KING_TOWER -> 2.0f
                    c.flying -> 1.0f
                    c.kind == Kind.BUILDING -> 0.5f
                    else -> 0.6f
                }
                projectiles += Projectile(
                    c.team, c.x, c.y, t, c.damage, c.splash, hitsAir, c.projectileSpeed, c.projectile, launch,
                )
            }
            c.splashAroundSelf -> {
                splashDamage(c.team, c.x, c.y, c.splash + c.radius, c.damage, hitsAir)
                effects += Effect(EffectKind.RING, c.x, c.y, c.splash + c.radius, 0xDDFFFFFF, 0.3f)
            }
            c.splash > 0f -> {
                splashDamage(c.team, t.x, t.y, c.splash, c.damage, hitsAir)
                effects += Effect(EffectKind.EXPLOSION, t.x, t.y, c.splash, 0xCCFFB74D, 0.35f)
            }
            else -> {
                damage(t, c.damage)
                effects += Effect(EffectKind.SLASH, t.x, t.y, max(0.5f, t.radius), 0xFFFFFFFF, 0.18f, c.x, c.y)
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
                e.rampTime = 0f
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
                // Enemy troops that aren't fighting each other (e.g. two Giants meeting on a
                // bridge) walk past one another instead of deadlocking.
                if (!aStatic && !bStatic && a.team != b.team && a.target !== b && b.target !== a) continue
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
                    sound(Sfx.EXPLOSION)
                    val color = if (p.style == ProjectileStyle.BOMB) 0xCCFFD54F else 0xCCFF7043
                    effects += Effect(EffectKind.EXPLOSION, p.tx, p.ty, p.splash, color, 0.4f)
                } else {
                    if (p.target.alive) damage(p.target, p.damage)
                    sound(Sfx.HIT)
                    effects += Effect(EffectKind.SPARK, p.tx, p.ty, 0.35f, 0xFFFFF59D, 0.2f)
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
                when {
                    s.card.freeze > 0f -> freezeArea(s)
                    s.card.strikes > 0 -> strikeToughest(s)
                    else -> splashDamage(
                        s.team, s.tx, s.ty, s.card.spellRadius, s.card.damage.toFloat(), hitsAir = true,
                        towerPct = s.card.towerDamagePct, stun = s.card.stun,
                    )
                }
                val color = when (s.card.id) {
                    "fireball" -> 0xDDFF6D00
                    "zap", "freeze" -> 0xDD80D8FF
                    "lightning" -> 0xDDFFF176
                    else -> 0xDDFFF59D
                }
                if (s.card.freeze > 0f) {
                    effects += Effect(EffectKind.FREEZE, s.tx, s.ty, s.card.spellRadius, color, s.card.freeze)
                } else if (s.card.strikes == 0) {
                    effects += Effect(EffectKind.EXPLOSION, s.tx, s.ty, s.card.spellRadius, color, 0.5f)
                }
                sound(
                    when (s.card.id) {
                        "fireball" -> Sfx.BIG_EXPLOSION
                        "zap" -> Sfx.ZAP
                        "freeze" -> Sfx.FREEZE
                        "lightning" -> Sfx.THUNDER
                        else -> Sfx.HIT
                    },
                )
                if (s.card.id == "zap") {
                    for (i in -1..1) {
                        val bx = s.tx + i * s.card.spellRadius * 0.5f
                        effects += Effect(EffectKind.LINE, bx, s.ty, 0f, 0xFFB3E5FC, 0.25f, s.tx + i * 0.3f, s.ty, lift = 5f)
                    }
                }
            } else {
                s.x += dx / dist * step
                s.y += dy / dist * step
            }
        }
        spells.removeAll { it.done }
    }

    /** Freeze: enemy troops in the area stop dead (no moving, no attacking) for the duration. */
    private fun freezeArea(s: SpellCast) {
        for (e in entities) {
            if (e.team == s.team || !e.alive || e.kind != Kind.TROOP) continue
            if (hypot(e.x - s.tx, e.y - s.ty) - e.radius > s.card.spellRadius) continue
            e.stunTimer = max(e.stunTimer, s.card.freeze)
            e.frozenTimer = max(e.frozenTimer, s.card.freeze)
            e.rampTime = 0f
            e.lockedOn = false
            e.target = null
        }
    }

    /** Lightning: one bolt each on the [SpellCast.card]'s `strikes` highest-HP enemies in range. */
    private fun strikeToughest(s: SpellCast) {
        val card = s.card
        val victims = entities
            .filter { it.team != s.team && it.alive && hypot(it.x - s.tx, it.y - s.ty) - it.radius <= card.spellRadius }
            .sortedByDescending { it.hp }
            .take(card.strikes)
        for (v in victims) {
            damage(v, if (v.isTower) card.damage * card.towerDamagePct else card.damage.toFloat())
            v.stunTimer = max(v.stunTimer, card.stun)
            v.rampTime = 0f
            effects += Effect(EffectKind.LINE, v.x, v.y, 1.2f, 0xFFFFF59D, 0.35f, v.x, v.y, lift = 7f)
            effects += Effect(EffectKind.EXPLOSION, v.x, v.y, 0.9f, 0xDDFFF176, 0.35f)
        }
    }

    /** Spawns [count] units of card [spawnId] in a ring around [c], on its side of the river. */
    private fun spawnAround(c: Combatant, spawnId: String, count: Int) {
        val card = Cards.get(spawnId) ?: return
        for (i in 0 until count) {
            val a = (2.0 * PI * i / max(1, count) + PI / 2).toFloat()
            val ring = if (count == 1) c.radius + 0.4f else c.radius + 0.5f
            val x = (c.x + ring * cos(a)).coerceIn(0.5f, Arena.WIDTH - 0.5f)
            var y = (c.y + ring * sin(a)).coerceIn(0.5f, Arena.HEIGHT - 0.5f)
            if (Arena.inRiver(y)) y = if (c.y < Arena.RIVER_MID) Arena.RIVER_TOP else Arena.RIVER_BOTTOM
            add(Combatant.fromCard(card, c.team, x, y).apply {
                deployTimer = 0.3f
                faceX = c.faceX
            })
        }
        effects += Effect(EffectKind.PUFF, c.x, c.y, c.radius * 1.4f, 0xAA9E9E9E, 0.35f)
        sound(Sfx.SPAWN)
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
            d.card?.let { card ->
                if (card.deathSpawnCount > 0 && card.spawnId != null) spawnAround(d, card.spawnId, card.deathSpawnCount)
            }
            if (!d.isTower) {
                sound(Sfx.DEATH)
                continue
            }
            sound(Sfx.TOWER_DOWN)
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

        /** Length of one simulation tick ([step]): 30 ticks per second. */
        const val TICK_SECONDS = 1f / 30f

        fun edgeDist(a: Combatant, b: Combatant): Float =
            hypot(a.x - b.x, a.y - b.y) - a.radius - b.radius

        fun teamColor(team: Team): Long = if (team == Team.PLAYER) 0xFF3FA7FF else 0xFFFF4B4B
    }
}
