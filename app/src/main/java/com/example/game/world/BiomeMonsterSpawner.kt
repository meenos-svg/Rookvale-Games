package com.example.game.world

import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import com.example.game.model.normalizeRegionId
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Weighted monster candidate inside a biome's natural spawn table.
 */
data class WeightedMonsterEntry(
    val type: MonsterType,
    val weight: Int
)

/**
 * Natural ecological biome types driving dynamic overland & dungeon monster selection
 * and strict biome-clamped level assignment.
 */
enum class BiomeType(
    val displayName: String,
    val overlandTier: OverlandBiomeTier,
    val minLevel: Int,
    val maxLevel: Int,
    val monsterPool: List<WeightedMonsterEntry>
) {
    HEARTLAND_MEADOWS(
        displayName = "Rookvale Starter Meadows",
        overlandTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
        minLevel = 1,
        maxLevel = 5,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.WILD_RABBIT, 34),
            WeightedMonsterEntry(MonsterType.CAVE_RAT, 28),
            WeightedMonsterEntry(MonsterType.YOUNG_SNAKE, 24),
            WeightedMonsterEntry(MonsterType.CRAG_WOLF, 14)
        )
    ),
    HEARTLAND_WILDS(
        displayName = "Rookvale Heartland Wilds",
        overlandTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
        minLevel = 5,
        maxLevel = 12,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.GOBLIN_SCOUT, 30),
            WeightedMonsterEntry(MonsterType.CRAG_WOLF, 24),
            WeightedMonsterEntry(MonsterType.SWAMP_TROLL, 20),
            WeightedMonsterEntry(MonsterType.POISON_SPIDER, 16),
            WeightedMonsterEntry(MonsterType.SKELETON, 10)
        )
    ),
    HEARTLAND_FRONTIER(
        displayName = "Heartland Frontier & Outer Woods",
        overlandTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
        minLevel = 12,
        maxLevel = 20,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.OUTLAW_BANDIT, 22),
            WeightedMonsterEntry(MonsterType.ORC_WARRIOR, 22),
            WeightedMonsterEntry(MonsterType.OUTLAW_ROGUE, 16),
            WeightedMonsterEntry(MonsterType.SKELETON, 16),
            WeightedMonsterEntry(MonsterType.SWAMP_TROLL, 14),
            WeightedMonsterEntry(MonsterType.POISON_SPIDER, 10)
        )
    ),
    HEARTLAND_OUTLAW_CAMP(
        displayName = "Rookvale Outlaw Camps",
        overlandTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
        minLevel = 12,
        maxLevel = 20,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.OUTLAW_BANDIT, 38),
            WeightedMonsterEntry(MonsterType.OUTLAW_ROGUE, 34),
            WeightedMonsterEntry(MonsterType.GOBLIN_SCOUT, 12),
            WeightedMonsterEntry(MonsterType.ORC_WARRIOR, 10),
            WeightedMonsterEntry(MonsterType.CRAG_WOLF, 6)
        )
    ),
    WHISPERING_MARSH(
        displayName = "Whispering Marsh & Mirelands",
        overlandTier = OverlandBiomeTier.WHISPERING_MARSH,
        minLevel = 20,
        maxLevel = 32,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.SWAMP_TROLL, 28),
            WeightedMonsterEntry(MonsterType.POISON_SPIDER, 28),
            WeightedMonsterEntry(MonsterType.GHOUL, 22),
            WeightedMonsterEntry(MonsterType.ORC_WARRIOR, 12),
            WeightedMonsterEntry(MonsterType.GOBLIN_SCOUT, 10)
        )
    ),
    EMERALD_JUNGLE(
        displayName = "Emerald Jungle & Overgrown Ruins",
        overlandTier = OverlandBiomeTier.WHISPERING_MARSH,
        minLevel = 22,
        maxLevel = 32,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.POISON_SPIDER, 32),
            WeightedMonsterEntry(MonsterType.ORC_WARRIOR, 24),
            WeightedMonsterEntry(MonsterType.SWAMP_TROLL, 18),
            WeightedMonsterEntry(MonsterType.YOUNG_SNAKE, 14),
            WeightedMonsterEntry(MonsterType.DRAGON_HATCHLING, 12)
        )
    ),
    SUNSCORCH_BADLANDS(
        displayName = "Sunscorch Badlands & Gorek Wastes",
        overlandTier = OverlandBiomeTier.SUNSCORCH_BADLANDS,
        minLevel = 30,
        maxLevel = 45,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.GOREK_RAIDER, 24),
            WeightedMonsterEntry(MonsterType.FIRE_DEVIL, 20),
            WeightedMonsterEntry(MonsterType.SKELETON, 20),
            WeightedMonsterEntry(MonsterType.GOREK_PHALANX, 18),
            WeightedMonsterEntry(MonsterType.GOREK_HEX_CALLER, 10),
            WeightedMonsterEntry(MonsterType.ORC_WARRIOR, 8)
        )
    ),
    FROSTPEAK_HIGHLANDS(
        displayName = "Frostpeak Highlands & Grimmaw Crags",
        overlandTier = OverlandBiomeTier.FROSTPEAK_HIGHLANDS,
        minLevel = 42,
        maxLevel = 58,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.GRIMMAW_QUARRYMAN, 28),
            WeightedMonsterEntry(MonsterType.GRIMMAW_CRUSHER, 24),
            WeightedMonsterEntry(MonsterType.GRIMMAW_ROCK_HURLER, 24),
            WeightedMonsterEntry(MonsterType.ORC_WARRIOR, 14),
            WeightedMonsterEntry(MonsterType.CRAG_WOLF, 10)
        )
    ),
    BLIGHTWOOD_REACH(
        displayName = "Blightwood Reach & Barrow Mists",
        overlandTier = OverlandBiomeTier.ASHEN_CALDERA,
        minLevel = 55,
        maxLevel = 68,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.NECROMANCER, 30),
            WeightedMonsterEntry(MonsterType.GHOUL, 28),
            WeightedMonsterEntry(MonsterType.SKELETON, 22),
            WeightedMonsterEntry(MonsterType.POISON_SPIDER, 10),
            WeightedMonsterEntry(MonsterType.GOREK_HEX_CALLER, 10)
        )
    ),
    ASHEN_CALDERA(
        displayName = "Ashen Caldera & Ember Wastes",
        overlandTier = OverlandBiomeTier.ASHEN_CALDERA,
        minLevel = 58,
        maxLevel = 75,
        monsterPool = listOf(
            WeightedMonsterEntry(MonsterType.FIRE_DEVIL, 24),
            WeightedMonsterEntry(MonsterType.DRAGON_HATCHLING, 20),
            WeightedMonsterEntry(MonsterType.GOREK_RAIDER, 18),
            WeightedMonsterEntry(MonsterType.GOREK_PHALANX, 16),
            WeightedMonsterEntry(MonsterType.GOREK_HEX_CALLER, 12),
            WeightedMonsterEntry(MonsterType.NECROMANCER, 10)
        )
    );

    val totalWeight: Int get() = monsterPool.sumOf { it.weight }.coerceAtLeast(1)

    fun containsSpecies(type: MonsterType): Boolean = monsterPool.any { it.type == type }
}

