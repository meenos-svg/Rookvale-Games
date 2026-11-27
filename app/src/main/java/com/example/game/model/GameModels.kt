package com.example.game.model

import com.example.game.multiplayer.LocalMonsterAttackEvent
import com.example.game.multiplayer.LocalMonsterHitEvent
import com.example.game.multiplayer.LocalPlayerActionFxEvent
import com.example.game.world.WeatherType
import com.squareup.moshi.JsonClass

const val CURRENT_MAP_VERSION = 6

/** Map version that rebuilt Deep Delve floors -1..-4 as 96x96 layouts (saves inside them move to the entrance). */
const val DEEP_DELVE_REBUILD_MAP_VERSION = 5

/** Map v6 rebuilt Quarry Mines (-11), North Mountain Cavern (-12) and Crag Deep Hollow (-14) at 96x96. */
const val HEARTLAND_SITE_REBUILD_MAP_VERSION = 6
const val V1_SURFACE_OFFSET = 32
const val V2_TO_V3_OFFSET_X = 149
const val V2_TO_V3_OFFSET_Y = 146
const val V3_TO_V4_OFFSET_X = 1200
const val V3_TO_V4_OFFSET_Y = 400
const val DEFAULT_REGION_ID = "rookvale_valley"
const val WORLD_CHUNK_SIZE = 32
const val OVERLAND_REGION_SIZE = 400
const val WORLD_REGION_COLS = 4
const val WORLD_REGION_ROWS = 3
const val WORLD_OVERLAND_WIDTH = OVERLAND_REGION_SIZE * WORLD_REGION_COLS
const val WORLD_OVERLAND_HEIGHT = OVERLAND_REGION_SIZE * WORLD_REGION_ROWS

val CANONICAL_OVERLAND_REGION_IDS: Set<String> = setOf(
    "caldera_peaks",
    "frostbay_straits",
    "blightwood_reach",
    "frostcrest_highlands",
    "writhing_marshes",
    "alder_sea_plains",
    "spirewood_west",
    "rookvale_valley",
    "sunspire_desert",
    "maelstrom_jungle_west",
    "emerald_jungle_east",
    "ember_isles"
)

/**
 * Normalizes any legacy, blank, or mixed-case region identifier so online clients
 * and saved characters always agree on canonical region IDs (defaulting to "rookvale_valley").
 */
fun normalizeRegionId(raw: String?): String {
    val cleaned = raw?.trim()?.lowercase()?.replace(' ', '_')
        ?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .orEmpty()
    return when {
        cleaned in setOf("", "rookvale", "rookvale_city", "rookvale_overland", "overland", "default", "main", "valley") -> DEFAULT_REGION_ID
        cleaned in CANONICAL_OVERLAND_REGION_IDS -> cleaned
        else -> cleaned
    }
}

/**
 * Returns true when two players on the same floor should see each other across region borders:
 * always true for matching normalized region IDs, and true on Floor 0 whenever both players
 * occupy any of the 12 connected canonical overland regions of the 1600x1200 continent world.
 */
fun isRegionCompatible(floor: Int, localRegionId: String, remoteRegionId: String): Boolean {
    val a = normalizeRegionId(localRegionId)
    val b = normalizeRegionId(remoteRegionId)
    if (a == b) return true
    return floor == 0 && a in CANONICAL_OVERLAND_REGION_IDS && b in CANONICAL_OVERLAND_REGION_IDS
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
const val LOOT_OWNER_PROTECTION_MS = 5_000L
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
    RING("Ring"),
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
    RING,
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
    FISHING_BAIT,
    RAW_ORE,
    METAL_INGOT,
    CRAFT_MATERIAL,
    LOOT_VALUABLE,
    ADMIN_SCROLL
}

enum class AdminEquipmentClassification(val label: String, val subtitle: String) {
    SWORD("Sword", "Cinderite Greatsword + Full Tier 5 Set"),
    AXE("Axe", "Cinderite Greataxe + Full Tier 5 Set"),
    CLUB("Club", "Cinderite Worldbreaker + Full Tier 5 Set"),
    DISTANCE("Distance", "Cinderite Ashen Bow + Shafts & Tier 5 Set"),
    MAGIC("Magic", "Cinderite Soulspire + Top Runes & Tier 5 Set"),
    ALL("All Classes", "Every Tier 5 Cinderite Weapon, Armor & Jewelry")
}

enum class ProcTriggerType(val label: String) {
    ON_ATTACK("On Attack"),
    ON_DEFEND("On Defend")
}

enum class ProcVisualStyle {
    SHOCKWAVE_RING,
    ARCANE_NOVA,
    CHAIN_ARC,
    METEOR_BURST,
    THORNS_PULSE,
    SIPHON_WAVE,
    ACID_SPLASH,
    FROST_SHATTER
}

enum class ProcBonusEffect(val label: String) {
    NONE("Pure Damage"),
    IGNITE_BURN("Ignites Burn DoT"),
    ARMOR_SHATTER("Shatters Armor"),
    STAGGER_SLOW("Staggers & Slows"),
    MANA_SIPHON("Restores Mana"),
    AEGIS_WARD("Restores Guard HP"),
    LIFE_SIPHON("Lifesteals 35% HP"),
    VENOM_DOT("Inflicts Venom DoT"),
    FROST_SHATTER_SLOW("Frost Shatter & Slow")
}

enum class BiomeEquipmentClass(
    val displayName: String,
    val shortBadge: String,
    val biomeName: String,
    val minLevel: Int,
    val accentColorHex: Long
) {
    NONE("Standard", "STD", "Rookvale", 1, 0xFFD9A448L),
    HEARTLAND("Heartland Forged", "HEARTLAND", "Rookvale Heartland", 1, 0xFFFFB84DL),
    MIRE_WOVEN("Mire-Woven / Bog-Steel", "MIRE-WOVEN", "Whispering Marsh", 20, 0xFF66E688L),
    VESPERITE_SUNSCORCH("Vesperite / Sunscorch", "VESPERITE", "Sunscorch & Grimmaw Quarry", 28, 0xFFB692FFL),
    RIME_PLATE("Rime-Plate / Frostpeak", "RIME-PLATE", "Frostpeak Highlands", 36, 0xFF6BE8FFL),
    CINDERITE_ASHEN("Cinderite / Ashen", "CINDERITE", "Ashen Caldera & Gorek Labyrinth", 45, 0xFFFF6A2BL)
}

