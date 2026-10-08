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
            "musketeer", "Musketeer", "💂", 4, CardType.TROOP, Rarity.RARE,
            "Long-range sniper. Hits air and ground.",
            hp = 640, damage = 190, hitSpeed = 1.0f, range = 6f, speed = 1.0f,
            projectile = ProjectileStyle.BULLET, projectileSpeed = 16f,
        ),
        CardDef(
            "minipekka", "Mini P.E.K.K.A", "🥞", 4, CardType.TROOP, Rarity.RARE,
            "Hits extremely hard and moves fast. Loves pancakes.",
            hp = 1100, damage = 600, hitSpeed = 1.6f, range = 0.6f, speed = 1.5f,
            targets = TargetType.GROUND,
        ),
        CardDef(
            "goblins", "Goblins", "👺", 2, CardType.TROOP, Rarity.COMMON,
            "Three very fast, fragile stabbers.",
            count = 3, hp = 170, damage = 100, hitSpeed = 1.1f, range = 0.5f, speed = 2.0f,
            targets = TargetType.GROUND, radius = 0.35f,
        ),
        CardDef(
            "skeletons", "Skeletons", "💀", 1, CardType.TROOP, Rarity.COMMON,
            "Three cheap bony distractions.",
            count = 3, hp = 80, damage = 80, hitSpeed = 1.0f, range = 0.5f, speed = 1.5f,
            targets = TargetType.GROUND, radius = 0.33f,
        ),
        CardDef(
            "babydragon", "Baby Dragon", "🐉", 4, CardType.TROOP, Rarity.EPIC,
            "Flying tank that breathes splash fireballs.",
            hp = 1000, damage = 135, hitSpeed = 1.5f, range = 3.5f, speed = 1.5f,
            flying = true, splash = 1.5f, radius = 0.55f,
            projectile = ProjectileStyle.FIRE, projectileSpeed = 10f,
        ),
        CardDef(
            "valkyrie", "Valkyrie", "🪓", 4, CardType.TROOP, Rarity.RARE,
            "Spins her axe, damaging every ground unit around her.",
            hp = 1650, damage = 220, hitSpeed = 1.5f, range = 0.7f, speed = 1.0f,
            targets = TargetType.GROUND, splash = 1.6f, splashAroundSelf = true, radius = 0.5f,
        ),
        CardDef(
            "hogrider", "Hog Rider", "🐗", 4, CardType.TROOP, Rarity.RARE,
            "Very fast building-hunter that jumps over the river.",
            hp = 1400, damage = 260, hitSpeed = 1.6f, range = 0.6f, sight = 7f, speed = 2.0f,
            targets = TargetType.BUILDINGS, jumpsRiver = true, radius = 0.55f,
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
            "pekka", "P.E.K.K.A", "🤖", 7, CardType.TROOP, Rarity.EPIC,
            "A heavily armored, slow, devastating melee machine.",
            hp = 3500, damage = 720, hitSpeed = 1.8f, range = 0.7f, speed = 0.75f,
            targets = TargetType.GROUND, radius = 0.7f,
        ),
        CardDef(
            "bomber", "Bomber", "🧨", 2, CardType.TROOP, Rarity.COMMON,
            "Throws bombs that splash ground units.",
            hp = 300, damage = 190, hitSpeed = 1.8f, range = 4.5f, speed = 1.0f,
            targets = TargetType.GROUND, splash = 1.5f,
            projectile = ProjectileStyle.BOMB, projectileSpeed = 9f,
        ),
        CardDef(
            "barbarians", "Barbarians", "🪖", 5, CardType.TROOP, Rarity.COMMON,
            "A horde of four sword-swinging brawlers.",
            count = 4, hp = 640, damage = 160, hitSpeed = 1.3f, range = 0.6f, speed = 1.0f,
            targets = TargetType.GROUND,
        ),
        CardDef(
            "speargoblins", "Spear Goblins", "🔱", 2, CardType.TROOP, Rarity.COMMON,
            "Three goblins throwing spears at air and ground.",
            count = 3, hp = 110, damage = 70, hitSpeed = 1.7f, range = 5f, speed = 2.0f,
            radius = 0.35f, projectile = ProjectileStyle.SPEAR, projectileSpeed = 14f,
        ),
        CardDef(
            "witch", "Witch", "🧙", 5, CardType.TROOP, Rarity.EPIC,
            "Shoots splashing dark magic and summons three skeletons every 7 seconds.",
            hp = 700, damage = 110, hitSpeed = 1.1f, range = 5f, speed = 1.0f, splash = 1f,
            projectile = ProjectileStyle.ORB, projectileSpeed = 11f,
            spawnId = "skeletons", spawnEvery = 7f, spawnCount = 3,
        ),
        CardDef(
            "megaminion", "Mega Minion", "🦇", 3, CardType.TROOP, Rarity.RARE,
            "A heavily armored flying minion that hits hard.",
            hp = 700, damage = 250, hitSpeed = 1.6f, range = 1.6f, speed = 1.0f,
            flying = true, radius = 0.5f, projectile = ProjectileStyle.ORB, projectileSpeed = 12f,
        ),
        CardDef(
            "royalgiant", "Royal Giant", "👑", 6, CardType.TROOP, Rarity.EPIC,
            "Fires his hand cannon at buildings from long range.",
            hp = 2900, damage = 160, hitSpeed = 1.7f, range = 5f, sight = 7f, speed = 0.75f,
            targets = TargetType.BUILDINGS, radius = 0.7f,
            projectile = ProjectileStyle.CANNONBALL, projectileSpeed = 14f,
        ),
        CardDef(
            "fireball", "Fireball", "🔥", 4, CardType.SPELL, Rarity.RARE,
            "Big area damage. Deals reduced damage to towers.",
            damage = 600, spellRadius = 2.5f, towerDamagePct = 0.35f, spellSpeed = 11f,
        ),
        CardDef(
            "arrows", "Arrows", "🏹", 3, CardType.SPELL, Rarity.COMMON,
            "A volley of arrows over a huge area. Shreds swarms.",
            damage = 300, spellRadius = 4f, towerDamagePct = 0.35f, spellSpeed = 15f,
        ),
        CardDef(
            "zap", "Zap", "⚡", 2, CardType.SPELL, Rarity.COMMON,
            "Instant small-area damage that briefly stuns.",
            damage = 170, spellRadius = 2.5f, towerDamagePct = 0.35f, stun = 0.6f,
        ),
        CardDef(
            "freeze", "Freeze", "❄️", 4, CardType.SPELL, Rarity.RARE,
            "A snowy Christmas slows everything to a halt. Freezes enemy troops in the area for 4 seconds.",
            damage = 0, spellRadius = 4f, towerDamagePct = 0f, freeze = 4f,
        ),
        CardDef(
            "lightning", "Lightning", "🌩️", 6, CardType.SPELL, Rarity.EPIC,
            "Strikes the three toughest enemies in the area, towers included.",
            damage = 860, spellRadius = 3.5f, towerDamagePct = 0.3f, stun = 0.5f, strikes = 3,
        ),
        CardDef(
            "cannon", "Cannon", "💣", 3, CardType.BUILDING, Rarity.COMMON,
            "Defensive building that shoots ground units. Decays over time.",
            hp = 900, damage = 180, hitSpeed = 0.9f, range = 5.5f, sight = 5.5f,
            targets = TargetType.GROUND, radius = 0.9f, lifetime = 30f,
            projectile = ProjectileStyle.CANNONBALL, projectileSpeed = 14f,
        ),
        CardDef(
            "tesla", "Tesla", "🗼", 4, CardType.BUILDING, Rarity.RARE,
            "Electric defense building that hits air and ground.",
            hp = 1000, damage = 200, hitSpeed = 1.1f, range = 5.5f, sight = 5.5f,
            radius = 0.9f, lifetime = 30f, projectile = ProjectileStyle.ZAP,
        ),
        CardDef(
            "infernotower", "Inferno Tower", "🔥", 5, CardType.BUILDING, Rarity.RARE,
            "Its beam burns hotter the longer it stays on one target. Melts tanks.",
            hp = 1500, damage = 35, hitSpeed = 0.4f, range = 6f, sight = 6f,
            radius = 0.9f, lifetime = 35f, projectile = ProjectileStyle.BEAM, rampDamage = 9f,
        ),
        CardDef(
            "tombstone", "Tombstone", "🪦", 3, CardType.BUILDING, Rarity.RARE,
            "Spawns a skeleton every 3 seconds, and four more when it's destroyed.",
            hp = 500, damage = 0, targets = TargetType.GROUND, radius = 0.8f, lifetime = 40f,
            spawnId = "skeletons", spawnEvery = 3f, spawnCount = 1, deathSpawnCount = 4,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): CardDef? = byId[id]

    val defaultDecks: List<List<String>> = listOf(
        listOf("knight", "archers", "giant", "musketeer", "minions", "fireball", "zap", "goblins"),
        listOf("hogrider", "valkyrie", "musketeer", "skeletons", "cannon", "fireball", "arrows", "speargoblins"),
        listOf("pekka", "babydragon", "wizard", "minipekka", "bomber", "zap", "tesla", "barbarians"),
    )

    /** Decks the AI picks from at random. */
    val aiDecks: List<List<String>> = listOf(
        listOf("giant", "musketeer", "valkyrie", "minions", "fireball", "zap", "knight", "archers"),
        listOf("hogrider", "wizard", "skeletons", "cannon", "arrows", "minipekka", "speargoblins", "babydragon"),
        listOf("pekka", "babydragon", "bomber", "goblins", "zap", "musketeer", "tesla", "barbarians"),
        listOf("giant", "wizard", "minipekka", "archers", "arrows", "fireball", "skeletons", "valkyrie"),
        listOf("royalgiant", "witch", "megaminion", "tombstone", "lightning", "zap", "knight", "archers"),
        listOf("hogrider", "infernotower", "freeze", "megaminion", "wizard", "goblins", "skeletons", "fireball"),
    )
}
