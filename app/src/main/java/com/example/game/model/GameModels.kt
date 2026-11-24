package com.example.game.model

import com.example.game.multiplayer.LocalMonsterAttackEvent
import com.example.game.multiplayer.LocalMonsterHitEvent
import com.example.game.multiplayer.LocalPlayerActionFxEvent
import com.squareup.moshi.JsonClass

const val CURRENT_MAP_VERSION = 3
const val V1_SURFACE_OFFSET = 32
const val V2_TO_V3_OFFSET_X = 149
const val V2_TO_V3_OFFSET_Y = 146
const val DEFAULT_REGION_ID = "rookvale_valley"
const val WORLD_CHUNK_SIZE = 32
const val OVERLAND_REGION_SIZE = 400

/**
 * Normalizes any legacy, blank, or mixed-case region identifier so online clients
 * and saved characters always agree on canonical region IDs (defaulting to "rookvale_valley").
 */
fun normalizeRegionId(raw: String?): String {
    val cleaned = raw?.trim()?.lowercase()?.replace(' ', '_')
        ?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .orEmpty()
    return when (cleaned) {
        "", "rookvale", "rookvale_city", "rookvale_overland", "overland", "default", "main", "valley" -> DEFAULT_REGION_ID
        else -> cleaned
    }
}
const val ORACLE_MIN_LEVEL = 20
const val TOOL_BAG_WEIGHT_MULTIPLIER = 0.5
const val BASE_MAX_CAP_OZ = 1500.0
const val ADVENTURER_CAP_PER_LEVEL_OZ = 125.0
const val GOLD_COIN_WEIGHT_OZ = 0.01
const val TREE_REGROW_TICKS = 60
const val TREE_CHOP_COOLDOWN_MS = 900L
const val REGULAR_CORPSE_LIFETIME_MS = 90_000L
const val BOSS_CORPSE_LIFETIME_MS = 180_000L
const val CORPSE_EXPIRY_WARNING_MS = 15_000L
const val BOSS_RESPAWN_MS = 300_000L

data class SpriteColorSwatch(
    val name: String,
    val primaryArgb: Int,
    val highlightArgb: Int
)

object AdventurerPalette {
    val tunicColors: List<SpriteColorSwatch> = listOf(
        SpriteColorSwatch("Forest Moss", 0xFF2E6B3E.toInt(), 0xFF428F55.toInt()),
        SpriteColorSwatch("Crimson Cloak", 0xFF9E2B2B.toInt(), 0xFFC94040.toInt()),
        SpriteColorSwatch("Slate Navy", 0xFF2B4E8A.toInt(), 0xFF426FB8.toInt()),
        SpriteColorSwatch("Ochre Leather", 0xFF9E6A2B.toInt(), 0xFFC78B3E.toInt()),
        SpriteColorSwatch("Royal Plum", 0xFF6B3278.toInt(), 0xFF8F499E.toInt()),
        SpriteColorSwatch("Ash Linen", 0xFF6D7078.toInt(), 0xFF9498A3.toInt())
    )

    val hairColors: List<SpriteColorSwatch> = listOf(
        SpriteColorSwatch("Chestnut", 0xFF5C351E.toInt(), 0xFF7D4B2B.toInt()),
        SpriteColorSwatch("Raven Black", 0xFF1F1D24.toInt(), 0xFF383542.toInt()),
        SpriteColorSwatch("Honey Gold", 0xFFC99B3B.toInt(), 0xFFE6BC5C.toInt()),
        SpriteColorSwatch("Auburn", 0xFF8F331E.toInt(), 0xFFB8492C.toInt()),
        SpriteColorSwatch("Silver Grey", 0xFF9CA3AF.toInt(), 0xFFD1D5DB.toInt()),
        SpriteColorSwatch("Ash Brown", 0xFF6B5A49.toInt(), 0xFF8C7761.toInt())
    )

    fun tunicAt(index: Int): SpriteColorSwatch = tunicColors[index.coerceIn(0, tunicColors.lastIndex)]
    fun hairAt(index: Int): SpriteColorSwatch = hairColors[index.coerceIn(0, hairColors.lastIndex)]
}

enum class Vocation(
    val displayName: String,
    val subtitle: String,
    val description: String,
    val hpPerLevel: Int,
    val mpPerLevel: Int,
    val capPerLevel: Double,
    val promotionCapBonus: Double,
    val baseHp: Int,
    val baseMp: Int,
    val baseCap: Double,
    val meleeGainRate: Double,
    val distanceGainRate: Double,
    val shieldingGainRate: Double,
    val magicGainRate: Double,
    val attackRange: Int
) {
    ADVENTURER(
        displayName = "Adventurer",
        subtitle = "Rookvale Novice",
        description = "Blank-slate explorer free to train any weapon, shield, rune, or gathering skill until Level 20 (+125 oz/lvl Cap).",
        hpPerLevel = 10,
        mpPerLevel = 10,
        capPerLevel = ADVENTURER_CAP_PER_LEVEL_OZ,
        promotionCapBonus = 0.0,
        baseHp = 150,
        baseMp = 55,
        baseCap = BASE_MAX_CAP_OZ,
        meleeGainRate = 1.0,
        distanceGainRate = 1.0,
        shieldingGainRate = 1.0,
        magicGainRate = 1.0,
        attackRange = 1
    ),
    KNIGHT(
        displayName = "Knight",
        subtitle = "Vanguard of Steel",
        description = "Instant +1,250 oz Cap surge at promotion, +200 oz/lvl thereafter, high HP growth, and mastery of Tier 3-4 melee gear.",
        hpPerLevel = 15,
        mpPerLevel = 5,
        capPerLevel = 200.0,
        promotionCapBonus = 1250.0,
        baseHp = 185,
        baseMp = 35,
        baseCap = BASE_MAX_CAP_OZ,
        meleeGainRate = 1.65,
        distanceGainRate = 0.75,
        shieldingGainRate = 1.75,
        magicGainRate = 0.65,
        attackRange = 1
    ),
    PALADIN(
        displayName = "Paladin",
        subtitle = "Sacred Marksman",
        description = "Instant +900 oz Cap surge at promotion, +160 oz/lvl thereafter, rapid Distance progression, and mastery of bows & arbalests.",
        hpPerLevel = 10,
        mpPerLevel = 15,
        capPerLevel = 160.0,
        promotionCapBonus = 900.0,
        baseHp = 155,
        baseMp = 75,
        baseCap = BASE_MAX_CAP_OZ,
        meleeGainRate = 1.0,
        distanceGainRate = 1.70,
        shieldingGainRate = 1.15,
        magicGainRate = 1.25,
        attackRange = 4
    ),
    MAGE(
        displayName = "Mage",
        subtitle = "Arcane Runecaster",
        description = "Instant +600 oz Cap surge at promotion, +125 oz/lvl thereafter, massive MP growth, and mastery of Tier 3-4 staves & runes.",
        hpPerLevel = 5,
        mpPerLevel = 30,
        capPerLevel = 125.0,
        promotionCapBonus = 600.0,
        baseHp = 135,
        baseMp = 140,
        baseCap = BASE_MAX_CAP_OZ,
        meleeGainRate = 0.65,
        distanceGainRate = 0.65,
        shieldingGainRate = 0.70,
        magicGainRate = 1.85,
        attackRange = 3
    );

    companion object {
        val ceremonyVocations: List<Vocation> = listOf(KNIGHT, PALADIN, MAGE)
    }
}

