package com.example.game.world

import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.normalizeRegionId
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Architectural & cultural ideology governing how towns, empires, forward camps, and
 * subterranean strongholds generate walls, walkways, plazas, and light fixtures.
 */
enum class BiomeIdeology {
    HEARTLAND,      // White-plaster & oak half-timbered towns, cobblestone plazas, temples & depots
    MARSHLAND,      // Raised wooden plank walkways, stilt huts, timber palisades & torch-lit campfires
    BADLANDS,       // Sandstone walls, shaded bazaar stalls, oasis wells & desert nomad camps
    FROSTPEAK,      // Heavy mortared granite citadels, timber palisades & roaring hearth-forges
    ASHEN_BLIGHT    // Obsidian/iron-reinforced fortresses, war banners & glowing braziers
}

/**
 * Five locked overland biome progression tiers spanning Level 1 Rookvale newcomers through
 * Level 75+ Ashen Caldera & deep subterranean raids.
 */
enum class OverlandBiomeTier(
    val id: String,
    val displayName: String,
    val minLevel: Int,
    val maxLevel: Int,
    val levelRangeLabel: String,
    val ideology: BiomeIdeology,
    val dangerLevel: String,
    val dangerSeverity: Int
) {
    ROOKVALE_HEARTLAND(
        id = "rookvale_heartland",
        displayName = "Rookvale Heartland & Green Meadows",
        minLevel = 1,
        maxLevel = 20,
        levelRangeLabel = "Lvl 1-20",
        ideology = BiomeIdeology.HEARTLAND,
        dangerLevel = "Low",
        dangerSeverity = 0
    ),
    WHISPERING_MARSH(
        id = "whispering_marsh",
        displayName = "Whispering Marsh & Mirelands",
        minLevel = 20,
        maxLevel = 32,
        levelRangeLabel = "Lvl 20-32",
        ideology = BiomeIdeology.MARSHLAND,
        dangerLevel = "Moderate",
        dangerSeverity = 1
    ),
    SUNSCORCH_BADLANDS(
        id = "sunscorch_badlands",
        displayName = "Sunscorch Badlands & Arid Dunes",
        minLevel = 30,
        maxLevel = 45,
        levelRangeLabel = "Lvl 30-45",
        ideology = BiomeIdeology.BADLANDS,
        dangerLevel = "High",
        dangerSeverity = 2
    ),
    FROSTPEAK_HIGHLANDS(
        id = "frostpeak_highlands",
        displayName = "Frostpeak Highlands & Boreal Woods",
        minLevel = 42,
        maxLevel = 58,
        levelRangeLabel = "Lvl 42-58",
        ideology = BiomeIdeology.FROSTPEAK,
        dangerLevel = "Very High",
        dangerSeverity = 3
    ),
    ASHEN_CALDERA(
        id = "ashen_caldera",
        displayName = "Ashen Caldera & Blighted Wastes",
        minLevel = 55,
        maxLevel = 75,
        levelRangeLabel = "Lvl 55-75+",
        ideology = BiomeIdeology.ASHEN_BLIGHT,
        dangerLevel = "Extreme",
        dangerSeverity = 4
    )
}

/**
 * Deterministic biome level progression and dynamic per-spawn stat scaling engine.
 *
 * Enforces strict biome level bands on Floor 0 (Levels 1-75+) and scales underground dungeon
 * floors off their surface biome (`surfaceMinLevel + abs(floor) * 6`).
 */
object BiomeProgressionRules {
    const val DUNGEON_DEPTH_STEP_LEVELS: Int = 6