/**
 * Biome-driven dynamic monster spawning & respawn resolution engine.
 *
 * Replaces static hand-placed town spawns with deterministic wilderness sampling across
 * walkable non-PZ terrain: each spawn and non-boss respawn inspects its tile's [BiomeType],
 * draws a weighted species from that biome's natural monster pool, and assigns a level
 * strictly clamped to that biome's progression band (with Rookvale Heartland locked to 1..20).
 */
object BiomeMonsterSpawner {

    // Radial thresholds (in tiles) from Rookvale Sanctuary Plaza (1396, 592) inside Heartland
    private const val HEARTLAND_MEADOW_RADIUS = 88.0
    private const val HEARTLAND_WILDS_RADIUS = 142.0
    private const val HEARTLAND_MAX_RADIUS = 235.0

    // Open rustic Lv 12-20 Rookvale Outlaw Camp centers (global Floor 0 coordinates)
    const val SW_OUTLAW_CAMP_X = WorldMaps.ROOKVALE_ORIGIN_X + 88   // 1288 (Briar Outlaw Camp, Lv 12-16)
    const val SW_OUTLAW_CAMP_Y = WorldMaps.ROOKVALE_ORIGIN_Y + 320  // 720
    const val SE_OUTLAW_CAMP_X = WorldMaps.ROOKVALE_ORIGIN_X + 312  // 1512 (Watchwood Outlaw Camp, Lv 15-20)
    const val SE_OUTLAW_CAMP_Y = WorldMaps.ROOKVALE_ORIGIN_Y + 320  // 720
    const val OUTLAW_CAMP_RADIUS = 38.0

