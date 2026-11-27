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
    const val MIRE_OUTFITTER = "odra"
    const val SUN_CARAVANEER = "tahir"
    const val FROST_FURRIER = "ulfa"
    const val ASH_SLAGWRIGHT = "sorrel"

    private val marshGoods = setOf("bile_gland", "spider_silk", "mire_silver_ore", "peat_coal_ore", "bog_iron_ore", "ghoul_gem", "jagged_ivory", "marsh_pike")
    private val frostGoods = setOf("frost_iron_ore", "glacier_quartz_ore", "grimmaw_stone_core", "colossus_tendon", "ancient_bone", "runic_slate", "glacier_salmon")
    private val ashGoods = setOf("cinderite_ore", "cinderite_bar", "molten_core", "ember_heart", "runic_slate", "wyrmscale_plate", "gorek_obsidian_horn", "emberfin_sturgeon")
    private val badlandsGoods = setOf("vesperite_ore", "gorek_obsidian_horn", "colossus_tendon", "ancient_bone", "runic_slate", "ember_heart", "sunfin_snapper")

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
            id = MIRE_OUTFITTER,
            npcName = "Odra",
            title = "Odra's Bog Outfitters",
            greeting = "Odra: 'Bloodworms, venom shafts, a good steel pick. And I pay best in Mirewatch for bog goods.'",
            stock = listOf(
                offer("fishing_rod"), offer("mire_bloodworm"),
                offer("health_potion"), offer("mana_potion"), offer("rope"),
                offer("venom_shafts", 12), offer("tempered_steel_pickaxe", 520)
            ),
            prefers = { it.id in marshGoods },
            preferredSellMultiplier = SPECIALIST_SELL_BONUS
        ),
        Vendor(
            id = SUN_CARAVANEER,
            npcName = "Tahir",
            title = "Tahir's Caravanserai",
            greeting = "Tahir: 'Water, worms and a vesperite pick for the deep veins. Horns, tendons and ore fetch a fine price here.'",
            stock = listOf(
                offer("fishing_rod"), offer("dune_sandworm"),
                offer("health_potion"), offer("mana_potion"), offer("rope"),
                offer("quenchsteel_quarrels", 14), offer("vesperite_tipped_pickaxe", 980)
            ),
            prefers = { it.id in badlandsGoods },
            preferredSellMultiplier = SPECIALIST_SELL_BONUS
        ),
        Vendor(
            id = FROST_FURRIER,
            npcName = "Ulfa",
            title = "Ulfa's Furs & Picks",
            greeting = "Ulfa: 'Glowworms for the ice holes, a glacier pick for the deep veins. I pay best for frost ore and quarry cores.'",
            stock = listOf(
                offer("fishing_rod"), offer("frost_glowworm"),
                offer("health_potion"), offer("mana_potion"), offer("rope"),
                offer("starlight_quarrels", 18), offer("glacier_forged_pickaxe", 1800)
            ),
            prefers = { it.id in frostGoods },
            preferredSellMultiplier = SPECIALIST_SELL_BONUS
        ),
        Vendor(
            id = ASH_SLAGWRIGHT,
            npcName = "Sorrel",
            title = "Sorrel's Slag & Picks",
            greeting = "Sorrel: 'Emberworms for the quench pool, a cinderite pick for the hot veins. I pay best for cinderite, molten cores and wyrmscale.'",
            stock = listOf(
                offer("fishing_rod"), offer("cinder_emberworm"),
                offer("health_potion"), offer("mana_potion"), offer("rope"),
                offer("starlight_quarrels", 18), offer("cinderite_core_pickaxe", 2600)
            ),
            prefers = { it.id in ashGoods },
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
        offer("viking_helmet", 130), offer("brass_legs", 170), offer("brass_boots", 140), offer("life_amulet"),
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
        if (vendorId == null) ItemCatalog.get(itemId).buyPrice  // Alaric: catalog price, as before
        else stockFor(vendorId, wallClockMs).firstOrNull { it.itemId == itemId }?.price ?: 0

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
    val hint: String,
    val board: String = HuntBoards.ROOKVALE
)

/** A town's hunt board and the hunt master who runs it. */
data class HuntBoard(val id: String, val masterName: String, val title: String, val where: String)

