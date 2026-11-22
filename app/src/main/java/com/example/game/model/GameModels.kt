package com.example.game.model

import com.example.game.multiplayer.LocalMonsterHitEvent
import com.squareup.moshi.JsonClass

const val CURRENT_MAP_VERSION = 2
const val V1_SURFACE_OFFSET = 32
const val ORACLE_MIN_LEVEL = 20
const val TOOL_BAG_WEIGHT_MULTIPLIER = 0.5

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
        description = "Blank-slate explorer free to train any weapon, shield, rune, or gathering skill until Level 20.",
        hpPerLevel = 10,
        mpPerLevel = 10,
        capPerLevel = 15.0,
        baseHp = 150,
        baseMp = 55,
        baseCap = 400.0,
        meleeGainRate = 1.0,
        distanceGainRate = 1.0,
        shieldingGainRate = 1.0,
        magicGainRate = 1.0,
        attackRange = 1
    ),
    KNIGHT(
        displayName = "Knight",
        subtitle = "Vanguard of Steel",
        description = "Higher HP & Cap growth per level, faster Melee & Shielding progression, and mastery of Tier 3-4 melee gear.",
        hpPerLevel = 15,
        mpPerLevel = 5,
        capPerLevel = 25.0,
        baseHp = 185,
        baseMp = 35,
        baseCap = 470.0,
        meleeGainRate = 1.65,
        distanceGainRate = 0.75,
        shieldingGainRate = 1.75,
        magicGainRate = 0.65,
        attackRange = 1
    ),
    PALADIN(
        displayName = "Paladin",
        subtitle = "Sacred Marksman",
        description = "Balanced HP/MP/Cap growth, rapid Distance Fighting progression, and mastery of advanced bows & arbalests.",
        hpPerLevel = 10,
        mpPerLevel = 15,
        capPerLevel = 20.0,
        baseHp = 155,
        baseMp = 75,
        baseCap = 410.0,
        meleeGainRate = 1.0,
        distanceGainRate = 1.70,
        shieldingGainRate = 1.15,
        magicGainRate = 1.25,
        attackRange = 4
    ),
    MAGE(
        displayName = "Mage",
        subtitle = "Arcane Runecaster",
        description = "High MP growth per level, fastest Magic Level progression, and mastery of Tier 3-4 staves & devastating runes.",
        hpPerLevel = 5,
        mpPerLevel = 30,
        capPerLevel = 10.0,
        baseHp = 135,
        baseMp = 140,
        baseCap = 340.0,
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
}