    // City outer perimeter buffer (global coords) so wild monsters never crowd city fences
    private const val ROOKVALE_CITY_BUFFER_MIN_X = WorldMaps.ROOKVALE_ORIGIN_X + 140
    private const val ROOKVALE_CITY_BUFFER_MAX_X = WorldMaps.ROOKVALE_ORIGIN_X + 262
    private const val ROOKVALE_CITY_BUFFER_MIN_Y = WorldMaps.ROOKVALE_ORIGIN_Y + 142
    private const val ROOKVALE_CITY_BUFFER_MAX_Y = WorldMaps.ROOKVALE_ORIGIN_Y + 234

    // Dedicated per-dungeon monster ecologies so every one of the 13 dungeons (+ Deep Delves -3/-4)
    // hosts its own distinct monsters across both its Upper Floor and Lower Boss Lair.
    private val briarCatacombsPool = listOf(
        WeightedMonsterEntry(MonsterType.BRIAR_CRYPT_CRAWLER, 36),
        WeightedMonsterEntry(MonsterType.CATACOMB_BONE_ARCHER, 32),
        WeightedMonsterEntry(MonsterType.POISON_SPIDER, 18),
        WeightedMonsterEntry(MonsterType.SKELETON, 14)
    )
    private val quarryMinesPool = listOf(
        WeightedMonsterEntry(MonsterType.TUNNEL_KOBOLD_SAPPER, 40),
        WeightedMonsterEntry(MonsterType.QUARRY_MOLE_BRUTE, 38),
        WeightedMonsterEntry(MonsterType.CAVE_RAT, 22)
    )
    private val northMountainCavernPool = listOf(
        WeightedMonsterEntry(MonsterType.CRAG_HARPY, 40),
        WeightedMonsterEntry(MonsterType.SHALE_GOLEM, 38),
        WeightedMonsterEntry(MonsterType.CRAG_WOLF, 22)
    )
    private val darkForestSinkholePool = listOf(
        WeightedMonsterEntry(MonsterType.THORN_BRAMBLE_STALKER, 40),
        WeightedMonsterEntry(MonsterType.SPORELING_LOBBER, 38),
        WeightedMonsterEntry(MonsterType.SWAMP_TROLL, 22)
    )
    private val cragDeepHollowPool = listOf(
        WeightedMonsterEntry(MonsterType.VAULT_SENTINEL, 40),
        WeightedMonsterEntry(MonsterType.HOLLOW_CROSSBOWMAN, 38),
        WeightedMonsterEntry(MonsterType.OUTLAW_BANDIT, 22)
    )
    private val watchwoodCavernsPool = listOf(
        WeightedMonsterEntry(MonsterType.CINDER_IMP, 34),
        WeightedMonsterEntry(MonsterType.WATCHWOOD_DREAD_KNIGHT, 34),
        WeightedMonsterEntry(MonsterType.GHOUL, 18),
        WeightedMonsterEntry(MonsterType.FIRE_DEVIL, 14)
    )
    private val sunkenMireVaultsPool = listOf(
        WeightedMonsterEntry(MonsterType.MIRE_LURKER, 42),
        WeightedMonsterEntry(MonsterType.BOG_LEECH_WEAVER, 38),
        WeightedMonsterEntry(MonsterType.SWAMP_TROLL, 20)
    )
    private val zigguratVaultPool = listOf(
        WeightedMonsterEntry(MonsterType.TEMPLE_SERPENT_GUARD, 42),
        WeightedMonsterEntry(MonsterType.SUN_PRIEST_ACOLYTE, 38),
        WeightedMonsterEntry(MonsterType.POISON_SPIDER, 20)
    )
    private val blightwoodBarrowPool = listOf(
        WeightedMonsterEntry(MonsterType.BARROW_WIGHT, 42),
        WeightedMonsterEntry(MonsterType.FROST_BANSHEE, 38),
        WeightedMonsterEntry(MonsterType.NECROMANCER, 20)
    )
    private val ashenFoundryPool = listOf(
        WeightedMonsterEntry(MonsterType.SLAG_AUTOMATON, 42),
        WeightedMonsterEntry(MonsterType.ASH_PYROMANCER, 38),
        WeightedMonsterEntry(MonsterType.FIRE_DEVIL, 20)
    )
    private val emberSanctumPool = listOf(
        WeightedMonsterEntry(MonsterType.EMBER_DRAKE, 42),
        WeightedMonsterEntry(MonsterType.OBSIDIAN_CULTIST, 38),
        WeightedMonsterEntry(MonsterType.DRAGON_HATCHLING, 20)
    )
    private val grimmawDungeonPool = listOf(
        WeightedMonsterEntry(MonsterType.GRIMMAW_QUARRYMAN, 38),
        WeightedMonsterEntry(MonsterType.GRIMMAW_ROCK_HURLER, 32),
        WeightedMonsterEntry(MonsterType.GRIMMAW_CRUSHER, 30)
    )
    private val gorekDungeonPool = listOf(
        WeightedMonsterEntry(MonsterType.GOREK_RAIDER, 36),
        WeightedMonsterEntry(MonsterType.GOREK_PHALANX, 34),
        WeightedMonsterEntry(MonsterType.GOREK_HEX_CALLER, 30)
    )
    private val cryptDungeonPool = listOf(
        WeightedMonsterEntry(MonsterType.NECROMANCER, 45),
        WeightedMonsterEntry(MonsterType.DRAGON_HATCHLING, 35),
        WeightedMonsterEntry(MonsterType.GHOUL, 20)
    )
    private val wyrmDungeonPool = listOf(
        WeightedMonsterEntry(MonsterType.DRAGON_HATCHLING, 46),
        WeightedMonsterEntry(MonsterType.FIRE_DEVIL, 32),
        WeightedMonsterEntry(MonsterType.NECROMANCER, 22)
    )

