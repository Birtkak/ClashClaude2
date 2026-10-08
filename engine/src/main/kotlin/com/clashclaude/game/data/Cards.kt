package com.clashclaude.game.data

enum class CardType { TROOP, SPELL, BUILDING }

/** What a unit is allowed to attack. */
enum class TargetType { GROUND, ANY, BUILDINGS }

enum class Rarity { COMMON, RARE, EPIC }

enum class ProjectileStyle { NONE, ARROW, SPEAR, BULLET, CANNONBALL, FIRE, BOMB, ORB, ZAP, BEAM }

/**
 * Static definition of a card. Distances are in arena tiles, times in seconds,
 * speed in tiles per second. Ranges are measured edge-to-edge between bodies.
 */
data class CardDef(
    val id: String,
    val name: String,
    val emoji: String,
    val cost: Int,
    val type: CardType,
    val rarity: Rarity,
    val description: String,
    val count: Int = 1,
    val hp: Int = 0,
    val damage: Int = 0,
    val hitSpeed: Float = 1f,
    val range: Float = 0.5f,
    val sight: Float = 5.5f,
    val speed: Float = 0f,
    val flying: Boolean = false,
    val targets: TargetType = TargetType.ANY,
    val splash: Float = 0f,
    val splashAroundSelf: Boolean = false,
    val radius: Float = 0.45f,
    val projectile: ProjectileStyle = ProjectileStyle.NONE,
    val projectileSpeed: Float = 0f,
    val lifetime: Float = 0f,
    val jumpsRiver: Boolean = false,
    // Spells
    val spellRadius: Float = 0f,
    val towerDamagePct: Float = 1f,
    val stun: Float = 0f,
    val spellSpeed: Float = 0f,
    /** Spells: seconds enemy troops in the area are frozen solid. */
    val freeze: Float = 0f,
    /** Spells: hit only the N toughest enemies in the area (Lightning) instead of everything. */
    val strikes: Int = 0,
    /** Spawners: card id of the unit spawned, how often, and how many. */
    val spawnId: String? = null,
    val spawnEvery: Float = 0f,
    val spawnCount: Int = 0,
    /** Units spawned when this dies (Tombstone). */
    val deathSpawnCount: Int = 0,
    /** Damage ramps up to (1 + rampDamage)x after 4s on the same target (Inferno Tower). */
    val rampDamage: Float = 0f,
) {
    val dps: Float get() = if (hitSpeed > 0f) damage / hitSpeed else 0f
    val isRanged: Boolean get() = range >= 2f
}

object Cards {
    /**
     * The test set for the 3D sprite pipeline: every card here has a model in
     * tools/baker (see docs/ART_PIPELINE.md). New cards need a model too.
     */
    val all: List<CardDef> = listOf(
        CardDef(
            "knight", "Knight", "🤺", 3, CardType.TROOP, Rarity.COMMON,
            "A tough melee fighter with a mustache. Great value tank for defense.",
            hp = 1450, damage = 170, hitSpeed = 1.2f, range = 0.6f, speed = 1.0f,
            targets = TargetType.GROUND,
        ),
        CardDef(
            "archers", "Archers", "🧝", 3, CardType.TROOP, Rarity.COMMON,
            "A pair of sharpshooters that can hit air and ground.",
            count = 2, hp = 300, damage = 105, hitSpeed = 0.9f, range = 5f, speed = 1.0f,
            projectile = ProjectileStyle.ARROW, projectileSpeed = 14f,
        ),
        CardDef(
            "giant", "Giant", "🗿", 5, CardType.TROOP, Rarity.RARE,
            "Slow but extremely durable. Only targets buildings.",
            hp = 3600, damage = 220, hitSpeed = 1.5f, range = 0.6f, sight = 7f, speed = 0.75f,
            targets = TargetType.BUILDINGS, radius = 0.7f,
        ),
        CardDef(
            "skeletons", "Skeletons", "💀", 1, CardType.TROOP, Rarity.COMMON,
            "Three cheap bony distractions.",
            count = 3, hp = 80, damage = 80, hitSpeed = 1.0f, range = 0.5f, speed = 1.5f,
            targets = TargetType.GROUND, radius = 0.33f,
        ),
        CardDef(
            "wizard", "Wizard", "🧙", 5, CardType.TROOP, Rarity.RARE,
            "Shoots fireballs that splash air and ground.",
            hp = 640, damage = 235, hitSpeed = 1.4f, range = 5.5f, speed = 1.0f,
            splash = 1.5f, projectile = ProjectileStyle.FIRE, projectileSpeed = 12f,
        ),
        CardDef(
            "minions", "Minions", "🦇", 3, CardType.TROOP, Rarity.COMMON,
            "Three fast flying blasters.",
            count = 3, hp = 190, damage = 85, hitSpeed = 1.0f, range = 2f, speed = 1.5f,
            flying = true, radius = 0.38f, projectile = ProjectileStyle.ORB, projectileSpeed = 14f,
        ),
        CardDef(
            "fireball", "Fireball", "🔥", 4, CardType.SPELL, Rarity.RARE,
            "Big area damage. Deals reduced damage to towers.",
            damage = 600, spellRadius = 2.5f, towerDamagePct = 0.35f, spellSpeed = 11f,
        ),
        CardDef(
            "zap", "Zap", "⚡", 2, CardType.SPELL, Rarity.COMMON,
            "Instant small-area damage that briefly stuns.",
            damage = 170, spellRadius = 2.5f, towerDamagePct = 0.35f, stun = 0.6f,
        ),
        CardDef(
            "freeze", "Freeze", "❄️", 4, CardType.SPELL, Rarity.RARE,
            "Freezes everything in the area for 4 seconds, towers and buildings included, with a little damage.",
            damage = 90, spellRadius = 3f, towerDamagePct = 0.35f, freeze = 4f,
        ),
        CardDef(
            "cannon", "Cannon", "💣", 3, CardType.BUILDING, Rarity.COMMON,
            "Defensive building that shoots ground units. Decays over time.",
            hp = 900, damage = 180, hitSpeed = 0.9f, range = 5.5f, sight = 5.5f,
            targets = TargetType.GROUND, radius = 0.9f, lifetime = 30f,
            projectile = ProjectileStyle.CANNONBALL, projectileSpeed = 14f,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): CardDef? = byId[id]

    val defaultDecks: List<List<String>> = listOf(
        listOf("knight", "archers", "giant", "wizard", "skeletons", "minions", "fireball", "zap"),
        listOf("knight", "archers", "giant", "minions", "skeletons", "cannon", "freeze", "zap"),
        listOf("giant", "wizard", "skeletons", "minions", "cannon", "fireball", "zap", "archers"),
    )

    /** Decks the AI picks from at random. */
    val aiDecks: List<List<String>> = listOf(
        listOf("giant", "knight", "archers", "minions", "skeletons", "fireball", "zap", "wizard"),
        listOf("giant", "knight", "wizard", "skeletons", "cannon", "freeze", "zap", "minions"),
        listOf("giant", "archers", "skeletons", "minions", "cannon", "fireball", "zap", "knight"),
    )
}