enum class SkillType(val displayName: String) {
    MELEE("Melee Fighting"),
    FIST("Fist Fighting"),
    CLUB("Club Fighting"),
    SWORD("Sword Fighting"),
    AXE("Axe Fighting"),
    DISTANCE("Distance Fighting"),
    SHIELDING("Shielding"),
    MAGIC("Magic Level"),
    FISHING("Fishing"),
    MINING("Mining")
}

@JsonClass(generateAdapter = true)
data class SkillProgress(
    val level: Int = 10,
    val points: Double = 0.0
) {
    fun requiredPointsForNext(): Double {
        val delta = (level - 9).coerceAtLeast(1)
        return 14.0 * Math.pow(1.22, delta.toDouble())
    }

    fun progressFraction(): Float {
        val req = requiredPointsForNext()
        return (points / req).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Dedicated Mana-driven Magic Level XP curve starting at Magic Level 1:
     * 30 * 1.24^(level - 1) so climbing 1 -> 4 (Healing) -> 7 (HMM) -> 12 (GFB) -> 15 (SD)
     * rewards steady casting and runecrafting.
     */
    fun requiredMagicXpForNext(): Double {
        val exp = (level - 1).coerceAtLeast(0)
        return 30.0 * Math.pow(1.24, exp.toDouble())
    }

    fun magicProgressFraction(): Float {
        val req = requiredMagicXpForNext()
        return (points / req).toFloat().coerceIn(0f, 1f)
    }
}

@JsonClass(generateAdapter = true)
data class PlayerSkills(
    val melee: SkillProgress = SkillProgress(10, 0.0),
    val fist: SkillProgress = SkillProgress(10, 0.0),
    val club: SkillProgress = SkillProgress(10, 0.0),
    val sword: SkillProgress = SkillProgress(10, 0.0),
    val axe: SkillProgress = SkillProgress(10, 0.0),
    val distance: SkillProgress = SkillProgress(10, 0.0),
    val shielding: SkillProgress = SkillProgress(10, 0.0),
    val magic: SkillProgress = SkillProgress(1, 0.0),
    val fishing: SkillProgress = SkillProgress(10, 0.0),
    val mining: SkillProgress = SkillProgress(10, 0.0)
) {
    fun levelFor(type: SkillType): Int = when (type) {
        SkillType.MELEE -> maxOf(melee.level, fist.level, club.level, sword.level, axe.level)
        SkillType.FIST -> fist.level
        SkillType.CLUB -> club.level
        SkillType.SWORD -> sword.level
        SkillType.AXE -> axe.level
        SkillType.DISTANCE -> distance.level
        SkillType.SHIELDING -> shielding.level
        SkillType.MAGIC -> magic.level
        SkillType.FISHING -> fishing.level
        SkillType.MINING -> mining.level
    }

    fun progressFor(type: SkillType): SkillProgress = when (type) {
        SkillType.MELEE -> melee
        SkillType.FIST -> fist
        SkillType.CLUB -> club
        SkillType.SWORD -> sword
        SkillType.AXE -> axe
        SkillType.DISTANCE -> distance
        SkillType.SHIELDING -> shielding
        SkillType.MAGIC -> magic
        SkillType.FISHING -> fishing
        SkillType.MINING -> mining
    }

    companion object {
        fun meleeSkillTypeForWeapon(weaponId: String?): SkillType {
            if (weaponId.isNullOrBlank()) return SkillType.FIST
            val lower = weaponId.lowercase()
            return when {
                lower.contains("club") || lower.contains("mace") || lower.contains("hammer") || lower.contains("cudgel") -> SkillType.CLUB
                lower.contains("axe") || lower.contains("halberd") || lower.contains("glaive") || lower.contains("hatchet") || lower.contains("cleaver") -> SkillType.AXE
                else -> SkillType.SWORD
            }
        }
    }
}

enum class EquipmentSlot(val label: String) {
    HELMET("Helmet"),
    AMULET("Amulet"),
    ARMOR("Armor"),
    WEAPON("Weapon"),
    SHIELD("Shield"),
    LEGS("Legs"),
    BOOTS("Boots"),
    AMMO("Ammo")
}

enum class AmmoKind {
    SHAFTS,
    QUARRELS
}

enum class ItemCategory {
    WEAPON_MELEE,
    WEAPON_DISTANCE,
    WEAPON_WAND,
    AMMO,
    SHIELD,
    ARMOR,
    HELMET,
    LEGS,
    BOOTS,
    AMULET,
    POTION_HP,
    POTION_MP,
    RUNE_BLANK,
    RUNE_HEAL,
    RUNE_ATTACK,
    TOOL_ROPE,
    TOOL_FISHING_ROD,
    TOOL_PICKAXE,
    TOOL_HATCHET,
    FOOD,
    RAW_ORE,
    METAL_INGOT,
    CRAFT_MATERIAL,
    LOOT_VALUABLE
}

data class ItemDefinition(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val weightOz: Double,
    val buyPrice: Int = 0,
    val sellPrice: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val armor: Int = 0,
    val range: Int = 1,
    val attackIntervalMs: Long = 1000L,
    val requiredSkillType: SkillType? = null,
    val requiredSkillLevel: Int = 10,
    val requiredMagicLevel: Int = 0,
    val aoeRadius: Int = 0,
    val runeTier: Int = 0,
    val requiredAmmoKind: AmmoKind? = null,
    val ammoKind: AmmoKind? = null,
    val pickaxeTier: Int = 0,
    val restoreAmount: Int = 0,
    val manaCost: Int = 0,
    val stackable: Boolean = false,
    val isTwoHanded: Boolean = false,
    val preferredVocation: Vocation? = null,
    val description: String = ""
) {
    val equipSlot: EquipmentSlot?
        get() = when (category) {
            ItemCategory.WEAPON_MELEE, ItemCategory.WEAPON_DISTANCE, ItemCategory.WEAPON_WAND -> EquipmentSlot.WEAPON
            ItemCategory.AMMO -> EquipmentSlot.AMMO
            ItemCategory.SHIELD -> EquipmentSlot.SHIELD
            ItemCategory.ARMOR -> EquipmentSlot.ARMOR
            ItemCategory.HELMET -> EquipmentSlot.HELMET
            ItemCategory.LEGS -> EquipmentSlot.LEGS
            ItemCategory.BOOTS -> EquipmentSlot.BOOTS
            ItemCategory.AMULET -> EquipmentSlot.AMULET
            else -> null
        }

    val isTool: Boolean
        get() = category == ItemCategory.TOOL_ROPE ||
            category == ItemCategory.TOOL_FISHING_ROD ||
            category == ItemCategory.TOOL_PICKAXE ||
            category == ItemCategory.TOOL_HATCHET

    fun carriedWeightOz(): Double = if (isTool) weightOz * TOOL_BAG_WEIGHT_MULTIPLIER else weightOz

    fun speedLabel(): String = when {
        attackIntervalMs <= 850L -> "Fast (${attackIntervalMs / 1000f}s)"
        attackIntervalMs <= 1050L -> "Balanced (${attackIntervalMs / 1000f}s)"
        else -> "Heavy (${attackIntervalMs / 1000f}s)"
    }
}

@JsonClass(generateAdapter = true)
data class ItemStack(
    val itemId: String,
    val count: Int = 1
)

@JsonClass(generateAdapter = true)
data class EquippedGear(
    val helmetId: String? = null,
    val amuletId: String? = null,
    val armorId: String? = null,
    val weaponId: String? = null,
    val shieldId: String? = null,
    val legsId: String? = null,
    val bootsId: String? = null,
    val ammoId: String? = null
) {
    fun getSlot(slot: EquipmentSlot): String? = when (slot) {
        EquipmentSlot.HELMET -> helmetId
        EquipmentSlot.AMULET -> amuletId
        EquipmentSlot.ARMOR -> armorId
        EquipmentSlot.WEAPON -> weaponId
        EquipmentSlot.SHIELD -> shieldId
        EquipmentSlot.LEGS -> legsId
        EquipmentSlot.BOOTS -> bootsId
        EquipmentSlot.AMMO -> ammoId
    }

    fun withSlot(slot: EquipmentSlot, itemId: String?): EquippedGear = when (slot) {
        EquipmentSlot.HELMET -> copy(helmetId = itemId)
        EquipmentSlot.AMULET -> copy(amuletId = itemId)
        EquipmentSlot.ARMOR -> copy(armorId = itemId)
        EquipmentSlot.WEAPON -> copy(weaponId = itemId)
        EquipmentSlot.SHIELD -> copy(shieldId = itemId)
        EquipmentSlot.LEGS -> copy(legsId = itemId)
        EquipmentSlot.BOOTS -> copy(bootsId = itemId)
        EquipmentSlot.AMMO -> copy(ammoId = itemId)
    }

    // Ammo stays stacked inside backpack; ammoId points to the selected quiver stack
    fun nonAmmoIds(): List<String> = listOfNotNull(helmetId, amuletId, armorId, weaponId, shieldId, legsId, bootsId)

    fun totalArmor(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).armor }
    fun totalDefense(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).defense }
    fun shieldDefense(): Int {
        if (weaponDef()?.isTwoHanded == true) return 0
        return shieldId?.let { ItemCatalog.get(it).defense } ?: 0
    }
    fun weaponDef(): ItemDefinition? = weaponId?.let { ItemCatalog.get(it) }
    fun ammoDef(): ItemDefinition? = ammoId?.let { ItemCatalog.get(it) }
    fun totalWeightOz(): Double = nonAmmoIds().sumOf { ItemCatalog.get(it).weightOz }
}

enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, -1),
    SOUTH(0, 1),
    WEST(-1, 0),
    EAST(1, 0)
}

enum class OreVeinKind(
    val displayName: String,
    val oreItemId: String,
    val requiredPickaxeTier: Int,
    val requiredPickaxeName: String,
    val maxCharges: Int,
    val regenTicks: Int,
    val miningXpPoints: Double
) {
    OCHRE("Ochre Vein", "ochre_nugget", 1, "Quarry Mattock", 4, 26, 4.5),
    BOGSTONE("Bogstone Vein", "bogstone_shard", 2, "Sunbrass Pick", 4, 34, 6.5),
    BRIMSTONE("Brimstone Seam", "cinder_pitch", 3, "Duskmetal Breaker", 4, 40, 8.0),
    AETHERIUM("Aetherium Lode", "pale_starstone", 4, "Quenchsteel Delver", 3, 55, 11.5)
}

enum class TileType(val walkable: Boolean, val blocksSight: Boolean = false) {
    GRASS(true),
    DARK_GRASS(true),
    COBBLESTONE(true),
    TEMPLE_FLOOR(true),
    TEMPLE_ALTAR(false),
    WOOD_PIER(true),
    WATER(false),
    STONE_WALL(false, true),
    CITY_WALL(false, true),
    MOUNTAIN_ROCK(false, true),
    TREE(false, true),
    TREE_STUMP(false),
    DEPOT_CHEST(false),
    MINI_DEPOT(false),
    SHOP_COUNTER(false),
    SMELTING_FURNACE(false),
    BLACKSMITH_ANVIL(false),
    FLETCHING_BENCH(false),
    RUNECRAFTING_TABLE(false),
    ORE_OCHRE(false),
    ORE_BOGSTONE(false),
    ORE_BRIMSTONE(false),
    ORE_AETHERIUM(false),
    DEPLETED_VEIN(false),
    DIRT_FLOOR(true),
    CAVE_WALL(false, true),
    CRYPT_FLOOR(true),
    LAVA(false),
    OBSIDIAN_FLOOR(true),
    STAIRS_DOWN(true),
    STAIRS_UP(true),
    ROPE_HOLE_DOWN(true),
    ROPE_SPOT_UP(true),
    TORCH_WALL(false, true),
    TIMBER_WALL(false, true),
    TIMBER_WINDOW_WALL(false, true),
    TEMPLE_WALL(false, true),
    GATE_PILLAR(false, true),
    WOOD_PLANK_FLOOR(true),
    ARCANE_FLOOR(true),
    FOUNTAIN(false),
    FLOWER_GRASS(true),
    HEDGE(false),
    WOOD_FENCE(false),
    LANTERN_POST(false),
    WOOD_BRIDGE(true),
    MARKET_AWNING(false),
    CRATE_BARREL(false),
    BOOKSHELF(false),
    CRYSTAL_PILLAR(false),
    TEMPLE_PEW(false),
    WEAPON_RACK(false),
    MOORED_BOAT(false),
    WOOD_BED(false),
    STONE_BRIDGE(true),
    IRON_BAR_GATE(true);