    /**
     * Returns the dedicated regular-monster spawn pool for any underground dungeon floor (`-1..-28`).
     */
    fun dungeonPoolForFloor(floorId: Int): List<WeightedMonsterEntry> =
        when (BiomeProgressionRules.parentUpperFloorFor(floorId)) {
            -1 -> briarCatacombsPool
            -2 -> watchwoodCavernsPool
            -3 -> cryptDungeonPool
            -4 -> wyrmDungeonPool
            -5 -> grimmawDungeonPool
            -6 -> gorekDungeonPool
            -7 -> sunkenMireVaultsPool
            -8 -> zigguratVaultPool
            -9 -> blightwoodBarrowPool
            -10 -> ashenFoundryPool
            -11 -> quarryMinesPool
            -12 -> northMountainCavernPool
            -13 -> darkForestSinkholePool
            -14 -> cragDeepHollowPool
            -15 -> emberSanctumPool
            else -> biomeForDungeonFloor(floorId).monsterPool
        }

    /**
     * Resolves the ecological [BiomeType] at global Floor 0 coordinates `(x, y)`.
     * Inside Rookvale Heartland, grades naturally by radial distance from Rookvale City's
     * Sanctuary Plaza so starter meadows stay Lv 1–5, mid-wilds stay Lv 5–12, and outer
     * Heartland borders / southern ruins / Spirewood Reach scale cleanly to Lv 12–20.
     */
    fun resolveBiomeTypeAt(x: Int, y: Int): BiomeType {
        val regionId = normalizeRegionId(WorldRegionMeta.forGlobalTile(x, y).regionId)
        return when (regionId) {
            "rookvale_valley" -> {
                if (isInOutlawCampZone(x, y)) {
                    BiomeType.HEARTLAND_OUTLAW_CAMP
                } else {
                    val dist = distanceFromRookvaleCenter(x, y)
                    when {
                        dist <= HEARTLAND_MEADOW_RADIUS -> BiomeType.HEARTLAND_MEADOWS
                        dist <= HEARTLAND_WILDS_RADIUS -> BiomeType.HEARTLAND_WILDS
                        else -> BiomeType.HEARTLAND_FRONTIER
                    }
                }
            }
            "spirewood_west" -> BiomeType.HEARTLAND_FRONTIER
            "writhing_marshes", "alder_sea_plains" -> BiomeType.WHISPERING_MARSH
            "emerald_jungle_east", "maelstrom_jungle_west" -> BiomeType.EMERALD_JUNGLE
            "sunspire_desert" -> BiomeType.SUNSCORCH_BADLANDS
            "frostcrest_highlands", "frostbay_straits" -> BiomeType.FROSTPEAK_HIGHLANDS
            "blightwood_reach" -> BiomeType.BLIGHTWOOD_REACH
            "caldera_peaks", "ember_isles" -> BiomeType.ASHEN_CALDERA
            else -> BiomeType.HEARTLAND_WILDS
        }
    }

