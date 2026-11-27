package com.example.game.world

import com.example.game.model.CraftingStationType
import com.example.game.model.OreVeinKind
import com.example.game.model.TileType
import com.example.game.model.WORLD_CHUNK_SIZE
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class HarborPort(
    val id: String,
    val name: String,
    val regionId: String,
    val captainName: String,
    val dockX: Int,
    val dockY: Int,
    val arrivalX: Int,
    val arrivalY: Int,
    val description: String,
    val recommendedLevel: String,
    val dangerSeverity: Int
)

enum class SettlementTier {
    EMPIRE,
    TOWN,
    FORWARD_CAMP
}

data class SettlementZone(
    val id: String,
    val name: String,
    val regionId: String,
    val centerX: Int,
    val centerY: Int,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val isSafeZone: Boolean = true,
    val ideology: BiomeIdeology = BiomeIdeology.HEARTLAND,
    val tier: SettlementTier = SettlementTier.TOWN,
    val levelRangeLabel: String = "Lvl 1-20"
) {
    fun contains(x: Int, y: Int): Boolean = x in minX..maxX && y in minY..maxY
}

data class OverworldOrePlacement(
    val x: Int,
    val y: Int,
    val kind: OreVeinKind
)

data class WorldMapPin(
    val id: String,
    val label: String,
    val x: Int,
    val y: Int,
    val category: WorldPinCategory,
    val subtitle: String = "",
    val levelRange: String = "",
    val minZoomToShow: Float = 1.0f
)

data class BiomeCraftingStallSpot(
    val station: CraftingStationType,
    val settlementName: String,
    val floor: Int,
    val x: Int,
    val y: Int
)

enum class WorldPinCategory {
    EMPIRE,
    CITY,
    CAMP,
    FORGE,
    FERRY,
    DUNGEON
}

/**
 * Deterministic 1600x1200 (4x3 grid of 400x400 regions) overland geographic blueprint
 * modeled proportionally on Rookvale's painted parchment world map.
 *
 * Enforces:
 * - Five locked overland biome tiers (Levels 1-75+) with biome-ideology empires, towns, and camps
 * - Biome-tiered ore veins (Copper/Tin/Bog Iron in Heartland & Marsh; Vesperite in Badlands/Frostpeak;
 *   Cinderite in Ashen Caldera & Blighted Wastes)
 * - Strict ocean coastline validation via [CoastalPortValidator] so ports NEVER spawn inland
 */
object WorldBiomeBlueprint {

    private val harborCandidates: List<HarborPortCandidate> = listOf(
        HarborPortCandidate(
            id = "rookvale_harbor",
            name = "Rookvale Royal Harbor",
            regionId = "rookvale_valley",
            captainName = "Captain Vance",
            candidateX = 1560,
            candidateY = 592,
            roadTargetX = 1510,
            roadTargetY = 592,
            description = "Eastern Ocean capital pier & royal trade hub",
            recommendedLevel = OverlandBiomeTier.ROOKVALE_HEARTLAND.levelRangeLabel,
            dangerSeverity = OverlandBiomeTier.ROOKVALE_HEARTLAND.dangerSeverity,
            preferredRegionBounds = 1525..1588,
            preferredRegionYBounds = 470..710
        ),
        HarborPortCandidate(
            id = "mirewatch_bay",
            name = "Mirewatch Bay Port",
            regionId = "alder_sea_plains",
            captainName = "Captain Helena",
            candidateX = 638,
            candidateY = 560,
            roadTargetX = 545,
            roadTargetY = 595,
            description = "Western marsh peninsula anchorage beside Mirewatch Citadel",
            recommendedLevel = OverlandBiomeTier.WHISPERING_MARSH.levelRangeLabel,
            dangerSeverity = OverlandBiomeTier.WHISPERING_MARSH.dangerSeverity,
            preferredRegionBounds = 580..720,
            preferredRegionYBounds = 490..660
        ),
        HarborPortCandidate(
            id = "duskport_quay",
            name = "Duskport Coastal Quay",
            regionId = "blightwood_reach",
            captainName = "Captain Thorne",
            candidateX = 855,
            candidateY = 250,
            roadTargetX = 904,
            roadTargetY = 250,
            description = "Northern Frostbay Strait gateway to Blighted Wastes",
            recommendedLevel = OverlandBiomeTier.ASHEN_CALDERA.levelRangeLabel,
            dangerSeverity = OverlandBiomeTier.ASHEN_CALDERA.dangerSeverity,
            preferredRegionBounds = 810..910,
            preferredRegionYBounds = 170..330
        ),
        HarborPortCandidate(
            id = "sunspire_dock",
            name = "Sunspire Oasis Dock",
            regionId = "sunspire_desert",
            captainName = "Captain Tariq",
            candidateX = 486,
            candidateY = 855,
            roadTargetX = 375,
            roadTargetY = 790,
            description = "Sun-drenched badlands ocean harbor serving Sunspire Empire",
            recommendedLevel = OverlandBiomeTier.SUNSCORCH_BADLANDS.levelRangeLabel,
            dangerSeverity = OverlandBiomeTier.SUNSCORCH_BADLANDS.dangerSeverity,
            preferredRegionBounds = 390..540,
            preferredRegionYBounds = 790..920
        ),
        HarborPortCandidate(
            id = "sunleaf_pier",
            name = "Sunleaf Jungle Pier",
            regionId = "emerald_jungle_east",
            captainName = "Captain Orin",
            candidateX = 960,
            candidateY = 914,
            roadTargetX = 960,
            roadTargetY = 995,
            description = "Raised canopy timber pier jutting into the Central Sea",
            recommendedLevel = OverlandBiomeTier.WHISPERING_MARSH.levelRangeLabel,
            dangerSeverity = OverlandBiomeTier.WHISPERING_MARSH.dangerSeverity,
            preferredRegionBounds = 890..1040,
            preferredRegionYBounds = 870..965
        ),
        HarborPortCandidate(
            id = "ember_landing",
            name = "Ember Isles Landing",
            regionId = "ember_isles",
            captainName = "Captain Kael",
            candidateX = 1406,
            candidateY = 952,
            roadTargetX = 1462,
            roadTargetY = 952,
            description = "Volcanic eastern archipelago harbor & lighthouse cove",
            recommendedLevel = OverlandBiomeTier.ASHEN_CALDERA.levelRangeLabel,
            dangerSeverity = OverlandBiomeTier.ASHEN_CALDERA.dangerSeverity,
            preferredRegionBounds = 1365..1448,
            preferredRegionYBounds = 900..1020
        )
    )

    /**
     * Strictly validated ocean-coastline Harbor Ports snapped via [CoastalPortValidator].
     * Every port is guaranteed to sit 1-2 tiles from an open Sea/Ocean body (>= 25 connected tiles).
     */
    val validatedCoastalPorts: List<ValidatedCoastalPort> by lazy {
        harborCandidates.map { candidate ->
            CoastalPortValidator.validateAndSnapPort(candidate) { tx, ty ->
                rawOverlandTerrainAt(tx, ty)
            }
        }
    }

    val harborPorts: List<HarborPort> by lazy {
        validatedCoastalPorts.map { it.port }
    }

    private val validatedPierLookup: Set<Long> by lazy {
        HashSet<Long>(512).apply {
            validatedCoastalPorts.forEach { addAll(it.pierTiles) }
        }
    }

    private val validatedBoatLookup: Set<Long> by lazy {
        HashSet<Long>(32).apply {
            validatedCoastalPorts.forEach { addAll(it.boatTiles) }
        }
    }