object HuntBoards {
    const val ROOKVALE = "rookvale"
    const val MIREWATCH = "mirewatch"
    const val SUNSPIRE = "sunspire"
    const val FROSTPEAK = "frostpeak"
    const val ASHEN = "ashen"

    val all: List<HuntBoard> = listOf(
        HuntBoard(ROOKVALE, "Captain Hale", "Captain Hale's Hunt Board", "Rookvale's plaza"),
        HuntBoard(MIREWATCH, "Warden Brisk", "Warden Brisk's Marsh Bounties", "Mirewatch's moot platform"),
        HuntBoard(SUNSPIRE, "Sergeant Kasim", "Sergeant Kasim's Desert Bounties", "Sunspire's Sun Plaza"),
        HuntBoard(FROSTPEAK, "Huntmaster Sigrun", "Huntmaster Sigrun's Frost Bounties", "Frostpeak's Hearthstone Plaza"),
        HuntBoard(ASHEN, "Warmaster Dravek", "Warmaster Dravek's Ember Bounties", "Ashen Bastion's Crucible Plaza")
    )

    fun byId(id: String?): HuntBoard = all.firstOrNull { it.id == id } ?: all.first()
    fun byMaster(name: String): HuntBoard = all.firstOrNull { it.masterName == name } ?: all.first()

    /** The hunt board panel keeps which board is open in `activeShopId` as `hunt:<board>`. */
    fun panelId(board: String) = "hunt:$board"
    fun fromPanelId(panelId: String?): HuntBoard = byId(panelId?.removePrefix("hunt:"))
}

object HuntContracts {
    private fun regular(id: String, title: String, t: MonsterType, count: Int, minLevel: Int, gold: Int, hint: String, board: String = HuntBoards.ROOKVALE) =
        HuntContract(id, title, t, count, minLevel, gold, (count * t.xpReward * 0.8).toLong().coerceAtLeast(100L), hint, board)

    private fun boss(id: String, t: MonsterType, minLevel: Int, gold: Int, hint: String, board: String = HuntBoards.ROOKVALE) =
        HuntContract(id, "Bounty: ${t.displayName}", t, 1, minLevel, gold, (t.xpReward * 1.0).toLong().coerceAtLeast(500L), hint, board)