    fun isInOutlawCampZone(x: Int, y: Int): Boolean {
        val dSw = hypot((x - SW_OUTLAW_CAMP_X).toDouble(), (y - SW_OUTLAW_CAMP_Y).toDouble())
        if (dSw <= OUTLAW_CAMP_RADIUS) return true
        val dSe = hypot((x - SE_OUTLAW_CAMP_X).toDouble(), (y - SE_OUTLAW_CAMP_Y).toDouble())
        return dSe <= OUTLAW_CAMP_RADIUS
    }

    /**
     * Maps each underground dungeon floor (`-1..-28`) to its natural ecological [BiomeType].
     * All six Rookvale dungeons (-1, -2, -11..-14 & their lower boss lairs -16..-21) belong
     * strictly to Rookvale Heartland's Lv 1-20 tier.
     */
    fun biomeForDungeonFloor(floorId: Int): BiomeType = when (BiomeProgressionRules.parentUpperFloorFor(floorId)) {
        -1 -> BiomeType.HEARTLAND_WILDS
        -2 -> BiomeType.HEARTLAND_FRONTIER
        -3 -> BiomeType.BLIGHTWOOD_REACH
        -4 -> BiomeType.ASHEN_CALDERA
        -5 -> BiomeType.FROSTPEAK_HIGHLANDS
        -6 -> BiomeType.ASHEN_CALDERA
        -7 -> BiomeType.WHISPERING_MARSH
        -8 -> BiomeType.EMERALD_JUNGLE
        -9 -> BiomeType.BLIGHTWOOD_REACH
        -10 -> BiomeType.ASHEN_CALDERA
        -11 -> BiomeType.HEARTLAND_WILDS
        -12 -> BiomeType.HEARTLAND_WILDS
        -13 -> BiomeType.HEARTLAND_WILDS
        -14 -> BiomeType.HEARTLAND_FRONTIER
        -15 -> BiomeType.ASHEN_CALDERA
        else -> BiomeType.HEARTLAND_WILDS
    }

    /**
     * Deterministically selects a weighted [MonsterType] from [biome]'s pool using [seed].
     */
    fun pickWeightedMonster(biome: BiomeType, seed: Int): MonsterType =
        pickFromPool(biome.monsterPool, seed)

    /**
     * Selects a weighted regular monster strictly from the dedicated dungeon roster for [floorId].
     */
    fun pickWeightedDungeonMonster(floorId: Int, seed: Int): MonsterType =
        pickFromPool(dungeonPoolForFloor(floorId), seed)