    fun biomeForRegion(regionId: String): OverlandBiomeTier = when (normalizeRegionId(regionId)) {
        "rookvale_valley", "spirewood_west" -> OverlandBiomeTier.ROOKVALE_HEARTLAND
        "writhing_marshes", "alder_sea_plains", "emerald_jungle_east", "maelstrom_jungle_west" -> OverlandBiomeTier.WHISPERING_MARSH
        "sunspire_desert" -> OverlandBiomeTier.SUNSCORCH_BADLANDS
        "frostcrest_highlands", "frostbay_straits" -> OverlandBiomeTier.FROSTPEAK_HIGHLANDS
        "caldera_peaks", "blightwood_reach", "ember_isles" -> OverlandBiomeTier.ASHEN_CALDERA
        else -> OverlandBiomeTier.ROOKVALE_HEARTLAND
    }

    fun biomeForGlobalTile(x: Int, y: Int): OverlandBiomeTier =
        biomeForRegion(WorldRegionMeta.forGlobalTile(x, y).regionId)

    /**
     * Canonical surface biome anchor for each underground dungeon floor so deeper floors
     * naturally progress from starter Rookvale sewers up to Level 90+ Gorek Labyrinth encounters.
     */
    fun surfaceBiomeForDungeonFloor(floorId: Int): OverlandBiomeTier = when (floorId) {
        -1 -> OverlandBiomeTier.ROOKVALE_HEARTLAND
        -2 -> OverlandBiomeTier.WHISPERING_MARSH
        -3 -> OverlandBiomeTier.SUNSCORCH_BADLANDS
        -4 -> OverlandBiomeTier.FROSTPEAK_HIGHLANDS
        -5 -> OverlandBiomeTier.SUNSCORCH_BADLANDS // Grimmaw Quarry anchored to Sunscorch Badlands
        -6 -> OverlandBiomeTier.ASHEN_CALDERA      // Gorek Labyrinth anchored to Ashen Caldera
        else -> OverlandBiomeTier.ROOKVALE_HEARTLAND
    }

    /**
     * Core dungeon progression formula: `surfaceMinLevel + abs(floor) * 6`.
     * Regular encounters span `[base, base + 4]`; dungeon bosses scale to `base + 5`.
     */
    fun dungeonBaseLevel(surfaceMinLevel: Int, floorId: Int): Int {
        val depth = abs(floorId).coerceAtLeast(1)
        return (surfaceMinLevel.coerceAtLeast(1) + depth * DUNGEON_DEPTH_STEP_LEVELS)
    }

    fun dungeonMonsterLevel(
        surfaceMinLevel: Int,
        floorId: Int,
        isBoss: Boolean = false,
        spreadOffset: Int = 0
    ): Int {
        val base = dungeonBaseLevel(surfaceMinLevel, floorId)
        return if (isBoss) base + 5 else base + spreadOffset.coerceIn(0, 4)
    }

    fun dungeonRecommendedLevelLabel(floorId: Int, surfaceMinLevel: Int = surfaceBiomeForDungeonFloor(floorId).minLevel): String {
        val base = dungeonBaseLevel(surfaceMinLevel, floorId)
        val top = base + 5
        return if (floorId <= -6) "Lvl $base-${top}+" else "Lvl $base-$top"
    }

    /**
     * Resolves the strict biome-locked level for an overland (Floor 0) monster spawn.
     * Rookvale Heartland spawns remain strictly in Levels 1-20 (preserving starter 1-3 meadows);
     * all outer biomes strictly populate their locked level ranges up to 75+.
     */
    fun resolveOverlandSpawnLevel(x: Int, y: Int, type: MonsterType): Int {
        val biome = biomeForGlobalTile(x, y)
        if (biome == OverlandBiomeTier.ROOKVALE_HEARTLAND) {
            // Preserve Rookvale starter 1-3 meadows and smoothly graduate outer frontier camps up to Lvl 20
            val clampedBase = type.threatLevel.coerceIn(biome.minLevel, biome.maxLevel)
            return if (x < WorldMaps.ROOKVALE_ORIGIN_X) {
                // Spirewood Western Reach (outer Heartland frontier: Lvl 10-20)
                val span = 10
                val hashOffset = (coordHash(x, y) and 0x7FFFFFFF) % (span + 1)
                (10 + ((clampedBase + hashOffset) / 2)).coerceIn(10, biome.maxLevel)
            } else {
                clampedBase
            }
        }

        val span = (biome.maxLevel - biome.minLevel).coerceAtLeast(1)
        val speciesFraction = ((type.tier - 1).coerceIn(0, 5) / 5.0)
        val jitter = ((coordHash(x, y) and 0x7FFFFFFF) % 5) - 2
        val raw = biome.minLevel + (span * speciesFraction).roundToInt() + jitter
        return if (type.isBoss) {
            (biome.maxLevel + 3).coerceAtLeast(biome.minLevel)
        } else {
            raw.coerceIn(biome.minLevel, biome.maxLevel)
        }
    }