    /**
     * Biome-ideology Empires, Towns, and Forward Camps across all 5 overland biome tiers.
     * Each settlement uses distinct cutaway walls, walkways, plazas, and light fixtures.
     */
    val secondarySettlements: List<SettlementZone> = listOf(
        // Heartland & Green Meadows (Lvl 1-20) — Half-timbered frontier camp
        SettlementZone(
            id = "spirewood_crossing",
            name = "Spirewood Crossing Camp",
            regionId = "spirewood_west",
            centerX = 1042,
            centerY = 592,
            minX = 1031,
            minY = 581,
            maxX = 1053,
            maxY = 603,
            ideology = BiomeIdeology.HEARTLAND,
            tier = SettlementTier.FORWARD_CAMP,
            levelRangeLabel = OverlandBiomeTier.ROOKVALE_HEARTLAND.levelRangeLabel
        ),
        // Whispering Marsh & Mirelands (Lvl 20-32) — Raised plank walkways, stilt huts & campfires
        SettlementZone(
            id = "mirewatch_fortress",
            name = "Mirewatch Stilt Citadel",
            regionId = "alder_sea_plains",
            centerX = 545,
            centerY = 595,
            minX = BiomeCities.MIRE_MIN_X,
            minY = BiomeCities.MIRE_MIN_Y,
            maxX = BiomeCities.MIRE_MAX_X,
            maxY = BiomeCities.MIRE_MAX_Y,
            ideology = BiomeIdeology.MARSHLAND,
            tier = SettlementTier.EMPIRE,
            levelRangeLabel = OverlandBiomeTier.WHISPERING_MARSH.levelRangeLabel
        ),
        SettlementZone(
            id = "boghollow_camp",
            name = "Boghollow Stilt Camp",
            regionId = "writhing_marshes",
            centerX = 358,
            centerY = 560,
            minX = 348,
            minY = 550,
            maxX = 368,
            maxY = 570,
            ideology = BiomeIdeology.MARSHLAND,
            tier = SettlementTier.FORWARD_CAMP,
            levelRangeLabel = OverlandBiomeTier.WHISPERING_MARSH.levelRangeLabel
        ),
        SettlementZone(
            id = "sunleaf_village",
            name = "Sunleaf Stilt Village",
            regionId = "emerald_jungle_east",
            centerX = 960,
            centerY = 995,
            minX = 944,
            minY = 980,
            maxX = 976,
            maxY = 1010,
            ideology = BiomeIdeology.MARSHLAND,
            tier = SettlementTier.TOWN,
            levelRangeLabel = OverlandBiomeTier.WHISPERING_MARSH.levelRangeLabel
        ),
        // Sunscorch Badlands & Arid Dunes (Lvl 30-45) — Sandstone walls, shaded bazaar stalls & oasis wells
        SettlementZone(
            id = "sunspire_oasis",
            name = "Sunspire Oasis Empire",
            regionId = "sunspire_desert",
            centerX = 375,
            centerY = 790,
            minX = BiomeCities.SUN_MIN_X,
            minY = BiomeCities.SUN_MIN_Y,
            maxX = BiomeCities.SUN_MAX_X,
            maxY = BiomeCities.SUN_MAX_Y,
            ideology = BiomeIdeology.BADLANDS,
            tier = SettlementTier.EMPIRE,
            levelRangeLabel = OverlandBiomeTier.SUNSCORCH_BADLANDS.levelRangeLabel
        ),
        SettlementZone(
            id = "scorchwind_camp",
            name = "Scorchwind Nomad Camp",
            regionId = "sunspire_desert",
            centerX = 315,
            centerY = 912,
            minX = 305,
            minY = 902,
            maxX = 325,
            maxY = 922,
            ideology = BiomeIdeology.BADLANDS,
            tier = SettlementTier.FORWARD_CAMP,
            levelRangeLabel = OverlandBiomeTier.SUNSCORCH_BADLANDS.levelRangeLabel
        ),
        // Frostpeak Highlands & Boreal Woods (Lvl 42-58) — Heavy mortared granite citadels & roaring hearth-forges
        SettlementZone(
            id = "frostpeak_citadel",
            name = "Frostpeak Granite Citadel",
            regionId = "frostcrest_highlands",
            centerX = 1396,
            centerY = 204,
            minX = 1378,
            minY = 188,
            maxX = 1414,
            maxY = 220,
            ideology = BiomeIdeology.FROSTPEAK,
            tier = SettlementTier.EMPIRE,
            levelRangeLabel = OverlandBiomeTier.FROSTPEAK_HIGHLANDS.levelRangeLabel
        ),
        SettlementZone(
            id = "boreal_watch_camp",
            name = "Boreal Watch Camp",
            regionId = "frostcrest_highlands",
            centerX = 1268,
            centerY = 258,
            minX = 1258,
            minY = 248,
            maxX = 1278,
            maxY = 268,
            ideology = BiomeIdeology.FROSTPEAK,
            tier = SettlementTier.FORWARD_CAMP,
            levelRangeLabel = OverlandBiomeTier.FROSTPEAK_HIGHLANDS.levelRangeLabel
        ),
        // Ashen Caldera & Blighted Wastes (Lvl 55-75+) — Obsidian/iron-reinforced fortresses, war banners & braziers
        SettlementZone(
            id = "ashen_bastion",
            name = "Ashen Bastion Empire",
            regionId = "caldera_peaks",
            centerX = 386,
            centerY = 260,
            minX = 370,
            minY = 246,
            maxX = 402,
            maxY = 274,
            ideology = BiomeIdeology.ASHEN_BLIGHT,
            tier = SettlementTier.EMPIRE,
            levelRangeLabel = OverlandBiomeTier.ASHEN_CALDERA.levelRangeLabel
        ),
        SettlementZone(
            id = "duskport_town",
            name = "Duskport Shadow Keep",
            regionId = "blightwood_reach",
            centerX = 904,
            centerY = 250,
            minX = 888,
            minY = 236,
            maxX = 920,
            maxY = 264,
            ideology = BiomeIdeology.ASHEN_BLIGHT,
            tier = SettlementTier.TOWN,
            levelRangeLabel = OverlandBiomeTier.ASHEN_CALDERA.levelRangeLabel
        ),
        SettlementZone(
            id = "ember_outpost",
            name = "Ember Watch Citadel",
            regionId = "ember_isles",
            centerX = 1462,
            centerY = 952,
            minX = 1448,
            minY = 938,
            maxX = 1476,
            maxY = 966,
            ideology = BiomeIdeology.ASHEN_BLIGHT,
            tier = SettlementTier.EMPIRE,
            levelRangeLabel = OverlandBiomeTier.ASHEN_CALDERA.levelRangeLabel
        )
    )

    /**
     * Canonical primary Biome Crafting Stall coordinates across all 5 overland biome tiers.
     * Players must stand within 2 tiles of a biome's crafting stall to forge its recipes.
     */
    val biomeCraftingStalls: List<BiomeCraftingStallSpot> = listOf(
        BiomeCraftingStallSpot(
            station = CraftingStationType.ROOKVALE_TOWN_FORGE,
            settlementName = "Rookvale City",
            floor = 0,
            x = 1421,
            y = 601
        ),
        BiomeCraftingStallSpot(
            station = CraftingStationType.STILT_TOWN_MIRE_BENCH,
            settlementName = "Mirewatch Stilt Citadel",
            floor = 0,
            x = 550,
            y = 591
        ),
        BiomeCraftingStallSpot(
            station = CraftingStationType.SUNSCORCH_OASIS_SMELTERY,
            settlementName = "Sunspire Oasis Empire",
            floor = 0,
            x = 380,
            y = 786
        ),
        BiomeCraftingStallSpot(
            station = CraftingStationType.FROSTPEAK_GREAT_HEARTH,
            settlementName = "Frostpeak Granite Citadel",
            floor = 0,
            x = 1401,
            y = 200
        ),
        BiomeCraftingStallSpot(
            station = CraftingStationType.ASHEN_OBSIDIAN_FOUNDRY,
            settlementName = "Ashen Bastion Empire",
            floor = 0,
            x = 391,
            y = 256
        )
    )

