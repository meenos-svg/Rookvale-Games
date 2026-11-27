package com.example.game.engine

import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemDefinition
import com.example.game.model.MonsterType
import kotlin.math.ceil

/**
 * Data and rules behind Rookvale's townsfolk: specialist shops, the Hunt Master's contracts,
 * Sage Ilsa's bestiary, the town crier's daily news and the dungeon guides.
 *
 * Everything here is deterministic. Rotations (wandering merchant stock, featured creature)
 * are keyed off real-world UTC time so every player sees the same thing at the same moment.
 */

// =========================================================================================
// Specialist shops
// =========================================================================================

data class VendorOffer(val itemId: String, val price: Int)

data class Vendor(
    val id: String,
    val npcName: String,
    val title: String,
    val greeting: String,
    /** Fixed stock. The wandering merchant's stock comes from [VendorCatalog.wanderingStock]. */
    val stock: List<VendorOffer>,
    /** Items this vendor pays extra for. */
    val prefers: (ItemDefinition) -> Boolean = { false },
    val preferredSellMultiplier: Double = 1.0
)

object VendorCatalog {
    const val FISHERMAN = "finn"
    const val BLACKSMITH = "borin"
    const val FLETCHER = "wren"
    const val WANDERER = "wanderer"

    /** Specialists pay 25% more than Alaric for goods in their trade. */
    const val SPECIALIST_SELL_BONUS = 1.25

    /** The wandering merchant's stock changes every 4 hours (UTC). */
    const val WANDERER_ROTATION_MS = 4L * 60L * 60L * 1000L
    private const val WANDERER_OFFERS = 4

    private val fishIds = setOf("fresh_fish", "silver_trout", "marsh_pike", "sunfin_snapper", "glacier_salmon", "emberfin_sturgeon")

    private fun offer(itemId: String, price: Int? = null) =
        VendorOffer(itemId, price ?: ItemCatalog.get(itemId).buyPrice)

    val vendors: Map<String, Vendor> = listOf(
        Vendor(
            id = FISHERMAN,
            npcName = "Old Finn",
            title = "Old Finn's Tackle",
            greeting = "Old Finn: 'Fine day for it! Rods, worms, and I pay well for a good catch.'",
            stock = listOf(
                offer("fishing_rod"),
                offer("garden_worm"), offer("dew_nightcrawler"), offer("mire_bloodworm"),
                offer("dune_sandworm"), offer("frost_glowworm"), offer("cinder_emberworm"),
                offer("cheese")
            ),
            prefers = { it.id in fishIds || it.category == ItemCategory.FISHING_BAIT },
            preferredSellMultiplier = SPECIALIST_SELL_BONUS
        ),
        Vendor(
            id = BLACKSMITH,
            npcName = "Borin",
            title = "Borin's Forge",
            greeting = "Borin: 'Need a better pick? Bring me ore and bars and I'll pay you fair, better than Alaric.'",
            stock = listOf(
                offer("bronze_pickaxe"), offer("quarry_mattock"),
                offer("sunbrass_pick", 220), offer("bog_iron_pickaxe", 260),
                offer("duskmetal_breaker", 420), offer("tempered_steel_pickaxe", 520),
                offer("hatchet"), offer("wooden_shield")
            ),
            prefers = { it.category == ItemCategory.RAW_ORE || it.category == ItemCategory.METAL_INGOT },
            preferredSellMultiplier = SPECIALIST_SELL_BONUS
        ),
        Vendor(
            id = FLETCHER,
            npcName = "Wren",
            title = "Wren's Fletchery",
            greeting = "Wren: 'Bows, arrows, and I'll buy every log and feather you can carry.'",
            stock = listOf(
                offer("wooden_arrows", 3), offer("ochre_shafts", 6),
                offer("hunting_spear", 70), offer("ashwood_shortbow", 180),
                offer("hunting_bow", 290), offer("crossbow", 380),
                offer("hatchet")
            ),
            prefers = {
                it.id == "wood_log" || it.id == "wild_feather" ||
                    it.category == ItemCategory.AMMO || it.category == ItemCategory.WEAPON_DISTANCE
            },
            preferredSellMultiplier = SPECIALIST_SELL_BONUS
        ),
        Vendor(
            id = WANDERER,
            npcName = "Zephyr the Wanderer",
            title = "Zephyr's Caravan",
            greeting = "Zephyr: 'Rare wares from far-off roads! My stock changes every few hours, so come back often.'",
            stock = emptyList()
        )
    ).associateBy { it.id }

