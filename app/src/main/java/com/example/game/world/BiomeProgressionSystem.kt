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
    const val MIN_DUNGEON_FLOOR: Int = -28
    const val DUNGEON_DEPTH_STEP_LEVELS: Int = 6

    /** Highest monster level anywhere in Rookvale (surface and every dungeon beneath it). */
    const val ROOKVALE_MAX_LEVEL: Int = 20

    /**
     * Maps any Lower Boss Floor (`-16..-28`) back to its paired Upper Dungeon Floor so biome
     * anchoring and level scaling stay strictly aligned with its host dungeon (keeping all 6
     * Rookvale Heartland dungeons & their lower boss lairs strictly within Levels 1-20).
     */
    fun parentUpperFloorFor(floorId: Int): Int = when (floorId) {
        -16 -> -1   // Briar Brood Sanctum -> Briar Catacombs
        -17 -> -11  // Foreman's Deep Vein -> NW Copper Quarry Mines
        -18 -> -12  // Crag Matriarch's Roost -> North Mountain Cavern
        -19 -> -13  // Heart-Root Abyss -> Dark Forest Sinkhole
        -20 -> -14  // The Sealed Granite Vault -> Crag Deep Hollow
        -21 -> -2   // Molten Pit Throne -> Watchwood Caverns
        -22 -> -5   // Kragor's Deep Quarry Pit -> The Grimmaw Quarry
        -23 -> -6   // Vaelgor's Inner Sanctum -> The Gorek Labyrinth
        -24 -> -7   // Sunken Sluice Depths -> The Sunken Mire Vaults
        -25 -> -8   // Ziggurat Sun-Altar Chamber -> The Overgrown Ziggurat Vault
        -26 -> -9   // Barrow Sovereign's Tomb -> The Blightwood Barrow Catacombs
        -27 -> -10  // Heart of the Crucible -> The Ashen Caldera Foundry
        -28 -> -15  // Caldera Dragon Throne -> The Ember Sanctum
        else -> floorId
    }

    fun isLowerBossFloor(floorId: Int): Boolean = floorId in -28..-16

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
     * Canonical surface biome anchor for each underground dungeon floor so deeper floors,
     * separated Rookvale caves (-1, -2, -11..-14), outer-continent biome dungeons (-5..-10, -15),
     * and their paired Lower Boss Lairs (-16..-28) scale strictly from their host biome.
     */
    fun surfaceBiomeForDungeonFloor(floorId: Int): OverlandBiomeTier = when (parentUpperFloorFor(floorId)) {
        -1 -> OverlandBiomeTier.ROOKVALE_HEARTLAND   // Briar Catacombs (Lvl 5-9) & Brood Sanctum boss (Lvl 10)
        -2 -> OverlandBiomeTier.ROOKVALE_HEARTLAND   // Watchwood Caverns (Lvl 9-13) & Molten Pit Throne boss (Lvl 14)
        -3 -> OverlandBiomeTier.ROOKVALE_HEARTLAND   // Abyssal Crypt (Lvl 13-17) & Demon Lord (Lvl 18)
        -4 -> OverlandBiomeTier.ROOKVALE_HEARTLAND   // Obsidian Wyrm Sanctum (Lvl 16-20) & Elder Dragon (Lvl 20)
        -5 -> OverlandBiomeTier.SUNSCORCH_BADLANDS   // Grimmaw Quarry & Deep Quarry Pit (Lvl 60-65)
        -6 -> OverlandBiomeTier.ASHEN_CALDERA        // Gorek Labyrinth & Inner Sanctum (Lvl 91-96+)
        -7 -> OverlandBiomeTier.WHISPERING_MARSH     // Sunken Mire Vaults & Sluice Depths (Lvl 26-31)
        -8 -> OverlandBiomeTier.WHISPERING_MARSH     // Overgrown Ziggurat & Sun-Altar (Lvl 32-37)
        -9 -> OverlandBiomeTier.FROSTPEAK_HIGHLANDS  // Blightwood Barrow & Sovereign's Tomb (Lvl 48-53)
        -10 -> OverlandBiomeTier.ASHEN_CALDERA       // Ashen Caldera Foundry & Crucible (Lvl 67-72+)
        -11 -> OverlandBiomeTier.ROOKVALE_HEARTLAND  // NW Copper Quarry & Deep Vein (Lvl 5-10)
        -12 -> OverlandBiomeTier.ROOKVALE_HEARTLAND  // North Mountain Cavern & Roost (Lvl 7-12)
        -13 -> OverlandBiomeTier.ROOKVALE_HEARTLAND  // Dark Forest Sinkhole & Heart-Root Abyss (Lvl 9-14)
        -14 -> OverlandBiomeTier.ROOKVALE_HEARTLAND  // Crag Deep Hollow & Sealed Vault (Lvl 13-18)
        -15 -> OverlandBiomeTier.ASHEN_CALDERA       // Ember Sanctum & Caldera Dragon Throne (Lvl 67-72+)
        else -> OverlandBiomeTier.ROOKVALE_HEARTLAND
    }

    /**
     * Effective progression depth multiplier for a dungeon floor.
     * Classic descending floors (-1..-6) scale directly by `abs(floorId)`; dedicated
     * continental & separated site dungeons (-7..-15) scale at depth 1 or 2 above their host biome;
     * paired Lower Boss Floors (-16..-28) inherit their parent dungeon's depth.
     */
    fun effectiveDungeonDepth(floorId: Int): Int = when (parentUpperFloorFor(floorId)) {
        -7, -9, -11, -12, -13 -> 1
        -8, -10, -14, -15 -> 2
        else -> abs(parentUpperFloorFor(floorId)).coerceAtLeast(1)
    }

    /**
     * Core dungeon progression formula: `surfaceMinLevel + effectiveDungeonDepth(floorId) * 6`,
     * with fine-tuned early Rookvale starter bands for NW Quarry (-11/-17: Lvl 5-10) and Dark Forest
     * Sinkhole (-13/-19: Lvl 9-14). Regular encounters span `[base, base + 4]`; bosses scale to `base + 5`.
     */
    fun dungeonBaseLevel(surfaceMinLevel: Int, floorId: Int): Int {
        val canonical = parentUpperFloorFor(floorId)
        if (surfaceMinLevel == OverlandBiomeTier.ROOKVALE_HEARTLAND.minLevel) {
            when (canonical) {
                // Deep Delve chain under Rookvale climbs steadily through Lv 5-20
                -1 -> return 5
                -2 -> return 9
                -3 -> return 13
                -4 -> return 16
                -11 -> return 5
                -13 -> return 9
            }
        }
        val depth = effectiveDungeonDepth(canonical)
        return (surfaceMinLevel.coerceAtLeast(1) + depth * DUNGEON_DEPTH_STEP_LEVELS)
    }

    fun dungeonMonsterLevel(
        surfaceMinLevel: Int,
        floorId: Int,
        isBoss: Boolean = false,
        spreadOffset: Int = 0
    ): Int {
        val base = dungeonBaseLevel(surfaceMinLevel, floorId)
        val level = if (isBoss) base + 5 else base + spreadOffset.coerceIn(0, 4)
        // Everything beneath Rookvale stays within the Heartland's Lv 1-20 band
        return if (surfaceMinLevel == OverlandBiomeTier.ROOKVALE_HEARTLAND.minLevel) level.coerceAtMost(ROOKVALE_MAX_LEVEL) else level
    }

    fun dungeonRecommendedLevelLabel(floorId: Int, surfaceMinLevel: Int = surfaceBiomeForDungeonFloor(floorId).minLevel): String {
        val canonical = parentUpperFloorFor(floorId)
        val base = dungeonBaseLevel(surfaceMinLevel, canonical)
        val top = if (surfaceMinLevel == OverlandBiomeTier.ROOKVALE_HEARTLAND.minLevel) (base + 5).coerceAtMost(ROOKVALE_MAX_LEVEL) else base + 5
        return if (canonical == -6 || canonical == -10 || canonical == -15) "Lvl $base-${top}+" else "Lvl $base-$top"
    }

    /**
     * Resolves the strict biome-locked level for an overland (Floor 0) monster spawn.
     * Rookvale Heartland spawns remain strictly in Levels 1-20 (grading gently from 1-5 near
     * Rookvale City gates up to 12-20 toward outer Heartland borders & ruins);
     * all outer biomes strictly populate their locked level ranges up to 75+.
     */
    fun resolveOverlandSpawnLevel(x: Int, y: Int, type: MonsterType): Int =
        BiomeMonsterSpawner.resolveOverlandLevel(x, y, type)

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