    fun oreVeinKind(): OreVeinKind? = when (this) {
        ORE_OCHRE -> OreVeinKind.OCHRE
        ORE_BOGSTONE -> OreVeinKind.BOGSTONE
        ORE_BRIMSTONE -> OreVeinKind.BRIMSTONE
        ORE_AETHERIUM -> OreVeinKind.AETHERIUM
        else -> null
    }
}

data class OreNodeState(
    val floor: Int,
    val x: Int,
    val y: Int,
    val kind: OreVeinKind,
    val chargesLeft: Int,
    val regenTicksLeft: Int = 0
) {
    val isDepleted: Boolean get() = chargesLeft <= 0
}

enum class MonsterAiStyle {
    MELEE,
    RANGED_KITE,
    BOSS_AOE_SUMMON
}

enum class ThreatBand {
    EASY,
    EVEN,
    CHALLENGING,
    DEADLY
}

data class FloorWarningBanner(
    val floorId: Int,
    val floorName: String,
    val dangerLevel: String,
    val recommendedLevel: String,
    val severity: Int,
    val createdAtMs: Long
)

enum class BossSpecialKind {
    NONE,
    VENOM_NOVA,
    FLAME_BURST
}

enum class MonsterType(
    val displayName: String,
    val tier: Int,
    val threatLevel: Int,
    val maxHp: Int,
    val armor: Int,
    val xpReward: Int,
    val minDmg: Int,
    val maxDmg: Int,
    val goldMin: Int,
    val goldMax: Int,
    val moveIntervalTicks: Int,
    val aiStyle: MonsterAiStyle = MonsterAiStyle.MELEE,
    val preferredRange: Int = 1,
    val rangedProjectile: ProjectileKind? = null,
    val inflictsPoison: Boolean = false,
    val inflictsFire: Boolean = false,
    val isBoss: Boolean = false,
    val summonTypeName: String? = null,
    val maxSummons: Int = 0,
    val specialKind: BossSpecialKind = BossSpecialKind.NONE,
    val bossTitle: String = ""
) {
    // --- TIER 1: Starter Overland (Levels 1-3) ---
    CAVE_RAT(
        displayName = "Cave Rat", tier = 1, threatLevel = 1, maxHp = 32, armor = 0,
        xpReward = 24, minDmg = 2, maxDmg = 6, goldMin = 4, goldMax = 12, moveIntervalTicks = 1
    ),
    WILD_RABBIT(
        displayName = "Wild Rabbit", tier = 1, threatLevel = 1, maxHp = 22, armor = 0,
        xpReward = 16, minDmg = 1, maxDmg = 4, goldMin = 1, goldMax = 6, moveIntervalTicks = 1
    ),
    YOUNG_SNAKE(
        displayName = "Young Snake", tier = 1, threatLevel = 2, maxHp = 40, armor = 1,
        xpReward = 32, minDmg = 3, maxDmg = 8, goldMin = 5, goldMax = 15, moveIntervalTicks = 1
    ),
    CRAG_WOLF(
        displayName = "Crag Wolf", tier = 1, threatLevel = 3, maxHp = 58, armor = 2,
        xpReward = 46, minDmg = 6, maxDmg = 12, goldMin = 7, goldMax = 20, moveIntervalTicks = 1
    ),

    // --- TIER 2: Outer Wilds & Floor -1 Sewers/Caves (Levels 3-6) ---
    SWAMP_TROLL(
        displayName = "Swamp Troll", tier = 2, threatLevel = 4, maxHp = 100, armor = 4,
        xpReward = 82, minDmg = 9, maxDmg = 18, goldMin = 14, goldMax = 34, moveIntervalTicks = 2
    ),
    SKELETON(
        displayName = "Skeleton", tier = 2, threatLevel = 5, maxHp = 118, armor = 5,
        xpReward = 98, minDmg = 11, maxDmg = 22, goldMin = 18, goldMax = 44, moveIntervalTicks = 1
    ),
    GOBLIN_SCOUT(
        displayName = "Goblin Scout", tier = 2, threatLevel = 5, maxHp = 92, armor = 3,
        xpReward = 95, minDmg = 10, maxDmg = 20, goldMin = 18, goldMax = 42, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.THROWN_STONE
    ),
    POISON_SPIDER(
        displayName = "Poison Spider", tier = 2, threatLevel = 6, maxHp = 125, armor = 4,
        xpReward = 115, minDmg = 11, maxDmg = 23, goldMin = 20, goldMax = 48, moveIntervalTicks = 1,
        inflictsPoison = true
    ),
    BROODMOTHER_ARACHNE(
        displayName = "Broodmother Arachne", tier = 2, threatLevel = 8, maxHp = 460, armor = 9,
        xpReward = 480, minDmg = 18, maxDmg = 34, goldMin = 110, goldMax = 220, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.VENOM_GLOB,
        inflictsPoison = true, isBoss = true, summonTypeName = "POISON_SPIDER", maxSummons = 1,
        specialKind = BossSpecialKind.VENOM_NOVA, bossTitle = "Matriarch of the Mossy Sewers"
    ),

    // --- TIER 3: Floor -2 Deep Caverns (Levels 6-10) ---
    MINOTAUR(
        displayName = "Minotaur", tier = 3, threatLevel = 7, maxHp = 215, armor = 10,
        xpReward = 190, minDmg = 17, maxDmg = 32, goldMin = 34, goldMax = 78, moveIntervalTicks = 2
    ),
    ORC_WARRIOR(
        displayName = "Orc Warrior", tier = 3, threatLevel = 8, maxHp = 245, armor = 11,
        xpReward = 225, minDmg = 20, maxDmg = 36, goldMin = 40, goldMax = 90, moveIntervalTicks = 1
    ),
    GHOUL(
        displayName = "Ghoul", tier = 3, threatLevel = 9, maxHp = 270, armor = 8,
        xpReward = 255, minDmg = 21, maxDmg = 38, goldMin = 46, goldMax = 102, moveIntervalTicks = 2,
        inflictsPoison = true
    ),
    FIRE_DEVIL(
        displayName = "Fire Devil", tier = 3, threatLevel = 10, maxHp = 235, armor = 7,
        xpReward = 275, minDmg = 22, maxDmg = 40, goldMin = 52, goldMax = 115, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true
    ),
    IGNIS_LORD_OF_THE_PIT(
        displayName = "Ignis, Lord of the Pit", tier = 3, threatLevel = 12, maxHp = 690, armor = 14,
        xpReward = 780, minDmg = 28, maxDmg = 52, goldMin = 180, goldMax = 340, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true, isBoss = true, summonTypeName = "FIRE_DEVIL", maxSummons = 1,
        specialKind = BossSpecialKind.FLAME_BURST, bossTitle = "Warden of the Molten Chasm"
    ),

    // --- TIER 4: Floor -3 Abyssal Crypt & Bosses (Levels 10+) ---
    CYCLOPS(
        displayName = "Cyclops", tier = 4, threatLevel = 11, maxHp = 370, armor = 15,
        xpReward = 360, minDmg = 28, maxDmg = 52, goldMin = 72, goldMax = 155, moveIntervalTicks = 2
    ),
    DRAGON_HATCHLING(
        displayName = "Dragon Hatchling", tier = 4, threatLevel = 13, maxHp = 440, armor = 14,
        xpReward = 450, minDmg = 32, maxDmg = 58, goldMin = 95, goldMax = 195, moveIntervalTicks = 2,
        preferredRange = 3, rangedProjectile = ProjectileKind.FIRE_BREATH, inflictsFire = true
    ),
    NECROMANCER(
        displayName = "Necromancer", tier = 4, threatLevel = 14, maxHp = 405, armor = 11,
        xpReward = 480, minDmg = 30, maxDmg = 56, goldMin = 105, goldMax = 210, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.NECRO_BOLT,
        summonTypeName = "SKELETON", maxSummons = 1
    ),
    DEMON_LORD(
        displayName = "Demon Lord", tier = 4, threatLevel = 18, maxHp = 860, armor = 18,
        xpReward = 1150, minDmg = 42, maxDmg = 76, goldMin = 240, goldMax = 440, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true, isBoss = true, summonTypeName = "FIRE_DEVIL", maxSummons = 2,
        specialKind = BossSpecialKind.FLAME_BURST, bossTitle = "Overlord of the Abyssal Forge"
    ),
    ELDER_DRAGON(
        displayName = "Elder Dragon", tier = 4, threatLevel = 20, maxHp = 1120, armor = 20,
        xpReward = 1550, minDmg = 48, maxDmg = 88, goldMin = 320, goldMax = 580, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIRE_BREATH,
        inflictsFire = true, isBoss = true, summonTypeName = "DRAGON_HATCHLING", maxSummons = 2,
        specialKind = BossSpecialKind.FLAME_BURST, bossTitle = "Sovereign of the Wyrm Sanctum"
    );

    fun threatBandFor(playerLevel: Int): ThreatBand = when {
        isBoss || threatLevel >= playerLevel + 5 -> ThreatBand.DEADLY
        threatLevel >= playerLevel + 2 -> ThreatBand.CHALLENGING
        threatLevel <= playerLevel - 3 -> ThreatBand.EASY
        else -> ThreatBand.EVEN
    }

    fun summonType(): MonsterType? = summonTypeName?.let { fromWireName(it) }

    companion object {
        fun fromWireName(raw: String?): MonsterType? {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return null
            return runCatching { valueOf(clean) }.getOrNull() ?: when (clean) {
                "RAT" -> CAVE_RAT
                "VESPER_BAT" -> GOBLIN_SCOUT
                "CAVE_SPIDER" -> POISON_SPIDER
                "STONEHORN_TROLL" -> SWAMP_TROLL
                "MARROW_ARCHER" -> GOBLIN_SCOUT
                "FORGE_BRUTE" -> ORC_WARRIOR
                "RUNIC_GOLEM" -> CYCLOPS
                "WYRM_HATCHLING" -> DRAGON_HATCHLING
                "CINDER_WRAITH" -> FIRE_DEVIL
                "DRAGON_BOSS" -> ELDER_DRAGON
                else -> null
            }
        }
    }
}

