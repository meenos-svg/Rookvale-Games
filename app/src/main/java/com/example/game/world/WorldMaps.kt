package com.example.game.world

import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.OVERLAND_REGION_SIZE
import com.example.game.model.OreNodeState
import com.example.game.model.TileType
import com.example.game.model.V3_TO_V4_OFFSET_X
import com.example.game.model.V3_TO_V4_OFFSET_Y
import com.example.game.model.WORLD_CHUNK_SIZE
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class FloorPortal(
    val fromFloor: Int,
    val fromX: Int,
    val fromY: Int,
    val toFloor: Int,
    val toX: Int,
    val toY: Int,
    val requiresRope: Boolean = false
)

data class NpcSpot(
    val floor: Int,
    val x: Int,
    val y: Int,
    val name: String,
    val role: NpcRole
)

enum class NpcRole {
    SHOP_ALCHEMIST,
    TEMPLE_PRIEST,
    ORACLE,
    HARBOR_CAPTAIN,
    REAPER_KEEPER
}

data class MonsterSpawn(
    val floor: Int,
    val x: Int,
    val y: Int,
    val type: MonsterType
)

data class LandmarkLabel(
    val floor: Int,
    val x: Int,
    val y: Int,
    val label: String
)

data class WorldFloor(
    val floorId: Int,
    val name: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val chunkedGrid: ChunkedTileGrid,
    val regionId: String = DEFAULT_REGION_ID,
    val buildings: List<BuildingFootprint> = emptyList(),
    val isProtectionZone: (Int, Int) -> Boolean = { _, _ -> false },
    val dangerLevel: String = "Low",
    val recommendedLevel: String = "Lvl 1-3",
    val dangerSeverity: Int = 0
) {
    fun tileAt(x: Int, y: Int): TileType = chunkedGrid.get(x, y)

    fun visibleChunkBounds(minTx: Int, maxTx: Int, minTy: Int, maxTy: Int): ChunkBounds =
        chunkedGrid.visibleChunkBounds(minTx, maxTx, minTy, maxTy)
}

object WorldMaps {
    // East-Central Heartland offset placing Rookvale City & Valley at grid sector (3, 1) of the 1600x1200 world
    const val ROOKVALE_ORIGIN_X = V3_TO_V4_OFFSET_X // 1200
    const val ROOKVALE_ORIGIN_Y = V3_TO_V4_OFFSET_Y // 400

    // Central Rookvale Sanctuary Plaza spawn inside rookvale_valley (196+1200, 192+400)
    const val TEMPLE_SPAWN_X = ROOKVALE_ORIGIN_X + 196 // 1396
    const val TEMPLE_SPAWN_Y = ROOKVALE_ORIGIN_Y + 192 // 592
    const val ORACLE_X = ROOKVALE_ORIGIN_X + 199       // 1399
    const val ORACLE_Y = ROOKVALE_ORIGIN_Y + 179       // 579
    const val REAPER_X = ROOKVALE_ORIGIN_X + 175       // 1375
    const val REAPER_Y = ROOKVALE_ORIGIN_Y + 158       // 558

    val regions: Map<String, WorldRegionMeta> = WorldRegionMeta.byId

    val harborPorts: List<HarborPort> get() = WorldBiomeBlueprint.harborPorts

    fun regionForGlobal(x: Int, y: Int): WorldRegionMeta = WorldRegionMeta.forGlobalTile(x, y)

    fun harborPortById(id: String): HarborPort? = harborPorts.firstOrNull { it.id == id }

    fun harborPortAtNpc(x: Int, y: Int): HarborPort? =
        harborPorts.firstOrNull { abs(it.dockX - x) <= 3 && abs(it.dockY - y) <= 3 }

    val npcs: List<NpcSpot> = listOf(
        // Rookvale Capital City NPCs
        NpcSpot(0, ROOKVALE_ORIGIN_X + 162, ROOKVALE_ORIGIN_Y + 204, "Alaric", NpcRole.SHOP_ALCHEMIST),
        NpcSpot(0, ROOKVALE_ORIGIN_X + 193, ROOKVALE_ORIGIN_Y + 179, "Brother Alden", NpcRole.TEMPLE_PRIEST),
        NpcSpot(0, ORACLE_X, ORACLE_Y, "The Oracle", NpcRole.ORACLE),
        NpcSpot(0, REAPER_X, REAPER_Y, "Mortis, Keeper of Reapers", NpcRole.REAPER_KEEPER),

        // 6 Coastal Harbor Ferry Captains across the World Map
        *WorldBiomeBlueprint.harborPorts.map { port ->
            NpcSpot(0, port.dockX, port.dockY, port.captainName, NpcRole.HARBOR_CAPTAIN)
        }.toTypedArray(),

        // Secondary Settlement Healers & Merchants
        NpcSpot(0, 549, 591, "Brother Corwin", NpcRole.TEMPLE_PRIEST),
        NpcSpot(0, 380, 787, "Merchant Zara", NpcRole.SHOP_ALCHEMIST),
        NpcSpot(0, 964, 992, "Elder Lyra", NpcRole.TEMPLE_PRIEST)
    )

    val landmarks: List<LandmarkLabel> = listOf(
        // Rookvale Valley & Capital City (shifted by +1200, +400)
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 192, "TEMPLE & SANCTUARY PLAZA"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 175, ROOKVALE_ORIGIN_Y + 162, "TEMPLE OF REAPERS"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 197, "TOWN SQUARE FOUNTAIN"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 164, ROOKVALE_ORIGIN_Y + 185, "TOWN DEPOT HALL"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 163, ROOKVALE_ORIGIN_Y + 205, "ALARIC'S MARKET QUARTER"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 225, ROOKVALE_ORIGIN_Y + 182, "RUNECRAFTING ACADEMY"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 224, ROOKVALE_ORIGIN_Y + 204, "CRAFT & FLETCHING WARD"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 245, ROOKVALE_ORIGIN_Y + 204, "LOGGING ORCHARD"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 183, ROOKVALE_ORIGIN_Y + 231, "WEST HARBOR FISHING PIER"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 211, ROOKVALE_ORIGIN_Y + 231, "ROOKVALE ROYAL HARBOR FERRY"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 242, ROOKVALE_ORIGIN_Y + 180, "CITY SEWERS (FLOOR -1)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 152, ROOKVALE_ORIGIN_Y + 173, "CELLAR ROPE HOLE (FLOOR -1)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 134, "NORTH STARTER MEADOWS"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 97, "NW COPPER QUARRY (FLOOR -1)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 264, ROOKVALE_ORIGIN_Y + 85, "MOUNTAIN CAVERN (FLOOR -1)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 67, "DEEP ABYSSAL CAVERN (FLOOR -2)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 207, "DARK FOREST SINKHOLE (FLOOR -1)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 88, ROOKVALE_ORIGIN_Y + 320, "GRIMMAW CRAGS CAMP (LVL 16+)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 317, "THE GRIMMAW QUARRY (FLOOR -5)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 320, "GOREK BADLANDS CAMP (LVL 20+)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 317, "THE GOREK LABYRINTH (FLOOR -6)"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 8, "FROSTCREST NORTH HIGHWAY"),
        LandmarkLabel(0, ROOKVALE_ORIGIN_X + 8, ROOKVALE_ORIGIN_Y + 192, "SPIREWOOD WEST HIGHWAY"),

        // Multi-Region Overland Cities, Harbors, Bridges & Dungeons
        LandmarkLabel(0, 545, 595, "MIREWATCH FORTRESS"),
        LandmarkLabel(0, 618, 560, "MIREWATCH BAY HARBOR"),
        LandmarkLabel(0, 882, 250, "DUSKPORT COASTAL QUAY"),
        LandmarkLabel(0, 375, 790, "SUNSPIRE OASIS CITY"),
        LandmarkLabel(0, 490, 865, "SUNSPIRE OASIS DOCK"),
        LandmarkLabel(0, 960, 995, "SUNLEAF JUNGLE VILLAGE"),
        LandmarkLabel(0, 959, 934, "SUNLEAF JUNGLE PIER"),
        LandmarkLabel(0, 1458, 952, "EMBER WATCH OUTPOST"),
        LandmarkLabel(0, 1427, 952, "EMBER ISLES LANDING"),
        LandmarkLabel(0, 760, 790, "MAELSTROM SEA-DRAGON SHRINE"),
        LandmarkLabel(0, 650, 211, "FROSTBAY SEA-BRIDGE"),
        LandmarkLabel(0, 770, 591, "CENTRAL ALDER CAUSEWAY"),
        LandmarkLabel(0, 318, 252, "ASHEN CALDERA SHAFT (FLOOR -2)"),
        LandmarkLabel(0, 295, 515, "WRITHING SINKHOLE (FLOOR -1)"),
        LandmarkLabel(0, 1020, 175, "BLIGHTWOOD BARROW (FLOOR -1)"),
        LandmarkLabel(0, 1320, 260, "FROSTCREST GRIMMAW RIDGE (FLOOR -5)"),
        LandmarkLabel(0, 242, 1020, "BADLANDS GOREK GATE (FLOOR -6)"),
        LandmarkLabel(0, 720, 1020, "JUNGLE RUIN VAULT (FLOOR -2)"),
        LandmarkLabel(0, 1510, 975, "EMBER SANCTUM DESCENT (FLOOR -2)"),

        // Dungeon Floor Landmarks
        LandmarkLabel(-1, 24, 34, "BROODMOTHER'S WEBBED DEN"),
        LandmarkLabel(-2, 24, 12, "MOLTEN PIT SANCTUM"),
        LandmarkLabel(-3, 24, 18, "DEEP FORGE OUTPOST"),
        LandmarkLabel(-5, 24, 12, "KRAGOR'S QUARRY PIT"),
        LandmarkLabel(-6, 24, 11, "VAELGOR'S LABYRINTH SANCTUM")
    )

    val portals: List<FloorPortal> = listOf(
        // Rookvale City Sewers Stairs <-> Floor -1
        FloorPortal(0, ROOKVALE_ORIGIN_X + 242, ROOKVALE_ORIGIN_Y + 179, -1, 28, 11, requiresRope = false),
        FloorPortal(-1, 28, 10, 0, ROOKVALE_ORIGIN_X + 242, ROOKVALE_ORIGIN_Y + 180, requiresRope = false),
        // Rookvale Western Town Cellar Rope-Hole <-> Floor -1
        FloorPortal(0, ROOKVALE_ORIGIN_X + 152, ROOKVALE_ORIGIN_Y + 172, -1, 10, 11, requiresRope = false),
        FloorPortal(-1, 10, 10, 0, ROOKVALE_ORIGIN_X + 152, ROOKVALE_ORIGIN_Y + 173, requiresRope = true),
        // Rookvale NW Quarry Rope-Hole <-> Floor -1
        FloorPortal(0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 96, -1, 14, 15, requiresRope = false),
        FloorPortal(-1, 14, 14, 0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 97, requiresRope = true),
        // Rookvale Mountain Cavern Stairs <-> Floor -1
        FloorPortal(0, ROOKVALE_ORIGIN_X + 264, ROOKVALE_ORIGIN_Y + 84, -1, 38, 11, requiresRope = false),
        FloorPortal(-1, 38, 10, 0, ROOKVALE_ORIGIN_X + 264, ROOKVALE_ORIGIN_Y + 85, requiresRope = false),
        // Rookvale Dark Forest Sinkhole Rope-Hole <-> Floor -1
        FloorPortal(0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 206, -1, 38, 29, requiresRope = false),
        FloorPortal(-1, 38, 28, 0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 207, requiresRope = true),
        // Rookvale NE Crag Hills Deep Cavern <-> Direct Floor -2 Deep Caverns
        FloorPortal(0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 66, -2, 11, 24, requiresRope = false),
        FloorPortal(-2, 10, 24, 0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 67, requiresRope = false),

        // Rookvale SW Frontier Grimmaw Crags Camp <-> Floor -5 The Grimmaw Quarry
        FloorPortal(0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 316, -5, 24, 40, requiresRope = false),
        FloorPortal(-5, 24, 41, 0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 317, requiresRope = false),

        // Rookvale SE Frontier Gorek Badlands War-Camp <-> Floor -6 The Gorek Labyrinth
        FloorPortal(0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 316, -6, 24, 40, requiresRope = false),
        FloorPortal(-6, 24, 41, 0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 317, requiresRope = false),

        // Outer-Continent Regional Dungeon Entrances (one-way descents into shared deep dungeon floors)
        FloorPortal(0, 318, 252, -2, 11, 24, requiresRope = false),   // Ashen Caldera Shaft -> Floor -2
        FloorPortal(0, 295, 515, -1, 14, 15, requiresRope = false),   // Writhing Sinkhole -> Floor -1
        FloorPortal(0, 1020, 175, -1, 38, 11, requiresRope = false),  // Blightwood Barrow -> Floor -1
        FloorPortal(0, 1320, 260, -5, 24, 40, requiresRope = false),  // Frostcrest Grimmaw Ridge -> Floor -5
        FloorPortal(0, 242, 1020, -6, 24, 40, requiresRope = false),  // Badlands Gorek Gate -> Floor -6
        FloorPortal(0, 720, 1020, -2, 11, 24, requiresRope = false),  // Jungle Ruin Vault -> Floor -2
        FloorPortal(0, 1510, 975, -2, 11, 24, requiresRope = false),  // Ember Sanctum -> Floor -2

        // Floor -1 <-> Floor -2 (Rope-Hole Down / Rope-Spot Up)
        FloorPortal(-1, 24, 38, -2, 24, 37, requiresRope = false),
        FloorPortal(-2, 24, 38, -1, 24, 37, requiresRope = true),

        // Floor -2 <-> Floor -3 (Crypt Stairs + Eastern Rope Pit)
        FloorPortal(-2, 24, 8, -3, 24, 9, requiresRope = false),
        FloorPortal(-3, 24, 8, -2, 24, 9, requiresRope = false),
        FloorPortal(-2, 40, 24, -3, 39, 24, requiresRope = false),
        FloorPortal(-3, 40, 24, -2, 39, 24, requiresRope = true),

        // Floor -3 <-> Floor -4 (Obsidian Stairs + Western Rope Pit)
        FloorPortal(-3, 24, 40, -4, 24, 39, requiresRope = false),
        FloorPortal(-4, 24, 40, -3, 24, 39, requiresRope = false),
        FloorPortal(-3, 10, 34, -4, 11, 34, requiresRope = false),
        FloorPortal(-4, 10, 34, -3, 11, 34, requiresRope = true)
    )