    /** Rare goods the wandering merchant can bring (roughly double their sell value). */
    private val wandererPool: List<VendorOffer> = listOf(
        offer("brindle_recurve", 360), offer("bilewood_conduit", 340), offer("crossbow", 300),
        offer("venom_shafts", 12), offer("quenchsteel_quarrels", 14),
        offer("bog_iron_pickaxe", 200), offer("sunbrass_pick", 170), offer("duskmetal_breaker", 330),
        offer("silver_trout", 30), offer("marsh_pike", 55),
        offer("viking_helmet", 130), offer("brass_legs", 170), offer("life_amulet"),
        offer("health_potion", 36), offer("mana_potion", 40), offer("rope", 35),
        offer("spider_silk", 50), offer("runic_slate", 170)
    ).filter { ItemCatalog.all.containsKey(it.itemId) && it.price > 0 }

    fun vendor(id: String?): Vendor? = id?.let { vendors[it] }

    fun wanderingStock(wallClockMs: Long = System.currentTimeMillis()): List<VendorOffer> {
        if (wandererPool.isEmpty()) return emptyList()
        val rotation = (wallClockMs / WANDERER_ROTATION_MS)
        val picks = LinkedHashSet<Int>()
        var i = 0
        while (picks.size < WANDERER_OFFERS.coerceAtMost(wandererPool.size) && i < 64) {
            picks += positiveHash(rotation, i.toLong()) % wandererPool.size
            i++
        }
        return picks.map { wandererPool[it] }
    }

    fun minutesUntilNextRotation(wallClockMs: Long = System.currentTimeMillis()): Long =
        (WANDERER_ROTATION_MS - (wallClockMs % WANDERER_ROTATION_MS)) / 60_000L

    /** What the shop currently sells. A null id is Alaric's general store. */
    fun stockFor(vendorId: String?, wallClockMs: Long = System.currentTimeMillis()): List<VendorOffer> = when (vendorId) {
        null -> ItemCatalog.shopInventoryIds.map { offer(it) }
        WANDERER -> wanderingStock(wallClockMs)
        else -> vendors[vendorId]?.stock ?: emptyList()
    }

    fun buyPrice(vendorId: String?, itemId: String, wallClockMs: Long = System.currentTimeMillis()): Int =
        stockFor(vendorId, wallClockMs).firstOrNull { it.itemId == itemId }?.price ?: 0

    fun sellPrice(vendorId: String?, itemId: String): Int {
        val def = ItemCatalog.get(itemId)
        if (def.sellPrice <= 0) return 0
        val v = vendor(vendorId) ?: return def.sellPrice
        return if (v.prefers(def)) ceil(def.sellPrice * v.preferredSellMultiplier).toInt() else def.sellPrice
    }

    fun isPreferred(vendorId: String?, itemId: String): Boolean =
        vendor(vendorId)?.prefers?.invoke(ItemCatalog.get(itemId)) == true

    internal fun positiveHash(a: Long, b: Long): Int {
        var h = a * 6364136223846793005L + b * 1442695040888963407L + 0x9E3779B97F4A7C15uL.toLong()
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = h xor (h ushr 29)
        return (h and 0x7FFFFFFF).toInt()
    }
}

// =========================================================================================
// Hunt Master contracts
// =========================================================================================

data class HuntContract(
    val id: String,
    val title: String,
    val target: MonsterType,
    val count: Int,
    val minLevel: Int,
    val goldReward: Int,
    val xpReward: Long,
    val hint: String
)

object HuntContracts {
    private fun regular(id: String, title: String, t: MonsterType, count: Int, minLevel: Int, gold: Int, hint: String) =
        HuntContract(id, title, t, count, minLevel, gold, (count * t.xpReward * 0.8).toLong().coerceAtLeast(100L), hint)

    private fun boss(id: String, t: MonsterType, minLevel: Int, gold: Int, hint: String) =
        HuntContract(id, "Bounty: ${t.displayName}", t, 1, minLevel, gold, (t.xpReward * 1.0).toLong().coerceAtLeast(500L), hint)