    /**
     * Resolves a strict biome-clamped monster level on Floor 0.
     * - Rookvale Heartland (`rookvale_valley` + `spirewood_west`): strictly `1..20`, grading
     *   smoothly from `1..5` near Rookvale City gates up to `12..20` at outer borders & ruins.
     * - All outer continent biomes: strictly within `[biome.minLevel, biome.maxLevel]`
     *   (which is always a subset of `biome.overlandTier.minLevel..maxLevel`).
     */
    fun resolveOverlandLevel(x: Int, y: Int, type: MonsterType, seedOffset: Int = 0): Int {
        val biome = resolveBiomeTypeAt(x, y)
        val tier = biome.overlandTier

        if (tier == OverlandBiomeTier.ROOKVALE_HEARTLAND) {
            val dist = distanceFromRookvaleCenter(x, y)
            val jitter = (positiveHash(x, y, 409 + seedOffset) % 3) - 1
            val raw = when (biome) {
                BiomeType.HEARTLAND_MEADOWS -> {
                    // Gentle Lv 1-5 ramp right outside Rookvale's gates
                    val t = ((dist - 48.0) / (HEARTLAND_MEADOW_RADIUS - 48.0)).coerceIn(0.0, 1.0)
                    (1.0 + t * 4.0).roundToInt() + jitter
                }
                BiomeType.HEARTLAND_WILDS -> {
                    val t = ((dist - HEARTLAND_MEADOW_RADIUS) / (HEARTLAND_WILDS_RADIUS - HEARTLAND_MEADOW_RADIUS))
                        .coerceIn(0.0, 1.0)
                    (5.0 + t * 7.0).roundToInt() + jitter
                }
                BiomeType.HEARTLAND_OUTLAW_CAMP -> {
                    // SW Briar Outlaw Camp grades Lv 12-16; SE Watchwood Outlaw Camp grades Lv 15-20
                    val isSeCamp = hypot((x - SE_OUTLAW_CAMP_X).toDouble(), (y - SE_OUTLAW_CAMP_Y).toDouble()) <
                        hypot((x - SW_OUTLAW_CAMP_X).toDouble(), (y - SW_OUTLAW_CAMP_Y).toDouble())
                    val bandMin = if (isSeCamp) 15 else 12
                    val bandMax = if (isSeCamp) 20 else 16
                    val roll = positiveHash(x, y, 521 + seedOffset) % (bandMax - bandMin + 1)
                    (bandMin + roll).coerceIn(bandMin, bandMax)
                }
                else -> {
                    // Outer Heartland borders & Spirewood Reach: Lv 12-20
                    val t = ((dist - HEARTLAND_WILDS_RADIUS) / (HEARTLAND_MAX_RADIUS - HEARTLAND_WILDS_RADIUS))
                        .coerceIn(0.0, 1.0)
                    (12.0 + t * 8.0).roundToInt() + jitter
                }
            }
            return raw.coerceIn(biome.minLevel, biome.maxLevel).coerceIn(1, 20)
        }

        val span = (biome.maxLevel - biome.minLevel).coerceAtLeast(1)
        val speciesFraction = ((type.tier - 1).coerceIn(0, 5) / 5.0)
        val jitter = (positiveHash(x, y, 719 + seedOffset) % 5) - 2
        val raw = biome.minLevel + (span * speciesFraction).roundToInt() + jitter
        return if (type.isBoss) {
            (tier.maxLevel + 3).coerceAtLeast(tier.minLevel)
        } else {
            raw.coerceIn(biome.minLevel, biome.maxLevel).coerceIn(tier.minLevel, tier.maxLevel)
        }
    }

    /**
     * Dynamically resolves species & biome-clamped level whenever a spawn point respawns.
     * - Bosses preserve their iconic boss species and dungeon/biome boss level.
     * - Non-bosses re-sample the underlying tile's [BiomeType] (or dungeon floor pool) so
     *   packs stay ecologically diverse and strictly bound to the biome's level range.
     */
    fun resolveRespawnSpeciesAndLevel(
        floor: Int,
        spawnX: Int,
        spawnY: Int,
        originalType: MonsterType,
        fallbackLevel: Int,
        respawnSeed: Int
    ): Pair<MonsterType, Int> {
        if (originalType.isBoss) {
            val bossLevel = if (floor == 0) {
                resolveOverlandLevel(spawnX, spawnY, originalType)
            } else {
                BiomeProgressionRules.resolveDungeonSpawnLevel(floor, spawnX, spawnY, originalType)
            }
            return originalType to bossLevel.coerceAtLeast(fallbackLevel)
        }

        return if (floor == 0) {
            val biome = resolveBiomeTypeAt(spawnX, spawnY)
            val newType = pickWeightedMonster(biome, positiveHash(spawnX, spawnY, respawnSeed))
            val newLevel = resolveOverlandLevel(spawnX, spawnY, newType, seedOffset = respawnSeed)
            newType to newLevel
        } else {
            val newType = pickWeightedDungeonMonster(floor, positiveHash(spawnX, spawnY, respawnSeed))
            val newLevel = BiomeProgressionRules.resolveDungeonSpawnLevel(floor, spawnX, spawnY, newType)
            newType to newLevel
        }
    }