enum class CraftingStationType(
    val displayName: String,
    val shortName: String,
    val biomeLabel: String,
    val settlementLabel: String,
    val gearClassificationLabel: String,
    val accentColorHex: Long
) {
    ROOKVALE_TOWN_FORGE(
        displayName = "Rookvale Town Forge",
        shortName = "Town Forge",
        biomeLabel = "Rookvale Heartland (Lvl 1-20)",
        settlementLabel = "Rookvale Capital",
        gearClassificationLabel = "Heartland & Starter",
        accentColorHex = 0xFFFFB84DL
    ),
    STILT_TOWN_MIRE_BENCH(
        displayName = "Stilt-Town Mire Bench",
        shortName = "Mire Bench",
        biomeLabel = "Whispering Marsh (Lvl 20-32)",
        settlementLabel = "Mirewatch Stilt Citadel",
        gearClassificationLabel = "Mire-Woven / Bog-Steel",
        accentColorHex = 0xFF66E688L
    ),
    SUNSCORCH_OASIS_SMELTERY(
        displayName = "Sunscorch Oasis Bazaar & Quarry Smeltery",
        shortName = "Oasis Smeltery",
        biomeLabel = "Sunscorch Badlands & Grimmaw Quarry (Lvl 30-45)",
        settlementLabel = "Sunspire Oasis Empire",
        gearClassificationLabel = "Vesperite / Sunscorch",
        accentColorHex = 0xFFB692FFL
    ),
    FROSTPEAK_GREAT_HEARTH(
        displayName = "Frostpeak Citadel Great Hearth",
        shortName = "Great Hearth",
        biomeLabel = "Frostpeak Highlands (Lvl 42-58)",
        settlementLabel = "Frostpeak Granite Citadel",
        gearClassificationLabel = "Rime-Plate / Frostpeak",
        accentColorHex = 0xFF6BE8FFL
    ),
    ASHEN_OBSIDIAN_FOUNDRY(
        displayName = "Ashen Caldera Obsidian Foundry",
        shortName = "Obsidian Foundry",
        biomeLabel = "Ashen Caldera & Gorek Labyrinth (Lvl 55-75+)",
        settlementLabel = "Ashen Bastion Empire",
        gearClassificationLabel = "Cinderite / Ashen",
        accentColorHex = 0xFFFF6A2BL
    );

    fun stallTileType(): TileType = when (this) {
        ROOKVALE_TOWN_FORGE -> TileType.BIOME_STALL_HEARTLAND
        STILT_TOWN_MIRE_BENCH -> TileType.BIOME_STALL_MARSH
        SUNSCORCH_OASIS_SMELTERY -> TileType.BIOME_STALL_BADLANDS
        FROSTPEAK_GREAT_HEARTH -> TileType.BIOME_STALL_FROSTPEAK
        ASHEN_OBSIDIAN_FOUNDRY -> TileType.BIOME_STALL_ASHEN
    }
}

/**
 * Data-driven automatic percentage-chance specialized equipment ability
 * supporting single-target, multi-monster AOE, chain-arc, and defensive bursts.
 */
data class EquipmentProc(
    val name: String,
    val triggerChancePercent: Int,
    val triggerType: ProcTriggerType,
    val aoeRadiusTiles: Float,
    val maxTargets: Int,
    val damageMultiplier: Float,
    val elementColor: Long,
    val bonusEffect: ProcBonusEffect = ProcBonusEffect.NONE,
    val visualStyle: ProcVisualStyle = ProcVisualStyle.SHOCKWAVE_RING,
    val summary: String = ""
)

data class ProcAoeEffect(
    val id: Long,
    val floor: Int,
    val centerX: Float,
    val centerY: Float,
    val aoeRadiusTiles: Float,
    val elementColor: Long,
    val visualStyle: ProcVisualStyle,
    val chainTargets: List<Pair<Float, Float>> = emptyList(),
    val createdAtMs: Long,
    val durationMs: Long = 520L
)

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
    val equipmentTier: Int = 0,
    val requiredLevel: Int = 1,
    val requiredSkill: Pair<SkillType, Int>? = null,
    val requiredSkillType: SkillType? = requiredSkill?.first,
    val requiredSkillLevel: Int = requiredSkill?.second ?: 1,
    val requiredMagicLevel: Int = 0,
    val aoeRadius: Int = 0,
    val runeTier: Int = 0,
    val requiredAmmoKind: AmmoKind? = null,
    val ammoKind: AmmoKind? = null,
    val pickaxeTier: Int = 0,
    val requiredMiningLevel: Int = 0,
    val requiredFishingLevel: Int = 0,
    val fishingTier: Int = 0,
    val baitTargetFishId: String? = null,
    val restoreAmount: Int = 0,
    val manaCost: Int = 0,
    val stackable: Boolean = false,
    val isTwoHanded: Boolean = false,
    val preferredVocation: Vocation? = null,
    val biomeClass: BiomeEquipmentClass = BiomeEquipmentClass.NONE,
    val poisonResistPercent: Int = 0,
    val proc: EquipmentProc? = null,
    val procChanceBonusPct: Int = 0,
    val procPowerBonusPct: Int = 0,
    val procExtraTargets: Int = 0,
    val bonusMaxHp: Int = 0,
    val adminOnly: Boolean = false,
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
            ItemCategory.RING -> EquipmentSlot.RING
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

    fun hasJewelryEnhancements(): Boolean =
        procChanceBonusPct > 0 || procPowerBonusPct > 0 || procExtraTargets > 0 || bonusMaxHp > 0
}