    /**
     * Deterministic biome-tiered outer-overworld mining veins:
     * - Heartland (Lvl 1-20): Copper, Tin & Bog Iron
     * - Whispering Marsh (Lvl 20-32): Mire-Silver, Peat-Coal & Bog Iron
     * - Sunscorch Badlands (Lvl 30-45): Vesperite & Bog Iron
     * - Frostpeak Highlands (Lvl 42-58): Glacier-Quartz & Frost-Iron
     * - Ashen Caldera & Blighted Wastes (Lvl 55-75+): Cinderite & Vesperite
     */
    val outerOverworldOres: List<OverworldOrePlacement> = listOf(
        // Spirewood Meadows (Heartland Lvl 1-20 — Copper/Tin/Bog Iron only)
        OverworldOrePlacement(1025, 538, OreVeinKind.COPPER),
        OverworldOrePlacement(1042, 548, OreVeinKind.TIN),
        OverworldOrePlacement(1118, 635, OreVeinKind.BOG_IRON),
        // Whispering Marsh & Mirelands (Lvl 20-32 — Mire-Silver, Peat-Coal & Bog Iron)
        OverworldOrePlacement(255, 495, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(268, 522, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(332, 540, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(218, 558, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(486, 568, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(503, 545, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(680, 995, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(748, 1012, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(1018, 1022, OreVeinKind.BOG_IRON),
        // Sunscorch Badlands & Arid Dunes (Lvl 30-45 — Vesperite veins + Bog Iron)
        OverworldOrePlacement(315, 765, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(424, 822, OreVeinKind.VESPERITE),
        OverworldOrePlacement(284, 1005, OreVeinKind.VESPERITE),
        OverworldOrePlacement(215, 1034, OreVeinKind.VESPERITE),
        // Frostpeak Highlands & Boreal Woods (Lvl 42-58 — Glacier-Quartz & Frost-Iron veins)
        OverworldOrePlacement(1278, 238, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(1292, 248, OreVeinKind.GLACIER_QUARTZ),
        OverworldOrePlacement(1352, 228, OreVeinKind.GLACIER_QUARTZ),
        OverworldOrePlacement(1442, 236, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(1460, 248, OreVeinKind.GLACIER_QUARTZ),
        // Ashen Caldera & Blighted Wastes (Lvl 55-75+ — Cinderite & Vesperite veins)
        OverworldOrePlacement(278, 224, OreVeinKind.VESPERITE),
        OverworldOrePlacement(286, 232, OreVeinKind.CINDERITE),
        OverworldOrePlacement(355, 240, OreVeinKind.CINDERITE),
        OverworldOrePlacement(364, 248, OreVeinKind.CINDERITE),
        OverworldOrePlacement(940, 215, OreVeinKind.VESPERITE),
        OverworldOrePlacement(965, 232, OreVeinKind.CINDERITE),
        OverworldOrePlacement(1062, 220, OreVeinKind.CINDERITE),
        OverworldOrePlacement(1485, 932, OreVeinKind.CINDERITE),
        OverworldOrePlacement(1511, 941, OreVeinKind.CINDERITE),
        // Whispering Marsh veins (Mining 20): mire-silver & peat-coal
        OverworldOrePlacement(376, 400, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(154, 622, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(1042, 898, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(430, 928, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(880, 1060, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(814, 1126, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(280, 400, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(580, 682, OreVeinKind.MIRE_SILVER),
        OverworldOrePlacement(724, 1120, OreVeinKind.PEAT_COAL),
        OverworldOrePlacement(184, 400, OreVeinKind.MIRE_SILVER),
        // Sunscorch Badlands veins (Mining 10-30): vesperite & bog iron
        OverworldOrePlacement(394, 964, OreVeinKind.VESPERITE),
        OverworldOrePlacement(220, 1138, OreVeinKind.VESPERITE),
        OverworldOrePlacement(118, 1048, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(124, 946, OreVeinKind.VESPERITE),
        OverworldOrePlacement(394, 1066, OreVeinKind.VESPERITE),
        OverworldOrePlacement(328, 1132, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(226, 802, OreVeinKind.VESPERITE),
        OverworldOrePlacement(82, 1120, OreVeinKind.VESPERITE),
        OverworldOrePlacement(454, 748, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(232, 946, OreVeinKind.VESPERITE),
        OverworldOrePlacement(388, 886, OreVeinKind.VESPERITE),
        OverworldOrePlacement(274, 1084, OreVeinKind.BOG_IRON),
        // Frostpeak Highlands veins (Mining 42): frost-iron & glacier-quartz
        OverworldOrePlacement(1312, 46, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(1240, 118, OreVeinKind.GLACIER_QUARTZ),
        OverworldOrePlacement(508, 268, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(442, 334, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(508, 172, OreVeinKind.GLACIER_QUARTZ),
        OverworldOrePlacement(1588, 256, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(1522, 322, OreVeinKind.GLACIER_QUARTZ),
        OverworldOrePlacement(1588, 160, OreVeinKind.FROST_IRON),
        OverworldOrePlacement(418, 166, OreVeinKind.GLACIER_QUARTZ),
        OverworldOrePlacement(1414, 334, OreVeinKind.FROST_IRON),
        // Ashen Caldera veins (Mining 52): cinderite
        OverworldOrePlacement(1192, 166, OreVeinKind.CINDERITE),
        OverworldOrePlacement(1048, 310, OreVeinKind.CINDERITE)
    )

    private val outerOreLookup: Map<Long, TileType> = outerOverworldOres.associate { ore ->
        val tile = when (ore.kind) {
            OreVeinKind.COPPER -> TileType.ORE_COPPER
            OreVeinKind.TIN -> TileType.ORE_TIN
            OreVeinKind.BOG_IRON -> TileType.ORE_BOG_IRON
            OreVeinKind.MIRE_SILVER -> TileType.ORE_MIRE_SILVER
            OreVeinKind.PEAT_COAL -> TileType.ORE_PEAT_COAL
            OreVeinKind.VESPERITE -> TileType.ORE_VESPERITE
            OreVeinKind.GLACIER_QUARTZ -> TileType.ORE_GLACIER_QUARTZ
            OreVeinKind.FROST_IRON -> TileType.ORE_FROST_IRON
            OreVeinKind.CINDERITE -> TileType.ORE_CINDERITE
        }
        ((ore.y.toLong() shl 32) or (ore.x.toLong() and 0xFFFFFFFFL)) to tile
    }

    /**
     * World Map pins calibrated to the 1600x1200 parchment map.
     * Empires, Cities, Ocean Ports, and Major Dungeons show at overview zoom (`1.0x`);
     * Forward Camps (`CAMP`) and exact settlement level badges auto-reveal when zoomed in (`>= 1.6x`).
     */
    val worldMapPins: List<WorldMapPin> by lazy {
        buildList {
            // Capital Heartland Empire
            add(
                WorldMapPin(
                    id = "empire_rookvale",
                    label = "Rookvale City",
                    x = 1396,
                    y = 592,
                    category = WorldPinCategory.EMPIRE,
                    subtitle = "Heartland Capital • Half-Timbered Sanctuary",
                    levelRange = OverlandBiomeTier.ROOKVALE_HEARTLAND.levelRangeLabel,
                    minZoomToShow = 1.0f
                )
            )
            // All Biome-Ideology Empires, Towns & Forward Camps
            for (s in secondarySettlements) {
                val category = when (s.tier) {
                    SettlementTier.EMPIRE -> WorldPinCategory.EMPIRE
                    SettlementTier.TOWN -> WorldPinCategory.CITY
                    SettlementTier.FORWARD_CAMP -> WorldPinCategory.CAMP
                }
                val minZoom = if (s.tier == SettlementTier.FORWARD_CAMP) 1.6f else 1.0f
                val styleTag = when (s.ideology) {
                    BiomeIdeology.HEARTLAND -> "Heartland Camp"
                    BiomeIdeology.MARSHLAND -> "Marsh Stilt Stronghold"
                    BiomeIdeology.BADLANDS -> "Sandstone Oasis"
                    BiomeIdeology.FROSTPEAK -> "Granite Citadel"
                    BiomeIdeology.ASHEN_BLIGHT -> "Obsidian Fortress"
                }
                add(
                    WorldMapPin(
                        id = "settlement_${s.id}",
                        label = s.name,
                        x = s.centerX,
                        y = s.centerY,
                        category = category,
                        subtitle = "$styleTag • ${s.levelRangeLabel}",
                        levelRange = s.levelRangeLabel,
                        minZoomToShow = minZoom
                    )
                )
            }
            // 5 Dedicated Biome Crafting Stalls (auto-reveal forge icons & names when zoomed in)
            for (stall in biomeCraftingStalls) {
                add(
                    WorldMapPin(
                        id = "forge_${stall.station.name.lowercase()}",
                        label = stall.station.displayName,
                        x = stall.x,
                        y = stall.y,
                        category = WorldPinCategory.FORGE,
                        subtitle = "${stall.station.biomeLabel.substringBefore(" (")} Forge • ${stall.settlementName}",
                        levelRange = stall.station.biomeLabel.substringAfter("(").substringBefore(")"),
                        minZoomToShow = 1.6f
                    )
                )
            }
            // All 6 Strictly Coastline-Validated Ocean Ferry Ports
            for (port in harborPorts) {
                add(
                    WorldMapPin(
                        id = "ferry_${port.id}",
                        label = port.name,
                        x = port.dockX,
                        y = port.dockY,
                        category = WorldPinCategory.FERRY,
                        subtitle = "${port.captainName} • Ocean Harbor",
                        levelRange = port.recommendedLevel,
                        minZoomToShow = 1.0f
                    )
                )
            }
            // Rookvale Heartland Open Rustic Outlaw Camps (auto-reveal when zoomed in)
            add(WorldMapPin("camp_briar_outlaws", "Briar Outlaw Camp", 1288, 720, WorldPinCategory.CAMP, "Rookvale Outlaw Camp • Lvl 12-16", "Lvl 12-16", 1.6f))
            add(WorldMapPin("camp_watchwood_outlaws", "Watchwood Outlaw Camp", 1512, 720, WorldPinCategory.CAMP, "Rookvale Outlaw Camp • Lvl 15-20", "Lvl 15-20", 1.6f))
            // Major Dungeon Entrances (zero inside Rookvale City; 6 separate Lv 1-20 Rookvale dungeons + 7 outer continent floors)
            add(WorldMapPin("dng_nw_quarry", "NW Copper Quarry Mines (-11)", 1292, 496, WorldPinCategory.DUNGEON, "Floor -11 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-11)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-11), 1.6f))
            add(WorldMapPin("dng_ne_mountain", "North Mountain Cavern (-12)", 1464, 484, WorldPinCategory.DUNGEON, "Floor -12 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-12)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-12), 1.6f))
            add(WorldMapPin("dng_east_sinkhole", "Dark Forest Sinkhole (-13)", 1512, 606, WorldPinCategory.DUNGEON, "Floor -13 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-13)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-13), 1.6f))
            add(WorldMapPin("dng_sw_ruins", "Briar Catacombs (-1)", 1284, 716, WorldPinCategory.DUNGEON, "Floor -1 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-1)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-1), 1.6f))
            add(WorldMapPin("dng_ne_caverns", "Crag Deep Hollow (-14)", 1508, 466, WorldPinCategory.DUNGEON, "Floor -14 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-14)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-14), 1.6f))
            add(WorldMapPin("dng_se_ruins", "Watchwood Caverns (-2)", 1516, 716, WorldPinCategory.DUNGEON, "Floor -2 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-2)} • Deep Delve", BiomeProgressionRules.dungeonRecommendedLevelLabel(-2), 1.6f))
            add(WorldMapPin("dng_marsh", "Sunken Mire Vaults (-7)", 295, 515, WorldPinCategory.DUNGEON, "Floor -7 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-7)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-7), 1.0f))
            add(WorldMapPin("dng_jungle", "Overgrown Ziggurat (-8)", 720, 1020, WorldPinCategory.DUNGEON, "Floor -8 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-8)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-8), 1.0f))
            add(WorldMapPin("dng_blightwood", "Blightwood Barrow (-9)", 1020, 175, WorldPinCategory.DUNGEON, "Floor -9 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-9)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-9), 1.0f))
            add(WorldMapPin("dng_frostcrest", "Grimmaw Quarry (-5)", 1320, 260, WorldPinCategory.DUNGEON, "Floor -5 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-5)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-5), 1.0f))
            add(WorldMapPin("dng_badlands", "Gorek Labyrinth (-6)", 242, 1020, WorldPinCategory.DUNGEON, "Floor -6 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-6)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-6), 1.0f))
            add(WorldMapPin("dng_caldera", "Ashen Caldera Foundry (-10)", 318, 252, WorldPinCategory.DUNGEON, "Floor -10 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-10)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-10), 1.0f))
            add(WorldMapPin("dng_murkfen", "Murkfen Hollow (-29)", FrontierSiteFloors.MURKFEN_ENTRY_X, FrontierSiteFloors.MURKFEN_ENTRY_Y, WorldPinCategory.DUNGEON, "Floor -29 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-29)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-29), 1.0f))
            add(WorldMapPin("dng_tomb", "Tomb of Akhet (-30)", FrontierSiteFloors.TOMB_ENTRY_X, FrontierSiteFloors.TOMB_ENTRY_Y, WorldPinCategory.DUNGEON, "Floor -30 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-30)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-30), 1.0f))
            add(WorldMapPin("dng_rift", "Scorchglass Rift (-31)", FrontierSiteFloors.RIFT_ENTRY_X, FrontierSiteFloors.RIFT_ENTRY_Y, WorldPinCategory.DUNGEON, "Floor -31 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-31)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-31), 1.0f))
            add(WorldMapPin("dng_ember", "The Ember Sanctum (-15)", 1510, 975, WorldPinCategory.DUNGEON, "Floor -15 • ${BiomeProgressionRules.dungeonRecommendedLevelLabel(-15)}", BiomeProgressionRules.dungeonRecommendedLevelLabel(-15), 1.0f))
        }
    }

    // --- Road Network Segments connecting every empire, town, forward camp, harbor, and dungeon entrance ---
    private data class RoadSeg(
        val x0: Int,
        val y0: Int,
        val x1: Int,
        val y1: Int,
        val halfWidth: Int,
        val tile: TileType
    )

    private val baseRoadSegments: List<RoadSeg> = listOf(
        // East-Central Heartland Connections (into Rookvale City borders at 1200,592 / 1396,400 / 1396,799)
        RoadSeg(942, 592, 1205, 592, 2, TileType.COBBLESTONE), // Spirewood Royal Highway -> Rookvale West
        RoadSeg(1396, 150, 1396, 405, 2, TileType.COBBLESTONE), // Frostpeak Citadel Highway -> Rookvale North
        RoadSeg(1396, 260, 1268, 258, 2, TileType.DIRT_FLOOR), // Frostpeak Grimmaw & Boreal Watch Camp spur
        RoadSeg(1396, 795, 1396, 952, 2, TileType.DIRT_FLOOR), // South Rookvale -> Ember Causeway
        RoadSeg(1396, 952, 1465, 952, 2, TileType.COBBLESTONE), // Ember Causeway -> Ember Watch Citadel
        RoadSeg(1465, 952, 1510, 975, 1, TileType.DIRT_FLOOR), // Ember Watch -> Ember Sanctum

        // Eastern Continent North-South Spine (Duskport <-> Blightwood <-> Spirewood Camp <-> Sunleaf)
        RoadSeg(868, 250, 1020, 250, 2, TileType.COBBLESTONE),
        RoadSeg(1020, 175, 1020, 250, 2, TileType.DIRT_FLOOR), // Blightwood Barrow spur
        RoadSeg(1020, 250, 1042, 592, 2, TileType.COBBLESTONE), // Blightwood -> Spirewood Crossing Camp
        RoadSeg(1042, 592, 1042, 760, 2, TileType.COBBLESTONE), // Spirewood -> South Bridgehead
        RoadSeg(1042, 985, 960, 995, 2, TileType.COBBLESTONE), // Bridgehead -> Sunleaf Stilt Village
        RoadSeg(960, 995, 720, 1020, 2, TileType.DIRT_FLOOR), // Sunleaf -> West Jungle Ruins
        RoadSeg(720, 1020, 645, 980, 2, TileType.DIRT_FLOOR), // West Jungle -> SW Bridge
        RoadSeg(760, 945, 760, 795, 1, TileType.STONE_BRIDGE), // Jungle -> Maelstrom Shrine Causeway

        // Western Continent Spine (Ashen Bastion <-> Writhing Marshes <-> Boghollow Camp <-> Mirewatch <-> Sunspire <-> Scorchwind <-> Badlands)
        RoadSeg(318, 252, 386, 260, 2, TileType.COBBLESTONE), // Ashen Caldera Shaft -> Ashen Bastion Empire
        RoadSeg(386, 260, 445, 212, 2, TileType.COBBLESTONE), // Ashen Bastion -> Frostbay Bridge West
        RoadSeg(318, 252, 295, 515, 2, TileType.DIRT_FLOOR), // Caldera -> Writhing Marshes Sinkhole
        RoadSeg(295, 515, 358, 560, 2, TileType.DIRT_FLOOR), // Sinkhole -> Boghollow Stilt Camp
        RoadSeg(358, 560, 375, 592, 2, TileType.DIRT_FLOOR), // Boghollow Camp -> Mirewatch West Road
        RoadSeg(375, 592, 545, 592, 2, TileType.COBBLESTONE), // Mirewatch Main Road
        RoadSeg(545, 592, 600, 592, 2, TileType.COBBLESTONE), // Mirewatch -> Central Alder Causeway
        RoadSeg(375, 592, 375, 790, 2, TileType.DIRT_FLOOR), // Western Road -> Sunspire Oasis Empire
        RoadSeg(375, 790, 455, 980, 2, TileType.DIRT_FLOOR), // Sunspire Oasis -> SW Jungle Bridge
        RoadSeg(375, 790, 315, 912, 2, TileType.DIRT_FLOOR), // Sunspire Oasis -> Scorchwind Nomad Camp
        RoadSeg(315, 912, 242, 1020, 2, TileType.DIRT_FLOOR) // Scorchwind Nomad Camp -> Badlands Gorek Gate
    )

    private val allRoadSegments: List<RoadSeg> by lazy {
        baseRoadSegments + validatedCoastalPorts.mapNotNull { vp ->
            // Link every validated ocean port's land anchor cleanly to its nearest inland highway/city
            if (vp.port.id == "rookvale_harbor") null
            else RoadSeg(
                x0 = vp.landAnchorX,
                y0 = vp.landAnchorY,
                x1 = vp.roadTargetX,
                y1 = vp.roadTargetY,
                halfWidth = 2,
                tile = TileType.COBBLESTONE
            )
        }
    }

    // The old inter-continental sea-bridges were removed: harbor ferry captains carry players
    // between continents instead.

    // --- Regional Dungeon Entrances outside Rookvale Valley ---
    private val outerPortals: Map<Long, TileType> = mapOf(
        packCoord(318, 252) to TileType.STAIRS_DOWN,   // Ashen Caldera Shaft -> Floor -2
        packCoord(295, 515) to TileType.ROPE_HOLE_DOWN,// Writhing Sinkhole -> Floor -1
        packCoord(1020, 175) to TileType.STAIRS_DOWN,  // Blightwood Barrow -> Floor -1
        packCoord(1320, 260) to TileType.STAIRS_DOWN,  // Frostpeak Grimmaw Ridge -> Floor -5
        packCoord(242, 1020) to TileType.STAIRS_DOWN,  // Badlands Gorek Gate -> Floor -6
        packCoord(720, 1020) to TileType.STAIRS_DOWN,  // Jungle Ruin Vault -> Floor -2
        packCoord(1510, 975) to TileType.STAIRS_DOWN,  // Ember Sanctum -> Floor -2
        packCoord(FrontierSiteFloors.MURKFEN_ENTRY_X, FrontierSiteFloors.MURKFEN_ENTRY_Y) to TileType.STAIRS_DOWN, // Murkfen Hollow -> Floor -29
        packCoord(FrontierSiteFloors.TOMB_ENTRY_X, FrontierSiteFloors.TOMB_ENTRY_Y) to TileType.STAIRS_DOWN,       // Tomb of Akhet -> Floor -30
        packCoord(FrontierSiteFloors.RIFT_ENTRY_X, FrontierSiteFloors.RIFT_ENTRY_Y) to TileType.STAIRS_DOWN        // Scorchglass Rift -> Floor -31
    )

    // --- Coarse Regional Waypoint Graph for Instant Cross-World Hierarchical Auto-Walk ---
    private data class WaypointNode(val id: String, val x: Int, val y: Int)

    private val waypointNodes: List<WaypointNode> = listOf(
        WaypointNode("rookvale_center", 1396, 592),
        WaypointNode("rookvale_west_gate", 1200, 592),
        WaypointNode("rookvale_east_harbor", 1558, 592),
        WaypointNode("rookvale_north_gate", 1396, 400),
        WaypointNode("rookvale_south_gate", 1396, 799),
        WaypointNode("frostcrest_citadel", 1396, 204),
        WaypointNode("frostcrest_hub", 1396, 260),
        WaypointNode("frostcrest_grimmaw", 1320, 260),
        WaypointNode("boreal_watch_camp", 1268, 258),
        WaypointNode("spirewood_cross", 1042, 592),
        WaypointNode("mirewatch", 545, 592),
        WaypointNode("mirewatch_west", 375, 592),
        WaypointNode("boghollow_camp", 358, 560),
        WaypointNode("writhing_marsh", 295, 515),
        WaypointNode("ashen_caldera", 318, 252),
        WaypointNode("ashen_bastion", 386, 260),
        WaypointNode("duskport", 904, 250),
        WaypointNode("blightwood_jct", 1020, 250),
        WaypointNode("sunspire_oasis", 375, 790),
        WaypointNode("scorchwind_camp", 315, 912),
        WaypointNode("sunspire_badlands", 242, 1020),
        WaypointNode("west_jungle", 720, 1020),
        WaypointNode("sunleaf_village", 960, 995),
        WaypointNode("ember_watch", 1462, 952)
    )

    private val waypointEdges: Map<String, List<String>> = buildMap {
        fun link(a: String, b: String) {
            put(a, (get(a) ?: emptyList()) + b)
            put(b, (get(b) ?: emptyList()) + a)
        }
        link("rookvale_center", "rookvale_west_gate")
        link("rookvale_center", "rookvale_east_harbor")
        link("rookvale_center", "rookvale_north_gate")
        link("rookvale_center", "rookvale_south_gate")
        link("rookvale_north_gate", "frostcrest_hub")
        link("frostcrest_hub", "frostcrest_citadel")
        link("frostcrest_hub", "frostcrest_grimmaw")
        link("frostcrest_grimmaw", "boreal_watch_camp")
        link("rookvale_west_gate", "spirewood_cross")
        link("mirewatch", "mirewatch_west")
        link("mirewatch_west", "boghollow_camp")
        link("boghollow_camp", "writhing_marsh")
        link("writhing_marsh", "ashen_caldera")
        link("ashen_caldera", "ashen_bastion")
        link("duskport", "blightwood_jct")
        link("blightwood_jct", "spirewood_cross")
        link("mirewatch_west", "sunspire_oasis")
        link("sunspire_oasis", "scorchwind_camp")
        link("scorchwind_camp", "sunspire_badlands")
        link("west_jungle", "sunleaf_village")
    }

    /**
     * Computes a coarse sequence of walkable highway/bridge waypoints between distant points
     * across the 1600x1200 continent grid so local A* can chain short, fast path segments.
     */
    fun findCoarseWaypoints(startX: Int, startY: Int, goalX: Int, goalY: Int): List<Pair<Int, Int>> {
        val nodeById = waypointNodes.associateBy { it.id }
        val startNode = waypointNodes.minByOrNull { hypot((it.x - startX).toDouble(), (it.y - startY).toDouble()) }
            ?: return listOf(goalX to goalY)
        val goalNode = waypointNodes.minByOrNull { hypot((it.x - goalX).toDouble(), (it.y - goalY).toDouble()) }
            ?: return listOf(goalX to goalY)

        if (startNode.id == goalNode.id) {
            return listOf(goalX to goalY)
        }

        val dist = HashMap<String, Double>()
        val prev = HashMap<String, String>()
        val unvisited = waypointNodes.map { it.id }.toMutableSet()
        waypointNodes.forEach { dist[it.id] = Double.POSITIVE_INFINITY }
        dist[startNode.id] = 0.0

        while (unvisited.isNotEmpty()) {
            val curId = unvisited.minByOrNull { dist[it] ?: Double.POSITIVE_INFINITY } ?: break
            val curDist = dist[curId] ?: Double.POSITIVE_INFINITY
            if (curDist.isInfinite() || curId == goalNode.id) break
            unvisited.remove(curId)
            val cur = nodeById.getValue(curId)
            for (nextId in waypointEdges[curId].orEmpty()) {
                if (nextId !in unvisited) continue
                val nxt = nodeById.getValue(nextId)
                val alt = curDist + hypot((nxt.x - cur.x).toDouble(), (nxt.y - cur.y).toDouble())
                if (alt < (dist[nextId] ?: Double.POSITIVE_INFINITY)) {
                    dist[nextId] = alt
                    prev[nextId] = curId
                }
            }
        }

        val chain = ArrayDeque<Pair<Int, Int>>()
        var trace: String? = goalNode.id
        var guard = 0
        while (trace != null && guard++ < 64) {
            val n = nodeById[trace] ?: break
            chain.addFirst(n.x to n.y)
            trace = prev[trace]
        }
        if (chain.isEmpty()) return listOf(goalX to goalY)

        val subdivided = mutableListOf<Pair<Int, Int>>()
        var prevPt = startX to startY
        for (pt in chain) {
            val d = abs(pt.first - prevPt.first) + abs(pt.second - prevPt.second)
            if (d > 110) {
                val steps = (d / 90).coerceAtLeast(2)
                for (i in 1 until steps) {
                    val t = i.toFloat() / steps.toFloat()
                    subdivided.add(
                        (prevPt.first + (pt.first - prevPt.first) * t).toInt() to
                            (prevPt.second + (pt.second - prevPt.second) * t).toInt()
                    )
                }
            }
            subdivided.add(pt)
            prevPt = pt
        }
        val finalDist = abs(goalX - prevPt.first) + abs(goalY - prevPt.second)
        if (finalDist > 110) {
            val steps = (finalDist / 90).coerceAtLeast(2)
            for (i in 1 until steps) {
                val t = i.toFloat() / steps.toFloat()
                subdivided.add(
                    (prevPt.first + (goalX - prevPt.first) * t).toInt() to
                        (prevPt.second + (goalY - prevPt.second) * t).toInt()
                )
            }
        }
        subdivided.add(goalX to goalY)
        return subdivided
    }

    fun isInRookvaleHeartland(x: Int, y: Int): Boolean =
        x in 1200..1599 && y in 400..799

    fun isSecondarySafeZone(x: Int, y: Int): Boolean =
        secondarySettlements.any { it.isSafeZone && it.contains(x, y) }

    /**
     * Organic Eastern Ocean Bay carved along Rookvale Valley's eastern flank (`lx >= ~360..366`),
     * connecting seamlessly to the eastern sea so Rookvale Royal Harbor anchors directly on true ocean water.
     */
    fun rawRookvaleEasternSeaTile(gx: Int, gy: Int): TileType? {
        if (!isInRookvaleHeartland(gx, gy)) return null
        val lx = gx - 1200
        val ly = gy - 400
        if (ly in 190..194) return null // Keep Rookvale East Border Highway causeway unbroken
        if (lx >= 394) return TileType.DEEP_WATER
        if (ly !in 14..386) return null
        val shoreX = 364 + (sin(ly * 0.032) * 3.5 + cos(ly * 0.017) * 2.0).toInt()
        if (lx < shoreX) return null
        return if (lx >= shoreX + 8) TileType.DEEP_WATER else TileType.WATER
    }

    /**
     * Pure continent land-vs-ocean elevation oracle used by [CoastalPortValidator] so harbor snapping
     * evaluates strictly against true open Sea/Ocean geometry (never rivers, ponds, or settlements).
     */
    fun rawOverlandTerrainAt(x: Int, y: Int): TileType {
        if (x < 6 || x >= WORLD_OVERLAND_WIDTH - 6 || y < 6 || y >= WORLD_OVERLAND_HEIGHT - 6) {
            return TileType.DEEP_WATER
        }
        if (isInRookvaleHeartland(x, y)) {
            rawRookvaleEasternSeaTile(x, y)?.let { return it }
            return TileType.GRASS
        }
        val landVal = landmassField(x, y)
        if (landVal < 0.0f) {
            return if (landVal > -0.14f) TileType.WATER else TileType.DEEP_WATER
        }
        return TileType.GRASS
    }

    /**
     * O(1) lookup for strictly validated ocean-coastline wooden piers and moored boats.
     * Checked for both outer continent chunks and Rookvale's Eastern Ocean Harbor.
     */
    fun validatedPortTileAt(x: Int, y: Int): TileType? {
        val key = packCoord(x, y)
        if (key in validatedPierLookup) return TileType.WOOD_PIER
        if (key in validatedBoatLookup) return TileType.MOORED_BOAT
        return null
    }

    fun generateOuterChunk(chunkX: Int, chunkY: Int): TileChunk {
        val chunk = TileChunk(chunkX, chunkY, WORLD_CHUNK_SIZE, TileType.DEEP_WATER)
        val baseX = chunkX * WORLD_CHUNK_SIZE
        val baseY = chunkY * WORLD_CHUNK_SIZE
        for (ly in 0 until WORLD_CHUNK_SIZE) {
            val gy = baseY + ly
            if (gy >= WORLD_OVERLAND_HEIGHT) continue
            for (lx in 0 until WORLD_CHUNK_SIZE) {
                val gx = baseX + lx
                if (gx >= WORLD_OVERLAND_WIDTH) continue
                chunk.setLocal(lx, ly, tileAtGlobal(gx, gy))
            }
        }
        return chunk
    }

    /**
     * Deterministic tile resolver for all 11 outer 400x400 regions around Rookvale Valley.
     */
    fun tileAtGlobal(x: Int, y: Int): TileType {
        // Outer ocean frame
        if (x < 6 || x >= WORLD_OVERLAND_WIDTH - 6 || y < 6 || y >= WORLD_OVERLAND_HEIGHT - 6) {
            return TileType.DEEP_WATER
        }

        // 1. Explicit dungeon portals outside Rookvale Valley
        outerPortals[packCoord(x, y)]?.let { return it }

        // 2. Biome-tiered outer-overworld ore veins
        outerOreLookup[packCoord(x, y)]?.let { return it }

        // 3. Strictly coastline-validated Ocean Harbor Piers, Boats & Maelstrom Shrine
        resolveOuterHarborTile(x, y)?.let { return it }

        // 3b. Hand-built biome cities (Mirewatch stilt city, Sunspire walled oasis)
        BiomeCities.cityAt(x, y)?.let { city -> return cityTile(city, x, y) }

        // 4. Biome-Ideology Empires, Towns & Forward Camps
        resolveSettlementTile(x, y)?.let { return it }

        // 6. Inter-regional Highways, Camp Roads & Auto-Linked Harbor Spurs
        resolveRoadTile(x, y)?.let { return it }

        // 7. Landmass & Biome Elevation Field
        val landVal = landmassField(x, y)
        if (landVal < 0.0f) {
            return if (landVal > -0.14f) TileType.WATER else TileType.DEEP_WATER
        }

        return resolveBiomeTile(x, y, landVal)
    }

    /**
     * A city cell, falling back to the natural terrain where the city leaves a gap. Highways that
     * cross a city's water or trees become bridges/boardwalks so roads never dead-end.
     */
    private fun cityTile(city: BiomeCities.City, x: Int, y: Int): TileType {
        val road = resolveRoadTile(x, y)
        val t = city.tileAt(x, y) ?: return road ?: naturalTile(x, y)
        if (road != null && (t == TileType.WATER || t == TileType.DEEP_WATER || t == TileType.TREE)) {
            return if (t == TileType.TREE) TileType.WOOD_PIER else TileType.WOOD_BRIDGE
        }
        return t
    }

    private fun naturalTile(x: Int, y: Int): TileType {
        val landVal = landmassField(x, y)
        if (landVal < 0.0f) return if (landVal > -0.14f) TileType.WATER else TileType.DEEP_WATER
        return resolveBiomeTile(x, y, landVal)
    }

    private fun resolveOuterHarborTile(x: Int, y: Int): TileType? {
        validatedPortTileAt(x, y)?.let { return it }

        // Maelstrom Sea-Dragon Shrine Isle plaza
        if (x in 752..768 && y in 784..796) {
            if (x == 760 && y == 790) return TileType.CRYSTAL_PILLAR
            if ((x == 754 || x == 766) && (y == 786 || y == 794)) return TileType.LANTERN_POST
            return TileType.ARCANE_FLOOR
        }
        return null
    }

    /**
     * Generates open-top cutaway walls, plazas, walkways, workshops, and lighting fixtures
     * tailored to each settlement's [BiomeIdeology] and [SettlementTier].
     */
    private fun resolveSettlementTile(x: Int, y: Int): TileType? {
        for (s in secondarySettlements) {
            if (!s.contains(x, y)) continue
            val onBorderX = x == s.minX || x == s.maxX
            val onBorderY = y == s.minY || y == s.maxY
            val gateHalf = if (s.tier == SettlementTier.FORWARD_CAMP) 2 else 3
            val nearCenterX = abs(x - s.centerX) <= gateHalf
            val nearCenterY = abs(y - s.centerY) <= gateHalf
            val dx = x - s.centerX
            val dy = y - s.centerY

            // 1. Cutaway Perimeter Walls & Fortifications with wide cardinal gates
            if ((onBorderX && !nearCenterY) || (onBorderY && !nearCenterX)) {
                return when (s.ideology) {
                    BiomeIdeology.HEARTLAND -> {
                        if ((x + y) % 5 == 0) TileType.TIMBER_WINDOW_WALL else TileType.TIMBER_WALL
                    }
                    BiomeIdeology.MARSHLAND -> {
                        if (s.tier == SettlementTier.EMPIRE && (x + y) % 4 == 0) TileType.TIMBER_WALL else TileType.WOOD_FENCE
                    }
                    BiomeIdeology.BADLANDS -> TileType.SANDSTONE_WALL
                    BiomeIdeology.FROSTPEAK -> {
                        if (s.tier == SettlementTier.FORWARD_CAMP) TileType.WOOD_FENCE else TileType.GRANITE_WALL
                    }
                    BiomeIdeology.ASHEN_BLIGHT -> TileType.OBSIDIAN_WALL
                }
            }

            // Iron-bar entrance gates for Ashen/Blight fortresses on north/south portals
            if (s.ideology == BiomeIdeology.ASHEN_BLIGHT && onBorderY && abs(dx) == gateHalf) {
                return TileType.IRON_BAR_GATE
            }

            // 2. Corner Light Fixtures & Ideology Standards
            val nearCornerX = x == s.minX + 3 || x == s.maxX - 3
            val nearCornerY = y == s.minY + 3 || y == s.maxY - 3
            if (nearCornerX && nearCornerY) {
                return when (s.ideology) {
                    BiomeIdeology.HEARTLAND, BiomeIdeology.BADLANDS -> TileType.LANTERN_POST
                    BiomeIdeology.MARSHLAND, BiomeIdeology.FROSTPEAK, BiomeIdeology.ASHEN_BLIGHT -> TileType.CAMPFIRE_BRAZIER
                }
            }

            // 3. Ideology-Specific Landmarks, Stalls, Stilt Huts, Forges & Banners
            when (s.ideology) {
                BiomeIdeology.HEARTLAND -> {
                    if (dx == 0 && dy == 0) return TileType.FOUNTAIN
                    if (dx == -4 && dy == -3) return TileType.MINI_DEPOT
                    if ((dx == -5 || dx == 5) && dy == 3) return TileType.MARKET_AWNING
                    if (dx == 4 && dy == -3) return TileType.BIOME_STALL_HEARTLAND
                    return if (abs(dx) <= 4 && abs(dy) <= 4) TileType.TEMPLE_FLOOR else TileType.COBBLESTONE
                }
                BiomeIdeology.MARSHLAND -> {
                    // Central roaring campfire, raised plank walkways, stilt huts & Stilt-Town Mire Bench
                    if (dx == 0 && dy == 0) return TileType.CAMPFIRE_BRAZIER
                    if (dx == -4 && dy == -3) return TileType.MINI_DEPOT
                    if (s.tier != SettlementTier.FORWARD_CAMP) {
                        if ((dx == -8 || dx == 8) && dy in -4..4 && dy != 0) return TileType.TIMBER_WALL
                        if (dx == 5 && dy == -4) return TileType.BIOME_STALL_MARSH
                        if (dx == 6 && dy == -4) return TileType.BLACKSMITH_ANVIL
                    } else if (dx == 4 && dy == -3) {
                        return TileType.BIOME_STALL_MARSH
                    }
                    if ((dx == -3 || dx == 3) && dy == 4) return TileType.CRATE_BARREL
                    return TileType.WOOD_PLANK_FLOOR
                }
                BiomeIdeology.BADLANDS -> {
                    // Oasis well, shaded desert bazaar awnings & Sunscorch Oasis Bazaar & Quarry Smeltery
                    if (dx == 0 && dy == 0) return TileType.FOUNTAIN
                    if (dx == -5 && dy == -4) return TileType.MINI_DEPOT
                    if ((dx == -6 || dx == 6) && (dy == -2 || dy == 2)) return TileType.MARKET_AWNING
                    if (s.tier == SettlementTier.EMPIRE) {
                        if (dx == 5 && dy == -4) return TileType.BIOME_STALL_BADLANDS
                        if (dx == 6 && dy == -4) return TileType.BLACKSMITH_ANVIL
                        if ((dx == -9 || dx == 9) && dy == 0) return TileType.SANDSTONE_WALL
                    } else if (dx == 4 && dy == -3) {
                        return TileType.BIOME_STALL_BADLANDS
                    }
                    return when {
                        abs(dx) <= 5 && abs(dy) <= 5 -> TileType.TEMPLE_FLOOR
                        abs(dx) <= 10 && abs(dy) <= 10 -> TileType.COBBLESTONE
                        else -> TileType.SAND
                    }
                }
                BiomeIdeology.FROSTPEAK -> {
                    // Heavy granite hearth-forges, timber palisades & Frostpeak Citadel Great Hearth
                    if (dx == 0 && dy == 0) return TileType.CAMPFIRE_BRAZIER
                    if (dx == -5 && dy == -4) return TileType.MINI_DEPOT
                    if (dx == 4 && dy == -4) return TileType.SMELTING_FURNACE
                    if (dx == 5 && dy == -4) return TileType.BIOME_STALL_FROSTPEAK
                    if (dx == 6 && dy == -4) return TileType.BLACKSMITH_ANVIL
                    if (dx == -4 && dy == 4) return TileType.WEAPON_RACK
                    if (s.tier == SettlementTier.EMPIRE && (dx == -8 || dx == 8) && (dy == -6 || dy == 6)) {
                        return TileType.GRANITE_WALL
                    }
                    return if (abs(dx) <= 5 && abs(dy) <= 5) TileType.COBBLESTONE else TileType.DIRT_FLOOR
                }
                BiomeIdeology.ASHEN_BLIGHT -> {
                    // Obsidian/iron fortress, crimson war banners, glowing braziers & Obsidian Foundry
                    if (dx == 0 && dy == 0) return TileType.CAMPFIRE_BRAZIER
                    if ((dx == -4 || dx == 4) && dy == 0) return TileType.WAR_BANNER
                    if (dx == -5 && dy == -4) return TileType.MINI_DEPOT
                    if (dx == 5 && dy == -4) return TileType.BIOME_STALL_ASHEN
                    if (dx == 6 && dy == -4) return TileType.BLACKSMITH_ANVIL
                    if (dx == 4 && dy == 4) return TileType.WEAPON_RACK
                    return if (abs(dx) <= 6 && abs(dy) <= 6) TileType.OBSIDIAN_FLOOR else TileType.COBBLESTONE
                }
            }
        }
        return null
    }

    private fun resolveRoadTile(x: Int, y: Int): TileType? {
        val fx = x.toFloat()
        val fy = y.toFloat()
        // Subtle organic wiggle so overland trails curve gently rather than stepping like harsh blocks
        val wobble = (sin(fx * 0.11f + fy * 0.09f) * 0.42f + cos(fx * 0.07f - fy * 0.13f) * 0.28f)
        for (seg in allRoadSegments) {
            val pad = seg.halfWidth + 2
            val minX = minOf(seg.x0, seg.x1) - pad
            val maxX = maxOf(seg.x0, seg.x1) + pad
            val minY = minOf(seg.y0, seg.y1) - pad
            val maxY = maxOf(seg.y0, seg.y1) + pad
            if (x !in minX..maxX || y !in minY..maxY) continue

            val dx = (seg.x1 - seg.x0).toFloat()
            val dy = (seg.y1 - seg.y0).toFloat()
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq < 0.001f) 0f else (((fx - seg.x0) * dx + (fy - seg.y0) * dy) / lenSq).coerceIn(0f, 1f)
            val px = seg.x0 + dx * t
            val py = seg.y0 + dy * t
            val dist = hypot((fx - px).toDouble(), (fy - py).toDouble()).toFloat()
            val effectiveHalf = seg.halfWidth + 0.55f + (if (seg.tile == TileType.DIRT_FLOOR) wobble else wobble * 0.35f)
            if (dist <= effectiveHalf) {
                return seg.tile
            }
        }
        return null
    }

    /**
     * Smooth organic road/bridge proximity signal in [0f, 1f] for continuous cartographic map rendering
     * without allocating or querying tile chunks. Returns positive coverage for roads/bridges and
     * encodes type via sign or separate bridge check.
     */
    /** Sea-bridges were removed (ferries cross the seas now); kept for map-rendering callers. */
    @Suppress("UNUSED_PARAMETER")
    fun isOnSeaBridge(fx: Float, fy: Float): Boolean = false

    fun continuousRoadCoverage(fx: Float, fy: Float): Float {
        val wobble = sin(fx * 0.095f + fy * 0.085f) * 0.55f + cos(fx * 0.065f - fy * 0.115f) * 0.35f
        var best = 0f
        val segs = allRoadSegments
        for (i in 0 until segs.size) {
            val seg = segs[i]
            val pad = seg.halfWidth + 4f
            val minX = minOf(seg.x0, seg.x1) - pad
            val maxX = maxOf(seg.x0, seg.x1) + pad
            val minY = minOf(seg.y0, seg.y1) - pad
            val maxY = maxOf(seg.y0, seg.y1) + pad
            if (fx < minX || fx > maxX || fy < minY || fy > maxY) continue

            val dx = (seg.x1 - seg.x0).toFloat()
            val dy = (seg.y1 - seg.y0).toFloat()
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq < 0.001f) 0f else (((fx - seg.x0) * dx + (fy - seg.y0) * dy) / lenSq).coerceIn(0f, 1f)
            val px = seg.x0 + dx * t
            val py = seg.y0 + dy * t
            val dist = hypot((fx - px).toDouble(), (fy - py).toDouble()).toFloat()
            val halfW = seg.halfWidth + 1.15f + wobble * 0.45f
            if (dist < halfW + 1.4f) {
                val cov = ((halfW + 1.4f - dist) / 2.2f).coerceIn(0f, 1f)
                if (cov > best) best = cov
            }
        }
        return best
    }

    /**
     * Smooth continuous continent elevation & bathymetry field across the entire 1600x1200 world:
     * Positive = land, Negative = sea (`-0.14..0` coastal shallows, `< -0.14` deep ocean).
     * Seamlessly incorporates Rookvale Valley's Eastern Ocean Bay so there is zero seam at x=1200 or y=400/800.
     */
    fun continuousLandmassField(fx: Float, fy: Float): Float {
        // Gentle world-edge ocean falloff so outer margins fade naturally into deep ocean
        val edgeDist = minOf(fx, WORLD_OVERLAND_WIDTH - 1f - fx, fy, WORLD_OVERLAND_HEIGHT - 1f - fy)
        val edgeFalloff = if (edgeDist < 28f) -((28f - edgeDist) / 28f) * 0.45f else 0f

        val base = rawLandmassFieldFloat(fx, fy) + edgeFalloff

        // Smoothly carve Rookvale Valley's organic Eastern Ocean Bay (around gx >= 1564, gy in 414..786)
        // using smoothstep transitions so the coastline flows continuously north/south into neighboring seas.
        if (fx > 1520f && fy > 380f && fy < 820f) {
            val ly = fy - 400f
            val shoreX = 1564f + sin(ly * 0.032f) * 3.5f + cos(ly * 0.017f) * 2.0f
            val northBlend = smoothstep(392f, 424f, fy)
            val southBlend = 1f - smoothstep(776f, 808f, fy)
            val bayEnvelope = northBlend * southBlend
            if (bayEnvelope > 0.001f) {
                val bayField = (shoreX - fx) / 56f
                val blended = base * (1f - bayEnvelope) + minOf(base, bayField) * bayEnvelope
                return blended
            }
        }
        return base
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun landmassField(x: Int, y: Int): Float = rawLandmassFieldFloat(x.toFloat(), y.toFloat())

    private fun rawLandmassFieldFloat(fx: Float, fy: Float): Float {
        val coastNoise = (sin(fx * 0.023f + fy * 0.017f) * 0.09f +
            cos(fx * 0.041f - fy * 0.031f) * 0.06f)

        var best = -1.0f

        fun addBlob(cx: Float, cy: Float, rx: Float, ry: Float, weight: Float = 1.0f) {
            val dx = (fx - cx) / rx
            val dy = (fy - cy) / ry
            val v = (1.0f - sqrt(dx * dx + dy * dy)) * weight
            if (v > best) best = v
        }

        // --- WESTERN CONTINENT ---
        addBlob(115f, 88f, 75f, 65f)       // NW Abyssal Isle
        addBlob(330f, 215f, 185f, 175f)    // Ashen Caldera & Northern Highlands
        addBlob(270f, 490f, 165f, 185f)    // The Writhing Marshes
        addBlob(530f, 595f, 130f, 130f)    // Mirewatch Peninsula
        addBlob(340f, 795f, 175f, 155f)    // Sunspire Desert
        addBlob(230f, 1020f, 180f, 130f)   // SW Volcanic Badlands

        // --- CENTRAL OCEAN SHRINE ---
        addBlob(760f, 790f, 44f, 36f)      // Maelstrom Sea-Dragon Shrine Isle

        // --- EASTERN CONTINENT ---
        addBlob(1010f, 195f, 175f, 165f)   // Blightwood & Duskport Coast
        addBlob(1365f, 200f, 235f, 185f)   // Frostpeak Highlands & Alpine Peaks
        addBlob(1065f, 545f, 205f, 235f)   // Spirewood Western Reach
        addBlob(1395f, 595f, 235f, 240f)   // Rookvale Heartland & Eastern Coast

        // --- SOUTHERN EMERALD JUNGLE CONTINENT ---
        addBlob(745f, 1020f, 185f, 115f)   // West Emerald Jungle
        addBlob(1005f, 1010f, 175f, 110f)  // Sunleaf East Emerald Jungle

        // --- SOUTHEAST EMBER ISLES ---
        addBlob(1485f, 965f, 95f, 82f)     // Ember Isles Archipelago

        return best + coastNoise
    }

    private fun resolveBiomeTile(x: Int, y: Int, landVal: Float): TileType {
        val h = deterministicHash(x, y, 31) and 255
        val detail = deterministicHash(x, y, 73) and 255

        // Continuous organic domain warp (±22 tiles) removes hard rectangular biome cutoffs
        val fx = x.toFloat()
        val fy = y.toFloat()
        val warpX = (sin(fy * 0.019f + fx * 0.008f) * 16f + cos(fy * 0.041f - fx * 0.023f) * 7f).toInt()
        val warpY = (cos(fx * 0.017f - fy * 0.009f) * 16f + sin(fx * 0.037f + fy * 0.021f) * 7f).toInt()
        val wx = x + warpX
        val wy = y + warpY

        val nearMonsterClearing = outerMonsterClearings.any { (sx, sy) ->
            abs(x - sx) <= 3 && abs(y - sy) <= 3
        }

        val nearPortal = nearMonsterClearing || outerPortals.keys.any { key ->
            val px = (key and 0xFFFFFFFFL).toInt()
            val py = (key ushr 32).toInt()
            abs(x - px) <= 6 && abs(y - py) <= 6
        }

        // 1. Far Northern Arctic Snow & Southern Alpine Rim
        if (wy < 95 && landVal > 0.08f) {
            if (!nearPortal && wy < 48 && h < 110) return TileType.MOUNTAIN_ROCK
            if (!nearPortal && h < 22) return TileType.TREE
            return TileType.SNOW
        }
        if (wy > 1115 && landVal > 0.1f) {
            if (!nearPortal && h < 120) return TileType.MOUNTAIN_ROCK
            return TileType.SNOW
        }

        // 2. NW Abyssal Isle & Ashen Caldera Volcano
        if (wx < 470 && wy < 355) {
            val craterDist = hypot((x - 328).toDouble(), (y - 185).toDouble())
            if (craterDist < 22.0) return TileType.LAVA
            if (!nearPortal && craterDist < 42.0 && h < 115) return TileType.MOUNTAIN_ROCK
            if (!nearPortal && h < 34 && landVal > 0.2f) return TileType.MOUNTAIN_ROCK
            return if (detail < 205) TileType.VOLCANIC_ASH else TileType.DIRT_FLOOR
        }

        // 3. The Writhing Marshes
        if (wx < 450 && wy in 355..670) {
            if (!nearPortal && h < 16 && landVal > 0.22f) return TileType.WATER
            if (!nearPortal && h in 16..46) return TileType.TREE
            return if (detail < 185) TileType.MARSH else TileType.DARK_GRASS
        }

        // 4. SW Volcanic Badlands
        if (wx < 395 && wy >= 940) {
            if (!nearPortal && h < 12 && landVal > 0.25f) return TileType.LAVA
            if (!nearPortal && h in 12..42 && landVal > 0.18f) return TileType.MOUNTAIN_ROCK
            return if (detail < 195) TileType.VOLCANIC_ASH else TileType.DIRT_FLOOR
        }

        // 5. Sunspire Golden Desert
        if (wx < 535 && wy in 670..995) {
            if (!nearPortal && h < 18 && landVal > 0.25f) return TileType.MOUNTAIN_ROCK
            return if (detail < 232) TileType.SAND else TileType.DIRT_FLOOR
        }

        // 6. Blightwood Corrupted Forest
        if (wx in 920..1150 && wy < 295) {
            if (!nearPortal && h < 62) return TileType.TREE
            return if (detail < 210) TileType.BLIGHT_SOIL else TileType.DARK_GRASS
        }

        // 7. Frostpeak Snowy Highlands
        if (wx >= 1145 && wy < 395) {
            val snowLine = 265 + (sin(x * 0.025) * 28.0).toInt()
            if (y < snowLine) {
                if (!nearPortal && h < 38 && landVal > 0.22f) return TileType.MOUNTAIN_ROCK
                if (!nearPortal && h in 38..74) return TileType.TREE
                return TileType.SNOW
            }
            if (!nearPortal && h < 48) return TileType.TREE
            return if (detail < 140) TileType.DARK_GRASS else TileType.GRASS
        }

        // 8. Southern Emerald Jungle Continent
        if (wx in 540..1240 && wy >= 870) {
            if (!nearPortal && h < 54 && landVal > 0.14f) return TileType.TREE
            return when {
                detail < 195 -> TileType.JUNGLE_FLOOR
                detail < 225 -> TileType.DARK_GRASS
                else -> TileType.FLOWER_GRASS
            }
        }

        // 9. SE Ember Isles Archipelago
        if (wx >= 1340 && wy >= 840) {
            val peakDist = hypot((x - 1518).toDouble(), (y - 952).toDouble())
            if (peakDist < 12.0) return TileType.LAVA
            if (!nearPortal && h < 34 && landVal > 0.2f) return TileType.MOUNTAIN_ROCK
            return if (detail < 185) TileType.VOLCANIC_ASH else TileType.DARK_GRASS
        }

        // 10. Temperate Coastal Plains & Spirewood / Mirewatch Heartland
        if (landVal < 0.06f) return TileType.SAND
        if (!nearPortal && h < 34 && landVal > 0.16f) return TileType.TREE
        return when {
            detail < 32 -> TileType.FLOWER_GRASS
            detail < 105 -> TileType.DARK_GRASS
            else -> TileType.GRASS
        }
    }

    private val outerMonsterClearings: List<Pair<Int, Int>> = listOf(
        1085 to 578, 1062 to 608, 1018 to 635, 1042 to 692,
        495 to 572, 488 to 622, 580 to 628,
        320 to 540, 275 to 520, 305 to 492, 250 to 560,
        960 to 240, 1010 to 210, 1042 to 190,
        1390 to 315, 1352 to 265, 1312 to 272, 1418 to 225,
        340 to 260, 300 to 270, 375 to 225,
        370 to 725, 435 to 835, 285 to 965, 255 to 1005, 232 to 1032,
        880 to 1005, 765 to 1015, 705 to 1012, 675 to 992,
        1488 to 962, 1502 to 985, 1495 to 938
    )

    private fun packCoord(x: Int, y: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)

    private fun deterministicHash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }
}