    /**
     * Deterministically populates overland (Floor 0) monsters purely from underlying terrain
     * biomes across Rookvale Heartland and all 11 outer regions — zero hard-coded town spots
     * and zero [ChunkedTileGrid] streaming churn.
     *
     * Every generated spawn is guaranteed walkable, outside Protection Zones (with a buffer
     * around city walls & settlements), and off main paved highways/bridges/piers.
     */
    fun generateOverlandBiomeSpawns(
        isProtectionZone: (Int, Int) -> Boolean,
        tileOracle: (Int, Int) -> TileType
    ): List<MonsterSpawn> {
        val spawns = mutableListOf<MonsterSpawn>()
        val occupied = HashSet<Long>()

        fun pack(x: Int, y: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)

        fun isValidWildernessSpawnTile(x: Int, y: Int): Boolean {
            if (x !in 12 until (WORLD_OVERLAND_WIDTH - 12) || y !in 12 until (WORLD_OVERLAND_HEIGHT - 12)) {
                return false
            }
            if (pack(x, y) in occupied) return false
            if (isProtectionZone(x, y)) return false
            // Keep wild creatures outside Rookvale City perimeter fence/garden buffer
            if (x in ROOKVALE_CITY_BUFFER_MIN_X..ROOKVALE_CITY_BUFFER_MAX_X &&
                y in ROOKVALE_CITY_BUFFER_MIN_Y..ROOKVALE_CITY_BUFFER_MAX_Y
            ) {
                return false
            }
            // Keep wild creatures clear of secondary settlement walls (8-tile peace ring)
            for (s in WorldBiomeBlueprint.secondarySettlements) {
                if (x in (s.minX - 8)..(s.maxX + 8) && y in (s.minY - 8)..(s.maxY + 8)) {
                    return false
                }
            }
            val tile = tileOracle(x, y)
            if (!tile.walkable) return false
            return isNaturalSpawnGround(tile)
        }

        fun tryPlaceBiomeSpawn(cx: Int, cy: Int, seed: Int): Boolean {
            // Search small 3-tile spiral around jittered candidate if exact tile is a tree/road
            val offsets = listOf(0 to 0, 2 to 0, -2 to 0, 0 to 2, 0 to -2, 3 to 3, -3 to -3, 3 to -3, -3 to 3)
            for ((dx, dy) in offsets) {
                val tx = cx + dx
                val ty = cy + dy
                if (isValidWildernessSpawnTile(tx, ty)) {
                    val biome = resolveBiomeTypeAt(tx, ty)
                    val species = pickWeightedMonster(biome, positiveHash(tx, ty, seed))
                    spawns += MonsterSpawn(0, tx, ty, species)
                    occupied += pack(tx, ty)
                    return true
                }
            }
            return false
        }

        // 1. Rookvale Heartland (1200..1599, 400..799) — denser starter/wilderness scatter (strictly Lv 1-20)
        val hStep = 34
        for (gy in (WorldMaps.ROOKVALE_ORIGIN_Y + 24) until (WorldMaps.ROOKVALE_ORIGIN_Y + 378) step hStep) {
            for (gx in (WorldMaps.ROOKVALE_ORIGIN_X + 24) until (WorldMaps.ROOKVALE_ORIGIN_X + 356) step hStep) {
                val h = positiveHash(gx, gy, 101)
                val jx = (h % 17) - 8
                val jy = ((h ushr 5) % 17) - 8
                val cx = gx + jx
                val cy = gy + jy
                if (tryPlaceBiomeSpawn(cx, cy, 211)) {
                    // Natural small packmate (~35% chance) 4-6 tiles away in same biome
                    if (((h ushr 10) % 100) < 35) {
                        val px = cx + (((h ushr 17) % 9) - 4).let { if (it in -1..1) 4 else it }
                        val py = cy + (((h ushr 21) % 9) - 4).let { if (it in -1..1) -4 else it }
                        tryPlaceBiomeSpawn(px, py, 307)
                    }
                }
            }
        }

        // 1B. Open Rustic Rookvale Outlaw Camps (Briar SW & Watchwood SE — biome-driven Lv 12-20 packs)
        val campCenters = listOf(SW_OUTLAW_CAMP_X to SW_OUTLAW_CAMP_Y, SE_OUTLAW_CAMP_X to SE_OUTLAW_CAMP_Y)
        val campRingOffsets = listOf(
            -12 to -10, 0 to -12, 12 to -10,
            -14 to 2, 14 to 2,
            -10 to 12, 2 to 13, 12 to 11,
            -22 to 0, 22 to -4
        )
        for ((campIdx, center) in campCenters.withIndex()) {
            val (ccx, ccy) = center
            for ((slotIdx, off) in campRingOffsets.withIndex()) {
                val tx = ccx + off.first
                val ty = ccy + off.second
                tryPlaceBiomeSpawn(tx, ty, 809 + campIdx * 97 + slotIdx * 31)
            }
        }

        // 2. Outer 11 Continent Regions — biome-driven wilderness packs across all outer biomes
        val oStep = 56
        for (gy in 48 until (WORLD_OVERLAND_HEIGHT - 48) step oStep) {
            for (gx in 48 until (WORLD_OVERLAND_WIDTH - 48) step oStep) {
                if (WorldBiomeBlueprint.isInRookvaleHeartland(gx, gy)) continue
                val h = positiveHash(gx, gy, 503)
                val jx = (h % 25) - 12
                val jy = ((h ushr 6) % 25) - 12
                val cx = gx + jx
                val cy = gy + jy
                if (WorldBiomeBlueprint.isInRookvaleHeartland(cx, cy)) continue
                if (tryPlaceBiomeSpawn(cx, cy, 613)) {
                    // Occasional biome packmate (~30% chance)
                    if (((h ushr 12) % 100) < 30) {
                        val px = cx + (((h ushr 18) % 11) - 5).let { if (it in -2..2) 5 else it }
                        val py = cy + (((h ushr 22) % 11) - 5).let { if (it in -2..2) -5 else it }
                        if (!WorldBiomeBlueprint.isInRookvaleHeartland(px, py)) {
                            tryPlaceBiomeSpawn(px, py, 733)
                        }
                    }
                }
            }
        }

        return spawns
    }

    private fun isNaturalSpawnGround(tile: TileType): Boolean = when (tile) {
        TileType.GRASS,
        TileType.FLOWER_GRASS,
        TileType.DARK_GRASS,
        TileType.DIRT_FLOOR,
        TileType.SAND,
        TileType.SNOW,
        TileType.JUNGLE_FLOOR,
        TileType.VOLCANIC_ASH,
        TileType.CRYPT_FLOOR,
        TileType.OBSIDIAN_FLOOR -> true
        else -> false
    }

    private fun pickFromPool(pool: List<WeightedMonsterEntry>, seed: Int): MonsterType {
        if (pool.isEmpty()) return MonsterType.CRAG_WOLF
        val total = pool.sumOf { it.weight }.coerceAtLeast(1)
        var roll = (seed and 0x7FFFFFFF) % total
        for (entry in pool) {
            roll -= entry.weight
            if (roll < 0) return entry.type
        }
        return pool.last().type
    }

    private fun distanceFromRookvaleCenter(x: Int, y: Int): Double =
        hypot((x - WorldMaps.TEMPLE_SPAWN_X).toDouble(), (y - WorldMaps.TEMPLE_SPAWN_Y).toDouble())

    private fun positiveHash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }
}