data class FishingTierSpec(
    val tier: Int,
    val baitId: String,
    val fishId: String,
    val requiredFishingLevel: Int,
    val baseCatchChance: Double,
    val catchXpPoints: Double,
    val missXpPoints: Double
)

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
    val ringId: String? = null,
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
        EquipmentSlot.RING -> ringId
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
        EquipmentSlot.RING -> copy(ringId = itemId)
        EquipmentSlot.LEGS -> copy(legsId = itemId)
        EquipmentSlot.BOOTS -> copy(bootsId = itemId)
        EquipmentSlot.AMMO -> copy(ammoId = itemId)
    }

    // Ammo stays stacked inside backpack; ammoId points to the selected quiver stack
    fun nonAmmoIds(): List<String> = listOfNotNull(helmetId, amuletId, armorId, weaponId, shieldId, ringId, legsId, bootsId)
    fun getAllEquippedIds(): List<String> = listOfNotNull(helmetId, amuletId, armorId, weaponId, shieldId, ringId, legsId, bootsId, ammoId)

    fun totalArmor(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).armor }
    fun totalDefense(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).defense }
    fun shieldDefense(): Int {
        if (weaponDef()?.isTwoHanded == true) return 0
        return shieldId?.let { ItemCatalog.get(it).defense } ?: 0
    }
    fun weaponDef(): ItemDefinition? = weaponId?.let { ItemCatalog.get(it) }
    fun shieldDef(): ItemDefinition? = shieldId?.let { ItemCatalog.get(it) }
    fun armorDef(): ItemDefinition? = armorId?.let { ItemCatalog.get(it) }
    fun amuletDef(): ItemDefinition? = amuletId?.let { ItemCatalog.get(it) }
    fun ringDef(): ItemDefinition? = ringId?.let { ItemCatalog.get(it) }
    fun ammoDef(): ItemDefinition? = ammoId?.let { ItemCatalog.get(it) }
    fun totalWeightOz(): Double = nonAmmoIds().sumOf { ItemCatalog.get(it).weightOz }

    fun totalProcChanceBonusPct(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).procChanceBonusPct }
    fun totalProcPowerBonusPct(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).procPowerBonusPct }
    fun totalProcExtraTargets(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).procExtraTargets }
    fun totalBonusMaxHp(): Int = nonAmmoIds().sumOf { ItemCatalog.get(it).bonusMaxHp }
    fun totalPoisonResistPercent(): Int =
        nonAmmoIds().sumOf { ItemCatalog.get(it).poisonResistPercent }.coerceIn(0, 75)
    fun totalAccessoryAttackBonus(): Int =
        listOfNotNull(amuletId, ringId).sumOf { ItemCatalog.get(it).attack }
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
    val miningXpPoints: Double,
    val requiredMiningLevel: Int = 1
) {
    COPPER("Copper Vein", "copper_ore", 1, "Bronze Pickaxe", 4, 24, 4.0, requiredMiningLevel = 1),
    TIN("Tin Vein", "tin_ore", 1, "Bronze Pickaxe", 4, 26, 4.5, requiredMiningLevel = 1),
    BOG_IRON("Bog Iron Vein", "bog_iron_ore", 2, "Bog Iron Pickaxe", 4, 32, 6.5, requiredMiningLevel = 10),
    MIRE_SILVER("Mire-Silver Vein", "mire_silver_ore", 3, "Tempered Steel Pickaxe", 4, 36, 9.0, requiredMiningLevel = 20),
    PEAT_COAL("Peat-Coal Seam", "peat_coal_ore", 3, "Tempered Steel Pickaxe", 5, 30, 8.5, requiredMiningLevel = 20),
    VESPERITE("Vesperite Vein", "vesperite_ore", 4, "Vesperite-Tipped Pickaxe", 4, 42, 12.0, requiredMiningLevel = 30),
    GLACIER_QUARTZ("Glacier-Quartz Vein", "glacier_quartz_ore", 5, "Glacier-Forged Pickaxe", 4, 48, 15.5, requiredMiningLevel = 42),
    FROST_IRON("Frost-Iron Vein", "frost_iron_ore", 5, "Glacier-Forged Pickaxe", 4, 46, 15.0, requiredMiningLevel = 42),
    CINDERITE("Cinderite Vein", "cinderite_ore", 6, "Cinderite-Core Pickaxe", 3, 55, 19.5, requiredMiningLevel = 52)
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
    ORE_COPPER(false),
    ORE_TIN(false),
    ORE_BOG_IRON(false),
    ORE_MIRE_SILVER(false),
    ORE_PEAT_COAL(false),
    ORE_VESPERITE(false),
    ORE_GLACIER_QUARTZ(false),
    ORE_FROST_IRON(false),
    ORE_CINDERITE(false),
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
    IRON_BAR_GATE(true),
    SAND(true),
    MARSH(true),
    SNOW(true),
    JUNGLE_FLOOR(true),
    VOLCANIC_ASH(true),
    BLIGHT_SOIL(true),
    DEEP_WATER(false),
    SANDSTONE_WALL(false, true),
    GRANITE_WALL(false, true),
    OBSIDIAN_WALL(false, true),
    WAR_BANNER(false),
    CAMPFIRE_BRAZIER(false),
    BIOME_STALL_HEARTLAND(false),
    BIOME_STALL_MARSH(false),
    BIOME_STALL_BADLANDS(false),
    BIOME_STALL_FROSTPEAK(false),
    BIOME_STALL_ASHEN(false);

    fun oreVeinKind(): OreVeinKind? = when (this) {
        ORE_COPPER -> OreVeinKind.COPPER
        ORE_TIN -> OreVeinKind.TIN
        ORE_BOG_IRON -> OreVeinKind.BOG_IRON
        ORE_MIRE_SILVER -> OreVeinKind.MIRE_SILVER
        ORE_PEAT_COAL -> OreVeinKind.PEAT_COAL
        ORE_VESPERITE -> OreVeinKind.VESPERITE
        ORE_GLACIER_QUARTZ -> OreVeinKind.GLACIER_QUARTZ
        ORE_FROST_IRON -> OreVeinKind.FROST_IRON
        ORE_CINDERITE -> OreVeinKind.CINDERITE
        else -> null
    }

    fun biomeCraftingStation(): CraftingStationType? = when (this) {
        BIOME_STALL_HEARTLAND -> CraftingStationType.ROOKVALE_TOWN_FORGE
        BIOME_STALL_MARSH -> CraftingStationType.STILT_TOWN_MIRE_BENCH
        BIOME_STALL_BADLANDS -> CraftingStationType.SUNSCORCH_OASIS_SMELTERY
        BIOME_STALL_FROSTPEAK -> CraftingStationType.FROSTPEAK_GREAT_HEARTH
        BIOME_STALL_ASHEN -> CraftingStationType.ASHEN_OBSIDIAN_FOUNDRY
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
    FLAME_BURST,
    QUARRY_QUAKE,
    LABYRINTH_PYRE
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
    OUTLAW_BANDIT(
        displayName = "Outlaw Bandit", tier = 2, threatLevel = 7, maxHp = 132, armor = 5,
        xpReward = 128, minDmg = 12, maxDmg = 24, goldMin = 26, goldMax = 58, moveIntervalTicks = 1
    ),
    BRIAR_CRYPT_CRAWLER(
        displayName = "Briar Crypt Crawler", tier = 2, threatLevel = 6, maxHp = 116, armor = 4,
        xpReward = 110, minDmg = 10, maxDmg = 21, goldMin = 18, goldMax = 46, moveIntervalTicks = 1,
        inflictsPoison = true
    ),
    CATACOMB_BONE_ARCHER(
        displayName = "Catacomb Bone Archer", tier = 2, threatLevel = 6, maxHp = 104, armor = 4,
        xpReward = 108, minDmg = 10, maxDmg = 21, goldMin = 20, goldMax = 46, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.ARROW
    ),
    TUNNEL_KOBOLD_SAPPER(
        displayName = "Tunnel Kobold Sapper", tier = 2, threatLevel = 5, maxHp = 96, armor = 3,
        xpReward = 96, minDmg = 9, maxDmg = 19, goldMin = 18, goldMax = 40, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.THROWN_STONE
    ),
    QUARRY_MOLE_BRUTE(
        displayName = "Quarry Mole Brute", tier = 2, threatLevel = 6, maxHp = 134, armor = 6,
        xpReward = 118, minDmg = 11, maxDmg = 23, goldMin = 22, goldMax = 50, moveIntervalTicks = 2
    ),
    CRAG_HARPY(
        displayName = "Crag Harpy", tier = 2, threatLevel = 6, maxHp = 108, armor = 4,
        xpReward = 112, minDmg = 11, maxDmg = 22, goldMin = 20, goldMax = 48, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.THROWN_STONE
    ),
    SHALE_GOLEM(
        displayName = "Shale Golem", tier = 2, threatLevel = 7, maxHp = 142, armor = 7,
        xpReward = 132, minDmg = 12, maxDmg = 24, goldMin = 24, goldMax = 54, moveIntervalTicks = 2
    ),
    THORN_BRAMBLE_STALKER(
        displayName = "Thorn Bramble Stalker", tier = 2, threatLevel = 7, maxHp = 128, armor = 5,
        xpReward = 126, minDmg = 12, maxDmg = 24, goldMin = 22, goldMax = 52, moveIntervalTicks = 1,
        inflictsPoison = true
    ),
    SPORELING_LOBBER(
        displayName = "Sporeling Lobber", tier = 2, threatLevel = 7, maxHp = 112, armor = 4,
        xpReward = 122, minDmg = 11, maxDmg = 23, goldMin = 22, goldMax = 50, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.VENOM_GLOB,
        inflictsPoison = true
    ),
    BROODMOTHER_ARACHNE(
        displayName = "Broodmother Arachne", tier = 2, threatLevel = 8, maxHp = 460, armor = 9,
        xpReward = 480, minDmg = 18, maxDmg = 34, goldMin = 110, goldMax = 220, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.VENOM_GLOB,
        inflictsPoison = true, isBoss = true, summonTypeName = "BRIAR_CRYPT_CRAWLER", maxSummons = 1,
        specialKind = BossSpecialKind.VENOM_NOVA, bossTitle = "Matriarch of the Briar Brood Sanctum"
    ),
    FOREMAN_IRONJAW(
        displayName = "Foreman Ironjaw", tier = 2, threatLevel = 7, maxHp = 420, armor = 10,
        xpReward = 440, minDmg = 17, maxDmg = 31, goldMin = 100, goldMax = 200, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 3, rangedProjectile = ProjectileKind.THROWN_STONE,
        isBoss = true, summonTypeName = "TUNNEL_KOBOLD_SAPPER", maxSummons = 1,
        specialKind = BossSpecialKind.QUARRY_QUAKE, bossTitle = "Taskmaster of the Deep Copper Vein"
    ),
    SKREE_RIDGE_MATRIARCH(
        displayName = "Skree, Ridge Matriarch", tier = 2, threatLevel = 8, maxHp = 450, armor = 9,
        xpReward = 470, minDmg = 18, maxDmg = 33, goldMin = 110, goldMax = 215, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.THROWN_STONE,
        isBoss = true, summonTypeName = "CRAG_HARPY", maxSummons = 1,
        specialKind = BossSpecialKind.VENOM_NOVA, bossTitle = "Sovereign of the Crag Roost"
    ),
    GNARLROOT_THE_BLIGHTED(
        displayName = "Gnarlroot the Blighted", tier = 2, threatLevel = 9, maxHp = 510, armor = 11,
        xpReward = 540, minDmg = 20, maxDmg = 36, goldMin = 125, goldMax = 245, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.VENOM_GLOB,
        inflictsPoison = true, isBoss = true, summonTypeName = "SPORELING_LOBBER", maxSummons = 1,
        specialKind = BossSpecialKind.VENOM_NOVA, bossTitle = "Ancient Terror of the Heart-Root Abyss"
    ),

    // --- TIER 3: Heartland Outlaw Frontier, Watchwood, Mire & Ziggurat Dungeons ---
    ORC_WARRIOR(
        displayName = "Orc Warrior", tier = 3, threatLevel = 8, maxHp = 245, armor = 11,
        xpReward = 225, minDmg = 20, maxDmg = 36, goldMin = 40, goldMax = 90, moveIntervalTicks = 1
    ),
    OUTLAW_ROGUE(
        displayName = "Outlaw Rogue", tier = 3, threatLevel = 9, maxHp = 228, armor = 8,
        xpReward = 240, minDmg = 19, maxDmg = 36, goldMin = 48, goldMax = 104, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.ARROW
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
    VAULT_SENTINEL(
        displayName = "Vault Sentinel", tier = 3, threatLevel = 10, maxHp = 268, armor = 12,
        xpReward = 265, minDmg = 21, maxDmg = 38, goldMin = 50, goldMax = 110, moveIntervalTicks = 2
    ),
    HOLLOW_CROSSBOWMAN(
        displayName = "Hollow Crossbowman", tier = 3, threatLevel = 10, maxHp = 222, armor = 8,
        xpReward = 250, minDmg = 20, maxDmg = 37, goldMin = 48, goldMax = 106, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.ARROW
    ),
    CINDER_IMP(
        displayName = "Cinder Imp", tier = 3, threatLevel = 10, maxHp = 218, armor = 7,
        xpReward = 245, minDmg = 20, maxDmg = 38, goldMin = 48, goldMax = 108, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 3, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true
    ),
    WATCHWOOD_DREAD_KNIGHT(
        displayName = "Watchwood Dread Knight", tier = 3, threatLevel = 11, maxHp = 282, armor = 12,
        xpReward = 290, minDmg = 23, maxDmg = 42, goldMin = 56, goldMax = 122, moveIntervalTicks = 1
    ),
    MIRE_LURKER(
        displayName = "Mire Lurker", tier = 3, threatLevel = 11, maxHp = 258, armor = 9,
        xpReward = 270, minDmg = 22, maxDmg = 40, goldMin = 54, goldMax = 118, moveIntervalTicks = 1,
        inflictsPoison = true
    ),
    BOG_LEECH_WEAVER(
        displayName = "Bog Leech-Weaver", tier = 3, threatLevel = 11, maxHp = 232, armor = 8,
        xpReward = 268, minDmg = 21, maxDmg = 39, goldMin = 52, goldMax = 116, moveIntervalTicks = 1,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.VENOM_GLOB,
        inflictsPoison = true
    ),
    TEMPLE_SERPENT_GUARD(
        displayName = "Temple Serpent Guard", tier = 3, threatLevel = 12, maxHp = 275, armor = 11,
        xpReward = 295, minDmg = 24, maxDmg = 43, goldMin = 60, goldMax = 130, moveIntervalTicks = 1,
        inflictsPoison = true
    ),
    SUN_PRIEST_ACOLYTE(
        displayName = "Sun-Priest Acolyte", tier = 3, threatLevel = 12, maxHp = 240, armor = 8,
        xpReward = 285, minDmg = 23, maxDmg = 42, goldMin = 58, goldMax = 128, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true
    ),
    WARDEN_STONEHELM(
        displayName = "Warden Stonehelm", tier = 3, threatLevel = 11, maxHp = 640, armor = 15,
        xpReward = 720, minDmg = 26, maxDmg = 48, goldMin = 165, goldMax = 310, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 3, rangedProjectile = ProjectileKind.THROWN_STONE,
        isBoss = true, summonTypeName = "VAULT_SENTINEL", maxSummons = 1,
        specialKind = BossSpecialKind.QUARRY_QUAKE, bossTitle = "Keeper of the Sealed Granite Vault"
    ),
    IGNIS_LORD_OF_THE_PIT(
        displayName = "Ignis, Lord of the Pit", tier = 3, threatLevel = 12, maxHp = 690, armor = 14,
        xpReward = 780, minDmg = 28, maxDmg = 52, goldMin = 180, goldMax = 340, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true, isBoss = true, summonTypeName = "CINDER_IMP", maxSummons = 1,
        specialKind = BossSpecialKind.FLAME_BURST, bossTitle = "Warden of the Molten Pit Throne"
    ),
    MIREMAW_THE_DROWNED(
        displayName = "Miremaw the Drowned", tier = 3, threatLevel = 12, maxHp = 740, armor = 14,
        xpReward = 840, minDmg = 29, maxDmg = 54, goldMin = 195, goldMax = 365, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.VENOM_GLOB,
        inflictsPoison = true, isBoss = true, summonTypeName = "MIRE_LURKER", maxSummons = 1,
        specialKind = BossSpecialKind.VENOM_NOVA, bossTitle = "Leviathan of the Sunken Sluice Depths"
    ),

    // --- TIER 4: Deep Delve Floors -3/-4, Ziggurat Boss & Blightwood Barrow Catacombs ---
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
    BARROW_WIGHT(
        displayName = "Barrow Wight", tier = 4, threatLevel = 14, maxHp = 430, armor = 15,
        xpReward = 470, minDmg = 32, maxDmg = 58, goldMin = 100, goldMax = 205, moveIntervalTicks = 1
    ),
    FROST_BANSHEE(
        displayName = "Frost Banshee", tier = 4, threatLevel = 15, maxHp = 395, armor = 12,
        xpReward = 490, minDmg = 31, maxDmg = 57, goldMin = 108, goldMax = 215, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.NECRO_BOLT
    ),
    XOLATL_THE_SUN_FANG(
        displayName = "Xolatl, the Sun-Fang", tier = 4, threatLevel = 14, maxHp = 880, armor = 16,
        xpReward = 1120, minDmg = 38, maxDmg = 68, goldMin = 240, goldMax = 430, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true, isBoss = true, summonTypeName = "TEMPLE_SERPENT_GUARD", maxSummons = 2,
        specialKind = BossSpecialKind.FLAME_BURST, bossTitle = "Solar Sovereign of the Ziggurat Altar"
    ),
    LORD_MALGRATH_THE_UNDYING(
        displayName = "Lord Malgrath the Undying", tier = 4, threatLevel = 16, maxHp = 1040, armor = 18,
        xpReward = 1380, minDmg = 44, maxDmg = 80, goldMin = 290, goldMax = 520, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.NECRO_BOLT,
        isBoss = true, summonTypeName = "BARROW_WIGHT", maxSummons = 2,
        specialKind = BossSpecialKind.LABYRINTH_PYRE, bossTitle = "Sovereign of the Blightwood Barrow"
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
    ),

    // --- TIER 5: Grimmaw Quarry, Ashen Caldera Foundry & Ember Sanctum ---
    GRIMMAW_QUARRYMAN(
        displayName = "Grimmaw Quarryman", tier = 5, threatLevel = 16, maxHp = 520, armor = 19,
        xpReward = 580, minDmg = 36, maxDmg = 64, goldMin = 120, goldMax = 240, moveIntervalTicks = 2
    ),
    GRIMMAW_ROCK_HURLER(
        displayName = "Grimmaw Rock-Hurler", tier = 5, threatLevel = 17, maxHp = 490, armor = 17,
        xpReward = 640, minDmg = 38, maxDmg = 68, goldMin = 135, goldMax = 265, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.THROWN_STONE
    ),
    GRIMMAW_CRUSHER(
        displayName = "Grimmaw Crusher", tier = 5, threatLevel = 19, maxHp = 640, armor = 22,
        xpReward = 780, minDmg = 44, maxDmg = 78, goldMin = 165, goldMax = 320, moveIntervalTicks = 2
    ),
    SLAG_AUTOMATON(
        displayName = "Slag Automaton", tier = 5, threatLevel = 18, maxHp = 610, armor = 22,
        xpReward = 720, minDmg = 42, maxDmg = 74, goldMin = 155, goldMax = 305, moveIntervalTicks = 2,
        inflictsFire = true
    ),
    ASH_PYROMANCER(
        displayName = "Ash Pyromancer", tier = 5, threatLevel = 18, maxHp = 515, armor = 16,
        xpReward = 690, minDmg = 40, maxDmg = 72, goldMin = 148, goldMax = 290, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true
    ),
    EMBER_DRAKE(
        displayName = "Ember Drake", tier = 5, threatLevel = 19, maxHp = 565, armor = 18,
        xpReward = 740, minDmg = 43, maxDmg = 76, goldMin = 160, goldMax = 315, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.FIRE_BREATH,
        inflictsFire = true
    ),
    OBSIDIAN_CULTIST(
        displayName = "Obsidian Cultist", tier = 5, threatLevel = 19, maxHp = 505, armor = 17,
        xpReward = 710, minDmg = 41, maxDmg = 74, goldMin = 152, goldMax = 298, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.NECRO_BOLT,
        inflictsFire = true
    ),
    VULKARIS_THE_FORGE_TITAN(
        displayName = "Vulkaris the Forge Titan", tier = 5, threatLevel = 22, maxHp = 1360, armor = 23,
        xpReward = 1980, minDmg = 52, maxDmg = 92, goldMin = 395, goldMax = 720, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true, isBoss = true, summonTypeName = "SLAG_AUTOMATON", maxSummons = 2,
        specialKind = BossSpecialKind.QUARRY_QUAKE, bossTitle = "Molten Colossus of the Foundry Crucible"
    ),
    IGNIVAR_ASH_WING_WYRM(
        displayName = "Ignivar, Ash-Wing Wyrm", tier = 5, threatLevel = 23, maxHp = 1400, armor = 23,
        xpReward = 2050, minDmg = 54, maxDmg = 95, goldMin = 415, goldMax = 750, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIRE_BREATH,
        inflictsFire = true, isBoss = true, summonTypeName = "EMBER_DRAKE", maxSummons = 2,
        specialKind = BossSpecialKind.FLAME_BURST, bossTitle = "Sovereign of the Caldera Dragon Throne"
    ),
    KRAGOR_THE_STONE_EYED(
        displayName = "Kragor the Stone-Eyed", tier = 5, threatLevel = 23, maxHp = 1420, armor = 24,
        xpReward = 2100, minDmg = 54, maxDmg = 96, goldMin = 420, goldMax = 760, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.THROWN_STONE,
        isBoss = true, summonTypeName = "GRIMMAW_QUARRYMAN", maxSummons = 2,
        specialKind = BossSpecialKind.QUARRY_QUAKE, bossTitle = "Colossus of the Grimmaw Quarry"
    ),

    // --- TIER 6: SE Frontier Gorek Badlands War-Camp & Floor -6 The Gorek Labyrinth (Pinnacle Endgame) ---
    GOREK_RAIDER(
        displayName = "Gorek Raider", tier = 6, threatLevel = 20, maxHp = 660, armor = 22,
        xpReward = 840, minDmg = 46, maxDmg = 82, goldMin = 180, goldMax = 350, moveIntervalTicks = 1
    ),
    GOREK_PHALANX(
        displayName = "Gorek Phalanx", tier = 6, threatLevel = 21, maxHp = 780, armor = 26,
        xpReward = 960, minDmg = 48, maxDmg = 86, goldMin = 210, goldMax = 400, moveIntervalTicks = 2
    ),
    GOREK_HEX_CALLER(
        displayName = "Gorek Hex-Caller", tier = 6, threatLevel = 22, maxHp = 620, armor = 20,
        xpReward = 1040, minDmg = 52, maxDmg = 94, goldMin = 225, goldMax = 430, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.RANGED_KITE, preferredRange = 4, rangedProjectile = ProjectileKind.NECRO_BOLT,
        inflictsFire = true
    ),
    VAELGOR_LABYRINTH_WARDEN(
        displayName = "Vaelgor the Labyrinth Warden", tier = 6, threatLevel = 26, maxHp = 1780, armor = 28,
        xpReward = 2850, minDmg = 64, maxDmg = 112, goldMin = 560, goldMax = 980, moveIntervalTicks = 2,
        aiStyle = MonsterAiStyle.BOSS_AOE_SUMMON, preferredRange = 4, rangedProjectile = ProjectileKind.FIREBALL,
        inflictsFire = true, isBoss = true, summonTypeName = "GOREK_RAIDER", maxSummons = 2,
        specialKind = BossSpecialKind.LABYRINTH_PYRE, bossTitle = "Warden of Vaelgor's Inner Sanctum"
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
                "MINOTAUR" -> GOREK_RAIDER
                "CYCLOPS", "RUNIC_GOLEM" -> GRIMMAW_CRUSHER
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
    val level: Int = type.threatLevel,
    val scaledArmor: Int = type.armor,
    val scaledMinDmg: Int = type.minDmg,
    val scaledMaxDmg: Int = type.maxDmg,
    val scaledXpReward: Int = type.xpReward,
    val scaledGoldMin: Int = type.goldMin,
    val scaledGoldMax: Int = type.goldMax,
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
    val summonCooldownTicks: Int = 3,
    val burnTicks: Int = 0,
    val burnDamagePerTick: Int = 0,
    val shatterTicks: Int = 0,
    val slowTicks: Int = 0
) {
    fun threatBandFor(playerLevel: Int): ThreatBand = when {
        type.isBoss || level >= playerLevel + 5 -> ThreatBand.DEADLY
        level >= playerLevel + 2 -> ThreatBand.CHALLENGING
        level <= playerLevel - 3 -> ThreatBand.EASY
        else -> ThreatBand.EVEN
    }
}

data class PendingRespawn(
    val type: MonsterType,
    val floor: Int,
    val spawnX: Int,
    val spawnY: Int,
    val ticksRemaining: Int,
    val originalMonsterId: Int = 0,
    val respawnAtMs: Long = 0L,
    val level: Int = type.threatLevel
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
    val expiresAtMs: Long = 0L,
    val ownerPlayerId: String? = null,
    val ownerName: String? = null,
    val publicAtMs: Long = 0L,
    val isDroppedBag: Boolean = false
) {
    fun remainingMs(nowMs: Long): Long? =
        if (isPlayerCorpse || expiresAtMs <= 0L) null else (expiresAtMs - nowMs).coerceAtLeast(0L)

    fun isExpired(nowMs: Long): Boolean =
        !isPlayerCorpse && expiresAtMs > 0L && nowMs >= expiresAtMs

    fun isExpiringSoon(nowMs: Long): Boolean {
        val rem = remainingMs(nowMs) ?: return false
        return rem in 1L..CORPSE_EXPIRY_WARNING_MS
    }

    /**
     * Returns true when this corpse/dropped pouch is still within its 5-second owner-only window
     * for [viewerPlayerId] (someone who did NOT slay the monster or drop the bag).
     */
    fun isOwnerLockedFor(viewerPlayerId: String?, nowMs: Long): Boolean {
        if (isPlayerCorpse) return !ownerPlayerId.isNullOrBlank() && ownerPlayerId != viewerPlayerId
        if (ownerPlayerId.isNullOrBlank() || publicAtMs <= 0L) return false
        if (!viewerPlayerId.isNullOrBlank() && ownerPlayerId == viewerPlayerId) return false
        return nowMs < publicAtMs
    }

    /**
     * Milliseconds remaining until [viewerPlayerId] can pick up this loot (0 if already unlocked).
     */
    fun ownerLockRemainingMs(viewerPlayerId: String?, nowMs: Long): Long {
        if (!isOwnerLockedFor(viewerPlayerId, nowMs)) return 0L
        if (isPlayerCorpse) return Long.MAX_VALUE
        return (publicAtMs - nowMs).coerceAtLeast(0L)
    }

    /**
     * Milliseconds remaining before an owner's own loot becomes public for bystanders (0 if already public).
     */
    fun publicCountdownMs(nowMs: Long): Long {
        if (isPlayerCorpse || ownerPlayerId.isNullOrBlank() || publicAtMs <= 0L) return 0L
        return (publicAtMs - nowMs).coerceAtLeast(0L)
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
    PROC_CALLOUT,
    INFO
}

data class FloatingText(
    val id: Long,
    val floor: Int,
    val tileX: Float,
    val tileY: Float,
    val text: String,
    val kind: FloatingTextKind,
    val createdAtMs: Long,
    val customColorHex: Long? = null
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
    val isAdmin: Boolean = false,
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
    val poisonTicks: Int = 0,
    val fireTicks: Int = 0,
    val skills: PlayerSkills = PlayerSkills(),
    val equipment: EquippedGear = EquippedGear(),
    val backpack: List<ItemStack> = emptyList(),
    val toolBag: List<ItemStack> = emptyList(),
    val depot: List<ItemStack> = emptyList(),
    val reaperVault: List<ItemStack> = emptyList(),
    val playerCorpses: List<CorpseEntity> = emptyList(),
    val mapVersion: Int = 1,
    val tunicColorIndex: Int = 0,
    val hairColorIndex: Int = 0,
    val questFlags: Map<String, Boolean> = emptyMap(),
    val boundAttackRuneId: String? = null,
    val boundHealRuneId: String? = "healing_rune",
    val selectedBaitId: String? = null,
    val customHotbarSlots: List<String?> = listOf(null, null),
    val regionId: String = DEFAULT_REGION_ID,
    val facingName: String = Direction.SOUTH.name,
    /** Townsfolk progress: hunt contract, bestiary kill counts and claims (see NpcServices). */
    val npcProgress: Map<String, Int> = emptyMap()
)

/**
 * Immutable fingerprint of ONLY permanent character progression, economy, items,
 * preferences, and floor/region metadata. Deliberately excludes routine walking
 * coordinates (x, y), combat HP/MP ticks, monsters, corpses, and ore nodes so
 * routine gameplay never triggers spurious Postgres writes.
 */
data class PersistentCharacterFingerprint(
    val playerName: String,
    val vocation: Vocation,
    val isAdmin: Boolean,
    val level: Int,
    val xp: Long,
    val maxHp: Int,
    val maxMp: Int,
    val skills: PlayerSkills,
    val gold: Int,
    val bankGold: Long,
    val equipment: EquippedGear,
    val backpack: List<ItemStack>,
    val toolBag: List<ItemStack>,
    val depot: List<ItemStack>,
    val reaperVault: List<ItemStack>,
    val floor: Int,
    val regionId: String,
    val tunicColorIndex: Int,
    val hairColorIndex: Int,
    val boundAttackRuneId: String?,
    val boundHealRuneId: String?,
    val selectedBaitId: String?,
    val customHotbarSlots: List<String?>,
    val questFlags: Map<String, Boolean>,
    val npcProgress: Map<String, Int> = emptyMap()
)

data class CharacterLocationSnapshot(
    val floor: Int,
    val regionId: String,
    val x: Int,
    val y: Int
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
    val useHdPostProcessShader: Boolean,
    val renderScaleFactor: Float = 1.0f,
    val maxDynamicPointLights: Int = 0
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
        spriteScaleMultiplier = 1.0f,
        strokeScale = 0.85f,
        useProceduralTileTextures = false,
        useDirectionalLighting = false,
        drawAmbientOcclusion = false,
        drawCastShadows = false,
        drawTwilightAtmosphere = false,
        drawPointLightPools = false,
        useHdPostProcessShader = false,
        renderScaleFactor = 1.0f,
        maxDynamicPointLights = 0
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
        useHdPostProcessShader = false,
        renderScaleFactor = 1.0f,
        maxDynamicPointLights = 6
    ),
    HIGH(
        displayName = "High",
        shortBadge = "HIGH",
        summaryNote = "Pixel-mosaic tiles, twilight glow & shadows",
        capabilityTags = listOf("Pixel-mosaic tiles", "Directional shadows", "10 dynamic lights"),
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
        cullMargin = 3,
        spriteScaleMultiplier = 1.0f,
        strokeScale = 1.0f,
        useProceduralTileTextures = true,
        useDirectionalLighting = true,
        drawAmbientOcclusion = true,
        drawCastShadows = true,
        drawTwilightAtmosphere = true,
        drawPointLightPools = true,
        useHdPostProcessShader = false,
        renderScaleFactor = 1.0f,
        maxDynamicPointLights = 10
    ),
    HD(
        displayName = "HD",
        shortBadge = "HD",
        summaryNote = "Full-resolution world, enhanced water, bloom & stone polish",
        capabilityTags = listOf("Full resolution", "Closest-14 lights", "Cached HD polish"),
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
        cullMargin = 3,
        spriteScaleMultiplier = 1.0f,
        strokeScale = 1.15f,
        useProceduralTileTextures = true,
        useDirectionalLighting = true,
        drawAmbientOcclusion = true,
        drawCastShadows = true,
        drawTwilightAtmosphere = true,
        drawPointLightPools = true,
        useHdPostProcessShader = false,
        renderScaleFactor = 1.0f,
        maxDynamicPointLights = 14
    );

    fun effectiveRenderScale(overrideScale: Float? = null): Float =
        clampRenderScale(overrideScale ?: renderScaleFactor)

    companion object {
        val DEFAULT: GraphicsQuality = MEDIUM
        /** Optional reduced world render scale (performance mode); HD itself renders at full resolution. */
        const val HD_RENDER_SCALE: Float = 0.8f
        const val MIN_RENDER_SCALE: Float = 0.5f
        const val MAX_RENDER_SCALE: Float = 1.0f
        const val MAX_HD_DYNAMIC_POINT_LIGHTS: Int = 14

        fun clampRenderScale(scale: Float): Float =
            scale.coerceIn(MIN_RENDER_SCALE, MAX_RENDER_SCALE)

        fun fromStorageName(raw: String?): GraphicsQuality {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class DisplaySettings(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.DEFAULT,
    val showMinimap: Boolean = true,
    val showFpsCounter: Boolean = false,
    val showWeatherEffects: Boolean = true,
    val showDamageNumbers: Boolean = true,
    val showHealingTicks: Boolean = true,
    val showProcNames: Boolean = true,
    val showStatusAndBlockTicks: Boolean = true,
    val showXpAndSkillGains: Boolean = true
) {
    /**
     * Determines whether a floating text popup should be drawn on the 2.5D viewport.
     * System INFO notices always remain visible so important non-combat alerts are never hidden.
     */
    fun shouldRenderFloatingText(kind: FloatingTextKind): Boolean = when (kind) {
        FloatingTextKind.DAMAGE_MONSTER,
        FloatingTextKind.DAMAGE_PLAYER -> showDamageNumbers
        FloatingTextKind.HEAL,
        FloatingTextKind.MANA -> showHealingTicks
        FloatingTextKind.PROC_CALLOUT -> showProcNames
        FloatingTextKind.POISON,
        FloatingTextKind.FIRE,
        FloatingTextKind.BLOCK -> showStatusAndBlockTicks
        FloatingTextKind.XP,
        FloatingTextKind.SKILL_UP -> showXpAndSkillGains
        FloatingTextKind.INFO -> true
    }
}

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
    HARBOR_FERRY,
    REAPER_TEMPLE,
    ADMIN_SCROLL,
    SETTINGS,
    BIOME_STALL,
    RECIPE_CODEX,
    HUNT_BOARD,
    BESTIARY
}

const val DEFAULT_TIME_OF_DAY_HOURS: Float = 19.5f

data class GameWorldState(
    val saveId: String = "",
    val playerName: String = "Aldric",
    val vocation: Vocation = Vocation.ADVENTURER,
    val isAdmin: Boolean = false,
    val infiniteHealth: Boolean = false,
    val isSpectator: Boolean = false,
    val timeOfDayHours: Float = DEFAULT_TIME_OF_DAY_HOURS,
    val adminWeatherOverride: WeatherType? = null,
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
    val playerX: Int = 1396,
    val playerY: Int = 592,
    val prevPlayerX: Int = 1396,
    val prevPlayerY: Int = 592,
    val lastPlayerMoveMs: Long = 0L,
    val playerFacing: Direction = Direction.SOUTH,
    val walkPath: List<Pair<Int, Int>> = emptyList(),
    val longRangeGoal: Pair<Int, Int>? = null,
    val longRangeWaypoints: List<Pair<Int, Int>> = emptyList(),
    val activeHarborPortId: String? = null,
    val poisonTicks: Int = 0,
    val fireTicks: Int = 0,
    val skills: PlayerSkills = PlayerSkills(),
    val equipment: EquippedGear = EquippedGear(),
    val backpack: List<ItemStack> = emptyList(),
    val toolBag: List<ItemStack> = emptyList(),
    val depot: List<ItemStack> = emptyList(),
    val reaperVault: List<ItemStack> = emptyList(),
    val monsters: List<MonsterEntity> = emptyList(),
    val pendingRespawns: List<PendingRespawn> = emptyList(),
    val oreNodes: Map<Triple<Int, Int, Int>, OreNodeState> = emptyMap(),
    val choppedTrees: Map<Triple<Int, Int, Int>, Int> = emptyMap(),
    val corpses: List<CorpseEntity> = emptyList(),
    val targetMonsterId: Int? = null,
    val autoFollowTarget: Boolean = false,
    val floatingTexts: List<FloatingText> = emptyList(),
    val projectiles: List<ProjectileEffect> = emptyList(),
    val procAoeEffects: List<ProcAoeEffect> = emptyList(),
    val lastWeaponProcAtMs: Long = 0L,
    val lastShieldProcAtMs: Long = 0L,
    val lastArmorProcAtMs: Long = 0L,
    val lastAmuletProcAtMs: Long = 0L,
    val lastRingProcAtMs: Long = 0L,
    val statusLog: List<String> = listOf("Welcome to Rookvale! Train freely and visit The Oracle at Level 20."),
    val activePanel: ActivePanel = ActivePanel.NONE,
    val activeBiomeStall: CraftingStationType? = null,
    val inspectingCorpseId: String? = null,
    val deathReport: DeathReport? = null,
    val showMinimap: Boolean = true,
    val tickCount: Long = 0L,
    val lastCombatTickMs: Long = 0L,
    val lastPlayerAttackMs: Long = 0L,
    val lastTreeChopMs: Long = 0L,
    val lastMineSwingMs: Long = 0L,
    val floorWarningBanner: FloorWarningBanner? = null,
    val inspectedPlayerId: String? = null,
    val inspectedPlayerUntilMs: Long = 0L,
    val outboundMonsterHits: List<LocalMonsterHitEvent> = emptyList(),
    val outboundMonsterAttacks: List<LocalMonsterAttackEvent> = emptyList(),
    val outboundPlayerEffects: List<LocalPlayerActionFxEvent> = emptyList(),
    val outboundSystemAlerts: List<String> = emptyList(),
    val outboundLootSpawns: List<CorpseEntity> = emptyList(),
    val questFlags: Map<String, Boolean> = emptyMap(),
    val boundAttackRuneId: String? = "magic_dart_rune",
    val boundHealRuneId: String? = "healing_rune",
    val selectedBaitId: String? = null,
    val customHotbarSlots: List<String?> = listOf(null, null),
    /** Townsfolk progress: hunt contract, bestiary kill counts and claims (see NpcServices). */
    val npcProgress: Map<String, Int> = emptyMap(),
    /** Which specialist shop is open (null = Alaric's general store). Not saved. */
    val activeShopId: String? = null
) {
    val isInvulnerable: Boolean
        get() = isAdmin || infiniteHealth

    fun persistentSaveFingerprint(): PersistentCharacterFingerprint = PersistentCharacterFingerprint(
        playerName = playerName,
        vocation = vocation,
        isAdmin = isAdmin,
        level = level,
        xp = xp,
        maxHp = maxHp,
        maxMp = maxMp,
        skills = skills,
        gold = gold,
        bankGold = bankGold,
        equipment = equipment,
        backpack = backpack,
        toolBag = toolBag,
        depot = depot,
        reaperVault = reaperVault,
        floor = floor,
        regionId = normalizeRegionId(regionId),
        tunicColorIndex = tunicColorIndex,
        hairColorIndex = hairColorIndex,
        boundAttackRuneId = boundAttackRuneId,
        boundHealRuneId = boundHealRuneId,
        selectedBaitId = selectedBaitId,
        customHotbarSlots = customHotbarSlots,
        questFlags = questFlags,
        npcProgress = npcProgress
    )

    fun locationSnapshot(): CharacterLocationSnapshot = CharacterLocationSnapshot(
        floor = floor,
        regionId = normalizeRegionId(regionId),
        x = playerX,
        y = playerY
    )

    fun effectiveMaxHp(): Int = maxHp + equipment.totalBonusMaxHp()

    fun unmetEquipRequirements(def: ItemDefinition): List<String> {
        val unmet = mutableListOf<String>()
        if (def.requiredLevel > 1 && level < def.requiredLevel) {
            unmet.add("Level ${def.requiredLevel} (you: $level)")
        }
        val reqSkill = def.requiredSkill
        if (reqSkill != null) {
            val (skillType, reqVal) = reqSkill
            val currentVal = skills.levelFor(skillType)
            if (currentVal < reqVal) {
                unmet.add("${skillType.displayName} $reqVal (you: $currentVal)")
            }
        }
        return unmet
    }

    fun meetsEquipRequirements(def: ItemDefinition): Boolean = unmetEquipRequirements(def).isEmpty()

    fun effectiveProcChancePercent(proc: EquipmentProc): Int =
        (proc.triggerChancePercent + equipment.totalProcChanceBonusPct()).coerceIn(1, 60)

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

    fun allPickaxes(): List<ItemDefinition> {
        val equippedPick = equipment.weaponId?.let { ItemCatalog.get(it) }
            ?.takeIf { it.category == ItemCategory.TOOL_PICKAXE }
        val carriedPicks = (toolBag + backpack)
            .map { ItemCatalog.get(it.itemId) }
            .filter { it.category == ItemCategory.TOOL_PICKAXE }
        return if (equippedPick != null) carriedPicks + equippedPick else carriedPicks
    }

    fun bestPickaxe(): ItemDefinition? =
        allPickaxes().maxByOrNull { it.pickaxeTier }

    /**
     * Selects the highest-tier pickaxe carried (Backpack, Tool Bag, or equipped hand) that the
     * player's current Mining skill qualifies to swing, preferring faster higher-tier picks when unlocked.
     */
    fun bestValidPickaxeFor(miningLevel: Int = skills.mining.level): ItemDefinition? =
        allPickaxes()
            .filter { miningLevel >= it.requiredMiningLevel }
            .maxByOrNull { it.pickaxeTier }

    fun hasHatchet(): Boolean {
        val equippedHatchet = equipment.weaponId?.let { ItemCatalog.get(it).category == ItemCategory.TOOL_HATCHET } == true
        return equippedHatchet || (toolBag + backpack).any { ItemCatalog.get(it.itemId).category == ItemCategory.TOOL_HATCHET }
    }

    fun isMeleeCombatSetup(): Boolean {
        val weapon = equipment.weaponDef()
        return weapon == null || weapon.category == ItemCategory.WEAPON_MELEE
    }

    fun activeAmmoCount(): Int = equipment.ammoId?.let { itemCount(it) } ?: 0

    fun activeFishingBaitDef(): ItemDefinition? =
        selectedBaitId?.let { ItemCatalog.all[it] }?.takeIf { it.category == ItemCategory.FISHING_BAIT }

    fun activeFishingBaitCount(): Int =
        activeFishingBaitDef()?.let { itemCount(it.id) } ?: 0

    fun carriedFishingBaitStacks(): List<ItemStack> =
        backpack.filter { ItemCatalog.all[it.itemId]?.category == ItemCategory.FISHING_BAIT && it.count > 0 }

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