    val all: List<HuntContract> = listOf(
        regular("rats", "Rat Catcher", MonsterType.CAVE_RAT, 15, 1, 60, "Cave rats scurry in the meadows around town."),
        regular("snakes", "Snake Season", MonsterType.YOUNG_SNAKE, 15, 1, 80, "Young snakes hide in the long grass outside the gates."),
        regular("wolves", "Thin the Wolf Pack", MonsterType.CRAG_WOLF, 20, 4, 150, "Crag wolves roam the wilds and the North Mountain Cavern."),
        regular("kobolds", "Kobold Sappers", MonsterType.TUNNEL_KOBOLD_SAPPER, 15, 5, 220, "Down the rope hole into the Copper Quarry Mines."),
        regular("goblins", "Goblin Raiders", MonsterType.GOBLIN_SCOUT, 15, 6, 250, "Goblin scouts prowl the outer Heartland."),
        regular("skeletons", "Bones of the Briar", MonsterType.SKELETON, 20, 6, 300, "Skeletons fill the Briar Catacombs."),
        regular("harpies", "Harpy Menace", MonsterType.CRAG_HARPY, 15, 8, 350, "Harpies nest up the east face of the North Mountain Cavern."),
        regular("trolls", "Troll Trouble", MonsterType.SWAMP_TROLL, 15, 9, 420, "Swamp trolls lurk in the wilds and the Dark Forest Sinkhole."),
        regular("dreadknights", "Dread Knights", MonsterType.WATCHWOOD_DREAD_KNIGHT, 12, 10, 520, "They guard the Watchwood Caverns."),
        regular("bandits", "Outlaw Bounty", MonsterType.OUTLAW_BANDIT, 20, 13, 700, "Outlaws hold the Crag Deep Hollow fortress."),
        regular("hatchlings", "Hatchling Hunt", MonsterType.DRAGON_HATCHLING, 12, 15, 900, "Dragon hatchlings roost in the Abyssal Crypt and below."),
        boss("b_arachne", MonsterType.BROODMOTHER_ARACHNE, 8, 600, "Beneath the Briar Catacombs' spider collapse."),
        boss("b_ironjaw", MonsterType.FOREMAN_IRONJAW, 8, 600, "Down the stairs in the Quarry Mines' Foreman's office."),
        boss("b_matriarch", MonsterType.SKREE_RIDGE_MATRIARCH, 10, 750, "Atop the North Mountain Cavern's summit stairs."),
        boss("b_gnarlroot", MonsterType.GNARLROOT_THE_BLIGHTED, 12, 900, "In the Heart-Root Abyss below the Dark Forest Sinkhole."),
        boss("b_ignis", MonsterType.IGNIS_LORD_OF_THE_PIT, 12, 900, "In the Molten Pit beneath the Watchwood Caverns."),
        boss("b_stonehelm", MonsterType.WARDEN_STONEHELM, 15, 1200, "Behind the Sealed Vault of Crag Deep Hollow."),
        boss("b_demonlord", MonsterType.DEMON_LORD, 16, 1600, "Deep in the Abyssal Crypt."),
        boss("b_elderdragon", MonsterType.ELDER_DRAGON, 18, 3000, "On the throne of the Obsidian Wyrm Sanctum.")
    )

    fun byId(id: String?): HuntContract? = all.firstOrNull { it.id == id }
    fun byIndex(index: Int): HuntContract? = all.getOrNull(index)
    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }
    fun availableFor(level: Int): List<HuntContract> = all.filter { level >= it.minLevel }
}

// =========================================================================================
// Sage Ilsa's bestiary
// =========================================================================================

object Bestiary {
    /** Kills needed to complete a regular creature's entry. Bosses need [BOSS_KILLS]. */
    const val REGULAR_KILLS = 50
    const val BOSS_KILLS = 3
    /** Lasting damage bonus against a creature once its entry is claimed. */
    const val DAMAGE_BONUS = 0.05

    fun killsNeeded(type: MonsterType): Int = if (type.isBoss) BOSS_KILLS else REGULAR_KILLS

    /** Gold Sage Ilsa pays when you complete an entry. */
    fun goldReward(type: MonsterType): Int = (type.xpReward * if (type.isBoss) 2 else 1).coerceIn(25, 5000)