    val monsterSpawns: List<MonsterSpawn> = listOf(
        // --- Floor 0 Rookvale Starter Meadows & Farms (TIER 1 — Levels 1-3, outside Rookvale City PZ) ---
        // North Gate Starter Meadows
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 188, ROOKVALE_ORIGIN_Y + 136, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 204, ROOKVALE_ORIGIN_Y + 134, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 190, ROOKVALE_ORIGIN_Y + 126, MonsterType.CAVE_RAT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 208, ROOKVALE_ORIGIN_Y + 124, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 114, MonsterType.CRAG_WOLF),
        // West Gate Glades & Farms
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 134, ROOKVALE_ORIGIN_Y + 186, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 128, ROOKVALE_ORIGIN_Y + 198, MonsterType.CAVE_RAT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 120, ROOKVALE_ORIGIN_Y + 184, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 114, ROOKVALE_ORIGIN_Y + 196, MonsterType.CAVE_RAT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 106, ROOKVALE_ORIGIN_Y + 180, MonsterType.CRAG_WOLF),
        // East Gate Meadow & Whispering Edge
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 266, ROOKVALE_ORIGIN_Y + 186, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 272, ROOKVALE_ORIGIN_Y + 198, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 280, ROOKVALE_ORIGIN_Y + 184, MonsterType.CRAG_WOLF),
        // South Valley Meadows (across river bridges)
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 184, ROOKVALE_ORIGIN_Y + 260, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 210, ROOKVALE_ORIGIN_Y + 262, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 278, MonsterType.CRAG_WOLF),

        // --- Floor 0 Rookvale Outer Wilds (TIER 2 — Levels 3-6, Quarry, Dark Woods & Crag Hills) ---
        // NW Ochre Quarry & Foothills
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 110, ROOKVALE_ORIGIN_Y + 114, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 98, ROOKVALE_ORIGIN_Y + 106, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 96, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 94, ROOKVALE_ORIGIN_Y + 82, MonsterType.SKELETON),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 76, ROOKVALE_ORIGIN_Y + 104, MonsterType.SWAMP_TROLL),
        // East Deep Dark Woods
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 294, ROOKVALE_ORIGIN_Y + 196, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 304, ROOKVALE_ORIGIN_Y + 212, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 322, ROOKVALE_ORIGIN_Y + 202, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 220, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 336, ROOKVALE_ORIGIN_Y + 194, MonsterType.POISON_SPIDER),
        // NE Crag Hills & Deep Cavern Approach
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 242, ROOKVALE_ORIGIN_Y + 98, MonsterType.CRAG_WOLF),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 256, ROOKVALE_ORIGIN_Y + 90, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 274, ROOKVALE_ORIGIN_Y + 82, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 288, ROOKVALE_ORIGIN_Y + 76, MonsterType.SKELETON),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 300, ROOKVALE_ORIGIN_Y + 72, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 68, MonsterType.SKELETON),

        // --- Floor 0 SW Rookvale Frontier: Grimmaw Crags & Quarry Camp (TIER 5) ---
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 114, ROOKVALE_ORIGIN_Y + 318, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 102, ROOKVALE_ORIGIN_Y + 310, MonsterType.GRIMMAW_ROCK_HURLER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 102, ROOKVALE_ORIGIN_Y + 330, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 322, MonsterType.GRIMMAW_CRUSHER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 74, ROOKVALE_ORIGIN_Y + 314, MonsterType.GRIMMAW_ROCK_HURLER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 78, ROOKVALE_ORIGIN_Y + 330, MonsterType.GRIMMAW_CRUSHER),

        // --- Floor 0 SE Rookvale Frontier: Gorek Badlands & War-Camp (TIER 6) ---
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 286, ROOKVALE_ORIGIN_Y + 318, MonsterType.GOREK_RAIDER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 298, ROOKVALE_ORIGIN_Y + 310, MonsterType.GOREK_PHALANX),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 298, ROOKVALE_ORIGIN_Y + 330, MonsterType.GOREK_RAIDER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 322, MonsterType.GOREK_HEX_CALLER),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 326, ROOKVALE_ORIGIN_Y + 314, MonsterType.GOREK_PHALANX),
        MonsterSpawn(0, ROOKVALE_ORIGIN_X + 324, ROOKVALE_ORIGIN_Y + 330, MonsterType.GOREK_HEX_CALLER),

        // --- Multi-Region World Spawns across the 11 Outer 400x400 Regions ---
        // Spirewood Western Reach (Lvl 4-10)
        MonsterSpawn(0, 1085, 578, MonsterType.CRAG_WOLF),
        MonsterSpawn(0, 1062, 608, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 1018, 635, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 1042, 692, MonsterType.ORC_WARRIOR),
        // Mirewatch Peninsula (Lvl 6-12)
        MonsterSpawn(0, 495, 572, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 488, 622, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 580, 628, MonsterType.ORC_WARRIOR),
        // The Writhing Marshes (Lvl 6-12)
        MonsterSpawn(0, 320, 540, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 275, 520, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 305, 492, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 250, 560, MonsterType.GHOUL),
        // Blightwood & Duskport Reach (Lvl 8-14)
        MonsterSpawn(0, 960, 240, MonsterType.SKELETON),
        MonsterSpawn(0, 1010, 210, MonsterType.GHOUL),
        MonsterSpawn(0, 1042, 190, MonsterType.NECROMANCER),
        // Frostcrest Highlands (Lvl 12-20)
        MonsterSpawn(0, 1390, 315, MonsterType.ORC_WARRIOR),
        MonsterSpawn(0, 1352, 265, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(0, 1312, 272, MonsterType.GRIMMAW_CRUSHER),
        MonsterSpawn(0, 1418, 225, MonsterType.GRIMMAW_ROCK_HURLER),
        // Ashen Caldera Peaks (Lvl 14-22)
        MonsterSpawn(0, 340, 260, MonsterType.FIRE_DEVIL),
        MonsterSpawn(0, 300, 270, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(0, 375, 225, MonsterType.FIRE_DEVIL),
        // Sunspire Desert & Badlands (Lvl 12-22)
        MonsterSpawn(0, 370, 725, MonsterType.SKELETON),
        MonsterSpawn(0, 435, 835, MonsterType.FIRE_DEVIL),
        MonsterSpawn(0, 285, 965, MonsterType.GOREK_RAIDER),
        MonsterSpawn(0, 255, 1005, MonsterType.GOREK_PHALANX),
        MonsterSpawn(0, 232, 1032, MonsterType.GOREK_HEX_CALLER),
        // Emerald Jungle Continent (Lvl 10-18)
        MonsterSpawn(0, 880, 1005, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 765, 1015, MonsterType.ORC_WARRIOR),
        MonsterSpawn(0, 705, 1012, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(0, 675, 992, MonsterType.POISON_SPIDER),
        // The Ember Isles (Lvl 18-26)
        MonsterSpawn(0, 1488, 962, MonsterType.FIRE_DEVIL),
        MonsterSpawn(0, 1502, 985, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(0, 1495, 938, MonsterType.NECROMANCER),

        // --- Floor -1: Mossy Sewers & Caves (TIER 2 — Levels 3-6 + Dungeon Boss) ---
        MonsterSpawn(-1, 26, 13, MonsterType.CAVE_RAT),
        MonsterSpawn(-1, 13, 21, MonsterType.SWAMP_TROLL),
        MonsterSpawn(-1, 37, 13, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(-1, 13, 24, MonsterType.SKELETON),
        MonsterSpawn(-1, 37, 30, MonsterType.POISON_SPIDER),
        MonsterSpawn(-1, 24, 27, MonsterType.SKELETON),
        MonsterSpawn(-1, 24, 19, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(-1, 21, 35, MonsterType.POISON_SPIDER),
        MonsterSpawn(-1, 27, 35, MonsterType.POISON_SPIDER),
        MonsterSpawn(-1, 24, 34, MonsterType.BROODMOTHER_ARACHNE),

        // --- Floor -2: Deep Caverns & Molten Chasm (TIER 3 — Levels 6-10 + Dungeon Boss) ---
        MonsterSpawn(-2, 12, 33, MonsterType.ORC_WARRIOR),
        MonsterSpawn(-2, 35, 33, MonsterType.ORC_WARRIOR),
        MonsterSpawn(-2, 13, 25, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-2, 37, 25, MonsterType.GHOUL),
        MonsterSpawn(-2, 24, 25, MonsterType.GHOUL),
        MonsterSpawn(-2, 20, 12, MonsterType.ORC_WARRIOR),
        MonsterSpawn(-2, 28, 12, MonsterType.GHOUL),
        MonsterSpawn(-2, 24, 15, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-2, 24, 12, MonsterType.IGNIS_LORD_OF_THE_PIT),

        // --- Floor -3: Abyssal Crypt & Boss (TIER 4 — Levels 10-18) ---
        MonsterSpawn(-3, 14, 14, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 34, 14, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 12, 24, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 35, 24, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 16, 32, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 32, 32, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 24, 34, MonsterType.DEMON_LORD),

        // --- Floor -4: Obsidian Wyrm Sanctum (Classic Endgame Raid — Levels 16-20) ---
        MonsterSpawn(-4, 18, 32, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-4, 32, 32, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-4, 14, 24, MonsterType.NECROMANCER),
        MonsterSpawn(-4, 34, 24, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-4, 24, 16, MonsterType.ELDER_DRAGON),

        // --- Floor -5: The Grimmaw Quarry (TIER 5 Endgame Dungeon) ---
        MonsterSpawn(-5, 14, 34, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(-5, 34, 34, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(-5, 12, 25, MonsterType.GRIMMAW_ROCK_HURLER),
        MonsterSpawn(-5, 36, 25, MonsterType.GRIMMAW_ROCK_HURLER),
        MonsterSpawn(-5, 20, 26, MonsterType.GRIMMAW_CRUSHER),
        MonsterSpawn(-5, 28, 26, MonsterType.GRIMMAW_CRUSHER),
        MonsterSpawn(-5, 24, 12, MonsterType.KRAGOR_THE_STONE_EYED),

        // --- Floor -6: The Gorek Labyrinth (TIER 6 Pinnacle Maze Dungeon) ---
        MonsterSpawn(-6, 13, 34, MonsterType.GOREK_RAIDER),
        MonsterSpawn(-6, 35, 34, MonsterType.GOREK_RAIDER),
        MonsterSpawn(-6, 13, 24, MonsterType.GOREK_PHALANX),
        MonsterSpawn(-6, 35, 24, MonsterType.GOREK_PHALANX),
        MonsterSpawn(-6, 24, 25, MonsterType.GOREK_HEX_CALLER),
        MonsterSpawn(-6, 19, 14, MonsterType.GOREK_HEX_CALLER),
        MonsterSpawn(-6, 29, 14, MonsterType.GOREK_PHALANX),
        MonsterSpawn(-6, 24, 11, MonsterType.VAELGOR_LABYRINTH_WARDEN)
    )

    private val rookvaleHeartlandGrid: ChunkedTileGrid by lazy { buildRookvaleHeartlandGrid() }

    val floors: Map<Int, WorldFloor> = mapOf(
        0 to buildOverlandSurface(),
        -1 to buildSewersFloor(),
        -2 to buildCryptsFloor(),
        -3 to buildForgehallFloor(),
        -4 to buildWyrmSanctumFloor(),
        -5 to buildGrimmawQuarryFloor(),
        -6 to buildGorekLabyrinthFloor()
    )

    fun getFloor(floorId: Int): WorldFloor = floors[floorId] ?: floors.getValue(0)

    fun portalAt(floorId: Int, x: Int, y: Int, requiresRope: Boolean? = null): FloorPortal? {
        return portals.firstOrNull { p ->
            p.fromFloor == floorId && p.fromX == x && p.fromY == y &&
                (requiresRope == null || p.requiresRope == requiresRope)
        }
    }

    fun fallbackLandingForFloor(floorId: Int): Pair<Int, Int> = when (floorId) {
        0 -> TEMPLE_SPAWN_X to TEMPLE_SPAWN_Y
        -1 -> 28 to 11
        -2 -> 24 to 37
        -3 -> 24 to 9
        -4 -> 24 to 39
        -5 -> 24 to 40
        -6 -> 24 to 40
        else -> TEMPLE_SPAWN_X to TEMPLE_SPAWN_Y
    }

    /**
     * Finds the closest walkable, NPC-free tile within [maxRadius] using BFS so migrated saves
     * near redesigned props/walls nudge smoothly onto adjacent open ground without teleporting players away.
     */
    fun findNearestWalkableTile(floorId: Int, startX: Int, startY: Int, maxRadius: Int = 8): Pair<Int, Int>? {
        val floorMap = getFloor(floorId)
        if (startX !in 0 until floorMap.width || startY !in 0 until floorMap.height) return null
        val npcCoords = npcs.filter { it.floor == floorId }.map { it.x to it.y }.toSet()

        fun isValid(x: Int, y: Int): Boolean {
            return x in 0 until floorMap.width &&
                y in 0 until floorMap.height &&
                floorMap.tileAt(x, y).walkable &&
                (x to y) !in npcCoords
        }

        if (isValid(startX, startY)) return startX to startY

        for (r in 1..maxRadius) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (maxOf(abs(dx), abs(dy)) != r) continue
                    val nx = startX + dx
                    val ny = startY + dy
                    if (isValid(nx, ny)) return nx to ny
                }
            }
        }
        return null
    }

    fun initialMonsters(): List<MonsterEntity> {
        return monsterSpawns.mapIndexed { idx, spawn ->
            MonsterEntity(
                id = idx + 1,
                type = spawn.type,
                floor = spawn.floor,
                x = spawn.x,
                y = spawn.y,
                spawnX = spawn.x,
                spawnY = spawn.y,
                hp = spawn.type.maxHp,
                maxHp = spawn.type.maxHp,
                animOffset = (idx * 0.37f) % 1f
            )
        }
    }

    fun initialOreNodes(): Map<Triple<Int, Int, Int>, OreNodeState> {
        val map = LinkedHashMap<Triple<Int, Int, Int>, OreNodeState>()

        // 1. Floor 0 Rookvale Valley veins (scanned from compact 400x400 heartland grid)
        val heartland = rookvaleHeartlandGrid
        for (ly in 0 until heartland.height) {
            for (lx in 0 until heartland.width) {
                val kind = heartland.get(lx, ly).oreVeinKind()
                if (kind != null) {
                    val gx = ROOKVALE_ORIGIN_X + lx
                    val gy = ROOKVALE_ORIGIN_Y + ly
                    map[Triple(0, gx, gy)] = OreNodeState(
                        floor = 0,
                        x = gx,
                        y = gy,
                        kind = kind,
                        chargesLeft = kind.maxCharges
                    )
                }
            }
        }

        // 2. Floor 0 Outer-Continent veins (Copper, Tin, Bog Iron — strictly low-tier!)
        WorldBiomeBlueprint.outerOverworldOres.forEach { ore ->
            map[Triple(0, ore.x, ore.y)] = OreNodeState(
                floor = 0,
                x = ore.x,
                y = ore.y,
                kind = ore.kind,
                chargesLeft = ore.kind.maxCharges
            )
        }

        // 3. Dungeon Floors (-1..-6, including Vesperite & Cinderite deep veins)
        floors.values.filter { it.floorId < 0 }.forEach { fl ->
            for (y in 0 until fl.height) {
                for (x in 0 until fl.width) {
                    val kind = fl.tileAt(x, y).oreVeinKind()
                    if (kind != null) {
                        map[Triple(fl.floorId, x, y)] = OreNodeState(
                            floor = fl.floorId,
                            x = x,
                            y = y,
                            kind = kind,
                            chargesLeft = kind.maxCharges
                        )
                    }
                }
            }
        }
        return map
    }

    private fun createBlank(w: Int, h: Int, fill: TileType): Array<Array<TileType>> =
        Array(h) { Array(w) { fill } }

    private fun fillRect(g: Array<Array<TileType>>, x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
        for (y in y1.coerceAtLeast(0)..y2.coerceAtMost(g.lastIndex)) {
            for (x in x1.coerceAtLeast(0)..x2.coerceAtMost(g[0].lastIndex)) {
                g[y][x] = tile
            }
        }
    }

    private fun deterministicHash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun riverCenterY(x: Int): Int {
        val wave = sin(x * 0.028) * 5.0 + cos(x * 0.013) * 3.0
        // Gentle harbor bay dip directly south of Rookvale City
        val harborDip = if (x in 165..235) {
            val t = (x - 200) / 35.0
            (1.0 - t * t) * 4.0
        } else 0.0
        return (235.0 + wave + harborDip).toInt()
    }

    /**
     * Builds the preserved 400x400 Rookvale City & Valley heartland grid (local coords 0..399)
     * anchored at (1200..1599, 400..799) inside the 1600x1200 multi-region world.
     */
    private fun buildRookvaleHeartlandGrid(): ChunkedTileGrid {
        val s = OVERLAND_REGION_SIZE // 400
        val grid = ChunkedTileGrid(s, s, WORLD_CHUNK_SIZE, TileType.GRASS)

        // 1. Natural valley meadows, wildflower patches & dark forest biomes across 400x400
        for (y in 0 until s) {
            for (x in 0 until s) {
                val h = deterministicHash(x, y, 7) and 255
                val inEastWoods = x >= 272 && y in 135..345
                val inFarWestWoods = x <= 90 && y in 250..365
                val tile = when {
                    inEastWoods -> if (h < 185) TileType.DARK_GRASS else TileType.GRASS
                    inFarWestWoods -> if (h < 150) TileType.DARK_GRASS else TileType.GRASS
                    h < 34 -> TileType.FLOWER_GRASS
                    else -> TileType.GRASS
                }
                grid.set(x, y, tile)
            }
        }

        // 2. Soft regional border rim (leaving wide open highways at North, East, West & South!)
        for (i in 0 until s) {
            if (i !in 191..201) {
                grid.set(i, 0, TileType.MOUNTAIN_ROCK)
                grid.set(i, 1, TileType.TREE)
                grid.set(i, s - 1, TileType.MOUNTAIN_ROCK)
                grid.set(i, s - 2, TileType.TREE)
            }
            if (i !in 187..197 && i !in 226..248) {
                grid.set(0, i, TileType.MOUNTAIN_ROCK)
                grid.set(1, i, TileType.TREE)
                grid.set(s - 1, i, TileType.MOUNTAIN_ROCK)
                grid.set(s - 2, i, TileType.TREE)
            }
        }

        // 3. Organic outer wilderness forests, rocky hills & clearings (avoiding central city & highways)
        for (y in 8 until s - 8 step 3) {
            for (x in 8 until s - 8 step 3) {
                // Keep central city core + immediate starter clearings + highway corridors clear
                if (x in 138..264 && y in 136..254) continue
                if (x in 188..204 || y in 184..200) continue
                if (x in 62..132 && y in 62..132) continue // NW Quarry
                if (x in 230..332 && y in 48..116) continue // NE Crag Hills
                if (x in 48..134 && y in 278..362) continue // SW Grimmaw Crags Camp
                if (x in 266..352 && y in 278..362) continue // SE Gorek Badlands War-Camp

                val h = deterministicHash(x, y, 19) and 255
                val inEastForest = x >= 278 && y in 140..340
                val inNorthRidge = y <= 88 && x !in 175..220
                val inSouthWilds = y >= 264

                when {
                    inEastForest && h < 92 -> {
                        grid.set(x, y, TileType.TREE)
                        if ((h and 3) == 0) grid.set(x + 1, y + 1, TileType.TREE)
                    }
                    inNorthRidge && h < 58 -> {
                        grid.set(x, y, if (h < 32) TileType.MOUNTAIN_ROCK else TileType.TREE)
                    }
                    inSouthWilds && h < 42 -> {
                        grid.set(x, y, TileType.TREE)
                    }
                    h < 28 -> {
                        grid.set(x, y, TileType.TREE)
                    }
                }
            }
        }

        // 4. Wide Regional Border Highways (connecting to future 400x400 neighboring cities!)
        // North Border Highway (x=194..198, y=0..150)
        grid.fillRect(194, 0, 198, 150, TileType.COBBLESTONE)
        // West Border Highway (x=0..148, y=190..194)
        grid.fillRect(0, 190, 148, 194, TileType.COBBLESTONE)
        // East Border Highway (x=254..399, y=190..194)
        grid.fillRect(254, 190, 399, 194, TileType.COBBLESTONE)
        // South Frontier Road (x=194..198, y=244..399)
        grid.fillRect(194, 244, 198, 399, TileType.DIRT_FLOOR)
        grid.fillRect(195, 244, 197, 305, TileType.COBBLESTONE)

        // Roadside lanterns along highway approaches near the city
        listOf(
            193 to 132, 199 to 132, 193 to 112, 199 to 112,
            130 to 189, 130 to 195, 112 to 189, 112 to 195,
            270 to 189, 270 to 195, 290 to 189, 290 to 195
        ).forEach { (lx, ly) -> grid.set(lx, ly, TileType.LANTERN_POST) }

        // 5. Northwest Ochre Quarry (x=64..128, y=64..128) & Winding Quarry Road
        grid.fillEllipse(94, 96, 30, 26, TileType.DIRT_FLOOR)
        // Natural crescent quarry cliff walls (leaving wide east/south entrances)
        for (x in 64..122) {
            if (x < 108) grid.set(x, 70, TileType.MOUNTAIN_ROCK)
            if (x < 98) grid.set(x, 71, TileType.MOUNTAIN_ROCK)
        }
        for (y in 70..118) {
            grid.set(65, y, TileType.MOUNTAIN_ROCK)
            if (y < 104) grid.set(66, y, TileType.MOUNTAIN_ROCK)
        }
        // Copper & Tin Veins around outer NW Quarry ledges (Overworld Outskirts Tier 1)
        listOf(
            70 to 78, 72 to 86, 70 to 98, 74 to 110,
            82 to 74, 96 to 74
        ).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_COPPER) }
        listOf(
            108 to 78, 116 to 88, 80 to 114, 104 to 112
        ).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_TIN) }
        // Winding trail from North Highway (194, 120) & West Highway (118, 190) into Quarry
        grid.drawRoadSegment(194, 120, 120, 112, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(120, 112, 92, 98, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(118, 190, 112, 116, 1, TileType.DIRT_FLOOR)
        grid.set(92, 96, TileType.ROPE_HOLE_DOWN)
        grid.set(92, 97, TileType.DIRT_FLOOR)

        // 6. Northern Crag Hills (Floor -1 Mountain Cavern + Direct Floor -2 Deep Abyssal Cavern)
        grid.fillEllipse(266, 84, 24, 18, TileType.DIRT_FLOOR)
        grid.fillEllipse(308, 68, 22, 16, TileType.DIRT_FLOOR)
        // Rocky ridge crest behind the caverns
        for (x in 244..332) {
            val ridgeY = 56 + ((sin(x * 0.14) * 3.0).toInt())
            grid.set(x, ridgeY, TileType.MOUNTAIN_ROCK)
            grid.set(x, ridgeY + 1, TileType.MOUNTAIN_ROCK)
        }
        // Copper, Tin & Bog Iron outcrops along outer Crag Hills
        listOf(254 to 76, 276 to 74).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_COPPER) }
        listOf(268 to 70, 296 to 62).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_TIN) }
        listOf(320 to 62, 246 to 82).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_BOG_IRON) }
        // Winding mountain trails from North Highway to both caverns
        grid.drawRoadSegment(198, 112, 248, 96, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(248, 96, 264, 86, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(264, 86, 294, 74, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(294, 74, 308, 68, 1, TileType.DIRT_FLOOR)
        // Floor -1 Mountain Cavern Stairs
        grid.set(264, 84, TileType.STAIRS_DOWN)
        grid.set(264, 85, TileType.DIRT_FLOOR)
        grid.set(262, 83, TileType.LANTERN_POST)
        grid.set(266, 83, TileType.LANTERN_POST)
        // Floor -2 Deep Abyssal Cavern Stairs (direct overland entry to Floor -2!)
        grid.set(308, 66, TileType.STAIRS_DOWN)
        grid.set(308, 67, TileType.DIRT_FLOOR)
        grid.set(306, 65, TileType.TORCH_WALL)
        grid.set(310, 65, TileType.TORCH_WALL)

        // 7. East Deep Dark Woods Trail, Bog Iron Marshes & Sinkhole (Floor -1)
        grid.fillEllipse(312, 206, 10, 8, TileType.DARK_GRASS)
        grid.drawRoadSegment(284, 192, 312, 206, 1, TileType.DIRT_FLOOR)
        grid.set(312, 206, TileType.ROPE_HOLE_DOWN)
        grid.set(312, 207, TileType.DIRT_FLOOR)
        listOf(302 to 214, 322 to 214, 180 to 274, 212 to 274).forEach { (vx, vy) ->
            grid.set(vx, vy, TileType.ORE_BOG_IRON)
        }

        // 7B. NEW FRONTIER AREA 1: SW GRIMMAW CRAGS & QUARRY CAMP (x=54..126, y=288..352 — High Difficulty, far off from Rookvale City)
        grid.fillEllipse(88, 320, 34, 28, TileType.DIRT_FLOOR)
        grid.fillRect(68, 306, 108, 334, TileType.COBBLESTONE)
        // Natural crag cliff ridge wrapping the west & south borders
        for (x in 56..118) {
            grid.set(x, 348, TileType.MOUNTAIN_ROCK)
            if (x < 96) grid.set(x, 347, TileType.MOUNTAIN_ROCK)
        }
        for (y in 294..348) {
            grid.set(56, y, TileType.MOUNTAIN_ROCK)
            if (y > 304) grid.set(57, y, TileType.MOUNTAIN_ROCK)
        }
        // Fortified Grimmaw stone camp barricades with wide East War-Gate (y=317..323)
        for (x in 66..112) {
            grid.set(x, 304, TileType.STONE_WALL)
            grid.set(x, 336, TileType.STONE_WALL)
        }
        for (y in 304..336) {
            grid.set(66, y, TileType.STONE_WALL)
            if (y !in 317..323) grid.set(112, y, TileType.STONE_WALL)
        }
        // East Gate iron-barred archway & warning torch beacons
        for (y in 318..322) grid.set(112, y, TileType.IRON_BAR_GATE)
        listOf(112 to 316, 112 to 324, 82 to 314, 86 to 314, 72 to 305, 104 to 305, 72 to 335, 104 to 335).forEach { (tx, ty) ->
            grid.set(tx, ty, TileType.TORCH_WALL)
        }
        // Quarry camp supply crates & weapon racks
        listOf(70 to 308, 71 to 308, 106 to 332, 107 to 332).forEach { (px, py) -> grid.set(px, py, TileType.CRATE_BARREL) }
        listOf(106 to 308, 70 to 332).forEach { (px, py) -> grid.set(px, py, TileType.WEAPON_RACK) }
        // Outskirts Copper/Tin/Bog Iron veins outside Grimmaw Crags
        listOf(62 to 300, 64 to 342).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_COPPER) }
        listOf(118 to 302, 118 to 338).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_BOG_IRON) }
        // Frontier Road branching west off South Highway (195, 318) -> Grimmaw Camp Gate (112, 320)
        grid.drawRoadSegment(195, 318, 154, 318, 2, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(154, 318, 110, 320, 2, TileType.DIRT_FLOOR)
        listOf(168 to 315, 168 to 321, 134 to 316, 134 to 322).forEach { (lx, ly) -> grid.set(lx, ly, TileType.LANTERN_POST) }
        // Grimmaw Quarry Dungeon Entrance (Floor -5)
        grid.set(84, 316, TileType.STAIRS_DOWN)
        grid.set(84, 317, TileType.COBBLESTONE)

        // 7C. NEW FRONTIER AREA 2: SE GOREK BADLANDS & WAR-CAMP (x=274..346, y=288..352 — Pinnacle Difficulty, far off from Rookvale City)
        grid.fillEllipse(312, 320, 34, 28, TileType.DARK_GRASS)
        grid.fillEllipse(312, 320, 28, 22, TileType.CRYPT_FLOOR)
        grid.fillRect(292, 306, 332, 334, TileType.OBSIDIAN_FLOOR)
        // Scorched lava fissures flanking the outer north & east badlands
        grid.fillRect(296, 294, 328, 296, TileType.LAVA)
        grid.fillRect(340, 304, 342, 336, TileType.LAVA)
        // Fortified Gorek War-Camp bastion walls with wide West Gate (y=317..323)
        for (x in 288..334) {
            grid.set(x, 304, TileType.STONE_WALL)
            grid.set(x, 336, TileType.STONE_WALL)
        }
        for (y in 304..336) {
            grid.set(334, y, TileType.STONE_WALL)
            if (y !in 317..323) grid.set(288, y, TileType.STONE_WALL)
        }
        // West Bastion Iron-Barred Gate & crimson war-braziers
        for (y in 318..322) grid.set(288, y, TileType.IRON_BAR_GATE)
        listOf(288 to 316, 288 to 324, 314 to 314, 318 to 314, 296 to 305, 328 to 305, 296 to 335, 328 to 335).forEach { (tx, ty) ->
            grid.set(tx, ty, TileType.TORCH_WALL)
        }
        // War-camp weapon racks & supply barrels
        listOf(294 to 308, 328 to 308, 294 to 332).forEach { (px, py) -> grid.set(px, py, TileType.WEAPON_RACK) }
        listOf(328 to 332, 329 to 332).forEach { (px, py) -> grid.set(px, py, TileType.CRATE_BARREL) }
        // Outskirts Tin & Bog Iron veins outside Gorek Badlands
        listOf(282 to 302, 282 to 338).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_BOG_IRON) }
        listOf(336 to 298, 336 to 342).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_TIN) }
        // Frontier Road branching east off South Highway (197, 318) -> Gorek Bastion Gate (288, 320)
        grid.drawRoadSegment(197, 318, 244, 318, 2, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(244, 318, 290, 320, 2, TileType.DIRT_FLOOR)
        listOf(228 to 315, 228 to 321, 266 to 316, 266 to 322).forEach { (lx, ly) -> grid.set(lx, ly, TileType.LANTERN_POST) }
        // Gorek Labyrinth Citadel Entrance (Floor -6)
        grid.set(316, 316, TileType.STAIRS_DOWN)
        grid.set(316, 317, TileType.OBSIDIAN_FLOOR)

        // 8. Starter Meadows & Rustic Farms outside North & West Gates
        // North farmstead garden plot (fenced patch with open gate)
        grid.fillRect(172, 126, 184, 138, TileType.FLOWER_GRASS)
        for (x in 172..184) {
            if (x !in 177..179) {
                grid.set(x, 126, TileType.WOOD_FENCE)
                grid.set(x, 138, TileType.WOOD_FENCE)
            }
        }
        // West farmstead wildflower clearing
        grid.fillEllipse(124, 174, 10, 8, TileType.FLOWER_GRASS)

        // 9. WIDE WINDING ROOKVALE RIVER & SOUTHERN HARBOR BASIN (full x = 0..399)
        for (x in 0 until s) {
            val cy = riverCenterY(x)
            val halfW = if (x in 172..224) 7 else 5
            for (y in (cy - halfW)..(cy + halfW)) {
                grid.set(x, y, TileType.WATER)
            }
        }

        // 10. FULL-SCALE ROOKVALE CITY CORE (x = 146..256, y = 148..228)
        // Lush green base lawn inside city with wildflowers
        for (y in 148..228) {
            for (x in 146..256) {
                if (grid.get(x, y) != TileType.WATER) {
                    val h = deterministicHash(x, y, 41) and 31
                    grid.set(x, y, if (h < 5) TileType.FLOWER_GRASS else TileType.GRASS)
                }
            }
        }

        // City Grand Boulevards & Connecting Cobblestone Lanes
        grid.fillRect(194, 148, 198, 228, TileType.COBBLESTONE) // Central North-South Boulevard
        grid.fillRect(146, 190, 256, 194, TileType.COBBLESTONE) // Central West-East Boulevard
        grid.fillRect(150, 222, 248, 227, TileType.COBBLESTONE) // South Riverfront Promenade
        grid.fillRect(159, 170, 162, 228, TileType.COBBLESTONE) // West Market & Depot Avenue
        grid.fillRect(234, 170, 237, 228, TileType.COBBLESTONE) // East Academy & Craft Avenue
        grid.fillRect(154, 168, 244, 170, TileType.COBBLESTONE) // North Garden Ring Lane
        grid.fillRect(154, 212, 240, 214, TileType.COBBLESTONE) // South Market Ring Lane

        // Open City Perimeter: Trimmed Hedges, Wooden Fences & Stone Gateposts (no cramped curtain walls!)
        for (x in 146..256) {
            if (x !in 193..199) {
                val borderTile = when {
                    x % 14 == 0 -> TileType.LANTERN_POST
                    (x / 5) % 2 == 0 -> TileType.HEDGE
                    else -> TileType.WOOD_FENCE
                }
                grid.set(x, 148, borderTile)
            }
        }
        for (y in 149..226) {
            if (y !in 189..195) {
                val borderTile = when {
                    y % 12 == 0 -> TileType.LANTERN_POST
                    (y / 5) % 2 == 0 -> TileType.HEDGE
                    else -> TileType.WOOD_FENCE
                }
                if (grid.get(146, y) != TileType.WATER) grid.set(146, y, borderTile)
                if (grid.get(256, y) != TileType.WATER) grid.set(256, y, borderTile)
            }
        }
        // Grand Stone Gateposts framing North, West, and East Highways
        listOf(
            193 to 148, 199 to 148, // North Gate
            146 to 189, 146 to 195, // West Gate
            256 to 189, 256 to 195  // East Gate
        ).forEach { (gx, gy) -> grid.set(gx, gy, TileType.GATE_PILLAR) }

        // --- DISTRICT 1: TEMPLE & SANCTUARY PLAZA (x = 182..210, y = 172..208) ---
        grid.fillRect(182, 188, 210, 208, TileType.COBBLESTONE)
        // Corner garden beds in Town Square
        grid.fillRect(183, 204, 187, 207, TileType.FLOWER_GRASS)
        grid.fillRect(205, 204, 209, 207, TileType.FLOWER_GRASS)
        grid.set(183, 204, TileType.HEDGE); grid.set(187, 207, TileType.HEDGE)
        grid.set(209, 204, TileType.HEDGE); grid.set(205, 207, TileType.HEDGE)
        // Bubbling Stone Fountain in the heart of Town Square + 4 Lantern Posts
        grid.set(196, 198, TileType.FOUNTAIN)
        listOf(191 to 194, 201 to 194, 191 to 202, 201 to 202).forEach { (lx, ly) ->
            grid.set(lx, ly, TileType.LANTERN_POST)
        }

        // Temple Sanctuary Complex (188..204, 174..187) — split into North Sanctuary Nave + West & East 7x7 Cleric Quarters
        grid.fillRect(188, 174, 204, 187, TileType.TEMPLE_FLOOR)
        grid.drawWallBorder(188, 174, 204, 187, TileType.TEMPLE_WALL)
        // Interior room partition along y = 181 creating cozy North Nave (17x8) and flanking 7x7 side rooms
        for (x in 189..203) {
            if (x !in 190..191 && x !in 195..197 && x !in 201..202) {
                grid.set(x, 181, TileType.TEMPLE_WALL)
            }
        }
        // Flanking vestibule partitions at x = 194 & x = 198 (south half y = 182..186) with double-wide doorways (y = 183..184)
        for (y in 182..186) {
            if (y !in 183..184) {
                grid.set(194, y, TileType.TEMPLE_WALL)
                grid.set(198, y, TileType.TEMPLE_WALL)
            }
        }
        // Grand 3-tile open South Portico opening onto Sanctuary Plaza (195..197)
        grid.fillRect(195, 187, 197, 188, TileType.TEMPLE_FLOOR)
        grid.set(194, 187, TileType.GATE_PILLAR)
        grid.set(198, 187, TileType.GATE_PILLAR)
        grid.set(192, 189, TileType.LANTERN_POST)
        grid.set(200, 189, TileType.LANTERN_POST)
        // North Sanctuary: Marble Altar, Bookshelves, Pews & open walkways around Brother Alden (193,179) & The Oracle (199,179)
        grid.set(196, 177, TileType.TEMPLE_ALTAR)
        listOf(189 to 175, 190 to 175, 202 to 175, 203 to 175).forEach { (bx, by) ->
            grid.set(bx, by, TileType.BOOKSHELF)
        }
        listOf(190 to 177, 202 to 177).forEach { (px, py) ->
            grid.set(px, py, TileType.TEMPLE_PEW)
        }
        // West Cleric Dormitory (188..194, 181..187 — 7x7): Cozy beds, bookshelf & supply barrel
        grid.set(189, 183, TileType.WOOD_BED)
        grid.set(189, 185, TileType.WOOD_BED)
        grid.set(191, 186, TileType.BOOKSHELF)
        grid.set(192, 182, TileType.CRATE_BARREL)
        // East Oracle Study & Rest Chamber (198..204, 181..187 — 7x7): Bed, bookshelves & pew
        grid.set(203, 185, TileType.WOOD_BED)
        grid.set(202, 186, TileType.BOOKSHELF)
        grid.set(203, 182, TileType.BOOKSHELF)
        grid.set(200, 185, TileType.TEMPLE_PEW)
        // Keep spawn tile (196, 192) crystal clear cobblestone in Sanctuary Plaza
        grid.set(TEMPLE_SPAWN_X - ROOKVALE_ORIGIN_X, TEMPLE_SPAWN_Y - ROOKVALE_ORIGIN_Y, TileType.COBBLESTONE)

        // --- DISTRICT 1B: TEMPLE OF REAPERS (Northwest Sanctuary Courtyard, x = 168..182, y = 153..164) ---
        // Obsidian vault hall inside Rookvale City PZ where Mortis holds fallen adventurers' lost items for gold reclaim
        grid.fillRect(168, 153, 182, 164, TileType.OBSIDIAN_FLOOR)
        grid.drawWallBorder(168, 153, 182, 164, TileType.TEMPLE_WALL)
        // Grand 3-tile South Archway & cobblestone walkway connecting directly to North Garden Ring Lane (y = 168..170)
        grid.fillRect(174, 164, 176, 168, TileType.COBBLESTONE)
        grid.set(173, 164, TileType.GATE_PILLAR)
        grid.set(177, 164, TileType.GATE_PILLAR)
        grid.set(172, 166, TileType.LANTERN_POST)
        grid.set(178, 166, TileType.LANTERN_POST)
        // Interior: Soul Basin Altar, 4 glowing Crystal Pillars, Tomes & clear floor around Mortis (175, 158)
        grid.set(175, 155, TileType.TEMPLE_ALTAR)
        listOf(171 to 156, 179 to 156, 171 to 161, 179 to 161).forEach { (cx, cy) ->
            grid.set(cx, cy, TileType.CRYSTAL_PILLAR)
        }
        listOf(169 to 154, 170 to 154, 180 to 154, 181 to 154).forEach { (bx, by) ->
            grid.set(bx, by, TileType.BOOKSHELF)
        }
        grid.set(175, 158, TileType.OBSIDIAN_FLOOR)

        // --- DISTRICT 2: GRAND MARKET & DEPOT QUARTER (x = 152..180, y = 174..212) ---
        // Town Depot Hall (156..172, 176..188) — split into cozy Vault Room (9x13) & Quartermaster Bunk/Cargo Rooms (9x7 + 9x7)
        grid.fillRect(156, 176, 172, 188, TileType.WOOD_PLANK_FLOOR)
        grid.drawWallBorder(156, 176, 172, 188, TileType.TIMBER_WALL)
        // Half-timbered partition wall down x = 164 with double-wide interior archway (y = 181..183)
        for (y in 177..187) {
            if (y !in 181..183) {
                grid.set(164, y, TileType.TIMBER_WALL)
            }
        }
        // Warm glowing windows along Depot north & south walls
        listOf(160 to 176, 168 to 176, 159 to 188, 169 to 188).forEach { (wx, wy) ->
            grid.set(wx, wy, TileType.TIMBER_WINDOW_WALL)
        }
        // Double-wide South & East Timber-Lintel Doorways
        grid.fillRect(160, 188, 162, 189, TileType.WOOD_PLANK_FLOOR)
        grid.fillRect(167, 188, 168, 189, TileType.WOOD_PLANK_FLOOR)
        grid.fillRect(172, 181, 173, 183, TileType.WOOD_PLANK_FLOOR)
        // West Vault Room: 3 Iron-bound Depot Chests, Clerk Counter, Bookshelf & Crates
        grid.set(158, 179, TileType.DEPOT_CHEST)
        grid.set(158, 182, TileType.DEPOT_CHEST)
        grid.set(158, 185, TileType.DEPOT_CHEST)
        grid.set(161, 178, TileType.SHOP_COUNTER)
        grid.set(162, 177, TileType.BOOKSHELF)
        grid.set(161, 186, TileType.CRATE_BARREL)
        // East Quartermaster Room: Cozy Bunk Beds, Bookshelf & Stacked Barrels/Crates
        grid.set(167, 178, TileType.WOOD_BED)
        grid.set(170, 178, TileType.WOOD_BED)
        grid.set(166, 186, TileType.BOOKSHELF)
        grid.set(169, 186, TileType.CRATE_BARREL)
        grid.set(170, 185, TileType.CRATE_BARREL)
        grid.set(173, 179, TileType.LANTERN_POST)
        grid.set(173, 185, TileType.LANTERN_POST)

        // Alaric's Market Hall (156..170, 198..210) — split into cozy 9x13 Shopfront & 7x13 Merchant Living Quarters
        grid.fillRect(156, 198, 170, 210, TileType.WOOD_PLANK_FLOOR)
        grid.drawWallBorder(156, 198, 170, 210, TileType.TIMBER_WALL)
        // Interior half-timbered partition at x = 164 with double-wide doorway (y = 203..205)
        for (y in 199..209) {
            if (y !in 203..205) {
                grid.set(164, y, TileType.TIMBER_WALL)
            }
        }
        listOf(158 to 198, 167 to 198, 159 to 210, 167 to 210).forEach { (wx, wy) ->
            grid.set(wx, wy, TileType.TIMBER_WINDOW_WALL)
        }
        // Double-wide North & East Timber-Lintel Doorways
        grid.fillRect(160, 197, 162, 198, TileType.WOOD_PLANK_FLOOR)
        grid.fillRect(170, 203, 171, 205, TileType.WOOD_PLANK_FLOOR)
        // West Shopfront: Alaric's Counters, Weapon Rack, Bookshelf, Crates & Alaric at (162, 204)
        grid.set(160, 201, TileType.SHOP_COUNTER)
        grid.set(162, 201, TileType.SHOP_COUNTER)
        grid.set(158, 201, TileType.WEAPON_RACK)
        grid.set(157, 206, TileType.BOOKSHELF)
        grid.set(158, 208, TileType.CRATE_BARREL)
        grid.set(162, 204, TileType.WOOD_PLANK_FLOOR)
        // East Merchant Quarters: Cozy Beds, Bookshelf, Armory Rack & Storage Barrels
        grid.set(166, 200, TileType.WOOD_BED)
        grid.set(168, 200, TileType.WOOD_BED)
        grid.set(169, 207, TileType.WEAPON_RACK)
        grid.set(166, 208, TileType.CRATE_BARREL)
        grid.set(168, 208, TileType.BOOKSHELF)

        // Open-Air Market Plaza & Colorful Awning Stalls (172..181, 196..210)
        grid.fillRect(171, 195, 181, 211, TileType.COBBLESTONE)
        listOf(175 to 199, 175 to 204, 175 to 209).forEach { (mx, my) ->
            grid.set(mx, my, TileType.MARKET_AWNING)
        }
        grid.set(178, 199, TileType.CRATE_BARREL)
        grid.set(178, 209, TileType.LANTERN_POST)

        // Western Town Cellar Rope-Hole (Floor -1) in cozy garden courtyard (152, 172)
        grid.fillRect(150, 170, 155, 175, TileType.COBBLESTONE)
        grid.set(152, 172, TileType.ROPE_HOLE_DOWN)
        grid.set(154, 171, TileType.LANTERN_POST)

        // --- DISTRICT 3: ARCANE & RUNECRAFTING QUARTER (x = 214..246, y = 172..188) ---
        // Split into 3 connected ~7x10 cozy Academy Chambers: West Study, Central Rune Sanctum & East Archive
        grid.fillRect(216, 174, 234, 187, TileType.ARCANE_FLOOR)
        grid.drawWallBorder(216, 174, 234, 187, TileType.TIMBER_WALL)
        // Interior partitions at x = 222 and x = 229 with double-wide archways (y = 180..182)
        for (y in 175..186) {
            if (y !in 180..182) {
                grid.set(222, y, TileType.TIMBER_WALL)
                grid.set(229, y, TileType.TIMBER_WALL)
            }
        }
        listOf(219 to 174, 225 to 174, 231 to 174, 219 to 187, 231 to 187).forEach { (wx, wy) ->
            grid.set(wx, wy, TileType.TIMBER_WINDOW_WALL)
        }
        // Double-wide South & West Timber-Lintel Doorways
        grid.fillRect(224, 187, 226, 189, TileType.ARCANE_FLOOR)
        grid.fillRect(215, 180, 216, 182, TileType.ARCANE_FLOOR)
        // Central Rune Sanctum (222..229): Runecrafting Altar, Crystal Pillars & Scholar Bookshelves
        grid.set(225, 179, TileType.RUNECRAFTING_TABLE)
        listOf(224 to 184, 227 to 184).forEach { (cx, cy) -> grid.set(cx, cy, TileType.CRYSTAL_PILLAR) }
        listOf(224 to 175, 225 to 175, 227 to 175).forEach { (bx, by) -> grid.set(bx, by, TileType.BOOKSHELF) }
        // West Apprentice Study (216..222): Crystal Pillar, Bookshelves & Dormitory Bed
        grid.set(219, 177, TileType.CRYSTAL_PILLAR)
        grid.set(218, 175, TileType.BOOKSHELF)
        grid.set(220, 175, TileType.BOOKSHELF)
        grid.set(218, 185, TileType.WOOD_BED)
        grid.set(220, 185, TileType.CRATE_BARREL)
        // East Arcane Archive (229..234): Crystal Pillar, Bookshelves, Bed & Supply Barrel
        grid.set(231, 177, TileType.CRYSTAL_PILLAR)
        grid.set(231, 175, TileType.BOOKSHELF)
        grid.set(232, 175, TileType.BOOKSHELF)
        grid.set(232, 185, TileType.WOOD_BED)
        grid.set(231, 183, TileType.CRATE_BARREL)
        // City Sewers Entrance (Floor -1) neatly beside Academy courtyard
        grid.fillRect(239, 176, 245, 183, TileType.COBBLESTONE)
        grid.set(242, 179, TileType.STAIRS_DOWN)
        grid.set(240, 177, TileType.LANTERN_POST)
        grid.set(244, 177, TileType.LANTERN_POST)

        // --- DISTRICT 4: CRAFT & FLETCHING WARD + LOGGING ORCHARD (x = 214..254, y = 196..218) ---
        // Split into 2 cozy ~9x9 open-top connected workshops: Stone-walled Blacksmith Forge + Timber Fletching Hall
        grid.fillRect(216, 198, 234, 211, TileType.COBBLESTONE)
        grid.fillRect(217, 199, 224, 207, TileType.COBBLESTONE) // Stone-floored Blacksmith Forge
        grid.fillRect(226, 199, 233, 207, TileType.WOOD_PLANK_FLOOR) // Timber-floored Fletching Workshop
        // Mortared Stone Walls enclosing the Forge (216..225, 198..208) & Half-Timbered Walls enclosing Fletching (225..234, 198..208)
        for (x in 216..225) {
            grid.set(x, 198, TileType.STONE_WALL)
            if (x !in 220..221) grid.set(x, 208, TileType.STONE_WALL)
        }
        for (y in 198..208) {
            if (y !in 203..204) grid.set(216, y, TileType.STONE_WALL)
            if (y !in 203..205) grid.set(225, y, TileType.TIMBER_WALL)
        }
        for (x in 226..234) {
            grid.set(x, 198, if (x in 229..231) TileType.TIMBER_WINDOW_WALL else TileType.TIMBER_WALL)
            if (x !in 229..230) grid.set(x, 208, TileType.TIMBER_WALL)
        }
        for (y in 198..208) {
            if (y !in 203..204) grid.set(234, y, TileType.TIMBER_WALL)
        }
        // Open fenced front craft yard along y = 211
        for (y in 209..211) {
            grid.set(216, y, TileType.WOOD_FENCE)
            grid.set(234, y, TileType.WOOD_FENCE)
        }
        for (x in 217..233) {
            if (x !in 221..229) grid.set(x, 211, TileType.WOOD_FENCE)
        }
        // West Blacksmith Forge: Glowing Lava/Coal Smelting Furnace, Horned Anvil, Weapon Rack & Coal Barrels
        grid.set(219, 201, TileType.SMELTING_FURNACE)
        grid.set(223, 201, TileType.BLACKSMITH_ANVIL)
        grid.set(218, 206, TileType.WEAPON_RACK)
        grid.set(223, 206, TileType.CRATE_BARREL)
        // East Fletching Workshop: Fletching Benches, Bookshelf, Master Bed & Lumber Crates
        grid.set(228, 201, TileType.FLETCHING_BENCH)
        grid.set(231, 201, TileType.FLETCHING_BENCH)
        grid.set(232, 206, TileType.WOOD_BED)
        grid.set(227, 206, TileType.CRATE_BARREL)
        grid.set(227, 199, TileType.BOOKSHELF)
        // Tall Timber Street Lanterns outside the Craft Ward
        grid.set(215, 202, TileType.LANTERN_POST)
        grid.set(235, 202, TileType.LANTERN_POST)
        grid.set(220, 211, TileType.LANTERN_POST)
        grid.set(230, 211, TileType.LANTERN_POST)

        // Rookvale Logging Orchard (spaced choppable trees right beside Fletching Ward!)
        for (y in 196..218) {
            for (x in 238..254) {
                grid.set(x, y, if ((x + y) % 3 == 0) TileType.FLOWER_GRASS else TileType.GRASS)
            }
        }
        listOf(
            241 to 199, 246 to 198, 251 to 200,
            240 to 204, 245 to 204, 250 to 205,
            242 to 209, 247 to 210, 252 to 210,
            241 to 215, 246 to 216, 251 to 215
        ).forEach { (tx, ty) -> grid.set(tx, ty, TileType.TREE) }

        // --- DISTRICT 5: SOUTH RIVERFRONT, BRIDGES & HARBOR DOCKS ---
        // Three Grand Bridges across the winding Rookvale River
        fun buildBridge(xStart: Int, xEnd: Int) {
            val midX = (xStart + xEnd) / 2
            val cy = riverCenterY(midX)
            for (y in (cy - 10)..(cy + 10)) {
                for (x in xStart..xEnd) {
                    if (grid.get(x, y) == TileType.WATER || y in (cy - 8)..(cy + 8)) {
                        grid.set(x, y, TileType.WOOD_BRIDGE)
                    }
                }
            }
            grid.set(xStart - 1, cy - 8, TileType.LANTERN_POST)
            grid.set(xEnd + 1, cy - 8, TileType.LANTERN_POST)
        }
        buildBridge(194, 198) // Grand Central River Bridge
        buildBridge(159, 162) // West Market Bridge
        buildBridge(234, 237) // East Craft Bridge

        // Harbor Fishing Piers extending from the Riverfront Promenade into the Harbor Basin
        grid.fillRect(180, 227, 185, 235, TileType.WOOD_PIER)
        grid.fillRect(209, 227, 214, 235, TileType.WOOD_PIER)
        // Moored Harbor Boats bobbing beside the piers
        listOf(179 to 232, 186 to 234, 208 to 234, 215 to 232).forEach { (bx, by) ->
            grid.set(bx, by, TileType.MOORED_BOAT)
        }
        // Waterfront lanterns & cargo crates along the Promenade
        listOf(176 to 224, 188 to 224, 204 to 224, 218 to 224).forEach { (lx, ly) ->
            grid.set(lx, ly, TileType.LANTERN_POST)
        }
        listOf(174 to 225, 220 to 225).forEach { (cx, cy) ->
            grid.set(cx, cy, TileType.CRATE_BARREL)
        }

        // Guarantee every Rookvale-local Floor 0 monster spawn & portal landing is walkable ground
        for (spawn in monsterSpawns) {
            if (spawn.floor == 0 && WorldBiomeBlueprint.isInRookvaleHeartland(spawn.x, spawn.y)) {
                val lx = spawn.x - ROOKVALE_ORIGIN_X
                val ly = spawn.y - ROOKVALE_ORIGIN_Y
                if (!grid.get(lx, ly).walkable) {
                    grid.set(lx, ly, TileType.GRASS)
                }
            }
        }
        for (portal in portals) {
            if (portal.toFloor == 0 && WorldBiomeBlueprint.isInRookvaleHeartland(portal.toX, portal.toY)) {
                val lx = portal.toX - ROOKVALE_ORIGIN_X
                val ly = portal.toY - ROOKVALE_ORIGIN_Y
                if (!grid.get(lx, ly).walkable) {
                    grid.set(lx, ly, TileType.DIRT_FLOOR)
                }
            }
        }
        return grid
    }

    /**
     * FLOOR 0: 1600x1200 (4x3 grid of 400x400 regions) Streaming Multi-Region Overland World.
     * Dynamically streams 32x32 chunks with an LRU cap (~180 chunks ≈ 185 KB RAM) while
     * anchoring the exact preserved Rookvale City & Valley in the East-Central Heartland (3, 1).
     */
    private fun buildOverlandSurface(): WorldFloor {
        val heartland = rookvaleHeartlandGrid
        val streamingGrid = ChunkedTileGrid(
            width = WORLD_OVERLAND_WIDTH,
            height = WORLD_OVERLAND_HEIGHT,
            chunkSize = WORLD_CHUNK_SIZE,
            defaultTile = TileType.DEEP_WATER,
            maxCachedChunks = 180,
            chunkGenerator = { cx, cy ->
                val minX = cx * WORLD_CHUNK_SIZE
                val maxX = minX + WORLD_CHUNK_SIZE - 1
                val minY = cy * WORLD_CHUNK_SIZE
                val maxY = minY + WORLD_CHUNK_SIZE - 1
                val overlapsHeartland = maxX >= ROOKVALE_ORIGIN_X &&
                    minX < ROOKVALE_ORIGIN_X + OVERLAND_REGION_SIZE &&
                    maxY >= ROOKVALE_ORIGIN_Y &&
                    minY < ROOKVALE_ORIGIN_Y + OVERLAND_REGION_SIZE
                if (!overlapsHeartland) {
                    WorldBiomeBlueprint.generateOuterChunk(cx, cy)
                } else {
                    val chunk = TileChunk(cx, cy, WORLD_CHUNK_SIZE, TileType.GRASS)
                    for (ly in 0 until WORLD_CHUNK_SIZE) {
                        val gy = minY + ly
                        if (gy >= WORLD_OVERLAND_HEIGHT) continue
                        for (lx in 0 until WORLD_CHUNK_SIZE) {
                            val gx = minX + lx
                            if (gx >= WORLD_OVERLAND_WIDTH) continue
                            val tile = if (WorldBiomeBlueprint.isInRookvaleHeartland(gx, gy)) {
                                heartland.get(gx - ROOKVALE_ORIGIN_X, gy - ROOKVALE_ORIGIN_Y)
                            } else {
                                WorldBiomeBlueprint.tileAtGlobal(gx, gy)
                            }
                            chunk.setLocal(lx, ly, tile)
                        }
                    }
                    chunk
                }
            }
        )
        streamingGrid.prewarmAround(TEMPLE_SPAWN_X, TEMPLE_SPAWN_Y, radiusChunks = 2)

        val cityBuildings = listOf(
            BuildingFootprint(
                id = "temple_sanctuary",
                name = "Temple Sanctuary",
                minX = ROOKVALE_ORIGIN_X + 188, minY = ROOKVALE_ORIGIN_Y + 174,
                maxX = ROOKVALE_ORIGIN_X + 204, maxY = ROOKVALE_ORIGIN_Y + 187,
                roofColorArgb = 0xFF7B6C58.toInt(),
                roofHighlightArgb = 0xFF9E8D74.toInt(),
                roofTrimArgb = 0xFFD9B462.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "temple_of_reapers",
                name = "Temple of Reapers",
                minX = ROOKVALE_ORIGIN_X + 168, minY = ROOKVALE_ORIGIN_Y + 153,
                maxX = ROOKVALE_ORIGIN_X + 182, maxY = ROOKVALE_ORIGIN_Y + 164,
                roofColorArgb = 0xFF282038.toInt(),
                roofHighlightArgb = 0xFF42345C.toInt(),
                roofTrimArgb = 0xFF9B6BFF.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "town_depot",
                name = "Town Depot Hall",
                minX = ROOKVALE_ORIGIN_X + 156, minY = ROOKVALE_ORIGIN_Y + 176,
                maxX = ROOKVALE_ORIGIN_X + 172, maxY = ROOKVALE_ORIGIN_Y + 188,
                roofColorArgb = 0xFF6B4631.toInt(),
                roofHighlightArgb = 0xFF8C5D43.toInt(),
                roofTrimArgb = 0xFF3E291D.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "alaric_shop",
                name = "Alaric's Market Hall",
                minX = ROOKVALE_ORIGIN_X + 156, minY = ROOKVALE_ORIGIN_Y + 198,
                maxX = ROOKVALE_ORIGIN_X + 170, maxY = ROOKVALE_ORIGIN_Y + 210,
                roofColorArgb = 0xFF7E332E.toInt(),
                roofHighlightArgb = 0xFFA3453E.toInt(),
                roofTrimArgb = 0xFFD4A44C.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "runecrafting_academy",
                name = "Runecrafting Academy",
                minX = ROOKVALE_ORIGIN_X + 216, minY = ROOKVALE_ORIGIN_Y + 174,
                maxX = ROOKVALE_ORIGIN_X + 234, maxY = ROOKVALE_ORIGIN_Y + 187,
                roofColorArgb = 0xFF3E3563.toInt(),
                roofHighlightArgb = 0xFF5A4E8C.toInt(),
                roofTrimArgb = 0xFF9F84E8.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "craft_fletching_ward",
                name = "Craft & Fletching Ward",
                minX = ROOKVALE_ORIGIN_X + 216, minY = ROOKVALE_ORIGIN_Y + 198,
                maxX = ROOKVALE_ORIGIN_X + 234, maxY = ROOKVALE_ORIGIN_Y + 205,
                roofColorArgb = 0xFF5E4A39.toInt(),
                roofHighlightArgb = 0xFF7D634E.toInt(),
                roofTrimArgb = 0xFF3A2D23.toInt(),
                ridgeAlongX = true
            )
        )

        return WorldFloor(
            floorId = 0,
            name = "Rookvale Realm",
            subtitle = "Multi-Region World • 1600×1200 (12 Regions)",
            width = WORLD_OVERLAND_WIDTH,
            height = WORLD_OVERLAND_HEIGHT,
            chunkedGrid = streamingGrid,
            regionId = DEFAULT_REGION_ID,
            buildings = cityBuildings,
            isProtectionZone = { x, y ->
                (x in (ROOKVALE_ORIGIN_X + 147)..(ROOKVALE_ORIGIN_X + 255) &&
                    y in (ROOKVALE_ORIGIN_Y + 149)..(ROOKVALE_ORIGIN_Y + 227)) ||
                    WorldBiomeBlueprint.isSecondarySafeZone(x, y)
            },
            dangerLevel = "Varies",
            recommendedLevel = "Lvl 1-26+",
            dangerSeverity = 0
        )
    }

    private fun buildSewersFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // 1. NW Cellar & Quarry Crypt (Portals: (10,10) Cellar & (14,14) Quarry)
        fillRect(g, 8, 9, 16, 17, TileType.CRYPT_FLOOR)
        fillRect(g, 11, 12, 15, 16, TileType.DIRT_FLOOR)
        listOf(10 to 13, 15 to 11).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Narrow 3-tile North Sewer Corridor -> North Gatehouse (Iron-Barred Gate threshold)
        fillRect(g, 17, 11, 24, 13, TileType.CRYPT_FLOOR)
        g[11][18] = TileType.IRON_BAR_GATE
        g[12][18] = TileType.IRON_BAR_GATE
        g[13][18] = TileType.STONE_WALL

        // 2. North Central Sewer Gatehouse (Portal: (28,10) City Sewers)
        fillRect(g, 25, 9, 31, 15, TileType.CRYPT_FLOOR)
        g[12][26] = TileType.STONE_WALL
        g[12][30] = TileType.STONE_WALL

        // Narrow 3-tile NE Corridor -> Mountain Cavern Gallery
        fillRect(g, 32, 11, 34, 13, TileType.DIRT_FLOOR)
        g[11][33] = TileType.IRON_BAR_GATE
        g[12][33] = TileType.IRON_BAR_GATE
        g[13][33] = TileType.STONE_WALL

        // 3. NE Mountain Cavern Gallery (Portal: (38,10) Mountain Cavern)
        fillRect(g, 35, 9, 41, 16, TileType.DIRT_FLOOR)
        g[13][39] = TileType.CAVE_WALL

        // 4. West Pillar-Lined Crypt Chamber (connected via narrow 2-tile vertical corridor)
        fillRect(g, 12, 18, 13, 19, TileType.CRYPT_FLOOR)
        fillRect(g, 9, 20, 17, 28, TileType.CRYPT_FLOOR)
        listOf(11 to 22, 15 to 22, 11 to 26, 15 to 26).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // 5. Central Flooded Cistern Chasm + Twin Stone Bridges
        fillRect(g, 27, 16, 28, 17, TileType.CRYPT_FLOOR) // Link from Gatehouse
        fillRect(g, 18, 18, 31, 28, TileType.CRYPT_FLOOR)
        fillRect(g, 18, 21, 31, 25, TileType.WATER) // Subterranean water chasm
        fillRect(g, 21, 21, 22, 25, TileType.STONE_BRIDGE) // West Stone Bridge
        fillRect(g, 27, 21, 28, 25, TileType.STONE_BRIDGE) // East Stone Bridge
        // Iron-barred gate linking West Crypt -> South Cistern Ledge
        g[26][18] = TileType.IRON_BAR_GATE
        g[27][18] = TileType.IRON_BAR_GATE
        g[28][18] = TileType.STONE_WALL

        // 6. Winding East Sinkhole Tunnel & Spider Alcove (Portal: (38,28) Dark Forest Sinkhole)
        fillRect(g, 38, 17, 39, 24, TileType.DIRT_FLOOR) // North-South winding tunnel
        fillRect(g, 32, 26, 34, 28, TileType.DIRT_FLOOR) // Cistern-to-East corridor
        fillRect(g, 35, 25, 41, 33, TileType.DIRT_FLOOR)
        g[29][36] = TileType.CAVE_WALL

        // 7. Boss Gatehouse Corridor -> Broodmother's Webbed Den (Boss Chamber)
        fillRect(g, 23, 29, 25, 30, TileType.CRYPT_FLOOR)
        g[30][23] = TileType.IRON_BAR_GATE
        g[30][24] = TileType.IRON_BAR_GATE
        g[30][25] = TileType.IRON_BAR_GATE

        // Broodmother Arachne's Boss Chamber (x=18..30, y=31..40)
        fillRect(g, 18, 31, 30, 40, TileType.CRYPT_FLOOR)
        fillRect(g, 20, 32, 28, 38, TileType.DIRT_FLOOR)
        listOf(20 to 33, 28 to 33, 20 to 37, 28 to 37).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Bog Iron & Vesperite Veins tucked in guarded Floor -1 dungeon alcoves
        listOf(9 to 15, 10 to 24, 40 to 14).forEach { (x, y) -> g[y][x] = TileType.ORE_BOG_IRON }
        listOf(16 to 28, 29 to 39, 40 to 31, 19 to 39).forEach { (x, y) -> g[y][x] = TileType.ORE_VESPERITE }

        // Atmospheric Flickering Iron Wall Torches along corridors, bridges, and Boss Den
        listOf(
            10 to 8, 14 to 8, 21 to 10, 28 to 8, 38 to 8,
            10 to 19, 13 to 29, 21 to 17, 29 to 17,
            37 to 20, 40 to 22, 22 to 30, 26 to 30,
            17 to 34, 31 to 34, 24 to 41
        ).forEach { (tx, ty) -> g[ty][tx] = TileType.TORCH_WALL }

        // Portals (exact coordinates preserved & guaranteed walkable)
        g[10][28] = TileType.STAIRS_UP
        g[10][38] = TileType.STAIRS_UP
        g[10][10] = TileType.ROPE_SPOT_UP
        g[14][14] = TileType.ROPE_SPOT_UP
        g[28][38] = TileType.ROPE_SPOT_UP
        g[38][24] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -1,
            name = "Mossy Sewers & Caves",
            subtitle = "Dungeon Floor -1",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 3-8",
            dangerSeverity = 1
        )
    }

    private fun buildCryptsFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.STONE_WALL)

        // 1. Southern Rope Landing Vestibule (Portal: (24,38) Rope-Spot Up to Floor -1)
        fillRect(g, 18, 34, 30, 40, TileType.CRYPT_FLOOR)
        listOf(20 to 36, 28 to 36).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Narrow 3-tile Central Crypt Corridor with Iron-Barred Gate
        fillRect(g, 23, 28, 25, 33, TileType.CRYPT_FLOOR)
        g[33][23] = TileType.IRON_BAR_GATE
        g[33][24] = TileType.IRON_BAR_GATE
        g[33][25] = TileType.IRON_BAR_GATE

        // 2. Central Pillar-Lined Ossuary Hall
        fillRect(g, 19, 22, 29, 27, TileType.CRYPT_FLOOR)
        listOf(21 to 24, 27 to 24).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // 3. West Crag Stairs Hall (Portal: (10,24) Stairs Up to Floor 0 NE Crag Hills)
        fillRect(g, 15, 23, 18, 25, TileType.CRYPT_FLOOR)
        g[23][16] = TileType.IRON_BAR_GATE
        g[24][16] = TileType.IRON_BAR_GATE
        g[25][16] = TileType.STONE_WALL
        fillRect(g, 8, 21, 14, 28, TileType.CRYPT_FLOOR)
        listOf(11 to 22, 11 to 27).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // 4. East Brimstone Crypt (Portal: (40,24) Rope Hole Down to Floor -3)
        fillRect(g, 30, 23, 33, 25, TileType.CRYPT_FLOOR)
        g[23][32] = TileType.STONE_WALL
        g[24][32] = TileType.IRON_BAR_GATE
        g[25][32] = TileType.IRON_BAR_GATE
        fillRect(g, 34, 21, 42, 28, TileType.CRYPT_FLOOR)
        listOf(36 to 22, 36 to 27).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // 5. Flanking West & East Crypt Vault Wings (winding 2-tile connector loops)
        fillRect(g, 10, 31, 15, 36, TileType.CRYPT_FLOOR)
        fillRect(g, 11, 29, 12, 30, TileType.CRYPT_FLOOR)
        fillRect(g, 16, 35, 17, 36, TileType.CRYPT_FLOOR)
        fillRect(g, 33, 31, 38, 36, TileType.CRYPT_FLOOR)
        fillRect(g, 36, 29, 37, 30, TileType.CRYPT_FLOOR)
        fillRect(g, 31, 35, 32, 36, TileType.CRYPT_FLOOR)

        // 6. Molten Lava Chasm crossed by Central & West Stone Bridges
        fillRect(g, 14, 18, 34, 21, TileType.LAVA)
        fillRect(g, 23, 18, 25, 21, TileType.STONE_BRIDGE) // Grand Central Stone Bridge
        fillRect(g, 16, 18, 17, 21, TileType.STONE_BRIDGE) // Secondary West Stone Bridge
        fillRect(g, 16, 22, 17, 22, TileType.CRYPT_FLOOR)  // South landing of West Bridge
        fillRect(g, 16, 17, 17, 17, TileType.CRYPT_FLOOR)  // North landing of West Bridge

        // Boss Sanctum Iron-Barred Gate at North end of Central Bridge
        g[17][23] = TileType.IRON_BAR_GATE
        g[17][24] = TileType.IRON_BAR_GATE
        g[17][25] = TileType.IRON_BAR_GATE

        // 7. Ignis, Lord of the Pit — Molten Pit Sanctum (x=15..33, y=7..16)
        fillRect(g, 15, 7, 33, 16, TileType.CRYPT_FLOOR)
        fillRect(g, 17, 8, 31, 15, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 15, 7, 17, 9, TileType.LAVA)
        fillRect(g, 31, 7, 33, 9, TileType.LAVA)
        listOf(19 to 10, 29 to 10, 19 to 14, 29 to 14).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Vesperite Veins & Deep Cinderite Veins guarded in Floor -2 chambers
        listOf(8 to 21, 8 to 28, 34 to 35).forEach { (x, y) -> g[y][x] = TileType.ORE_VESPERITE }
        listOf(18 to 8, 30 to 8, 42 to 27, 19 to 39).forEach { (x, y) -> g[y][x] = TileType.ORE_CINDERITE }

        // Flickering Iron Wall Torches casting warm light pools down every corridor & chamber
        listOf(
            24 to 41, 20 to 33, 28 to 33, 13 to 30, 35 to 30,
            10 to 20, 40 to 20, 22 to 28, 26 to 28,
            22 to 17, 26 to 17, 14 to 12, 34 to 12, 24 to 6
        ).forEach { (tx, ty) -> g[ty][tx] = TileType.TORCH_WALL }

        // Portals (exact coordinates preserved & guaranteed walkable)
        g[38][24] = TileType.ROPE_SPOT_UP
        g[24][10] = TileType.STAIRS_UP // Direct return stairway up to NE Crag Hills on Floor 0!
        g[8][24] = TileType.STAIRS_DOWN
        g[24][40] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -2,
            name = "Deep Caverns",
            subtitle = "Dungeon Floor -2",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "High",
            recommendedLevel = "Lvl 6-12",
            dangerSeverity = 2
        )
    }

    private fun buildForgehallFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // Outer forgehall corridors
        fillRect(g, 8, 6, 41, 16, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 8, 16, 17, 37, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 31, 16, 42, 37, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 14, 30, 34, 42, TileType.OBSIDIAN_FLOOR)

        // Central Unprotected Deep Forge Outpost (NO Protection Zone — tense risk/reward!)
        fillRect(g, 19, 17, 29, 27, TileType.COBBLESTONE)
        fillRect(g, 17, 21, 31, 23, TileType.COBBLESTONE)
        fillRect(g, 23, 15, 25, 30, TileType.COBBLESTONE)
        g[20][22] = TileType.SMELTING_FURNACE
        g[20][26] = TileType.BLACKSMITH_ANVIL
        g[23][24] = TileType.MINI_DEPOT
        g[18][21] = TileType.TORCH_WALL
        g[18][27] = TileType.TORCH_WALL

        // Vesperite Veins & Deep Cinderite Veins
        listOf(10 to 10, 39 to 10, 10 to 28, 39 to 30).forEach { (x, y) -> g[y][x] = TileType.ORE_VESPERITE }
        listOf(9 to 35, 38 to 35, 16 to 40, 32 to 40).forEach { (x, y) -> g[y][x] = TileType.ORE_CINDERITE }

        // Portals
        g[8][24] = TileType.STAIRS_UP
        g[24][40] = TileType.ROPE_SPOT_UP
        g[40][24] = TileType.STAIRS_DOWN
        g[34][10] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -3,
            name = "Abyssal Crypt",
            subtitle = "Dungeon Floor -3 • Deep Outpost",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Extreme",
            recommendedLevel = "Lvl 10-18",
            dangerSeverity = 3
        )
    }

    private fun buildWyrmSanctumFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // Volcanic lava caldera background
        fillRect(g, 4, 4, 43, 43, TileType.LAVA)

        // Southern entry bastion & western rope cliff
        fillRect(g, 16, 34, 32, 42, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 8, 28, 18, 37, TileType.OBSIDIAN_FLOOR)
        // Twin volcanic bridges
        fillRect(g, 12, 20, 17, 30, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 31, 20, 36, 35, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 22, 24, 26, 34, TileType.OBSIDIAN_FLOOR)
        // Grand Ancient Dragon Sanctum
        fillRect(g, 11, 9, 37, 23, TileType.OBSIDIAN_FLOOR)

        // Obsidian cover pillars for dodging Dragon fire breath
        listOf(17 to 13, 31 to 13, 17 to 19, 31 to 19).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Rich Cinderite Veins guarded by Wyrms & the Ancient Dragon
        listOf(13 to 10, 35 to 10, 12 to 22, 36 to 22, 24 to 9).forEach { (x, y) -> g[y][x] = TileType.ORE_CINDERITE }

        // Portals
        g[40][24] = TileType.STAIRS_UP
        g[34][10] = TileType.ROPE_SPOT_UP

        return WorldFloor(
            floorId = -4,
            name = "Obsidian Wyrm Sanctum",
            subtitle = "Dungeon Floor -4 • Classic Endgame Lair",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Deadly",
            recommendedLevel = "Lvl 16-20",
            dangerSeverity = 4
        )
    }

    /**
     * Dedicated Endgame Floor -5: THE GRIMMAW QUARRY
     * Entered via the SW Frontier Grimmaw Crags Camp. Features towering stone pillars,
     * excavated quarry terraces, a flooded subterranean chasm with stone bridges, rich
     * Vesperite veins, and Kragor the Stone-Eyed's boss pit.
     */
    private fun buildGrimmawQuarryFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.STONE_WALL)

        // 1. Southern Entry Quarry Terrace (Stairs Up at (24,41) -> Floor 0 Grimmaw Crags Camp)
        fillRect(g, 19, 36, 29, 42, TileType.CRYPT_FLOOR)
        listOf(21 to 38, 27 to 38).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Flanking West & East Excavation Yards (connected via 3-tile quarry haul passages)
        fillRect(g, 9, 31, 17, 38, TileType.DIRT_FLOOR)
        fillRect(g, 18, 36, 18, 38, TileType.CRYPT_FLOOR)
        fillRect(g, 31, 31, 39, 38, TileType.DIRT_FLOOR)
        fillRect(g, 30, 36, 30, 38, TileType.CRYPT_FLOOR)

        // Central Iron-Barred Quarry Checkpoints leading north into Main Pillared Gallery
        fillRect(g, 23, 30, 25, 35, TileType.CRYPT_FLOOR)
        g[33][23] = TileType.IRON_BAR_GATE
        g[33][24] = TileType.IRON_BAR_GATE
        g[33][25] = TileType.IRON_BAR_GATE

        // 2. Grand Pillared Excavation Gallery (x=9..39, y=22..29)
        fillRect(g, 9, 22, 39, 29, TileType.CRYPT_FLOOR)
        fillRect(g, 11, 29, 13, 31, TileType.DIRT_FLOOR) // West haul link
        fillRect(g, 35, 29, 37, 31, TileType.DIRT_FLOOR) // East haul link
        // Towering carved stone support pillars
        listOf(
            14 to 24, 14 to 27, 20 to 24, 20 to 27,
            28 to 24, 28 to 27, 34 to 24, 34 to 27
        ).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // 3. Subterranean Flooded Quarry Chasm + Twin Stone Bridges
        fillRect(g, 12, 18, 36, 21, TileType.WATER)
        fillRect(g, 23, 18, 25, 21, TileType.STONE_BRIDGE)
        fillRect(g, 15, 18, 16, 21, TileType.STONE_BRIDGE)
        fillRect(g, 32, 18, 33, 21, TileType.STONE_BRIDGE)

        // 4. Kragor the Stone-Eyed's Boss Pit (x=14..34, y=7..17)
        fillRect(g, 14, 7, 34, 17, TileType.CRYPT_FLOOR)
        fillRect(g, 17, 9, 31, 15, TileType.DIRT_FLOOR)
        g[17][23] = TileType.IRON_BAR_GATE
        g[17][24] = TileType.IRON_BAR_GATE
        g[17][25] = TileType.IRON_BAR_GATE
        listOf(18 to 10, 30 to 10, 18 to 14, 30 to 14).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Rich Vesperite Ore Veins across quarry alcoves & Kragor's pit rim
        listOf(
            10 to 32, 10 to 37, 38 to 32, 38 to 37,
            10 to 23, 38 to 23, 15 to 8, 33 to 8
        ).forEach { (vx, vy) -> g[vy][vx] = TileType.ORE_VESPERITE }

        // Atmospheric Flickering Iron Wall Torches
        listOf(
            24 to 43, 18 to 39, 30 to 39, 22 to 33, 26 to 33,
            9 to 25, 39 to 25, 22 to 22, 26 to 22,
            22 to 17, 26 to 17, 13 to 12, 35 to 12, 24 to 6
        ).forEach { (tx, ty) -> g[ty][tx] = TileType.TORCH_WALL }

        // Return Stairway Up to Floor 0 Grimmaw Crags Camp
        g[41][24] = TileType.STAIRS_UP

        return WorldFloor(
            floorId = -5,
            name = "The Grimmaw Quarry",
            subtitle = "Endgame Dungeon • Floor -5 (Vesperite Veins)",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Extreme",
            recommendedLevel = "Lvl 16-24",
            dangerSeverity = 4
        )
    }

    /**
     * Dedicated Pinnacle Floor -6: THE GOREK LABYRINTH
     * Entered via the SE Frontier Gorek Badlands War-Camp. Features winding 2-3 tile
     * branching labyrinth corridors, guarded chambers, iron-barred gates, lava chasms
     * with stone bridges, glowing Cinderite veins, and Vaelgor the Labyrinth Warden's sanctum.
     */
    private fun buildGorekLabyrinthFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.STONE_WALL)

        // 1. Southern Labyrinth Entry Gatehouse (Stairs Up at (24,41) -> Floor 0 Gorek War-Camp)
        fillRect(g, 20, 37, 28, 42, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 23, 33, 25, 36, TileType.CRYPT_FLOOR)
        g[36][23] = TileType.IRON_BAR_GATE
        g[36][24] = TileType.IRON_BAR_GATE
        g[36][25] = TileType.IRON_BAR_GATE

        // 2. Branching South-West & South-East Maze Corridors + Guard Chambers
        fillRect(g, 10, 33, 22, 35, TileType.CRYPT_FLOOR)
        fillRect(g, 26, 33, 38, 35, TileType.CRYPT_FLOOR)
        fillRect(g, 10, 30, 16, 36, TileType.OBSIDIAN_FLOOR) // SW Raider Barracks
        fillRect(g, 32, 30, 38, 36, TileType.OBSIDIAN_FLOOR) // SE Raider Barracks

        // Winding Vertical Labyrinth Arms (2-3 tiles wide with maze zigzags)
        fillRect(g, 11, 22, 13, 29, TileType.CRYPT_FLOOR)
        fillRect(g, 35, 22, 37, 29, TileType.CRYPT_FLOOR)
        fillRect(g, 14, 24, 19, 26, TileType.CRYPT_FLOOR)
        fillRect(g, 29, 24, 34, 26, TileType.CRYPT_FLOOR)
        // Central Labyrinth Crossroads Chamber
        fillRect(g, 20, 22, 28, 28, TileType.OBSIDIAN_FLOOR)
        listOf(22 to 24, 26 to 24, 22 to 26, 26 to 26).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }
        // Narrow central north-south passage with iron gate
        fillRect(g, 23, 29, 25, 32, TileType.CRYPT_FLOOR)
        g[30][23] = TileType.IRON_BAR_GATE
        g[30][24] = TileType.IRON_BAR_GATE
        g[30][25] = TileType.IRON_BAR_GATE

        // 3. Volcanic Labyrinth Chasm + Twin Narrow Stone Bridges
        fillRect(g, 10, 18, 38, 21, TileType.LAVA)
        fillRect(g, 23, 18, 25, 21, TileType.STONE_BRIDGE)
        fillRect(g, 12, 18, 13, 21, TileType.STONE_BRIDGE)
        fillRect(g, 35, 18, 36, 21, TileType.STONE_BRIDGE)
        // Northern flanking corridors around Sanctum
        fillRect(g, 11, 13, 14, 17, TileType.CRYPT_FLOOR)
        fillRect(g, 34, 13, 37, 17, TileType.CRYPT_FLOOR)
        fillRect(g, 15, 14, 16, 15, TileType.CRYPT_FLOOR)
        fillRect(g, 32, 14, 33, 15, TileType.CRYPT_FLOOR)

        // 4. Vaelgor the Labyrinth Warden — Inner Obsidian Sanctum (x=16..32, y=7..16)
        fillRect(g, 16, 7, 32, 16, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 16, 7, 18, 9, TileType.LAVA)
        fillRect(g, 30, 7, 32, 9, TileType.LAVA)
        g[17][23] = TileType.IRON_BAR_GATE
        g[17][24] = TileType.IRON_BAR_GATE
        g[17][25] = TileType.IRON_BAR_GATE
        listOf(20 to 10, 28 to 10, 20 to 14, 28 to 14).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Deep Cinderite Ore Veins tucked in maze dead-ends & Sanctum alcoves
        listOf(
            10 to 31, 38 to 31, 11 to 22, 37 to 22,
            11 to 13, 37 to 13, 19 to 8, 29 to 8
        ).forEach { (vx, vy) -> g[vy][vx] = TileType.ORE_CINDERITE }

        // Flickering Iron Wall Torches throughout the winding corridors
        listOf(
            24 to 43, 22 to 36, 26 to 36, 13 to 29, 35 to 29,
            19 to 25, 29 to 25, 22 to 22, 26 to 22,
            22 to 17, 26 to 17, 15 to 11, 33 to 11, 24 to 6
        ).forEach { (tx, ty) -> g[ty][tx] = TileType.TORCH_WALL }

        // Return Stairway Up to Floor 0 Gorek Badlands War-Camp
        g[41][24] = TileType.STAIRS_UP

        return WorldFloor(
            floorId = -6,
            name = "The Gorek Labyrinth",
            subtitle = "Pinnacle Maze • Floor -6 (Cinderite Veins)",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Pinnacle",
            recommendedLevel = "Lvl 20-30+",
            dangerSeverity = 4
        )
    }
}