data class MonsterEntity(
    val id: Int,
    val type: MonsterType,
    val floor: Int,
    val x: Int,
    val y: Int,
    val spawnX: Int,
    val spawnY: Int,
    val hp: Int,
    val maxHp: Int,
    val facing: Direction = Direction.SOUTH,
    val moveCooldown: Int = 0,
    val animOffset: Float = 0f,
    val lastMoveFromX: Int = x,
    val lastMoveFromY: Int = y,
    val lastMoveTimeMs: Long = 0L,
    val lastAttackTimeMs: Long = 0L,
    val targetPlayerId: String? = null,
    val contributors: Set<String> = emptySet(),
    val isSummon: Boolean = false,
    val summonerId: Int? = null,
    val aoeCooldownTicks: Int = 2,
    val pendingAoeTiles: List<Pair<Int, Int>> = emptyList(),
    val summonCooldownTicks: Int = 3
)

data class PendingRespawn(
    val type: MonsterType,
    val floor: Int,
    val spawnX: Int,
    val spawnY: Int,
    val ticksRemaining: Int,
    val originalMonsterId: Int = 0,
    val respawnAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class CorpseEntity(
    val id: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val label: String,
    val isPlayerCorpse: Boolean,
    val monsterTypeName: String? = null,
    val gold: Int = 0,
    val items: List<ItemStack> = emptyList(),
    val createdAtMs: Long = 0L,
    val expiresAtMs: Long = 0L
) {
    fun remainingMs(nowMs: Long): Long? =
        if (isPlayerCorpse || expiresAtMs <= 0L) null else (expiresAtMs - nowMs).coerceAtLeast(0L)

    fun isExpired(nowMs: Long): Boolean =
        !isPlayerCorpse && expiresAtMs > 0L && nowMs >= expiresAtMs

    fun isExpiringSoon(nowMs: Long): Boolean {
        val rem = remainingMs(nowMs) ?: return false
        return rem in 1L..CORPSE_EXPIRY_WARNING_MS
    }
}

enum class FloatingTextKind {
    DAMAGE_MONSTER,
    DAMAGE_PLAYER,
    HEAL,
    MANA,
    POISON,
    FIRE,
    BLOCK,
    XP,
    SKILL_UP,
    INFO
}

data class FloatingText(
    val id: Long,
    val floor: Int,
    val tileX: Float,
    val tileY: Float,
    val text: String,
    val kind: FloatingTextKind,
    val createdAtMs: Long
)

enum class ProjectileKind {
    SPEAR,
    ARROW,
    BOLT,
    ENERGY_BOLT,
    ARCANE_DART,
    FIREBALL_AOE,
    HEAL_RUNE,
    DEATH_RUNE,
    FIRE_BREATH,
    FIREBALL,
    THROWN_STONE,
    NECRO_BOLT,
    VENOM_GLOB,
    SPLASH,
    MINING_SPARK,
    WOOD_CHIP
}

data class ProjectileEffect(
    val id: Long,
    val floor: Int,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val kind: ProjectileKind,
    val createdAtMs: Long,
    val durationMs: Long = 260L
)

data class DeathReport(
    val xpLost: Long,
    val floorDropped: Int,
    val corpseX: Int,
    val corpseY: Int,
    val droppedItemsCount: Int,
    val droppedGold: Int = 0,
    val safeBankGold: Long = 0L
)

@JsonClass(generateAdapter = true)
data class PlayerSaveData(
    val slotId: String,
    val name: String,
    val vocationName: String,
    val level: Int,
    val xp: Long,
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val soul: Int,
    val maxSoul: Int,
    val maxCapOz: Double,
    val gold: Int,
    val bankGold: Long = 0L,
    val floor: Int,
    val x: Int,
    val y: Int,
    val poisonTicks: Int,
    val fireTicks: Int,
    val skills: PlayerSkills = PlayerSkills(),
    val equipment: EquippedGear = EquippedGear(),
    val backpack: List<ItemStack> = emptyList(),
    val toolBag: List<ItemStack> = emptyList(),
    val depot: List<ItemStack> = emptyList(),
    val playerCorpses: List<CorpseEntity> = emptyList(),
    val mapVersion: Int = 1,
    val tunicColorIndex: Int = 0,
    val hairColorIndex: Int = 0,
    val questFlags: Map<String, Boolean> = emptyMap(),
    val boundAttackRuneId: String? = null,
    val boundHealRuneId: String? = "healing_rune",
    val customHotbarSlots: List<String?> = listOf(null, null),
    val regionId: String = DEFAULT_REGION_ID,
    val facingName: String = Direction.SOUTH.name
)

enum class LightingDetailMode {
    OFF,
    STATIC_HALO,
    DYNAMIC_FLICKER,
    HD_MULTI_LAYER
}

enum class ParticleDetailLevel {
    MINIMAL,
    MODERATE,
    FULL,
    HD_ENHANCED
}

enum class GraphicsQuality(
    val displayName: String,
    val shortBadge: String,
    val summaryNote: String,
    val capabilityTags: List<String>,
    val drawGroundClutter: Boolean,
    val drawWildflowers: Boolean,
    val drawShorelineDetails: Boolean,
    val drawContactShadows: Boolean,
    val lightingMode: LightingDetailMode,
    val drawCutawayRoofs: Boolean,
    val drawEnhancedWater: Boolean,
    val drawHdStonePolish: Boolean,
    val drawHdVignette: Boolean,
    val particleLevel: ParticleDetailLevel,
    val treeDetailLayers: Int,
    val cullMargin: Int,
    val spriteScaleMultiplier: Float,
    val strokeScale: Float,
    val useProceduralTileTextures: Boolean,
    val useDirectionalLighting: Boolean,
    val drawAmbientOcclusion: Boolean,
    val drawCastShadows: Boolean,
    val drawTwilightAtmosphere: Boolean,
    val drawPointLightPools: Boolean,
    val useHdPostProcessShader: Boolean
) {
    LOW(
        displayName = "Low",
        shortBadge = "LOW",
        summaryNote = "Max FPS & battery",
        capabilityTags = listOf("Simplified terrain", "Shadows off", "Minimal FX"),
        drawGroundClutter = false,
        drawWildflowers = false,
        drawShorelineDetails = false,
        drawContactShadows = false,
        lightingMode = LightingDetailMode.OFF,
        drawCutawayRoofs = false,
        drawEnhancedWater = false,
        drawHdStonePolish = false,
        drawHdVignette = false,
        particleLevel = ParticleDetailLevel.MINIMAL,
        treeDetailLayers = 1,
        cullMargin = 2,
        spriteScaleMultiplier = 0.90f,
        strokeScale = 0.85f,
        useProceduralTileTextures = false,
        useDirectionalLighting = false,
        drawAmbientOcclusion = false,
        drawCastShadows = false,
        drawTwilightAtmosphere = false,
        drawPointLightPools = false,
        useHdPostProcessShader = false
    ),
    MEDIUM(
        displayName = "Medium",
        shortBadge = "MED",
        summaryNote = "Balanced detail & battery",
        capabilityTags = listOf("Standard props", "Basic shadows", "Balanced lighting"),
        drawGroundClutter = false,
        drawWildflowers = true,
        drawShorelineDetails = false,
        drawContactShadows = true,
        lightingMode = LightingDetailMode.STATIC_HALO,
        drawCutawayRoofs = true,
        drawEnhancedWater = false,
        drawHdStonePolish = false,
        drawHdVignette = false,
        particleLevel = ParticleDetailLevel.MODERATE,
        treeDetailLayers = 2,
        cullMargin = 3,
        spriteScaleMultiplier = 1.0f,
        strokeScale = 1.0f,
        useProceduralTileTextures = false,
        useDirectionalLighting = false,
        drawAmbientOcclusion = false,
        drawCastShadows = false,
        drawTwilightAtmosphere = false,
        drawPointLightPools = false,
        useHdPostProcessShader = false
    ),
    HIGH(
        displayName = "High",
        shortBadge = "HIGH",
        summaryNote = "Pixel-mosaic tiles, twilight glow & shadows",
        capabilityTags = listOf("Pixel-mosaic tiles", "Directional shadows", "Twilight lights"),
        drawGroundClutter = true,
        drawWildflowers = true,
        drawShorelineDetails = true,
        drawContactShadows = true,
        lightingMode = LightingDetailMode.DYNAMIC_FLICKER,
        drawCutawayRoofs = true,
        drawEnhancedWater = false,
        drawHdStonePolish = false,
        drawHdVignette = false,
        particleLevel = ParticleDetailLevel.FULL,
        treeDetailLayers = 3,
        cullMargin = 4,
        spriteScaleMultiplier = 1.0f,
        strokeScale = 1.0f,
        useProceduralTileTextures = true,
        useDirectionalLighting = true,
        drawAmbientOcclusion = true,
        drawCastShadows = true,
        drawTwilightAtmosphere = true,
        drawPointLightPools = true,
        useHdPostProcessShader = false
    ),
    HD(
        displayName = "HD",
        shortBadge = "HD",
        summaryNote = "Maximum fidelity, bloom & twilight shader",
        capabilityTags = listOf("HD mosaic & water", "Multi-layer bloom", "Twilight shader"),
        drawGroundClutter = true,
        drawWildflowers = true,
        drawShorelineDetails = true,
        drawContactShadows = true,
        lightingMode = LightingDetailMode.HD_MULTI_LAYER,
        drawCutawayRoofs = true,
        drawEnhancedWater = true,
        drawHdStonePolish = true,
        drawHdVignette = true,
        particleLevel = ParticleDetailLevel.HD_ENHANCED,
        treeDetailLayers = 3,
        cullMargin = 4,
        spriteScaleMultiplier = 1.06f,
        strokeScale = 1.15f,
        useProceduralTileTextures = true,
        useDirectionalLighting = true,
        drawAmbientOcclusion = true,
        drawCastShadows = true,
        drawTwilightAtmosphere = true,
        drawPointLightPools = true,
        useHdPostProcessShader = true
    );

    companion object {
        val DEFAULT: GraphicsQuality = MEDIUM

        fun fromStorageName(raw: String?): GraphicsQuality {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class DisplaySettings(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.DEFAULT,
    val showMinimap: Boolean = true,
    val showFpsCounter: Boolean = false
)

enum class ActivePanel {
    NONE,
    EQUIPMENT,
    BACKPACK,
    SKILLS,
    DEPOT,
    SHOP,
    FURNACE,
    ANVIL,
    FLETCHING_BENCH,
    RUNECRAFTING_TABLE,
    LOOT_CORPSE,
    ORACLE,
    WORLD_MAP,
    SETTINGS
}

data class GameWorldState(
    val saveId: String = "",
    val playerName: String = "Aldric",
    val vocation: Vocation = Vocation.ADVENTURER,
    val tunicColorIndex: Int = 0,
    val hairColorIndex: Int = 0,
    val level: Int = 1,
    val xp: Long = 0L,
    val hp: Int = 150,
    val maxHp: Int = 150,
    val mp: Int = 55,
    val maxMp: Int = 55,
    val soul: Int = 100,
    val maxSoul: Int = 100,
    val maxCapOz: Double = BASE_MAX_CAP_OZ,
    val gold: Int = 120,
    val bankGold: Long = 0L,
    val regionId: String = DEFAULT_REGION_ID,
    val floor: Int = 0,
    val playerX: Int = 196,
    val playerY: Int = 192,
    val prevPlayerX: Int = 196,
    val prevPlayerY: Int = 192,
    val lastPlayerMoveMs: Long = 0L,
    val playerFacing: Direction = Direction.SOUTH,
    val walkPath: List<Pair<Int, Int>> = emptyList(),
    val poisonTicks: Int = 0,
    val fireTicks: Int = 0,
    val skills: PlayerSkills = PlayerSkills(),
    val equipment: EquippedGear = EquippedGear(),
    val backpack: List<ItemStack> = emptyList(),
    val toolBag: List<ItemStack> = emptyList(),
    val depot: List<ItemStack> = emptyList(),
    val monsters: List<MonsterEntity> = emptyList(),
    val pendingRespawns: List<PendingRespawn> = emptyList(),
    val oreNodes: Map<Triple<Int, Int, Int>, OreNodeState> = emptyMap(),
    val choppedTrees: Map<Triple<Int, Int, Int>, Int> = emptyMap(),
    val corpses: List<CorpseEntity> = emptyList(),
    val targetMonsterId: Int? = null,
    val autoFollowTarget: Boolean = false,
    val floatingTexts: List<FloatingText> = emptyList(),
    val projectiles: List<ProjectileEffect> = emptyList(),
    val statusLog: List<String> = listOf("Welcome to Rookvale! Train freely and visit The Oracle at Level 20."),
    val activePanel: ActivePanel = ActivePanel.NONE,
    val inspectingCorpseId: String? = null,
    val deathReport: DeathReport? = null,
    val showMinimap: Boolean = true,
    val tickCount: Long = 0L,
    val lastCombatTickMs: Long = 0L,
    val lastPlayerAttackMs: Long = 0L,
    val lastTreeChopMs: Long = 0L,
    val floorWarningBanner: FloorWarningBanner? = null,
    val inspectedPlayerId: String? = null,
    val inspectedPlayerUntilMs: Long = 0L,
    val outboundMonsterHits: List<LocalMonsterHitEvent> = emptyList(),
    val outboundMonsterAttacks: List<LocalMonsterAttackEvent> = emptyList(),
    val outboundPlayerEffects: List<LocalPlayerActionFxEvent> = emptyList(),
    val outboundSystemAlerts: List<String> = emptyList(),
    val questFlags: Map<String, Boolean> = emptyMap(),
    val boundAttackRuneId: String? = "magic_dart_rune",
    val boundHealRuneId: String? = "healing_rune",
    val customHotbarSlots: List<String?> = listOf(null, null)
) {
    fun toolBagWeightOz(): Double =
        toolBag.sumOf { ItemCatalog.get(it.itemId).weightOz * TOOL_BAG_WEIGHT_MULTIPLIER * it.count }

    fun usedCapOz(): Double {
        val bpWeight = backpack.sumOf { ItemCatalog.get(it.itemId).weightOz * it.count }
        val tbWeight = toolBagWeightOz()
        val eqWeight = equipment.totalWeightOz()
        val goldWeight = gold * GOLD_COIN_WEIGHT_OZ
        return bpWeight + tbWeight + eqWeight + goldWeight
    }

    fun remainingCapOz(): Double = (maxCapOz - usedCapOz()).coerceAtLeast(0.0)

    fun hasItem(itemId: String, minCount: Int = 1): Boolean = itemCount(itemId) >= minCount

    fun itemCount(itemId: String): Int {
        val inMain = backpack.filter { it.itemId == itemId }.sumOf { it.count }
        val inTools = toolBag.filter { it.itemId == itemId }.sumOf { it.count }
        return inMain + inTools
    }

    fun allCarriedItems(): List<ItemStack> = backpack + toolBag

    fun bestPickaxe(): ItemDefinition? {
        return (toolBag + backpack)
            .map { ItemCatalog.get(it.itemId) }
            .filter { it.category == ItemCategory.TOOL_PICKAXE }
            .maxByOrNull { it.pickaxeTier }
    }

    fun hasHatchet(): Boolean {
        val equippedHatchet = equipment.weaponId?.let { ItemCatalog.get(it).category == ItemCategory.TOOL_HATCHET } == true
        return equippedHatchet || (toolBag + backpack).any { ItemCatalog.get(it.itemId).category == ItemCategory.TOOL_HATCHET }
    }

    fun isMeleeCombatSetup(): Boolean {
        val weapon = equipment.weaponDef()
        return weapon == null || weapon.category == ItemCategory.WEAPON_MELEE
    }

    fun activeAmmoCount(): Int = equipment.ammoId?.let { itemCount(it) } ?: 0

    val magicLevel: Int get() = skills.magic.level
    val magicXp: Double get() = skills.magic.points
    fun magicXpToNext(): Double = skills.magic.requiredMagicXpForNext()

    fun resolvedHealRuneId(): String = boundHealRuneId?.takeIf {
        ItemCatalog.all[it]?.category == ItemCategory.RUNE_HEAL
    } ?: "healing_rune"

    fun resolvedAttackRuneId(): String? {
        val bound = boundAttackRuneId?.takeIf { ItemCatalog.all[it]?.category == ItemCategory.RUNE_ATTACK }
        if (bound != null && hasItem(bound)) return bound
        return bestAttackRuneId() ?: bound
    }

    fun bestAttackRuneId(): String? {
        val ordered = listOf("sd_rune", "fireball_rune", "hmm_rune", "magic_dart_rune")
        val usable = ordered.firstOrNull { id ->
            hasItem(id) && magicLevel >= ItemCatalog.get(id).requiredMagicLevel
        }
        return usable ?: ordered.firstOrNull { hasItem(it) }
    }

    fun isUsingTwoHandedWeapon(): Boolean = equipment.weaponDef()?.isTwoHanded == true

    fun hasActiveShield(): Boolean = equipment.shieldId != null && !isUsingTwoHandedWeapon()

    fun attackSkillMultiplier(skillLevel: Int): Double =
        1.0 + (skillLevel - 10).coerceAtLeast(0) * 0.06

    fun magicSkillMultiplier(): Double =
        1.0 + (skills.magic.level - 1).coerceAtLeast(0) * 0.06

    fun weaponOffVocationMultiplier(weapon: ItemDefinition?): Double {
        val pref = weapon?.preferredVocation ?: return 1.0
        return if (vocation == pref) 1.0 else 0.75
    }

    /**
     * Shielding acts as an active Dodge chance:
     * - 1H + Shield: full Shielding effectiveness + Shield DEF bonus (10%..60% melee dodge)
     * - 1H unarmed off-hand: moderate scaling without shield bonus (6%..35% melee dodge)
     * - 2H weapon (e.g. Bow, Arbalest, Claymore, Heavy Staff): halved Shielding scaling & -6% penalty (4%..25% melee dodge)
     * Ranged/breath dodge is half of melee dodge.
     */
    fun shieldDodgeChance(isRanged: Boolean = false): Double {
        val shieldLvlAbove10 = (skills.shielding.level - 10).coerceAtLeast(0)
        val meleeDodge = when {
            hasActiveShield() -> {
                val shieldDef = equipment.shieldDefense()
                (0.10 + shieldLvlAbove10 * 0.025 + shieldDef * 0.004).coerceIn(0.10, 0.60)
            }
            isUsingTwoHandedWeapon() -> {
                ((0.08 + shieldLvlAbove10 * 0.0125) - 0.06).coerceIn(0.04, 0.25)
            }
            else -> {
                (0.08 + shieldLvlAbove10 * 0.016).coerceIn(0.06, 0.35)
            }
        }
        return if (isRanged) meleeDodge * 0.5 else meleeDodge
    }

    fun xpForLevel(lvl: Int): Long {
        if (lvl <= 1) return 0L
        val n = (lvl - 1).toLong()
        return (50L * n * n + 50L * n)
    }

    fun nextLevelXp(): Long = xpForLevel(level + 1)

    fun levelProgressFraction(): Float {
        val cur = xpForLevel(level)
        val next = nextLevelXp()
        val span = (next - cur).coerceAtLeast(1L)
        return ((xp - cur).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Canonical Rookvale carrying capacity progression:
         * - Levels 1..20 (Adventurer): 1500.0 oz base + 125.0 oz per level gained (Lvl 20 = 3875.0 oz).
         * - Level 20 Oracle Job Advance: proportionally scaled weight surge (+1250 Knight / +900 Paladin / +600 Mage)
         *   plus boosted post-20 per-level growth (+200 Knight / +160 Paladin / +125 Mage).
         */
        fun maxCapForLevel(level: Int, vocation: Vocation = Vocation.ADVENTURER): Double {
            val safeLevel = level.coerceAtLeast(1)
            if (vocation == Vocation.ADVENTURER) {
                return BASE_MAX_CAP_OZ + (safeLevel - 1) * ADVENTURER_CAP_PER_LEVEL_OZ
            }
            val prePromotionLevels = (minOf(safeLevel, ORACLE_MIN_LEVEL) - 1).coerceAtLeast(0)
            val postPromotionLevels = (safeLevel - ORACLE_MIN_LEVEL).coerceAtLeast(0)
            return BASE_MAX_CAP_OZ +
                prePromotionLevels * ADVENTURER_CAP_PER_LEVEL_OZ +
                vocation.promotionCapBonus +
                postPromotionLevels * vocation.capPerLevel
        }
    }
}
