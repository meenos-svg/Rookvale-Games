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

data class UndergroundCampZone(
    val floorId: Int,
    val name: String,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val healerX: Int,
    val healerY: Int,
    val healerName: String,
    val merchantX: Int,
    val merchantY: Int,
    val merchantName: String
) {
    fun contains(x: Int, y: Int): Boolean = x in minX..maxX && y in minY..maxY
}

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

    val undergroundCamps: List<UndergroundCampZone> by lazy {
        listOf(
            UndergroundCampZone(
                floorId = -1,
                name = "CATACOMBS VANGUARD CAMP (PZ)",
                minX = 25, minY = 9, maxX = 31, maxY = 12,
                healerX = 26, healerY = 10, healerName = "Sister Mara",
                merchantX = 30, merchantY = 10, merchantName = "Quartermaster Bram"
            ),
            UndergroundCampZone(
                floorId = -2,
                name = "DEEP CAVERNS FORWARD CAMP (PZ)",
                minX = 20, minY = 35, maxX = 28, maxY = 40,
                healerX = 21, healerY = 38, healerName = "Brother Tavin",
                merchantX = 27, merchantY = 38, merchantName = "Alchemist Veyra"
            ),
            UndergroundCampZone(
                floorId = -3,
                name = "ABYSSAL CRYPT FORWARD CAMP (PZ)",
                minX = 20, minY = 6, maxX = 28, maxY = 11,
                healerX = 21, healerY = 8, healerName = "Chaplain Oren",
                merchantX = 27, merchantY = 8, merchantName = "Supplier Kora"
            ),
            UndergroundCampZone(
                floorId = -4,
                name = "WYRM SANCTUM VANGUARD CAMP (PZ)",
                minX = 19, minY = 35, maxX = 29, maxY = 41,
                healerX = 21, healerY = 39, healerName = "Priestess Selene",
                merchantX = 27, merchantY = 39, merchantName = "Quartermaster Dax"
            ),
            UndergroundCampZone(
                floorId = -5,
                name = "GRIMMAW QUARRY FORWARD CAMP (PZ)",
                minX = 20, minY = 37, maxX = 28, maxY = 42,
                healerX = 21, healerY = 40, healerName = "Brother Halden",
                merchantX = 27, merchantY = 40, merchantName = "Alchemist Talia"
            ),
            UndergroundCampZone(
                floorId = -6,
                name = "GOREK LABYRINTH VANGUARD CAMP (PZ)",
                minX = 20, minY = 38, maxX = 28, maxY = 42,
                healerX = 21, healerY = 40, healerName = "High Cleric Vesper",
                merchantX = 27, merchantY = 40, merchantName = "Quartermaster Ryn"
            )
        ) + ContinentalDungeonGenerator.generatedDungeons.map { it.camp }
    }

    fun undergroundCampForFloor(floorId: Int): UndergroundCampZone? =
        undergroundCamps.firstOrNull { it.floorId == floorId }

    val npcs: List<NpcSpot> by lazy {
        buildList {
            // Rookvale Capital City NPCs
            add(NpcSpot(0, ROOKVALE_ORIGIN_X + 162, ROOKVALE_ORIGIN_Y + 204, "Alaric", NpcRole.SHOP_ALCHEMIST))
            add(NpcSpot(0, ROOKVALE_ORIGIN_X + 193, ROOKVALE_ORIGIN_Y + 179, "Brother Alden", NpcRole.TEMPLE_PRIEST))
            add(NpcSpot(0, ORACLE_X, ORACLE_Y, "The Oracle", NpcRole.ORACLE))
            add(NpcSpot(0, REAPER_X, REAPER_Y, "Mortis, Keeper of Reapers", NpcRole.REAPER_KEEPER))

            // 6 Strictly Coastline-Validated Ocean Harbor Ferry Captains
            WorldBiomeBlueprint.harborPorts.forEach { port ->
                add(NpcSpot(0, port.dockX, port.dockY, port.captainName, NpcRole.HARBOR_CAPTAIN))
            }

            // Biome-Ideology Overland Empires, Towns & Forward Camps (Healers + Supply Alchemists)
            add(NpcSpot(0, 1044, 590, "Sister Elen", NpcRole.TEMPLE_PRIEST))        // Spirewood Crossing Camp
            add(NpcSpot(0, 1040, 594, "Peddler Fen", NpcRole.SHOP_ALCHEMIST))       // Spirewood Crossing Camp
            add(NpcSpot(0, 549, 591, "Brother Corwin", NpcRole.TEMPLE_PRIEST))      // Mirewatch Stilt Citadel
            add(NpcSpot(0, 541, 599, "Apothecary Maren", NpcRole.SHOP_ALCHEMIST))   // Mirewatch Stilt Citadel
            add(NpcSpot(0, 360, 558, "Bog Priestess Nira", NpcRole.TEMPLE_PRIEST))  // Boghollow Stilt Camp
            add(NpcSpot(0, 356, 562, "Supplier Galt", NpcRole.SHOP_ALCHEMIST))      // Boghollow Stilt Camp
            add(NpcSpot(0, 380, 787, "Merchant Zara", NpcRole.SHOP_ALCHEMIST))      // Sunspire Oasis Empire
            add(NpcSpot(0, 371, 793, "Sun Cleric Hakim", NpcRole.TEMPLE_PRIEST))    // Sunspire Oasis Empire
            add(NpcSpot(0, 317, 910, "Nomad Healer Safi", NpcRole.TEMPLE_PRIEST))   // Scorchwind Nomad Camp
            add(NpcSpot(0, 313, 914, "Caravaner Idris", NpcRole.SHOP_ALCHEMIST))    // Scorchwind Nomad Camp
            add(NpcSpot(0, 1400, 200, "Frost Cleric Bjorn", NpcRole.TEMPLE_PRIEST)) // Frostpeak Granite Citadel
            add(NpcSpot(0, 1392, 208, "Runesmith Astrid", NpcRole.SHOP_ALCHEMIST))  // Frostpeak Granite Citadel
            add(NpcSpot(0, 1270, 256, "Healer Freya", NpcRole.TEMPLE_PRIEST))       // Boreal Watch Camp
            add(NpcSpot(0, 1266, 260, "Quartermaster Tor", NpcRole.SHOP_ALCHEMIST)) // Boreal Watch Camp
            add(NpcSpot(0, 390, 257, "Ash Cleric Malakor", NpcRole.TEMPLE_PRIEST))  // Ashen Bastion Empire
            add(NpcSpot(0, 382, 263, "War Alchemist Nyx", NpcRole.SHOP_ALCHEMIST))  // Ashen Bastion Empire
            add(NpcSpot(0, 908, 247, "Shade Priest Vane", NpcRole.TEMPLE_PRIEST))   // Duskport Shadow Keep
            add(NpcSpot(0, 900, 253, "Merchant Kestrel", NpcRole.SHOP_ALCHEMIST))   // Duskport Shadow Keep
            add(NpcSpot(0, 964, 992, "Elder Lyra", NpcRole.TEMPLE_PRIEST))          // Sunleaf Stilt Village
            add(NpcSpot(0, 956, 998, "Herbalist Soren", NpcRole.SHOP_ALCHEMIST))    // Sunleaf Stilt Village
            add(NpcSpot(0, 1466, 949, "Ember Cleric Ignar", NpcRole.TEMPLE_PRIEST)) // Ember Watch Citadel
            add(NpcSpot(0, 1458, 955, "Supplier Cassian", NpcRole.SHOP_ALCHEMIST))  // Ember Watch Citadel

            // Subterranean Forward Camp Healers & Supply Alchemists across Floors -1..-6
            undergroundCamps.forEach { camp ->
                add(NpcSpot(camp.floorId, camp.healerX, camp.healerY, camp.healerName, NpcRole.TEMPLE_PRIEST))
                add(NpcSpot(camp.floorId, camp.merchantX, camp.merchantY, camp.merchantName, NpcRole.SHOP_ALCHEMIST))
            }
        }
    }

    val landmarks: List<LandmarkLabel> by lazy {
        buildList {
            // Rookvale Valley & Capital City (shifted by +1200, +400)
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 192, "TEMPLE & SANCTUARY PLAZA"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 175, ROOKVALE_ORIGIN_Y + 162, "TEMPLE OF REAPERS"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 197, "TOWN SQUARE FOUNTAIN"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 164, ROOKVALE_ORIGIN_Y + 185, "TOWN DEPOT HALL"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 163, ROOKVALE_ORIGIN_Y + 205, "ALARIC'S MARKET QUARTER"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 225, ROOKVALE_ORIGIN_Y + 182, "RUNECRAFTING ACADEMY"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 224, ROOKVALE_ORIGIN_Y + 204, "CRAFT & FLETCHING WARD"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 245, ROOKVALE_ORIGIN_Y + 204, "LOGGING ORCHARD"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 183, ROOKVALE_ORIGIN_Y + 231, "WEST RIVER FISHING PIER"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 242, ROOKVALE_ORIGIN_Y + 179, "SCHOLAR'S MARKET PLAZA"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 152, ROOKVALE_ORIGIN_Y + 172, "WEST GARDEN COURTYARD"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 134, "NORTH STARTER MEADOWS"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 97, "NW COPPER QUARRY MINES (FLOOR -11)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 264, ROOKVALE_ORIGIN_Y + 85, "NORTH MOUNTAIN CAVERN (FLOOR -12)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 67, "CRAG DEEP HOLLOW (FLOOR -14)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 207, "DARK FOREST SINKHOLE (FLOOR -13)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 88, ROOKVALE_ORIGIN_Y + 320, "BRIAR OUTLAW CAMP (LVL 12-16)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 317, "BRIAR CATACOMBS (FLOOR -1)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 320, "WATCHWOOD OUTLAW CAMP (LVL 15-20)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 317, "WATCHWOOD CAVERNS (FLOOR -2)"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 196, ROOKVALE_ORIGIN_Y + 8, "FROSTPEAK NORTH HIGHWAY"))
            add(LandmarkLabel(0, ROOKVALE_ORIGIN_X + 8, ROOKVALE_ORIGIN_Y + 192, "SPIREWOOD WEST HIGHWAY"))

            // Biome-Ideology Empires, Towns & Forward Camps
            WorldBiomeBlueprint.secondarySettlements.forEach { s ->
                add(LandmarkLabel(0, s.centerX, s.centerY, "${s.name.uppercase()} (${s.levelRangeLabel.uppercase()})"))
            }
            // Validated Ocean Harbor Ports
            WorldBiomeBlueprint.harborPorts.forEach { port ->
                add(LandmarkLabel(0, port.dockX, port.dockY, port.name.uppercase()))
            }

            // Sea-Bridges, Shrines & Outer-Continent Dedicated Biome Dungeon Entrances
            add(LandmarkLabel(0, 760, 790, "MAELSTROM SEA-DRAGON SHRINE"))
            add(LandmarkLabel(0, 650, 211, "FROSTBAY SEA-BRIDGE"))
            add(LandmarkLabel(0, 770, 591, "CENTRAL ALDER CAUSEWAY"))
            add(LandmarkLabel(0, 318, 252, "ASHEN CALDERA FOUNDRY (FLOOR -10)"))
            add(LandmarkLabel(0, 295, 515, "SUNKEN MIRE VAULTS (FLOOR -7)"))
            add(LandmarkLabel(0, 1020, 175, "BLIGHTWOOD BARROW (FLOOR -9)"))
            add(LandmarkLabel(0, 1320, 260, "FROSTPEAK GRIMMAW QUARRY (FLOOR -5)"))
            add(LandmarkLabel(0, 242, 1020, "BADLANDS GOREK LABYRINTH (FLOOR -6)"))
            add(LandmarkLabel(0, 720, 1020, "OVERGROWN ZIGGURAT VAULT (FLOOR -8)"))
            add(LandmarkLabel(0, 1510, 975, "THE EMBER SANCTUM (FLOOR -15)"))

            // Dungeon Floor Landmarks & Subterranean Safe Forward Camps
            undergroundCamps.forEach { camp ->
                add(LandmarkLabel(camp.floorId, (camp.minX + camp.maxX) / 2, (camp.minY + camp.maxY) / 2, camp.name))
            }
            add(LandmarkLabel(-1, 24, 34, "DESCENT TO BRIAR BROOD SANCTUM"))
            add(LandmarkLabel(-2, 24, 12, "DESCENT TO MOLTEN PIT THRONE"))
            add(LandmarkLabel(-3, 24, 18, "DEEP FORGE OUTPOST"))
            add(LandmarkLabel(-5, 24, 12, "DESCENT TO KRAGOR'S DEEP QUARRY PIT"))
            add(LandmarkLabel(-6, 24, 11, "DESCENT TO VAELGOR'S INNER SANCTUM"))
            ContinentalDungeonGenerator.generatedDungeons.forEach { d ->
                add(d.descentLandmark)
            }
            ContinentalDungeonGenerator.generatedLowerBossLairs.forEach { lair ->
                add(lair.bossLandmark)
            }
        }
    }

    val portals: List<FloorPortal> = listOf(
        // --- 6 Completely Separate Rookvale Heartland Dungeons (Strictly 1-to-1, Lv 1-20, ZERO inside Rookvale City!) ---
        // 1. SW Briar Outlaw Camp <-> Floor -1 Briar Catacombs (Lvl 7-12, standalone)
        FloorPortal(0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 316, -1, 28, 11, requiresRope = false),
        FloorPortal(-1, 28, 10, 0, ROOKVALE_ORIGIN_X + 84, ROOKVALE_ORIGIN_Y + 317, requiresRope = false),
        // 2. SE Watchwood Outlaw Camp <-> Floor -2 Watchwood Caverns (Lvl 13-18, sole Deep Delve entry to -3/-4)
        FloorPortal(0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 316, -2, 24, 37, requiresRope = false),
        FloorPortal(-2, 24, 38, 0, ROOKVALE_ORIGIN_X + 316, ROOKVALE_ORIGIN_Y + 317, requiresRope = false),
        // 3. NW Copper Quarry Rope-Hole <-> Floor -11 NW Copper Quarry Mines (Lvl 5-10, standalone)
        FloorPortal(0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 96, -11, 24, 40, requiresRope = false),
        FloorPortal(-11, 24, 41, 0, ROOKVALE_ORIGIN_X + 92, ROOKVALE_ORIGIN_Y + 97, requiresRope = true),
        // 4. North Mountain Cavern Stairs <-> Floor -12 North Mountain Cavern (Lvl 7-12, standalone)
        FloorPortal(0, ROOKVALE_ORIGIN_X + 264, ROOKVALE_ORIGIN_Y + 84, -12, 24, 40, requiresRope = false),
        FloorPortal(-12, 24, 41, 0, ROOKVALE_ORIGIN_X + 264, ROOKVALE_ORIGIN_Y + 85, requiresRope = false),
        // 5. East Dark Forest Sinkhole Rope-Hole <-> Floor -13 Dark Forest Sinkhole (Lvl 9-14, standalone)
        FloorPortal(0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 206, -13, 24, 40, requiresRope = false),
        FloorPortal(-13, 24, 41, 0, ROOKVALE_ORIGIN_X + 312, ROOKVALE_ORIGIN_Y + 207, requiresRope = true),
        // 6. NE Crag Hills Stairs <-> Floor -14 Crag Deep Hollow (Lvl 13-18, standalone)
        FloorPortal(0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 66, -14, 24, 40, requiresRope = false),
        FloorPortal(-14, 24, 41, 0, ROOKVALE_ORIGIN_X + 308, ROOKVALE_ORIGIN_Y + 67, requiresRope = false),

        // --- 7 Separate Outer-Continent Dedicated Biome Dungeons (Strictly 1-to-1 Bidirectional!) ---
        // Frostpeak Grimmaw Ridge <-> Floor -5 The Grimmaw Quarry
        FloorPortal(0, 1320, 260, -5, 24, 40, requiresRope = false),
        FloorPortal(-5, 24, 41, 0, 1320, 261, requiresRope = false),
        // Badlands Gorek Gate <-> Floor -6 The Gorek Labyrinth
        FloorPortal(0, 242, 1020, -6, 24, 40, requiresRope = false),
        FloorPortal(-6, 24, 41, 0, 242, 1021, requiresRope = false),
        // Writhing Sinkhole (Whispering Marsh) <-> Floor -7 The Sunken Mire Vaults
        FloorPortal(0, 295, 515, -7, 24, 40, requiresRope = false),
        FloorPortal(-7, 24, 41, 0, 295, 516, requiresRope = false),
        // Jungle Ruin Vault (Emerald Jungle) <-> Floor -8 The Overgrown Ziggurat Vault
        FloorPortal(0, 720, 1020, -8, 24, 40, requiresRope = false),
        FloorPortal(-8, 24, 41, 0, 720, 1021, requiresRope = false),
        // Blightwood Barrow (Northern Frontier) <-> Floor -9 The Blightwood Barrow Catacombs
        FloorPortal(0, 1020, 175, -9, 24, 40, requiresRope = false),
        FloorPortal(-9, 24, 41, 0, 1020, 176, requiresRope = false),
        // Ashen Caldera Shaft <-> Floor -10 The Ashen Caldera Foundry
        FloorPortal(0, 318, 252, -10, 24, 40, requiresRope = false),
        FloorPortal(-10, 24, 41, 0, 318, 253, requiresRope = false),
        // Ember Isles Volcanic Descent <-> Floor -15 The Ember Sanctum
        FloorPortal(0, 1510, 975, -15, 24, 40, requiresRope = false),
        FloorPortal(-15, 24, 41, 0, 1510, 976, requiresRope = false),

        // --- Classic Deep Delve Chain (Exclusive to Watchwood Caverns Floor -2) ---
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
    ) + ContinentalDungeonGenerator.lowerBossLairSpecs.flatMap { lair ->
        // Dedicated 1-to-1 Upper Dungeon Floor <-> Lower Boss Lair (-16..-28)
        // Lower Boss Lairs have ONLY STAIRS_UP returning to their paired upper floor (no surface exit).
        listOf(
            FloorPortal(
                fromFloor = lair.parentUpperFloorId,
                fromX = lair.parentStairsDownX,
                fromY = lair.parentStairsDownY,
                toFloor = lair.floorId,
                toX = ContinentalDungeonGenerator.LOWER_LANDING_X,
                toY = ContinentalDungeonGenerator.LOWER_LANDING_Y,
                requiresRope = false
            ),
            FloorPortal(
                fromFloor = lair.floorId,
                fromX = ContinentalDungeonGenerator.LOWER_STAIRS_UP_X,
                fromY = ContinentalDungeonGenerator.LOWER_STAIRS_UP_Y,
                toFloor = lair.parentUpperFloorId,
                toX = lair.parentReturnX,
                toY = lair.parentReturnY,
                requiresRope = false
            )
        )
    }

    private val dungeonFixedSpawns: List<MonsterSpawn> = listOf(
        // --- Floor -1: Briar Catacombs (Dedicated Briar Roster — Boss dwells below in Floor -16) ---
        MonsterSpawn(-1, 26, 13, MonsterType.BRIAR_CRYPT_CRAWLER),
        MonsterSpawn(-1, 13, 21, MonsterType.CATACOMB_BONE_ARCHER),
        MonsterSpawn(-1, 37, 13, MonsterType.BRIAR_CRYPT_CRAWLER),
        MonsterSpawn(-1, 13, 24, MonsterType.SKELETON),
        MonsterSpawn(-1, 37, 30, MonsterType.POISON_SPIDER),
        MonsterSpawn(-1, 24, 27, MonsterType.CATACOMB_BONE_ARCHER),
        MonsterSpawn(-1, 24, 19, MonsterType.BRIAR_CRYPT_CRAWLER),
        MonsterSpawn(-1, 21, 35, MonsterType.BRIAR_CRYPT_CRAWLER),
        MonsterSpawn(-1, 27, 35, MonsterType.CATACOMB_BONE_ARCHER),

        // --- Floor -2: Watchwood Caverns (Dedicated Watchwood Roster — Boss dwells below in Floor -21) ---
        MonsterSpawn(-2, 12, 33, MonsterType.CINDER_IMP),
        MonsterSpawn(-2, 35, 33, MonsterType.WATCHWOOD_DREAD_KNIGHT),
        MonsterSpawn(-2, 13, 25, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-2, 37, 25, MonsterType.GHOUL),
        MonsterSpawn(-2, 24, 25, MonsterType.WATCHWOOD_DREAD_KNIGHT),
        MonsterSpawn(-2, 20, 12, MonsterType.CINDER_IMP),
        MonsterSpawn(-2, 28, 12, MonsterType.WATCHWOOD_DREAD_KNIGHT),
        MonsterSpawn(-2, 24, 15, MonsterType.FIRE_DEVIL),

        // --- Floor -3: Abyssal Crypt & Boss (Classic Deep Delve below Watchwood Caverns) ---
        MonsterSpawn(-3, 14, 14, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 34, 14, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 12, 24, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 35, 24, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 16, 32, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 32, 32, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 24, 34, MonsterType.DEMON_LORD),

        // --- Floor -4: Obsidian Wyrm Sanctum (Classic Deep Delve below Abyssal Crypt) ---
        MonsterSpawn(-4, 18, 32, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-4, 32, 32, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-4, 14, 24, MonsterType.NECROMANCER),
        MonsterSpawn(-4, 34, 24, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-4, 24, 16, MonsterType.ELDER_DRAGON),

        // --- Floor -5: The Grimmaw Quarry (Dedicated Grimmaw Roster — Kragor dwells below in Floor -22) ---
        MonsterSpawn(-5, 14, 34, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(-5, 34, 34, MonsterType.GRIMMAW_QUARRYMAN),
        MonsterSpawn(-5, 12, 25, MonsterType.GRIMMAW_ROCK_HURLER),
        MonsterSpawn(-5, 36, 25, MonsterType.GRIMMAW_ROCK_HURLER),
        MonsterSpawn(-5, 20, 26, MonsterType.GRIMMAW_CRUSHER),
        MonsterSpawn(-5, 28, 26, MonsterType.GRIMMAW_CRUSHER),

        // --- Floor -6: The Gorek Labyrinth (Dedicated Gorek Roster — Vaelgor dwells below in Floor -23) ---
        MonsterSpawn(-6, 13, 34, MonsterType.GOREK_RAIDER),
        MonsterSpawn(-6, 35, 34, MonsterType.GOREK_RAIDER),
        MonsterSpawn(-6, 13, 24, MonsterType.GOREK_PHALANX),
        MonsterSpawn(-6, 35, 24, MonsterType.GOREK_PHALANX),
        MonsterSpawn(-6, 24, 25, MonsterType.GOREK_HEX_CALLER),
        MonsterSpawn(-6, 19, 14, MonsterType.GOREK_HEX_CALLER),
        MonsterSpawn(-6, 29, 14, MonsterType.GOREK_PHALANX)
    )

    /**
     * Overland (Floor 0) monsters are generated dynamically from the underlying terrain
     * [BiomeType] pools via [BiomeMonsterSpawner] (no hand-placed town spots, zero PZ
     * intrusion, zero chunk-cache churn). Underground upper floors (-1..-15) append their
     * dedicated regular patrols, and Lower Boss Lairs (-16..-28) append their unique bosses
     * and elite escorts.
     */
    val monsterSpawns: List<MonsterSpawn> by lazy {
        val overlandSpawns = BiomeMonsterSpawner.generateOverlandBiomeSpawns(
            isProtectionZone = { x, y -> isOverlandProtectionZone(x, y) },
            tileOracle = { x, y -> fastHeartlandTileOrNull(x, y) ?: WorldBiomeBlueprint.tileAtGlobal(x, y) }
        )
        overlandSpawns +
            dungeonFixedSpawns +
            ContinentalDungeonGenerator.generatedDungeons.flatMap { it.spawns } +
            ContinentalDungeonGenerator.generatedLowerBossLairs.flatMap { it.spawns }
    }

    private fun isOverlandProtectionZone(x: Int, y: Int): Boolean {
        return (x in (ROOKVALE_ORIGIN_X + 147)..(ROOKVALE_ORIGIN_X + 255) &&
            y in (ROOKVALE_ORIGIN_Y + 149)..(ROOKVALE_ORIGIN_Y + 227)) ||
            WorldBiomeBlueprint.isSecondarySafeZone(x, y)
    }

    private val rookvaleHeartlandGrid: ChunkedTileGrid by lazy { buildRookvaleHeartlandGrid() }

    val floors: Map<Int, WorldFloor> by lazy {
        buildMap {
            put(0, buildOverlandSurface())
            put(-1, buildSewersFloor())
            put(-2, buildCryptsFloor())
            put(-3, buildForgehallFloor())
            put(-4, buildWyrmSanctumFloor())
            put(-5, buildGrimmawQuarryFloor())
            put(-6, buildGorekLabyrinthFloor())
            ContinentalDungeonGenerator.generatedDungeons.forEach { d ->
                put(d.spec.floorId, d.floor)
            }
            ContinentalDungeonGenerator.generatedLowerBossLairs.forEach { lair ->
                put(lair.spec.floorId, lair.floor)
            }
        }
    }

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
        in -15..-7 -> 24 to 40
        in -28..-16 -> ContinentalDungeonGenerator.LOWER_LANDING_X to ContinentalDungeonGenerator.LOWER_LANDING_Y
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
            val resolvedLevel = if (spawn.floor == 0) {
                BiomeProgressionRules.resolveOverlandSpawnLevel(spawn.x, spawn.y, spawn.type)
            } else {
                BiomeProgressionRules.resolveDungeonSpawnLevel(spawn.floor, spawn.x, spawn.y, spawn.type)
            }
            BiomeProgressionRules.createScaledMonster(
                id = idx + 1,
                type = spawn.type,
                floor = spawn.floor,
                x = spawn.x,
                y = spawn.y,
                targetLevel = resolvedLevel,
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

        // 2. Seamless organic valley edge transition (no hard rectangular rock/tree box border!)
        for (i in 0 until s) {
            val edgeHashN = deterministicHash(i, 0, 53) and 255
            val edgeHashS = deterministicHash(i, s - 1, 59) and 255
            val edgeHashW = deterministicHash(0, i, 61) and 255
            val edgeHashE = deterministicHash(s - 1, i, 67) and 255
            if (i !in 186..206) {
                if (edgeHashN < 18) grid.set(i, 1, TileType.TREE)
                if (edgeHashS < 18) grid.set(i, s - 2, TileType.TREE)
            }
            if (i !in 182..202 && i !in 222..252) {
                if (edgeHashW < 18) grid.set(1, i, TileType.TREE)
                if (edgeHashE < 18 && i < 14) grid.set(s - 2, i, TileType.TREE)
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
                if ((x - 88) * (x - 88) + (y - 320) * (y - 320) <= 18 * 18) continue // SW Briar Outlaw Camp clearing
                if ((x - 312) * (x - 312) + (y - 320) * (y - 320) <= 18 * 18) continue // SE Watchwood Outlaw Camp clearing

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

        // 7B. SW ROOKVALE HEARTLAND: BRIAR OUTLAW CAMP (Open Rustic Lv 12-16 Camp — NO rectangular walls/slabs!)
        // Organic irregular dirt clearing blended smoothly into Heartland grass & woodland
        for (cy in 307..333) {
            for (cx in 75..101) {
                val dx = (cx - 88).toDouble()
                val dy = (cy - 320).toDouble()
                val angle = kotlin.math.atan2(dy, dx)
                val organicRadius = 11.5 + sin(angle * 3.0) * 1.6 + cos(angle * 2.0 + 0.8) * 1.2
                val dist = kotlin.math.hypot(dx, dy)
                if (dist <= organicRadius) {
                    val h = deterministicHash(cx, cy, 311) and 31
                    val tile = when {
                        dist <= 7.5 -> TileType.DIRT_FLOOR
                        h < 18 -> TileType.DIRT_FLOOR
                        h < 25 -> TileType.GRASS
                        else -> TileType.FLOWER_GRASS
                    }
                    grid.set(cx, cy, tile)
                }
            }
        }
        // Rustic outlaw camp props: blazing campfires, pitched canvas tents, supply barrels, weapon racks & lanterns
        listOf(88 to 320, 82 to 325).forEach { (fx, fy) -> grid.set(fx, fy, TileType.CAMPFIRE_BRAZIER) }
        listOf(81 to 313, 82 to 313, 94 to 314, 95 to 314, 93 to 326, 94 to 326).forEach { (tx, ty) ->
            grid.set(tx, ty, TileType.MARKET_AWNING)
        }
        listOf(80 to 314, 96 to 315, 92 to 327, 85 to 327).forEach { (px, py) -> grid.set(px, py, TileType.CRATE_BARREL) }
        listOf(91 to 316, 86 to 325).forEach { (px, py) -> grid.set(px, py, TileType.WEAPON_RACK) }
        listOf(99 to 318, 99 to 322, 83 to 315, 85 to 315).forEach { (lx, ly) -> grid.set(lx, ly, TileType.LANTERN_POST) }
        // Natural woodland shade trees framing the rustic camp edge organically
        listOf(73 to 312, 74 to 327, 88 to 306, 102 to 311, 101 to 330, 85 to 334).forEach { (tx, ty) ->
            grid.set(tx, ty, TileType.TREE)
        }
        // Low-tier Heartland Copper & Bog Iron outcrops tucked among surrounding meadows
        listOf(71 to 319, 76 to 334).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_COPPER) }
        listOf(105 to 310, 104 to 331).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_BOG_IRON) }
        // Winding organic dirt trail curving west off South Frontier Road (195, 318) -> Briar Outlaw Camp (98, 320)
        grid.drawRoadSegment(195, 318, 162, 314, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(162, 314, 128, 324, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(128, 324, 98, 320, 1, TileType.DIRT_FLOOR)
        // Briar Catacombs Entrance (Floor -1 • Lvl 7-12) tucked on camp's north-west edge
        grid.set(84, 316, TileType.STAIRS_DOWN)
        grid.set(84, 317, TileType.DIRT_FLOOR)

        // 7C. SE ROOKVALE HEARTLAND: WATCHWOOD OUTLAW CAMP (Open Rustic Lv 15-20 Camp — NO rectangular walls/slabs!)
        for (cy in 307..333) {
            for (cx in 299..325) {
                val dx = (cx - 312).toDouble()
                val dy = (cy - 320).toDouble()
                val angle = kotlin.math.atan2(dy, dx)
                val organicRadius = 11.5 + sin(angle * 3.0 + 1.1) * 1.6 + cos(angle * 2.0) * 1.2
                val dist = kotlin.math.hypot(dx, dy)
                if (dist <= organicRadius) {
                    val h = deterministicHash(cx, cy, 317) and 31
                    val tile = when {
                        dist <= 7.5 -> TileType.DIRT_FLOOR
                        h < 18 -> TileType.DIRT_FLOOR
                        h < 26 -> TileType.DARK_GRASS
                        else -> TileType.GRASS
                    }
                    grid.set(cx, cy, tile)
                }
            }
        }
        // Rustic woodland outlaw camp props: campfires, pitched canvas tents, supply crates, weapon racks & lanterns
        listOf(312 to 320, 307 to 324).forEach { (fx, fy) -> grid.set(fx, fy, TileType.CAMPFIRE_BRAZIER) }
        listOf(305 to 313, 306 to 313, 319 to 314, 320 to 314, 316 to 326, 317 to 326).forEach { (tx, ty) ->
            grid.set(tx, ty, TileType.MARKET_AWNING)
        }
        listOf(304 to 314, 321 to 315, 315 to 327, 309 to 327).forEach { (px, py) -> grid.set(px, py, TileType.CRATE_BARREL) }
        listOf(309 to 316, 314 to 324).forEach { (px, py) -> grid.set(px, py, TileType.WEAPON_RACK) }
        listOf(301 to 318, 301 to 322, 315 to 315, 317 to 315).forEach { (lx, ly) -> grid.set(lx, ly, TileType.LANTERN_POST) }
        // Natural woodland trees framing Watchwood Outlaw Camp organically
        listOf(298 to 312, 299 to 328, 312 to 306, 326 to 312, 325 to 329, 311 to 334).forEach { (tx, ty) ->
            grid.set(tx, ty, TileType.TREE)
        }
        // Low-tier Heartland Tin & Bog Iron outcrops tucked among surrounding Watchwood trees
        listOf(295 to 319, 300 to 334).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_BOG_IRON) }
        listOf(328 to 316, 323 to 333).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_TIN) }
        // Winding organic dirt trail curving east off South Frontier Road (197, 318) -> Watchwood Outlaw Camp (302, 320)
        grid.drawRoadSegment(197, 318, 232, 323, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(232, 323, 268, 315, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(268, 315, 302, 320, 1, TileType.DIRT_FLOOR)
        // Watchwood Caverns Entrance (Floor -2 • Lvl 13-18) tucked on camp's north-east edge
        grid.set(316, 316, TileType.STAIRS_DOWN)
        grid.set(316, 317, TileType.DIRT_FLOOR)

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

        // Sunlit West Garden Courtyard (replaces old cellar rope-hole — zero dungeons inside Rookvale City)
        grid.fillRect(150, 170, 155, 175, TileType.FLOWER_GRASS)
        grid.fillRect(151, 171, 154, 174, TileType.COBBLESTONE)
        grid.set(152, 172, TileType.FLOWER_GRASS)
        grid.set(153, 172, TileType.FLOWER_GRASS)
        grid.set(150, 170, TileType.HEDGE)
        grid.set(155, 170, TileType.HEDGE)
        grid.set(150, 175, TileType.HEDGE)
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
        // Open Scholar's Market Courtyard & Garden Plaza beside Academy (replaces old city sewers entrance)
        grid.fillRect(239, 176, 245, 183, TileType.COBBLESTONE)
        grid.fillRect(240, 181, 244, 182, TileType.FLOWER_GRASS)
        grid.set(242, 178, TileType.MARKET_AWNING)
        grid.set(244, 179, TileType.CRATE_BARREL)
        grid.set(239, 183, TileType.HEDGE)
        grid.set(245, 183, TileType.HEDGE)
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
        // West Blacksmith Forge: Glowing Lava/Coal Smelting Furnace, Rookvale Town Forge Stall, Horned Anvil, Weapon Rack & Coal Barrels
        grid.set(219, 201, TileType.SMELTING_FURNACE)
        grid.set(221, 201, TileType.BIOME_STALL_HEARTLAND)
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

        // --- DISTRICT 6: ROOKVALE EASTERN OCEAN BAY (true open sea for Rookvale Royal Harbor) ---
        for (ly in 0 until s) {
            for (lx in 354 until s) {
                val seaTile = WorldBiomeBlueprint.rawRookvaleEasternSeaTile(
                    ROOKVALE_ORIGIN_X + lx,
                    ROOKVALE_ORIGIN_Y + ly
                )
                if (seaTile != null) {
                    grid.set(lx, ly, seaTile)
                } else if (lx >= 358 && ly in 14..386 && ly !in 189..195) {
                    // Golden coastal beach apron along Rookvale's Eastern Ocean shoreline
                    val prev = grid.get(lx, ly)
                    if (prev == TileType.GRASS || prev == TileType.DARK_GRASS || prev == TileType.FLOWER_GRASS) {
                        grid.set(lx, ly, TileType.SAND)
                    }
                }
            }
        }

        // Guarantee every Rookvale-local Floor 0 portal landing is walkable ground
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
    /**
     * Fast direct lookup inside the pre-built Rookvale Heartland grid (1200..1599, 400..799)
     * without allocating or evicting streaming chunks in [ChunkedTileGrid].
     */
    fun fastHeartlandTileOrNull(gx: Int, gy: Int): TileType? {
        if (!WorldBiomeBlueprint.isInRookvaleHeartland(gx, gy)) return null
        WorldBiomeBlueprint.validatedPortTileAt(gx, gy)?.let { return it }
        return rookvaleHeartlandGrid.get(gx - ROOKVALE_ORIGIN_X, gy - ROOKVALE_ORIGIN_Y)
    }

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
                            val tile = WorldBiomeBlueprint.validatedPortTileAt(gx, gy)
                                ?: if (WorldBiomeBlueprint.isInRookvaleHeartland(gx, gy)) {
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
            isProtectionZone = { x, y -> isOverlandProtectionZone(x, y) },
            dangerLevel = "Varies",
            recommendedLevel = "Lvl 1-75+",
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

        // Subterranean Safe Forward Camp (PZ) around Sewer Gatehouse entry (x=25..31, y=9..12)
        val camp = undergroundCampForFloor(-1)!!
        g[9][25] = TileType.WAR_BANNER
        g[9][31] = TileType.WAR_BANNER
        g[11][25] = TileType.CAMPFIRE_BRAZIER
        g[11][31] = TileType.CAMPFIRE_BRAZIER
        g[10][26] = TileType.COBBLESTONE
        g[10][30] = TileType.COBBLESTONE

        // Sole dedicated return stairway back up to SW Briar Outlaw Camp (1284, 717)
        // plus lower boss lair descent stairs at the far end down to Floor -16 (Briar Brood Sanctum)
        g[10][28] = TileType.STAIRS_UP
        g[34][24] = TileType.STAIRS_DOWN

        return WorldFloor(
            floorId = -1,
            name = "Briar Catacombs",
            subtitle = "Briar Outlaw Ruins • Floor -1 • Forward Camp PZ",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = "Moderate",
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(-1),
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

        // Mire-Silver, Peat-Coal, Vesperite & Deep Cinderite Veins guarded in Floor -2 chambers
        listOf(10 to 32, 37 to 32).forEach { (x, y) -> g[y][x] = TileType.ORE_MIRE_SILVER }
        listOf(11 to 35, 36 to 35).forEach { (x, y) -> g[y][x] = TileType.ORE_PEAT_COAL }
        listOf(8 to 21, 8 to 28, 34 to 35).forEach { (x, y) -> g[y][x] = TileType.ORE_VESPERITE }
        listOf(18 to 8, 30 to 8, 42 to 27, 19 to 39).forEach { (x, y) -> g[y][x] = TileType.ORE_CINDERITE }

        // Flickering Iron Wall Torches casting warm light pools down every corridor & chamber
        listOf(
            24 to 41, 20 to 33, 28 to 33, 13 to 30, 35 to 30,
            10 to 20, 40 to 20, 22 to 28, 26 to 28,
            22 to 17, 26 to 17, 14 to 12, 34 to 12, 24 to 6
        ).forEach { (tx, ty) -> g[ty][tx] = TileType.TORCH_WALL }

        // Subterranean Safe Forward Camp (PZ) in Southern Vestibule (x=20..28, y=35..40)
        val camp = undergroundCampForFloor(-2)!!
        g[35][20] = TileType.OBSIDIAN_WALL
        g[35][28] = TileType.OBSIDIAN_WALL
        g[36][21] = TileType.CAMPFIRE_BRAZIER
        g[36][27] = TileType.CAMPFIRE_BRAZIER
        g[39][22] = TileType.WAR_BANNER
        g[39][26] = TileType.WAR_BANNER
        g[38][21] = TileType.COBBLESTONE
        g[38][27] = TileType.COBBLESTONE

        // Portals: dedicated return stairway back up to SE Watchwood Outlaw Camp (1516, 717),
        // lower boss lair descent stairs at (24,12) -> Floor -21 (Molten Pit Throne),
        // plus exclusive Deep Delve portals down to Floor -3 (Abyssal Crypt) & Floor -4 (Wyrm Sanctum)
        g[38][24] = TileType.STAIRS_UP
        g[12][24] = TileType.STAIRS_DOWN
        g[8][24] = TileType.STAIRS_DOWN
        g[24][40] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -2,
            name = "Watchwood Caverns",
            subtitle = "Watchwood Outlaw Caverns • Floor -2 • Deep Delve Entry • Forward Camp PZ",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = "High",
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(-2),
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

        // Subterranean Safe Forward Camp (PZ) at Northern Entry Vestibule (x=20..28, y=6..11)
        val camp = undergroundCampForFloor(-3)!!
        g[6][20] = TileType.OBSIDIAN_WALL
        g[6][28] = TileType.OBSIDIAN_WALL
        g[10][20] = TileType.CAMPFIRE_BRAZIER
        g[10][28] = TileType.CAMPFIRE_BRAZIER
        g[7][22] = TileType.WAR_BANNER
        g[7][26] = TileType.WAR_BANNER
        g[8][21] = TileType.COBBLESTONE
        g[8][27] = TileType.COBBLESTONE

        // Portals
        g[8][24] = TileType.STAIRS_UP
        g[24][40] = TileType.ROPE_SPOT_UP
        g[40][24] = TileType.STAIRS_DOWN
        g[34][10] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -3,
            name = "Abyssal Crypt",
            subtitle = "Dungeon Floor -3 • Forward Camp PZ",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = "Extreme",
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(-3),
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

        // Rich Glacier-Quartz, Frost-Iron & Cinderite Veins guarded by Wyrms & the Ancient Dragon
        listOf(9 to 30, 14 to 36).forEach { (x, y) -> g[y][x] = TileType.ORE_GLACIER_QUARTZ }
        listOf(10 to 36, 33 to 31).forEach { (x, y) -> g[y][x] = TileType.ORE_FROST_IRON }
        listOf(13 to 10, 35 to 10, 12 to 22, 36 to 22, 24 to 9).forEach { (x, y) -> g[y][x] = TileType.ORE_CINDERITE }

        // Subterranean Safe Vanguard Camp (PZ) at Southern Entry Bastion (x=19..29, y=35..41)
        val camp = undergroundCampForFloor(-4)!!
        g[35][19] = TileType.OBSIDIAN_WALL
        g[35][29] = TileType.OBSIDIAN_WALL
        g[36][20] = TileType.CAMPFIRE_BRAZIER
        g[36][28] = TileType.CAMPFIRE_BRAZIER
        g[40][20] = TileType.WAR_BANNER
        g[40][28] = TileType.WAR_BANNER
        g[39][21] = TileType.COBBLESTONE
        g[39][27] = TileType.COBBLESTONE

        // Portals
        g[40][24] = TileType.STAIRS_UP
        g[34][10] = TileType.ROPE_SPOT_UP

        return WorldFloor(
            floorId = -4,
            name = "Obsidian Wyrm Sanctum",
            subtitle = "Dungeon Floor -4 • Vanguard Camp PZ",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = "Deadly",
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(-4),
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

        // Subterranean Safe Forward Camp (PZ) on Southern Quarry Terrace (x=20..28, y=37..42)
        val camp = undergroundCampForFloor(-5)!!
        g[37][20] = TileType.CAMPFIRE_BRAZIER
        g[37][28] = TileType.CAMPFIRE_BRAZIER
        g[41][20] = TileType.WAR_BANNER
        g[41][28] = TileType.WAR_BANNER
        g[40][21] = TileType.COBBLESTONE
        g[40][27] = TileType.COBBLESTONE
        g[38][24] = TileType.BIOME_STALL_BADLANDS

        // Return Stairway Up to Floor 0 Grimmaw Crags Camp & Lower Boss Lair Descent to Floor -22
        g[41][24] = TileType.STAIRS_UP
        g[12][24] = TileType.STAIRS_DOWN

        return WorldFloor(
            floorId = -5,
            name = "The Grimmaw Quarry",
            subtitle = "Endgame Dungeon • Floor -5 • Forward Camp PZ",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = "Extreme",
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(-5),
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

        // Subterranean Safe Vanguard Camp (PZ) in Southern Gatehouse (x=20..28, y=37..42)
        val camp = undergroundCampForFloor(-6)!!
        g[38][21] = TileType.CAMPFIRE_BRAZIER
        g[38][27] = TileType.CAMPFIRE_BRAZIER
        g[41][21] = TileType.WAR_BANNER
        g[41][27] = TileType.WAR_BANNER
        g[40][21] = TileType.COBBLESTONE
        g[40][27] = TileType.COBBLESTONE
        g[38][24] = TileType.BIOME_STALL_ASHEN

        // Return Stairway Up to Floor 0 Gorek Badlands War-Camp & Lower Boss Lair Descent to Floor -23
        g[41][24] = TileType.STAIRS_UP
        g[11][24] = TileType.STAIRS_DOWN

        return WorldFloor(
            floorId = -6,
            name = "The Gorek Labyrinth",
            subtitle = "Pinnacle Maze • Floor -6 • Vanguard Camp PZ",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = "Pinnacle",
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(-6),
            dangerSeverity = 4
        )
    }
}