    private const val M = HuntBoards.MIREWATCH
    private const val S = HuntBoards.SUNSPIRE
    private const val F = HuntBoards.FROSTPEAK
    private const val A = HuntBoards.ASHEN

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
        boss("b_elderdragon", MonsterType.ELDER_DRAGON, 18, 3000, "On the throne of the Obsidian Wyrm Sanctum."),
        // --- Mirewatch: Warden Brisk's marsh bounties (Lv 20-37). Appended so saved contract indexes stay valid. ---
        regular("m_ghouls", "Ghouls in the Reeds", MonsterType.GHOUL, 25, 20, 1500, "Ghouls haunt the marsh wilds around Mirewatch.", M),
        regular("m_hags", "Hag Hunt", MonsterType.BOG_HAG, 20, 20, 1600, "Murkfen Hollow, just west of Mirewatch.", M),
        regular("m_gators", "Gator Wallows", MonsterType.GATOR_BRUTE, 20, 20, 1600, "Gator brutes wallow in Murkfen Hollow's southern pools.", M),
        regular("m_wisps", "Snuff the Wisps", MonsterType.FEN_WISP, 20, 20, 1500, "Fen wisps drift through Murkfen's northern glades.", M),
        regular("m_lurkers", "Clear the Vaults", MonsterType.MIRE_LURKER, 20, 25, 2200, "Mire lurkers fill the Sunken Mire Vaults, north-west of town.", M),
        regular("m_leeches", "Leech Weavers", MonsterType.BOG_LEECH_WEAVER, 20, 25, 2200, "They nest in the Mire Vaults' south-east caverns.", M),
        regular("m_serpents", "Temple Serpents", MonsterType.TEMPLE_SERPENT_GUARD, 15, 30, 2800, "Serpent guards hold the Overgrown Ziggurat, far to the south.", M),
        regular("m_acolytes", "Silence the Acolytes", MonsterType.SUN_PRIEST_ACOLYTE, 15, 30, 2800, "Sun-priest acolytes chant in the Ziggurat's cloisters.", M),
        boss("m_b_hagmother", MonsterType.HAGMOTHER_VESSA, 22, 3500, "On her moated island at the far east of Murkfen Hollow.", M),
        boss("m_b_miremaw", MonsterType.MIREMAW_THE_DROWNED, 27, 5000, "Down the Sluice Sump stairs beneath the Mire Vaults.", M),
        boss("m_b_xolatl", MonsterType.XOLATL_THE_SUN_FANG, 32, 6500, "Below the Ziggurat's summit altar.", M),
        // --- Sunspire: Sergeant Kasim's desert bounties (Lv 30-46) ---
        regular("s_scorpions", "Scorpion Sweep", MonsterType.DUNE_SCORPION, 20, 30, 2600, "Tomb of Akhet, south-west of Sunspire.", S),
        regular("s_wraiths", "Lay the Wraiths", MonsterType.SAND_WRAITH, 20, 30, 2600, "Sand wraiths drift through the Tomb's burial wings.", S),
        regular("s_basilisks", "Basilisk Hides", MonsterType.SUNSCALE_BASILISK, 20, 31, 2800, "Sunscale basilisks bask in the Tomb's Hall of Kings.", S),
        regular("s_devils", "Fire Devils", MonsterType.FIRE_DEVIL, 20, 32, 2600, "Fire devils roam the open badlands around Sunspire.", S),
        regular("s_raiders", "Break the Warband", MonsterType.GOREK_RAIDER, 20, 36, 3600, "Gorek raiders hold the Gorek Labyrinth, far to the south-west.", S),
        regular("s_hexcallers", "Hex-Caller Hunt", MonsterType.GOREK_HEX_CALLER, 15, 37, 3800, "Hex-callers guard the Labyrinth's obsidian ring.", S),
        regular("s_stalkers", "Glass Stalkers", MonsterType.SCORCHGLASS_STALKER, 20, 41, 4400, "Scorchglass Rift, west of the Tomb.", S),
        regular("s_dustdevils", "Calm the Dust Devils", MonsterType.DUST_DEVIL, 20, 41, 4400, "Dust devils whirl in the Rift's Dust Bowl.", S),
        regular("s_golems", "Salt-Crust Golems", MonsterType.SALTGLASS_GOLEM, 15, 42, 4600, "Saltglass golems guard the Rift's Salt Flats.", S),
        boss("s_b_akhet", MonsterType.AKHET_THE_SAND_PHARAOH, 32, 6000, "Sealed in the Sanctum at the top of the Tomb of Akhet.", S),
        boss("s_b_vaelgor", MonsterType.VAELGOR_LABYRINTH_WARDEN, 38, 8000, "Down the stairs north of the Gorek Labyrinth's ring.", S),
        boss("s_b_sirocco", MonsterType.SIROCCO_THE_STORM_DJINN, 43, 9500, "Inside the Djinn's Eye at the top of the Scorchglass Rift.", S),
        // --- Frostpeak: Huntmaster Sigrun's frost bounties (Lv 42-58) ---
        regular("f_quarrymen", "Quarry Giants", MonsterType.GRIMMAW_QUARRYMAN, 20, 42, 4800, "Grimmaw quarrymen work the Grimmaw Quarry, south-west of the citadel.", F),
        regular("f_hurlers", "Rock-Hurlers", MonsterType.GRIMMAW_ROCK_HURLER, 20, 42, 4800, "Rock-hurlers throw from the Quarry's upper alcoves.", F),
        regular("f_crushers", "Crushers", MonsterType.GRIMMAW_CRUSHER, 15, 44, 5200, "Crushers guard the Quarry's deep terraces.", F),
        regular("f_wights", "Barrow Wights", MonsterType.BARROW_WIGHT, 20, 48, 6000, "Wights stalk the Blightwood Barrow Catacombs.", F),
        regular("f_banshees", "Silence the Banshees", MonsterType.FROST_BANSHEE, 20, 48, 6000, "Frost banshees wail in the Barrow's frozen halls.", F),
        boss("f_b_kragor", MonsterType.KRAGOR_THE_STONE_EYED, 44, 9000, "In Kragor's Deep Quarry Pit, below the Grimmaw Quarry.", F),
        boss("f_b_malgrath", MonsterType.LORD_MALGRATH_THE_UNDYING, 50, 12000, "In the Barrow Sovereign's Tomb, below the Blightwood Barrow.", F),
        regular("f_yetis", "Yeti Cull", MonsterType.YETI_RAVAGER, 20, 54, 7200, "Yetis den in the south caves of the Rimeheart Caverns, north of the citadel.", F),
        regular("f_rimefangs", "Rimefang Pack", MonsterType.RIMEFANG_STALKER, 20, 54, 7000, "Rimefangs prowl the Rimeheart's snowdrift tunnels.", F),
        regular("f_shamans", "Break the Shamans", MonsterType.GLACIER_SHAMAN, 15, 55, 7400, "Glacier shamans chant in the Shaman Grotto.", F),
        boss("f_b_hrimgar", MonsterType.HRIMGAR_THE_GLACIER_KING, 56, 15000, "On the Throne of Rime, west of the frozen falls.", F),
        // --- Ashen Bastion: Warmaster Dravek's ember bounties (Lv 55-75) ---
        regular("a_ghouls", "Ash-Field Ghouls", MonsterType.GHOUL, 25, 56, 6400, "Ghouls dig through the ash fields around the Bastion.", A),
        regular("a_necromancers", "Burn the Necromancers", MonsterType.NECROMANCER, 20, 60, 7800, "Necromancers raise the dead across the caldera wastes.", A),
        regular("a_devils", "Fire Devils", MonsterType.FIRE_DEVIL, 20, 63, 8400, "Fire devils dance on the lava fields outside the moat.", A),
        regular("a_hatchlings", "Hatchling Cull", MonsterType.DRAGON_HATCHLING, 20, 64, 8800, "Hatchlings nest on the caldera slopes and in the Ember Sanctum.", A),
        regular("a_automatons", "Scrap the Automatons", MonsterType.SLAG_AUTOMATON, 15, 66, 9600, "Slag automatons work the Ashen Caldera Foundry, down the shaft west of the West Gate.", A),
        regular("a_pyromancers", "Douse the Pyromancers", MonsterType.ASH_PYROMANCER, 15, 66, 9600, "Ash pyromancers tend the Foundry's crucibles.", A),
        regular("a_drakes", "Ember Drakes", MonsterType.EMBER_DRAKE, 15, 67, 10000, "Ember drakes roost in the Ember Sanctum on the Ember Isles.", A),
        regular("a_cultists", "Break the Cult", MonsterType.OBSIDIAN_CULTIST, 20, 67, 10000, "Obsidian cultists chant in the Ember Sanctum.", A),
        boss("a_b_vulkaris", MonsterType.VULKARIS_THE_FORGE_TITAN, 70, 20000, "In the Heart of the Crucible, below the Caldera Foundry.", A),
        boss("a_b_ignivar", MonsterType.IGNIVAR_ASH_WING_WYRM, 70, 22000, "On the Caldera Dragon Throne, below the Ember Sanctum.", A)
    )

    fun byId(id: String?): HuntContract? = all.firstOrNull { it.id == id }
    fun byIndex(index: Int): HuntContract? = all.getOrNull(index)
    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }
    fun availableFor(level: Int): List<HuntContract> = all.filter { level >= it.minLevel }
    fun forBoard(board: String): List<HuntContract> = all.filter { it.board == board }
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

    private val marshPool = listOf(
        MonsterType.BOG_HAG, MonsterType.GATOR_BRUTE, MonsterType.FEN_WISP, MonsterType.MIRE_LURKER,
        MonsterType.BOG_LEECH_WEAVER, MonsterType.SWAMP_TROLL, MonsterType.GHOUL,
        MonsterType.TEMPLE_SERPENT_GUARD, MonsterType.SUN_PRIEST_ACOLYTE
    )
    private val badlandsPool = listOf(
        MonsterType.DUNE_SCORPION, MonsterType.SAND_WRAITH, MonsterType.SUNSCALE_BASILISK, MonsterType.FIRE_DEVIL,
        MonsterType.GOREK_RAIDER, MonsterType.GOREK_PHALANX, MonsterType.GOREK_HEX_CALLER,
        MonsterType.SCORCHGLASS_STALKER, MonsterType.DUST_DEVIL, MonsterType.SALTGLASS_GOLEM
    )

    fun featuredCreature(wallClockMs: Long = System.currentTimeMillis()): MonsterType {
        val day = wallClockMs / DAY_MS
        return featuredPool[VendorCatalog.positiveHash(day, 77L) % featuredPool.size]
    }

    /** Mirewatch's crier announces a marsh creature each day. */
    fun featuredMarshCreature(wallClockMs: Long = System.currentTimeMillis()): MonsterType =
        marshPool[VendorCatalog.positiveHash(wallClockMs / DAY_MS, 131L) % marshPool.size]

    /** Sunspire's crier announces a badlands creature each day. */
    fun featuredBadlandsCreature(wallClockMs: Long = System.currentTimeMillis()): MonsterType =
        badlandsPool[VendorCatalog.positiveHash(wallClockMs / DAY_MS, 197L) % badlandsPool.size]

    private val frostPool = listOf(
        MonsterType.GRIMMAW_QUARRYMAN, MonsterType.GRIMMAW_ROCK_HURLER, MonsterType.GRIMMAW_CRUSHER,
        MonsterType.BARROW_WIGHT, MonsterType.FROST_BANSHEE,
        MonsterType.YETI_RAVAGER, MonsterType.RIMEFANG_STALKER, MonsterType.GLACIER_SHAMAN
    )

    /** Frostpeak's crier announces a highland creature each day. */
    fun featuredFrostCreature(wallClockMs: Long = System.currentTimeMillis()): MonsterType =
        frostPool[VendorCatalog.positiveHash(wallClockMs / DAY_MS, 263L) % frostPool.size]

    private val ashPool = listOf(
        MonsterType.GHOUL, MonsterType.NECROMANCER, MonsterType.FIRE_DEVIL, MonsterType.DRAGON_HATCHLING,
        MonsterType.SLAG_AUTOMATON, MonsterType.ASH_PYROMANCER, MonsterType.EMBER_DRAKE, MonsterType.OBSIDIAN_CULTIST
    )

    /** Ashen Bastion's crier announces a caldera creature each day. */
    fun featuredAshCreature(wallClockMs: Long = System.currentTimeMillis()): MonsterType =
        ashPool[VendorCatalog.positiveHash(wallClockMs / DAY_MS, 331L) % ashPool.size]

    /** Every creature that earns today's bonus XP (one per town). */
    fun featuredCreatures(wallClockMs: Long = System.currentTimeMillis()): Set<MonsterType> =
        setOf(featuredCreature(wallClockMs), featuredMarshCreature(wallClockMs), featuredBadlandsCreature(wallClockMs), featuredFrostCreature(wallClockMs), featuredAshCreature(wallClockMs))

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
            "Outlaws hold the fortress. Crossbowmen cover the bailey, so keep moving."),
        "Fen Guide Oswin" to DungeonGuideInfo(-29, "Murkfen Hollow", "Lv 20-24", MonsterType.HAGMOTHER_VESSA.displayName,
            "Wisps lure you into deep water. The hag village guards the only bridge to her bower."),
        "Tomb Robber Sefa" to DungeonGuideInfo(-30, "Tomb of Akhet", "Lv 30-34", MonsterType.AKHET_THE_SAND_PHARAOH.displayName,
            "Scorpions nest in the east dens. The sanctum gates lie north of the Hall of Kings."),
        "Rift Scout Imran" to DungeonGuideInfo(-31, "Scorchglass Rift", "Lv 41-45", MonsterType.SIROCCO_THE_STORM_DJINN.displayName,
            "Golems guard the salt flats. The Djinn's Eye sits behind a lava ring at the top of the rift."),
        "Icewarden Kjell" to DungeonGuideInfo(-32, "Rimeheart Caverns", "Lv 54-58", MonsterType.HRIMGAR_THE_GLACIER_KING.displayName,
            "Yetis den in the south caves. The Throne of Rime lies west across the frozen falls.")
    )

    fun messageFor(guideName: String): String {
        val g = byGuideName[guideName] ?: return "$guideName: 'Tread carefully below.'"
        return "$guideName: 'Below lies the ${g.dungeonName} (${g.levels}). Its master, ${g.bossName}, waits on the deepest floor. ${g.tip}'"
    }
}