    fun killKey(type: MonsterType) = "kill_${type.name}"
    fun claimedKey(type: MonsterType) = "bestiary_${type.name}"
}

// =========================================================================================
// Town crier: today's featured creature
// =========================================================================================

object TownCrier {
    const val DAY_MS = 24L * 60L * 60L * 1000L
    /** Kills of today's featured creature give this much extra XP. */
    const val FEATURED_XP_BONUS = 0.5

    private val featuredPool = listOf(
        MonsterType.CAVE_RAT, MonsterType.YOUNG_SNAKE, MonsterType.CRAG_WOLF, MonsterType.GOBLIN_SCOUT,
        MonsterType.SKELETON, MonsterType.POISON_SPIDER, MonsterType.SWAMP_TROLL, MonsterType.TUNNEL_KOBOLD_SAPPER,
        MonsterType.QUARRY_MOLE_BRUTE, MonsterType.CRAG_HARPY, MonsterType.SHALE_GOLEM, MonsterType.BRIAR_CRYPT_CRAWLER,
        MonsterType.CATACOMB_BONE_ARCHER, MonsterType.THORN_BRAMBLE_STALKER, MonsterType.SPORELING_LOBBER,
        MonsterType.WATCHWOOD_DREAD_KNIGHT, MonsterType.GHOUL, MonsterType.CINDER_IMP, MonsterType.OUTLAW_BANDIT,
        MonsterType.HOLLOW_CROSSBOWMAN, MonsterType.VAULT_SENTINEL, MonsterType.DRAGON_HATCHLING
    )

    fun featuredCreature(wallClockMs: Long = System.currentTimeMillis()): MonsterType {
        val day = wallClockMs / DAY_MS
        return featuredPool[VendorCatalog.positiveHash(day, 77L) % featuredPool.size]
    }

    fun hoursUntilTomorrow(wallClockMs: Long = System.currentTimeMillis()): Long =
        ((DAY_MS - (wallClockMs % DAY_MS)) + 3_599_999L) / 3_600_000L
}

// =========================================================================================
// Dungeon guides
// =========================================================================================

data class DungeonGuideInfo(val floorId: Int, val dungeonName: String, val levels: String, val bossName: String, val tip: String)

object DungeonGuides {
    val byGuideName: Map<String, DungeonGuideInfo> = mapOf(
        "Scout Bryn" to DungeonGuideInfo(-1, "Briar Catacombs", "Lv 5-9", MonsterType.BROODMOTHER_ARACHNE.displayName,
            "Mind the ossuary maze. The Broodmother waits past the spider collapse."),
        "Ranger Tamsin" to DungeonGuideInfo(-2, "Watchwood Caverns", "Lv 9-13", MonsterType.IGNIS_LORD_OF_THE_PIT.displayName,
            "This is the only way down to the Abyssal Crypt and the Wyrm Sanctum. Bring potions."),
        "Miner Dell" to DungeonGuideInfo(-11, "Copper Quarry Mines", "Lv 5-9", MonsterType.FOREMAN_IRONJAW.displayName,
            "You'll need a rope to climb back out. Plenty of copper and tin down there."),
        "Climber Asha" to DungeonGuideInfo(-12, "North Mountain Cavern", "Lv 7-11", MonsterType.SKREE_RIDGE_MATRIARCH.displayName,
            "Take the switchback ledges or brave the sky-bridge. The Matriarch roosts at the summit."),
        "Woodsman Garth" to DungeonGuideInfo(-13, "Dark Forest Sinkhole", "Lv 9-13", MonsterType.GNARLROOT_THE_BLIGHTED.displayName,
            "Bring a rope. Thorn stalkers hide in the root dens."),
        "Warden Kael" to DungeonGuideInfo(-14, "Crag Deep Hollow", "Lv 13-17", MonsterType.WARDEN_STONEHELM.displayName,
            "Outlaws hold the fortress. Crossbowmen cover the bailey, so keep moving.")
    )

    fun messageFor(guideName: String): String {
        val g = byGuideName[guideName] ?: return "$guideName: 'Tread carefully below.'"
        return "$guideName: 'Below lies the ${g.dungeonName} (${g.levels}). Its master, ${g.bossName}, waits on the deepest floor. ${g.tip}'"
    }
}