    fun resolveDungeonSpawnLevel(
        floorId: Int,
        x: Int,
        y: Int,
        type: MonsterType,
        surfaceMinLevel: Int = surfaceBiomeForDungeonFloor(floorId).minLevel
    ): Int {
        val spread = (coordHash(x, y) and 0x7FFFFFFF) % 5
        return dungeonMonsterLevel(
            surfaceMinLevel = surfaceMinLevel,
            floorId = floorId,
            isBoss = type.isBoss,
            spreadOffset = spread
        )
    }

    /**
     * Builds a [MonsterEntity] with stats dynamically scaled to [targetLevel] while keeping
     * Rookvale's Level 1-20 baseline monsters true to their handcrafted balance.
     */
    fun createScaledMonster(
        id: Int,
        type: MonsterType,
        floor: Int,
        x: Int,
        y: Int,
        targetLevel: Int,
        animOffset: Float = 0f,
        isSummon: Boolean = false,
        summonerId: Int? = null
    ): MonsterEntity {
        val lvl = targetLevel.coerceAtLeast(1)
        val baseLvl = type.threatLevel.coerceAtLeast(1)
        val ratio = (lvl.toDouble() / baseLvl.toDouble()).coerceIn(0.65, 12.0)

        // Smooth RPG scaling curve: preserves exact base stats when lvl == baseLvl
        val hpMult = if (lvl == baseLvl) 1.0 else ratio.pow(1.18)
        val dmgMult = if (lvl == baseLvl) 1.0 else ratio.pow(0.95)
        val armorMult = if (lvl == baseLvl) 1.0 else ratio.pow(0.88)
        val xpMult = if (lvl == baseLvl) 1.0 else ratio.pow(1.24)
        val goldMult = if (lvl == baseLvl) 1.0 else ratio.pow(1.08)

        val scaledMaxHp = (type.maxHp * hpMult).roundToInt().coerceAtLeast(16)
        val scaledArmor = (type.armor * armorMult).roundToInt().coerceAtLeast(if (lvl >= 15) lvl / 3 else type.armor)
        val scaledMinDmg = (type.minDmg * dmgMult).roundToInt().coerceAtLeast(1)
        val scaledMaxDmg = (type.maxDmg * dmgMult).roundToInt().coerceAtLeast(scaledMinDmg + 2)
        val scaledXp = (type.xpReward * xpMult).roundToInt().coerceAtLeast(12)
        val scaledGoldMin = (type.goldMin * goldMult).roundToInt().coerceAtLeast(1)
        val scaledGoldMax = (type.goldMax * goldMult).roundToInt().coerceAtLeast(scaledGoldMin + 2)

        return MonsterEntity(
            id = id,
            type = type,
            floor = floor,
            x = x,
            y = y,
            spawnX = x,
            spawnY = y,
            hp = scaledMaxHp,
            maxHp = scaledMaxHp,
            level = lvl,
            scaledArmor = scaledArmor,
            scaledMinDmg = scaledMinDmg,
            scaledMaxDmg = scaledMaxDmg,
            scaledXpReward = scaledXp,
            scaledGoldMin = scaledGoldMin,
            scaledGoldMax = scaledGoldMax,
            animOffset = animOffset,
            isSummon = isSummon,
            summonerId = summonerId
        )
    }

    private fun coordHash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263 + 9187 * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }
}
