package com.example.game.world

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
    val isSafeZone: Boolean = true
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
    val subtitle: String = ""
)

enum class WorldPinCategory {
    CITY,
    FERRY,
    DUNGEON
}

/**
 * Deterministic 1600x1200 (4x3 grid of 400x400 regions) overland geographic blueprint
 * modeled proportionally on Rookvale's painted parchment world map.
 *
 * Generates 32x32 chunks on demand with O(1) per-tile evaluation while reserving the
 * East-Central Heartland sector (x=1200..1599, y=400..799) for Rookvale City & Valley.
 */
object WorldBiomeBlueprint {

    val harborPorts: List<HarborPort> = listOf(
        HarborPort(
            id = "rookvale_harbor",
            name = "Rookvale Royal Harbor",
            regionId = "rookvale_valley",
            captainName = "Captain Vance",
            dockX = 1411,
            dockY = 631,
            arrivalX = 1412,
            arrivalY = 630,
            description = "East-Central Heartland capital pier & trade hub",
            recommendedLevel = "Lvl 1-8",
            dangerSeverity = 0
        ),
        HarborPort(
            id = "mirewatch_bay",
            name = "Mirewatch Bay Port",
            regionId = "alder_sea_plains",
            captainName = "Captain Helena",
            dockX = 618,
            dockY = 560,
            arrivalX = 615,
            arrivalY = 560,
            description = "Western peninsula anchorage beside Mirewatch Fortress",
            recommendedLevel = "Lvl 6-12",
            dangerSeverity = 1
        ),
        HarborPort(
            id = "duskport_quay",
            name = "Duskport Coastal Quay",
            regionId = "blightwood_reach",
            captainName = "Captain Thorne",
            dockX = 866,
            dockY = 250,
            arrivalX = 870,
            arrivalY = 250,
            description = "Northern strait gateway to Blightwood & Frostcrest",
            recommendedLevel = "Lvl 8-14",
            dangerSeverity = 2
        ),
        HarborPort(
            id = "sunspire_dock",
            name = "Sunspire Oasis Dock",
            regionId = "maelstrom_jungle_west",
            captainName = "Captain Tariq",
            dockX = 490,
            dockY = 865,
            arrivalX = 486,
            arrivalY = 865,
            description = "Sun-drenched desert harbor near Sunspire Oasis City",
            recommendedLevel = "Lvl 12-22",
            dangerSeverity = 3
        ),
        HarborPort(
            id = "sunleaf_pier",
            name = "Sunleaf Jungle Pier",
            regionId = "emerald_jungle_east",
            captainName = "Captain Orin",
            dockX = 959,
            dockY = 934,
            arrivalX = 960,
            arrivalY = 937,
            description = "Emerald canopy timber pier serving Sunleaf Village",
            recommendedLevel = "Lvl 10-16",
            dangerSeverity = 2
        ),
        HarborPort(
            id = "ember_landing",
            name = "Ember Isles Landing",
            regionId = "ember_isles",
            captainName = "Captain Kael",
            dockX = 1427,
            dockY = 952,
            arrivalX = 1430,
            arrivalY = 952,
            description = "Volcanic eastern archipelago harbor & lighthouse cove",
            recommendedLevel = "Lvl 18-26",
            dangerSeverity = 4
        )
    )

    val secondarySettlements: List<SettlementZone> = listOf(
        SettlementZone(
            id = "mirewatch_fortress",
            name = "Mirewatch Fortress",
            regionId = "alder_sea_plains",
            centerX = 545,
            centerY = 595,
            minX = 528,
            minY = 578,
            maxX = 562,
            maxY = 612
        ),
        SettlementZone(
            id = "duskport_town",
            name = "Duskport Quay",
            regionId = "blightwood_reach",
            centerX = 882,
            centerY = 250,
            minX = 866,
            minY = 236,
            maxX = 898,
            maxY = 264
        ),
        SettlementZone(
            id = "sunspire_oasis",
            name = "Sunspire Oasis City",
            regionId = "sunspire_desert",
            centerX = 375,
            centerY = 790,
            minX = 358,
            minY = 774,
            maxX = 392,
            maxY = 806
        ),
        SettlementZone(
            id = "sunleaf_village",
            name = "Sunleaf Village",
            regionId = "emerald_jungle_east",
            centerX = 960,
            centerY = 995,
            minX = 944,
            minY = 980,
            maxX = 976,
            maxY = 1010
        ),
        SettlementZone(
            id = "ember_outpost",
            name = "Ember Watch Outpost",
            regionId = "ember_isles",
            centerX = 1458,
            centerY = 952,
            minX = 1444,
            minY = 940,
            maxX = 1472,
            maxY = 964
        )
    )