@JsonClass(generateAdapter = true)
data class PlayerSkills(
    val melee: SkillProgress = SkillProgress(10, 0.0),
    val distance: SkillProgress = SkillProgress(10, 0.0),
    val shielding: SkillProgress = SkillProgress(10, 0.0),
    val magic: SkillProgress = SkillProgress(10, 0.0),
    val fishing: SkillProgress = SkillProgress(10, 0.0),
    val mining: SkillProgress = SkillProgress(10, 0.0)
) {
    fun levelFor(type: SkillType): Int = when (type) {
        SkillType.MELEE -> melee.level
        SkillType.DISTANCE -> distance.level
        SkillType.SHIELDING -> shielding.level
        SkillType.MAGIC -> magic.level
        SkillType.FISHING -> fishing.level
        SkillType.MINING -> mining.level
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
    RUNE_ATTACK,
    TOOL_ROPE,
    TOOL_FISHING_ROD,
    TOOL_PICKAXE,
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
            category == ItemCategory.TOOL_PICKAXE

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
    DEPOT_CHEST(false),
    MINI_DEPOT(false),
    SHOP_COUNTER(false),
    SMELTING_FURNACE(false),
    BLACKSMITH_ANVIL(false),
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
    TORCH_WALL(false, true);

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

enum class MonsterType(
    val displayName: String,
    val maxHp: Int,
    val xpReward: Int,
    val minDmg: Int,
    val maxDmg: Int,
    val goldMin: Int,
    val goldMax: Int,
    val moveIntervalTicks: Int,
    val inflictsPoison: Boolean = false,
    val inflictsFire: Boolean = false,
    val isBoss: Boolean = false,
    val rangedBreathRange: Int = 1
) {
    RAT("Cave Rat", 35, 25, 2, 7, 4, 14, 1),
    CRAG_WOLF("Crag Wolf", 54, 42, 6, 13, 6, 18, 1),
    VESPER_BAT("Vesper Bat", 48, 38, 4, 10, 5, 16, 1),
    CAVE_SPIDER("Briar Spider", 66, 52, 6, 14, 10, 24, 1, inflictsPoison = true),
    STONEHORN_TROLL("Stonehorn Troll", 95, 78, 9, 18, 16, 36, 2),
    SKELETON("Skeleton", 115, 90, 10, 22, 18, 44, 1),
    MARROW_ARCHER("Marrow Archer", 105, 105, 12, 24, 20, 48, 2, rangedBreathRange = 3),
    GHOUL("Crypt Ghoul", 170, 150, 16, 30, 28, 70, 2, inflictsPoison = true),
    CYCLOPS("Cyclops", 285, 255, 25, 46, 52, 115, 2),
    FORGE_BRUTE("Forge Brute", 335, 310, 30, 54, 65, 135, 2),
    RUNIC_GOLEM("Runic Golem", 360, 340, 28, 52, 70, 145, 3),
    WYRM_HATCHLING("Wyrm Hatchling", 410, 420, 32, 58, 95, 180, 2, inflictsFire = true, rangedBreathRange = 3),
    CINDER_WRAITH("Cinder Wraith", 375, 390, 34, 60, 88, 165, 2, inflictsFire = true, rangedBreathRange = 3),
    DRAGON_BOSS("Ancient Dragon", 720, 1050, 40, 76, 210, 380, 2, inflictsFire = true, isBoss = true, rangedBreathRange = 4)
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
    val targetPlayerId: String? = null,
    val contributors: Set<String> = emptySet()
)

data class PendingRespawn(
    val type: MonsterType,
    val floor: Int,
    val spawnX: Int,
    val spawnY: Int,
    val ticksRemaining: Int,
    val originalMonsterId: Int = 0
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
    val items: List<ItemStack> = emptyList()
)

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
    DEATH_RUNE,
    FIRE_BREATH,
    SPLASH,
    MINING_SPARK
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
    val droppedItemsCount: Int
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
    val hairColorIndex: Int = 0
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
    LOOT_CORPSE,
    ORACLE
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
    val maxCapOz: Double = 400.0,
    val gold: Int = 120,
    val floor: Int = 0,
    val playerX: Int = 47,
    val playerY: Int = 46,
    val prevPlayerX: Int = 47,
    val prevPlayerY: Int = 46,
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
    val corpses: List<CorpseEntity> = emptyList(),
    val targetMonsterId: Int? = null,
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
    val outboundMonsterHits: List<LocalMonsterHitEvent> = emptyList()
) {
    fun toolBagWeightOz(): Double =
        toolBag.sumOf { ItemCatalog.get(it.itemId).weightOz * TOOL_BAG_WEIGHT_MULTIPLIER * it.count }

    fun usedCapOz(): Double {
        val bpWeight = backpack.sumOf { ItemCatalog.get(it.itemId).weightOz * it.count }
        val tbWeight = toolBagWeightOz()
        val eqWeight = equipment.totalWeightOz()
        val goldWeight = gold * 0.1
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

    fun activeAmmoCount(): Int = equipment.ammoId?.let { itemCount(it) } ?: 0

    fun bestAttackRuneId(): String? = when {
        hasItem("sd_rune") -> "sd_rune"
        hasItem("hmm_rune") -> "hmm_rune"
        else -> null
    }

    fun isUsingTwoHandedWeapon(): Boolean = equipment.weaponDef()?.isTwoHanded == true

    fun hasActiveShield(): Boolean = equipment.shieldId != null && !isUsingTwoHandedWeapon()

    fun attackSkillMultiplier(skillLevel: Int): Double =
        1.0 + (skillLevel - 10).coerceAtLeast(0) * 0.06

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
}