    /**
     * Deterministic outer-overworld mining nodes across the 11 non-Rookvale regions.
     * Strictly low-to-mid tier (Copper, Tin, Bog Iron) — Vesperite & Cinderite remain dungeon-exclusive!
     */
    val outerOverworldOres: List<OverworldOrePlacement> = listOf(
        // Ashen Caldera Peaks (NW)
        OverworldOrePlacement(278, 224, OreVeinKind.COPPER),
        OverworldOrePlacement(286, 232, OreVeinKind.TIN),
        OverworldOrePlacement(355, 240, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(364, 248, OreVeinKind.BOG_IRON),
        // Writhing Marshes (W)
        OverworldOrePlacement(255, 495, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(268, 522, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(332, 540, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(220, 560, OreVeinKind.TIN),
        // Mirewatch Peninsula
        OverworldOrePlacement(498, 558, OreVeinKind.COPPER),
        OverworldOrePlacement(510, 548, OreVeinKind.TIN),
        // Blightwood & Duskport
        OverworldOrePlacement(940, 215, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(965, 232, OreVeinKind.TIN),
        OverworldOrePlacement(1062, 220, OreVeinKind.BOG_IRON),
        // Frostcrest Highlands (NE)
        OverworldOrePlacement(1278, 238, OreVeinKind.COPPER),
        OverworldOrePlacement(1292, 248, OreVeinKind.TIN),
        OverworldOrePlacement(1352, 228, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(1442, 236, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(1460, 248, OreVeinKind.TIN),
        // Spirewood West
        OverworldOrePlacement(1025, 538, OreVeinKind.COPPER),
        OverworldOrePlacement(1042, 548, OreVeinKind.TIN),
        OverworldOrePlacement(1118, 635, OreVeinKind.BOG_IRON),
        // Sunspire Desert & Badlands (SW)
        OverworldOrePlacement(315, 765, OreVeinKind.COPPER),
        OverworldOrePlacement(424, 822, OreVeinKind.TIN),
        OverworldOrePlacement(284, 1005, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(215, 1034, OreVeinKind.BOG_IRON),
        // Emerald Jungle (South)
        OverworldOrePlacement(680, 995, OreVeinKind.COPPER),
        OverworldOrePlacement(748, 1012, OreVeinKind.TIN),
        OverworldOrePlacement(1018, 1022, OreVeinKind.BOG_IRON),
        // Ember Isles (SE)
        OverworldOrePlacement(1485, 932, OreVeinKind.BOG_IRON),
        OverworldOrePlacement(1518, 948, OreVeinKind.BOG_IRON)
    )

    private val outerOreLookup: Map<Long, TileType> = outerOverworldOres.associate { ore ->
        val tile = when (ore.kind) {
            OreVeinKind.COPPER -> TileType.ORE_COPPER
            OreVeinKind.TIN -> TileType.ORE_TIN
            OreVeinKind.BOG_IRON -> TileType.ORE_BOG_IRON
            OreVeinKind.VESPERITE -> TileType.ORE_VESPERITE
            OreVeinKind.CINDERITE -> TileType.ORE_CINDERITE
        }
        ((ore.y.toLong() shl 32) or (ore.x.toLong() and 0xFFFFFFFFL)) to tile
    }

    /**
     * World Map pins calibrated to the 1600x1200 parchment artwork.
     */
    val worldMapPins: List<WorldMapPin> = listOf(
        // Major Cities & Settlements
        WorldMapPin("city_rookvale", "Rookvale City", 1396, 592, WorldPinCategory.CITY, "Capital Sanctuary • Lvl 1-8"),
        WorldMapPin("city_mirewatch", "Mirewatch Fortress", 545, 595, WorldPinCategory.CITY, "Peninsula Keep • Lvl 6-12"),
        WorldMapPin("city_duskport", "Duskport Quay", 882, 250, WorldPinCategory.CITY, "Northern Harbor • Lvl 8-14"),
        WorldMapPin("city_sunspire", "Sunspire Oasis", 375, 790, WorldPinCategory.CITY, "Desert Oasis • Lvl 12-22"),
        WorldMapPin("city_sunleaf", "Sunleaf Village", 960, 995, WorldPinCategory.CITY, "Emerald Jungle • Lvl 10-16"),
        WorldMapPin("city_ember", "Ember Watch", 1458, 952, WorldPinCategory.CITY, "Volcanic Isles • Lvl 18-26"),

        // Coastal Ferry Ports
        WorldMapPin("ferry_rookvale", "Rookvale Harbor", 1411, 631, WorldPinCategory.FERRY, "Captain Vance"),
        WorldMapPin("ferry_mirewatch", "Mirewatch Port", 618, 560, WorldPinCategory.FERRY, "Captain Helena"),
        WorldMapPin("ferry_duskport", "Duskport Pier", 866, 250, WorldPinCategory.FERRY, "Captain Thorne"),
        WorldMapPin("ferry_sunspire", "Sunspire Dock", 490, 865, WorldPinCategory.FERRY, "Captain Tariq"),
        WorldMapPin("ferry_sunleaf", "Sunleaf Pier", 959, 934, WorldPinCategory.FERRY, "Captain Orin"),
        WorldMapPin("ferry_ember", "Ember Landing", 1427, 952, WorldPinCategory.FERRY, "Captain Kael"),

        // Major Dungeon Entrances
        WorldMapPin("dng_sewers", "Rookvale Sewers (-1)", 1442, 579, WorldPinCategory.DUNGEON, "Floor -1 • Lvl 3-6"),
        WorldMapPin("dng_grimmaw_valley", "Grimmaw Quarry (-5)", 1284, 716, WorldPinCategory.DUNGEON, "Floor -5 • Vesperite"),
        WorldMapPin("dng_gorek_valley", "Gorek Labyrinth (-6)", 1516, 716, WorldPinCategory.DUNGEON, "Floor -6 • Cinderite"),
        WorldMapPin("dng_caldera", "Ashen Caldera Shaft (-2)", 318, 252, WorldPinCategory.DUNGEON, "Floor -2 • Molten Chasm"),
        WorldMapPin("dng_marsh", "Writhing Sinkhole (-1)", 295, 515, WorldPinCategory.DUNGEON, "Floor -1 • Bog Crypt"),
        WorldMapPin("dng_blightwood", "Blightwood Barrow (-1)", 1020, 175, WorldPinCategory.DUNGEON, "Floor -1 • Dark Barrow"),
        WorldMapPin("dng_frostcrest", "Frostcrest Quarry (-5)", 1320, 260, WorldPinCategory.DUNGEON, "Floor -5 • Grimmaw Peak"),
        WorldMapPin("dng_badlands", "Badlands Labyrinth (-6)", 242, 1020, WorldPinCategory.DUNGEON, "Floor -6 • Gorek Gate"),
        WorldMapPin("dng_jungle", "Jungle Ruin Vault (-2)", 720, 1020, WorldPinCategory.DUNGEON, "Floor -2 • Deep Caverns"),
        WorldMapPin("dng_ember", "Ember Sanctum (-2)", 1510, 975, WorldPinCategory.DUNGEON, "Floor -2 • Volcanic Pit")
    )

    // --- Road Network Segments connecting every city, bridge, harbor, and dungeon entrance ---
    private data class RoadSeg(
        val x0: Int,
        val y0: Int,
        val x1: Int,
        val y1: Int,
        val halfWidth: Int,
        val tile: TileType
    )

    private val roadSegments: List<RoadSeg> = listOf(
        // East-Central Heartland Connections (into Rookvale City borders at 1200,592 / 1396,400 / 1396,799)
        RoadSeg(942, 592, 1205, 592, 2, TileType.COBBLESTONE), // Spirewood Royal Highway -> Rookvale West
        RoadSeg(1396, 150, 1396, 405, 2, TileType.COBBLESTONE), // Frostcrest North Highway -> Rookvale North
        RoadSeg(1396, 260, 1320, 260, 2, TileType.DIRT_FLOOR), // Frostcrest Grimmaw Ridge spur
        RoadSeg(1396, 795, 1396, 952, 2, TileType.DIRT_FLOOR), // South Rookvale -> Ember Causeway
        RoadSeg(1396, 952, 1465, 952, 2, TileType.COBBLESTONE), // Ember Causeway -> Ember Watch
        RoadSeg(1465, 952, 1510, 975, 1, TileType.DIRT_FLOOR), // Ember Watch -> Ember Sanctum

        // Eastern Continent North-South Spine (Duskport <-> Blightwood <-> Spirewood <-> Sunleaf)
        RoadSeg(882, 250, 1020, 250, 2, TileType.COBBLESTONE),
        RoadSeg(1020, 175, 1020, 250, 2, TileType.DIRT_FLOOR), // Blightwood Barrow spur
        RoadSeg(1020, 250, 1042, 592, 2, TileType.COBBLESTONE), // Blightwood -> Spirewood Crossroads
        RoadSeg(1042, 592, 1042, 760, 2, TileType.COBBLESTONE), // Spirewood -> South Bridgehead
        RoadSeg(1042, 985, 960, 995, 2, TileType.COBBLESTONE), // Bridgehead -> Sunleaf Village
        RoadSeg(960, 995, 960, 936, 2, TileType.WOOD_PIER), // Sunleaf Village -> Sunleaf Jungle Pier
        RoadSeg(960, 995, 720, 1020, 2, TileType.DIRT_FLOOR), // Sunleaf -> West Jungle Ruins
        RoadSeg(720, 1020, 645, 980, 2, TileType.DIRT_FLOOR), // West Jungle -> SW Bridge
        RoadSeg(760, 945, 760, 795, 1, TileType.STONE_BRIDGE), // Jungle -> Maelstrom Shrine Causeway

        // Western Continent Spine (Ashen Caldera <-> Writhing Marshes <-> Mirewatch <-> Sunspire <-> Badlands)
        RoadSeg(318, 252, 445, 212, 2, TileType.DIRT_FLOOR), // Ashen Caldera -> Frostbay Bridge West
        RoadSeg(318, 252, 295, 515, 2, TileType.DIRT_FLOOR), // Caldera -> Writhing Marshes Sinkhole
        RoadSeg(295, 515, 375, 592, 2, TileType.DIRT_FLOOR), // Writhing Marshes -> Mirewatch West Road
        RoadSeg(375, 592, 545, 592, 2, TileType.COBBLESTONE), // Mirewatch Main Road
        RoadSeg(545, 592, 600, 592, 2, TileType.COBBLESTONE), // Mirewatch -> Central Alder Causeway
        RoadSeg(545, 592, 616, 560, 2, TileType.COBBLESTONE), // Mirewatch -> Mirewatch Bay Port
        RoadSeg(375, 592, 375, 790, 2, TileType.DIRT_FLOOR), // Western Road -> Sunspire Oasis City
        RoadSeg(375, 790, 486, 865, 2, TileType.DIRT_FLOOR), // Sunspire Oasis -> Sunspire Dock
        RoadSeg(375, 790, 455, 980, 2, TileType.DIRT_FLOOR), // Sunspire Oasis -> SW Jungle Bridge
        RoadSeg(375, 790, 242, 1020, 2, TileType.DIRT_FLOOR) // Sunspire Oasis -> Badlands Gorek Gate
    )

    // --- 5 Inter-Continental Walkable Stone Sea-Bridges ---
    private data class BridgeRect(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    private val seaBridges: List<BridgeRect> = listOf(
        BridgeRect(440, 210, 868, 213),   // 1. Frostbay Sea-Bridge (NW <-> Duskport)
        BridgeRect(595, 590, 946, 593),   // 2. Central Alder Causeway (Mirewatch <-> Spirewood/Rookvale)
        BridgeRect(450, 978, 652, 981),   // 3. Sunspire–Jungle Strait Bridge (Desert <-> West Jungle)
        BridgeRect(1040, 755, 1043, 992), // 4. Spirewood–Sunleaf Causeway (East Continent <-> Jungle)
        BridgeRect(1394, 950, 1446, 953)  // 5. Rookvale–Ember Isles Causeway (SE Mainland <-> Ember Isles)
    )

    // --- Regional Dungeon Entrances outside Rookvale Valley ---
    private val outerPortals: Map<Long, TileType> = mapOf(
        packCoord(318, 252) to TileType.STAIRS_DOWN,   // Ashen Caldera Shaft -> Floor -2
        packCoord(295, 515) to TileType.ROPE_HOLE_DOWN,// Writhing Sinkhole -> Floor -1
        packCoord(1020, 175) to TileType.STAIRS_DOWN,  // Blightwood Barrow -> Floor -1
        packCoord(1320, 260) to TileType.STAIRS_DOWN,  // Frostcrest Grimmaw Ridge -> Floor -5
        packCoord(242, 1020) to TileType.STAIRS_DOWN,  // Badlands Gorek Gate -> Floor -6
        packCoord(720, 1020) to TileType.STAIRS_DOWN,  // Jungle Ruin Vault -> Floor -2
        packCoord(1510, 975) to TileType.STAIRS_DOWN   // Ember Sanctum -> Floor -2
    )

    // --- Coarse Regional Waypoint Graph for Instant Cross-World Hierarchical Auto-Walk ---
    private data class WaypointNode(val id: String, val x: Int, val y: Int)

    private val waypointNodes: List<WaypointNode> = listOf(
        WaypointNode("rookvale_center", 1396, 592),
        WaypointNode("rookvale_west_gate", 1200, 592),
        WaypointNode("rookvale_north_gate", 1396, 400),
        WaypointNode("rookvale_south_gate", 1396, 799),
        WaypointNode("frostcrest_hub", 1396, 260),
        WaypointNode("frostcrest_grimmaw", 1320, 260),
        WaypointNode("spirewood_cross", 1042, 592),
        WaypointNode("alder_bridge_east", 942, 592),
        WaypointNode("alder_bridge_west", 595, 592),
        WaypointNode("mirewatch", 545, 592),
        WaypointNode("mirewatch_west", 375, 592),
        WaypointNode("writhing_marsh", 295, 515),
        WaypointNode("ashen_caldera", 318, 252),
        WaypointNode("frostbay_west", 442, 212),
        WaypointNode("frostbay_east", 866, 212),
        WaypointNode("duskport", 882, 250),
        WaypointNode("blightwood_jct", 1020, 250),
        WaypointNode("sunspire_oasis", 375, 790),
        WaypointNode("sunspire_badlands", 242, 1020),
        WaypointNode("sw_bridge_west", 452, 980),
        WaypointNode("sw_bridge_east", 650, 980),
        WaypointNode("west_jungle", 720, 1020),
        WaypointNode("sunleaf_village", 960, 995),
        WaypointNode("sunleaf_bridge_south", 1042, 988),
        WaypointNode("sunleaf_bridge_north", 1042, 756),
        WaypointNode("ember_causeway_west", 1396, 952),
        WaypointNode("ember_watch", 1458, 952)
    )

    private val waypointEdges: Map<String, List<String>> = buildMap {
        fun link(a: String, b: String) {
            put(a, (get(a) ?: emptyList()) + b)
            put(b, (get(b) ?: emptyList()) + a)
        }
        link("rookvale_center", "rookvale_west_gate")
        link("rookvale_center", "rookvale_north_gate")
        link("rookvale_center", "rookvale_south_gate")
        link("rookvale_north_gate", "frostcrest_hub")
        link("frostcrest_hub", "frostcrest_grimmaw")
        link("rookvale_west_gate", "spirewood_cross")
        link("spirewood_cross", "alder_bridge_east")
        link("alder_bridge_east", "alder_bridge_west")
        link("alder_bridge_west", "mirewatch")
        link("mirewatch", "mirewatch_west")
        link("mirewatch_west", "writhing_marsh")
        link("writhing_marsh", "ashen_caldera")
        link("ashen_caldera", "frostbay_west")
        link("frostbay_west", "frostbay_east")
        link("frostbay_east", "duskport")
        link("duskport", "blightwood_jct")
        link("blightwood_jct", "spirewood_cross")
        link("mirewatch_west", "sunspire_oasis")
        link("sunspire_oasis", "sunspire_badlands")
        link("sunspire_oasis", "sw_bridge_west")
        link("sw_bridge_west", "sw_bridge_east")
        link("sw_bridge_east", "west_jungle")
        link("west_jungle", "sunleaf_village")
        link("sunleaf_village", "sunleaf_bridge_south")
        link("sunleaf_bridge_south", "sunleaf_bridge_north")
        link("sunleaf_bridge_north", "spirewood_cross")
        link("rookvale_south_gate", "ember_causeway_west")
        link("ember_causeway_west", "ember_watch")
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

        // Subdivide any long straight bridge/highway legs (> 110 tiles) so each local A* hop stays fast
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
     * Matches the continents, seas, biomes, harbors, bridges, and settlements of 4308.jpg.
     */
    fun tileAtGlobal(x: Int, y: Int): TileType {
        // Outer ocean frame
        if (x < 6 || x >= WORLD_OVERLAND_WIDTH - 6 || y < 6 || y >= WORLD_OVERLAND_HEIGHT - 6) {
            return TileType.DEEP_WATER
        }

        // 1. Explicit dungeon portals outside Rookvale Valley
        outerPortals[packCoord(x, y)]?.let { return it }

        // 2. Explicit outer-overworld ore veins (Copper, Tin, Bog Iron only)
        outerOreLookup[packCoord(x, y)]?.let { return it }

        // 3. Harbor Piers & Moored Boats for outer coastal ports
        resolveOuterHarborTile(x, y)?.let { return it }

        // 4. Secondary Settlement Plazas, Walls & Mini-Depots
        resolveSettlementTile(x, y)?.let { return it }

        // 5. Walkable Stone Sea-Bridges (highest priority over water!)
        for (b in seaBridges) {
            if (x in b.x1..b.x2 && y in b.y1..b.y2) {
                return TileType.STONE_BRIDGE
            }
        }

        // 6. Inter-regional Highways & Trails
        resolveRoadTile(x, y)?.let { return it }

        // 7. Landmass & Biome Elevation Field matching 4308.jpg
        val landVal = landmassField(x, y)
        if (landVal < 0.0f) {
            return if (landVal > -0.14f) TileType.WATER else TileType.DEEP_WATER
        }

        return resolveBiomeTile(x, y, landVal)
    }

    private fun resolveOuterHarborTile(x: Int, y: Int): TileType? {
        // Mirewatch Bay Port (east pier jutting into Central Sea)
        if (x in 612..624 && y in 558..562) return TileType.WOOD_PIER
        if ((x == 625 && y == 559) || (x == 625 && y == 561)) return TileType.MOORED_BOAT

        // Duskport Coastal Quay (west pier jutting into Frostbay Straits)
        if (x in 858..870 && y in 248..252) return TileType.WOOD_PIER
        if ((x == 857 && y == 249) || (x == 857 && y == 251)) return TileType.MOORED_BOAT

        // Sunspire Oasis Dock (SE desert bay pier)
        if (x in 484..496 && y in 863..867) return TileType.WOOD_PIER
        if ((x == 497 && y == 864) || (x == 497 && y == 866)) return TileType.MOORED_BOAT

        // Sunleaf Jungle Pier (north pier into Central Sea)
        if (x in 957..963 && y in 928..942) return TileType.WOOD_PIER
        if ((x == 956 && y == 930) || (x == 964 && y == 930)) return TileType.MOORED_BOAT

        // Ember Isles Landing (west cove pier — boats moored along north flank so causeway stays clear)
        if (x in 1422..1434 && y in 950..954) return TileType.WOOD_PIER
        if ((x == 1425 && y == 949) || (x == 1428 && y == 949)) return TileType.MOORED_BOAT

        // Maelstrom Sea-Dragon Shrine Isle plaza
        if (x in 752..768 && y in 784..796) {
            if (x == 760 && y == 790) return TileType.CRYSTAL_PILLAR
            if ((x == 754 || x == 766) && (y == 786 || y == 794)) return TileType.LANTERN_POST
            return TileType.ARCANE_FLOOR
        }
        return null
    }

    private fun resolveSettlementTile(x: Int, y: Int): TileType? {
        for (s in secondarySettlements) {
            if (!s.contains(x, y)) continue
            val onBorderX = x == s.minX || x == s.maxX
            val onBorderY = y == s.minY || y == s.maxY
            val nearCenterX = abs(x - s.centerX) <= 3
            val nearCenterY = abs(y - s.centerY) <= 3

            // Leave wide open gates on all 4 cardinal sides
            if ((onBorderX && !nearCenterY) || (onBorderY && !nearCenterX)) {
                return if (s.id == "sunleaf_village") TileType.WOOD_FENCE else TileType.CITY_WALL
            }
            // Corner lanterns inside settlement plaza
            if ((x == s.minX + 3 || x == s.maxX - 3) && (y == s.minY + 3 || y == s.maxY - 3)) {
                return TileType.LANTERN_POST
            }
            // Central Fountain & Mini-Depot chest
            if (x == s.centerX && y == s.centerY) return TileType.FOUNTAIN
            if (x == s.centerX - 5 && y == s.centerY - 4) return TileType.MINI_DEPOT
            if (x == s.centerX + 5 && y == s.centerY - 4) return TileType.SMELTING_FURNACE
            if (x == s.centerX + 6 && y == s.centerY - 4) return TileType.BLACKSMITH_ANVIL

            return when (s.id) {
                "sunleaf_village" -> TileType.WOOD_PLANK_FLOOR
                "sunspire_oasis" -> if (abs(x - s.centerX) <= 6 && abs(y - s.centerY) <= 6) TileType.TEMPLE_FLOOR else TileType.COBBLESTONE
                else -> TileType.COBBLESTONE
            }
        }
        return null
    }

    private fun resolveRoadTile(x: Int, y: Int): TileType? {
        for (seg in roadSegments) {
            val minX = minOf(seg.x0, seg.x1) - seg.halfWidth
            val maxX = maxOf(seg.x0, seg.x1) + seg.halfWidth
            val minY = minOf(seg.y0, seg.y1) - seg.halfWidth
            val maxY = maxOf(seg.y0, seg.y1) + seg.halfWidth
            if (x !in minX..maxX || y !in minY..maxY) continue

            val dx = (seg.x1 - seg.x0).toFloat()
            val dy = (seg.y1 - seg.y0).toFloat()
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq < 0.001f) 0f else (((x - seg.x0) * dx + (y - seg.y0) * dy) / lenSq).coerceIn(0f, 1f)
            val px = seg.x0 + dx * t
            val py = seg.y0 + dy * t
            val dist = hypot((x - px).toDouble(), (y - py).toDouble())
            if (dist <= seg.halfWidth + 0.55) {
                return seg.tile
            }
        }
        return null
    }

    /**
     * Smooth organic continent distance field matching 4308.jpg:
     * Positive = land, Negative = sea (`-0.14..0` coastal shallows, `< -0.14` deep ocean).
     */
    private fun landmassField(x: Int, y: Int): Float {
        val fx = x.toFloat()
        val fy = y.toFloat()
        // Organic coastal shoreline wobble
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
        addBlob(1365f, 200f, 235f, 185f)   // Frostcrest Highlands & Alpine Peaks
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

        // Natural 7x7 glade clearings around outer-continent monster camps so trees/rocks never spawn on monsters
        val nearMonsterClearing = outerMonsterClearings.any { (sx, sy) ->
            abs(x - sx) <= 3 && abs(y - sy) <= 3
        }

        // Keep immediate dungeon entrance, settlement apron & monster clearings walkable
        val nearPortal = nearMonsterClearing || outerPortals.keys.any { key ->
            val px = (key and 0xFFFFFFFFL).toInt()
            val py = (key ushr 32).toInt()
            abs(x - px) <= 6 && abs(y - py) <= 6
        }

        // 1. Far Northern Arctic Snow & Southern Alpine Rim
        if (y < 95 && landVal > 0.08f) {
            if (!nearPortal && y < 48 && h < 110) return TileType.MOUNTAIN_ROCK
            if (!nearPortal && h < 22) return TileType.TREE
            return TileType.SNOW
        }
        if (y > 1115 && landVal > 0.1f) {
            if (!nearPortal && h < 120) return TileType.MOUNTAIN_ROCK
            return TileType.SNOW
        }

        // 2. NW Abyssal Isle & Ashen Caldera Volcano (x < 460, y < 355)
        if (x < 470 && y < 355) {
            val craterDist = hypot((x - 328).toDouble(), (y - 185).toDouble())
            if (craterDist < 22.0) return TileType.LAVA
            if (!nearPortal && craterDist < 42.0 && h < 115) return TileType.MOUNTAIN_ROCK
            if (!nearPortal && h < 34 && landVal > 0.2f) return TileType.MOUNTAIN_ROCK
            return if (detail < 205) TileType.VOLCANIC_ASH else TileType.DIRT_FLOOR
        }

        // 3. The Writhing Marshes (x < 445, y in 355..670)
        if (x < 445 && y in 355..670) {
            if (!nearPortal && h < 16 && landVal > 0.22f) return TileType.WATER
            if (!nearPortal && h in 16..46) return TileType.TREE
            return if (detail < 185) TileType.MARSH else TileType.DARK_GRASS
        }

        // 4. SW Volcanic Badlands (x < 395, y >= 940)
        if (x < 395 && y >= 940) {
            if (!nearPortal && h < 12 && landVal > 0.25f) return TileType.LAVA
            if (!nearPortal && h in 12..42 && landVal > 0.18f) return TileType.MOUNTAIN_ROCK
            return if (detail < 195) TileType.VOLCANIC_ASH else TileType.DIRT_FLOOR
        }

        // 5. Sunspire Golden Desert (x < 535, y in 670..995)
        if (x < 535 && y in 670..995) {
            if (!nearPortal && h < 18 && landVal > 0.25f) return TileType.MOUNTAIN_ROCK
            return if (detail < 232) TileType.SAND else TileType.DIRT_FLOOR
        }

        // 6. Blightwood Corrupted Forest (x in 925..1145, y < 290)
        if (x in 925..1145 && y < 290) {
            if (!nearPortal && h < 62) return TileType.TREE
            return if (detail < 210) TileType.BLIGHT_SOIL else TileType.DARK_GRASS
        }

        // 7. Frostcrest Snowy Highlands (x >= 1145, y < 395)
        if (x >= 1145 && y < 395) {
            val snowLine = 265 + (sin(x * 0.025) * 28.0).toInt()
            if (y < snowLine) {
                if (!nearPortal && h < 38 && landVal > 0.22f) return TileType.MOUNTAIN_ROCK
                if (!nearPortal && h in 38..74) return TileType.TREE
                return TileType.SNOW
            }
            if (!nearPortal && h < 48) return TileType.TREE
            return if (detail < 140) TileType.DARK_GRASS else TileType.GRASS
        }

        // 8. Southern Emerald Jungle Continent (x in 540..1240, y >= 870)
        if (x in 540..1240 && y >= 870) {
            if (!nearPortal && h < 54 && landVal > 0.14f) return TileType.TREE
            return when {
                detail < 195 -> TileType.JUNGLE_FLOOR
                detail < 225 -> TileType.DARK_GRASS
                else -> TileType.FLOWER_GRASS
            }
        }

        // 9. SE Ember Isles Archipelago (x >= 1340, y >= 840)
        if (x >= 1340 && y >= 840) {
            val peakDist = hypot((x - 1518).toDouble(), (y - 952).toDouble())
            if (peakDist < 12.0) return TileType.LAVA
            if (!nearPortal && h < 34 && landVal > 0.2f) return TileType.MOUNTAIN_ROCK
            return if (detail < 185) TileType.VOLCANIC_ASH else TileType.DARK_GRASS
        }

        // 10. Temperate Coastal Plains & Spirewood / Mirewatch Heartland
        if (landVal < 0.06f) return TileType.SAND // Sandy coastal beaches
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
