package com.example.game.model

import kotlin.random.Random

/**
 * A single weighted drop entry inside a monster's loot table.
 *
 * - [dropChance] controls the independent probability (`0f..1f`) when evaluated directly,
 *   and [weight] governs selection likelihood during weighted bonus loot rolls.
 */
data class WeightedLootDrop(
    val itemId: String,
    val minQty: Int = 1,
    val maxQty: Int = 1,
    val dropChance: Float,
    val weight: Int = 10
)

/**
 * Per-monster structured loot specification containing guaranteed gold range,
 * guaranteed item drops (if any), and weighted random item drops.
 */
data class MonsterLootEntry(
    val minGp: Int,
    val maxGp: Int,
    val guaranteedDrops: List<ItemStack> = emptyList(),
    val weightedDrops: List<WeightedLootDrop> = emptyList(),
    val bonusWeightRolls: Int = 1
)

data class RolledLoot(
    val gold: Int,
    val items: List<ItemStack>
)

/**
 * Data-driven per-monster weighted loot table registry mapping every Rookvale [MonsterType]
 * (all 19 overland, dungeon, and boss species) to creature-tailored gold, materials, runes,
 * potions, and rare gear drops.
 */
object MonsterLootTable {

    val entries: Map<MonsterType, MonsterLootEntry> = mapOf(
        // --- TIER 1: Starter Overland ---
        MonsterType.WILD_RABBIT to MonsterLootEntry(
            minGp = 1, maxGp = 6,
            guaranteedDrops = listOf(ItemStack("fresh_meat", 1)),
            weightedDrops = listOf(
                WeightedLootDrop("brindle_hide", 1, 1, dropChance = 0.45f, weight = 35),
                WeightedLootDrop("wild_feather", 1, 2, dropChance = 0.35f, weight = 30),
                WeightedLootDrop("fresh_meat", 1, 2, dropChance = 0.28f, weight = 25),
                WeightedLootDrop("cheese", 1, 1, dropChance = 0.14f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.CAVE_RAT to MonsterLootEntry(
            minGp = 4, maxGp = 12,
            weightedDrops = listOf(
                WeightedLootDrop("cheese", 1, 2, dropChance = 0.55f, weight = 40),
                WeightedLootDrop("fresh_meat", 1, 1, dropChance = 0.30f, weight = 28),
                WeightedLootDrop("brindle_hide", 1, 1, dropChance = 0.20f, weight = 20),
                WeightedLootDrop("wooden_club", 1, 1, dropChance = 0.10f, weight = 8),
                WeightedLootDrop("leather_boots", 1, 1, dropChance = 0.06f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.YOUNG_SNAKE to MonsterLootEntry(
            minGp = 5, maxGp = 15,
            weightedDrops = listOf(
                WeightedLootDrop("bile_gland", 1, 1, dropChance = 0.42f, weight = 36),
                WeightedLootDrop("fresh_meat", 1, 1, dropChance = 0.34f, weight = 30),
                WeightedLootDrop("ochre_shafts", 2, 4, dropChance = 0.22f, weight = 22),
                WeightedLootDrop("blank_rune", 1, 1, dropChance = 0.14f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.CRAG_WOLF to MonsterLootEntry(
            minGp = 7, maxGp = 20,
            weightedDrops = listOf(
                WeightedLootDrop("brindle_hide", 1, 2, dropChance = 0.62f, weight = 40),
                WeightedLootDrop("fresh_meat", 1, 2, dropChance = 0.45f, weight = 30),
                WeightedLootDrop("jagged_ivory", 1, 1, dropChance = 0.32f, weight = 18),
                WeightedLootDrop("leather_helmet", 1, 1, dropChance = 0.12f, weight = 7),
                WeightedLootDrop("leather_armor", 1, 1, dropChance = 0.08f, weight = 5)
            ),
            bonusWeightRolls = 1
        ),

        // --- TIER 2: Outer Wilds & Floor -1 Mossy Sewers ---
        MonsterType.SWAMP_TROLL to MonsterLootEntry(
            minGp = 14, maxGp = 34,
            weightedDrops = listOf(
                WeightedLootDrop("fresh_meat", 1, 2, dropChance = 0.48f, weight = 32),
                WeightedLootDrop("jagged_ivory", 1, 1, dropChance = 0.38f, weight = 26),
                WeightedLootDrop("copper_ore", 1, 2, dropChance = 0.30f, weight = 20),
                WeightedLootDrop("wooden_shield", 1, 1, dropChance = 0.16f, weight = 10),
                WeightedLootDrop("leather_legs", 1, 1, dropChance = 0.14f, weight = 8),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.12f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SKELETON to MonsterLootEntry(
            minGp = 18, maxGp = 44,
            weightedDrops = listOf(
                WeightedLootDrop("ancient_bone", 1, 2, dropChance = 0.65f, weight = 38),
                WeightedLootDrop("sunbrass_dirk", 1, 1, dropChance = 0.22f, weight = 18),
                WeightedLootDrop("rookvale_gladius", 1, 1, dropChance = 0.14f, weight = 12),
                WeightedLootDrop("wooden_shield", 1, 1, dropChance = 0.18f, weight = 14),
                WeightedLootDrop("viking_helmet", 1, 1, dropChance = 0.11f, weight = 10),
                WeightedLootDrop("magic_dart_rune", 1, 2, dropChance = 0.16f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GOBLIN_SCOUT to MonsterLootEntry(
            minGp = 18, maxGp = 42,
            weightedDrops = listOf(
                WeightedLootDrop("ochre_shafts", 4, 8, dropChance = 0.56f, weight = 34),
                WeightedLootDrop("wild_feather", 1, 3, dropChance = 0.40f, weight = 26),
                WeightedLootDrop("ivory_javelin", 1, 2, dropChance = 0.24f, weight = 18),
                WeightedLootDrop("ashwood_shortbow", 1, 1, dropChance = 0.12f, weight = 10),
                WeightedLootDrop("leather_boots", 1, 1, dropChance = 0.14f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.POISON_SPIDER to MonsterLootEntry(
            minGp = 20, maxGp = 48,
            weightedDrops = listOf(
                WeightedLootDrop("spider_silk", 1, 2, dropChance = 0.60f, weight = 36),
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.48f, weight = 28),
                WeightedLootDrop("venom_shafts", 3, 6, dropChance = 0.30f, weight = 18),
                WeightedLootDrop("magic_dart_rune", 1, 2, dropChance = 0.22f, weight = 12),
                WeightedLootDrop("healing_rune", 1, 1, dropChance = 0.14f, weight = 6)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.OUTLAW_BANDIT to MonsterLootEntry(
            minGp = 26, maxGp = 58,
            weightedDrops = listOf(
                WeightedLootDrop("rookvale_gladius", 1, 1, dropChance = 0.22f, weight = 24),
                WeightedLootDrop("leather_boots", 1, 1, dropChance = 0.18f, weight = 18),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.14f, weight = 14),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.34f, weight = 26),
                WeightedLootDrop("bog_iron_ore", 1, 2, dropChance = 0.26f, weight = 18)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.BROODMOTHER_ARACHNE to MonsterLootEntry(
            minGp = 110, maxGp = 220,
            guaranteedDrops = listOf(
                ItemStack("spider_silk", 3),
                ItemStack("bile_gland", 2),
                ItemStack("health_potion", 1)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("venom_shafts", 8, 14, dropChance = 0.65f, weight = 26),
                WeightedLootDrop("healing_rune", 2, 3, dropChance = 0.58f, weight = 24),
                WeightedLootDrop("hmm_rune", 2, 3, dropChance = 0.50f, weight = 20),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.45f, weight = 16),
                WeightedLootDrop("bilewood_conduit", 1, 1, dropChance = 0.30f, weight = 10),
                WeightedLootDrop("steel_shield", 1, 1, dropChance = 0.26f, weight = 9),
                WeightedLootDrop("duskmetal_falchion", 1, 1, dropChance = 0.22f, weight = 8),
                WeightedLootDrop("bog_iron_ward_pendant", 1, 1, dropChance = 0.20f, weight = 8),
                WeightedLootDrop("duskmetal_vampire_band", 1, 1, dropChance = 0.20f, weight = 8),
                WeightedLootDrop("life_amulet", 1, 1, dropChance = 0.14f, weight = 5)
            ),
            bonusWeightRolls = 2
        ),

        // --- TIER 3: Heartland Outlaw Frontier & Floor -2 Deep Caverns ---
        MonsterType.ORC_WARRIOR to MonsterLootEntry(
            minGp = 40, maxGp = 90,
            weightedDrops = listOf(
                WeightedLootDrop("duskmetal_falchion", 1, 1, dropChance = 0.16f, weight = 18),
                WeightedLootDrop("rookvale_gladius", 1, 1, dropChance = 0.22f, weight = 22),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.15f, weight = 16),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.30f, weight = 24),
                WeightedLootDrop("bog_iron_ore", 1, 2, dropChance = 0.28f, weight = 20)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.OUTLAW_ROGUE to MonsterLootEntry(
            minGp = 48, maxGp = 104,
            weightedDrops = listOf(
                WeightedLootDrop("ochre_shafts", 6, 12, dropChance = 0.58f, weight = 28),
                WeightedLootDrop("ashwood_shortbow", 1, 1, dropChance = 0.20f, weight = 18),
                WeightedLootDrop("duskmetal_falchion", 1, 1, dropChance = 0.16f, weight = 16),
                WeightedLootDrop("healing_rune", 1, 2, dropChance = 0.26f, weight = 18),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.32f, weight = 20)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GHOUL to MonsterLootEntry(
            minGp = 46, maxGp = 102,
            weightedDrops = listOf(
                WeightedLootDrop("ancient_bone", 2, 3, dropChance = 0.56f, weight = 32),
                WeightedLootDrop("ghoul_gem", 1, 1, dropChance = 0.36f, weight = 26),
                WeightedLootDrop("healing_rune", 1, 2, dropChance = 0.28f, weight = 18),
                WeightedLootDrop("hmm_rune", 1, 2, dropChance = 0.24f, weight = 14),
                WeightedLootDrop("life_amulet", 1, 1, dropChance = 0.10f, weight = 6),
                WeightedLootDrop("mana_potion", 1, 1, dropChance = 0.20f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.FIRE_DEVIL to MonsterLootEntry(
            minGp = 52, maxGp = 115,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.45f, weight = 30),
                WeightedLootDrop("ember_heart", 1, 1, dropChance = 0.30f, weight = 22),
                WeightedLootDrop("fireball_rune", 1, 2, dropChance = 0.32f, weight = 22),
                WeightedLootDrop("mana_potion", 1, 1, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("mystic_robe", 1, 1, dropChance = 0.12f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.IGNIS_LORD_OF_THE_PIT to MonsterLootEntry(
            minGp = 180, maxGp = 340,
            guaranteedDrops = listOf(
                ItemStack("ember_heart", 2),
                ItemStack("vesperite_ore", 3),
                ItemStack("mana_potion", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("fireball_rune", 2, 4, dropChance = 0.72f, weight = 26),
                WeightedLootDrop("health_potion", 2, 3, dropChance = 0.65f, weight = 22),
                WeightedLootDrop("quenchsteel_quarrels", 8, 14, dropChance = 0.45f, weight = 16),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.34f, weight = 14),
                WeightedLootDrop("quenchsteel_bastard", 1, 1, dropChance = 0.28f, weight = 12),
                WeightedLootDrop("tallowbone_crosier", 1, 1, dropChance = 0.26f, weight = 10),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.18f, weight = 8),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.22f, weight = 9),
                WeightedLootDrop("frostfang_coil", 1, 1, dropChance = 0.22f, weight = 9),
                WeightedLootDrop("sd_rune", 1, 2, dropChance = 0.30f, weight = 10)
            ),
            bonusWeightRolls = 2
        ),

        // --- TIER 4: Floor -3 Abyssal Crypt & Floor -4 Wyrm Sanctum ---
        MonsterType.DRAGON_HATCHLING to MonsterLootEntry(
            minGp = 95, maxGp = 195,
            weightedDrops = listOf(
                WeightedLootDrop("dragon_ham", 1, 2, dropChance = 0.56f, weight = 32),
                WeightedLootDrop("wyrmscale_plate", 1, 1, dropChance = 0.40f, weight = 26),
                WeightedLootDrop("fireball_rune", 1, 2, dropChance = 0.34f, weight = 20),
                WeightedLootDrop("dragon_tooth", 1, 1, dropChance = 0.24f, weight = 14),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.10f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.NECROMANCER to MonsterLootEntry(
            minGp = 105, maxGp = 210,
            weightedDrops = listOf(
                WeightedLootDrop("runic_slate", 1, 2, dropChance = 0.52f, weight = 30),
                WeightedLootDrop("sd_rune", 1, 2, dropChance = 0.40f, weight = 24),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.42f, weight = 22),
                WeightedLootDrop("tallowbone_crosier", 1, 1, dropChance = 0.20f, weight = 12),
                WeightedLootDrop("mystic_robe", 1, 1, dropChance = 0.18f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.DEMON_LORD to MonsterLootEntry(
            minGp = 240, maxGp = 440,
            guaranteedDrops = listOf(
                ItemStack("ember_heart", 3),
                ItemStack("sd_rune", 2),
                ItemStack("health_potion", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_ore", 2, 3, dropChance = 0.70f, weight = 24),
                WeightedLootDrop("fireball_rune", 2, 4, dropChance = 0.65f, weight = 22),
                WeightedLootDrop("mana_potion", 2, 3, dropChance = 0.60f, weight = 20),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.38f, weight = 14),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("dragon_mail", 1, 1, dropChance = 0.26f, weight = 8)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.ELDER_DRAGON to MonsterLootEntry(
            minGp = 320, maxGp = 580,
            guaranteedDrops = listOf(
                ItemStack("wyrmscale_plate", 3),
                ItemStack("dragon_tooth", 2),
                ItemStack("dragon_ham", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_ore", 2, 4, dropChance = 0.74f, weight = 24),
                WeightedLootDrop("sd_rune", 2, 4, dropChance = 0.68f, weight = 22),
                WeightedLootDrop("fireball_rune", 2, 4, dropChance = 0.62f, weight = 20),
                WeightedLootDrop("dragon_mail", 1, 1, dropChance = 0.45f, weight = 14),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.42f, weight = 14),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.16f, weight = 8)
            ),
            bonusWeightRolls = 2
        ),

        // --- TIER 5: Grimmaw Crags Camp & Floor -5 The Grimmaw Quarry (Endgame Stone-Giant Clan) ---
        MonsterType.GRIMMAW_QUARRYMAN to MonsterLootEntry(
            minGp = 120, maxGp = 240,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.56f, weight = 32),
                WeightedLootDrop("colossus_tendon", 1, 2, dropChance = 0.44f, weight = 26),
                WeightedLootDrop("grimmaw_stone_core", 1, 1, dropChance = 0.26f, weight = 18),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.40f, weight = 18),
                WeightedLootDrop("vesperite_cleaver", 1, 1, dropChance = 0.05f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GRIMMAW_ROCK_HURLER to MonsterLootEntry(
            minGp = 135, maxGp = 265,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 1, 3, dropChance = 0.58f, weight = 30),
                WeightedLootDrop("starlight_quarrels", 8, 16, dropChance = 0.42f, weight = 24),
                WeightedLootDrop("grimmaw_stone_core", 1, 1, dropChance = 0.30f, weight = 20),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.36f, weight = 18),
                WeightedLootDrop("vesperite_recurve_bow", 1, 1, dropChance = 0.06f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GRIMMAW_CRUSHER to MonsterLootEntry(
            minGp = 165, maxGp = 320,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 2, 3, dropChance = 0.62f, weight = 28),
                WeightedLootDrop("grimmaw_stone_core", 1, 2, dropChance = 0.38f, weight = 24),
                WeightedLootDrop("vesperite_bar", 1, 1, dropChance = 0.24f, weight = 18),
                WeightedLootDrop("health_potion", 2, 3, dropChance = 0.44f, weight = 18),
                WeightedLootDrop("vesperite_warmaul", 1, 1, dropChance = 0.07f, weight = 5),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.06f, weight = 5)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.KRAGOR_THE_STONE_EYED to MonsterLootEntry(
            minGp = 420, maxGp = 760,
            guaranteedDrops = listOf(
                ItemStack("vesperite_bar", 2),
                ItemStack("grimmaw_stone_core", 2),
                ItemStack("health_potion", 3)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 3, 5, dropChance = 0.78f, weight = 22),
                WeightedLootDrop("sd_rune", 2, 4, dropChance = 0.65f, weight = 18),
                WeightedLootDrop("mana_potion", 2, 3, dropChance = 0.60f, weight = 16),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.24f, weight = 10),
                WeightedLootDrop("vesperite_cleaver", 1, 1, dropChance = 0.24f, weight = 10),
                WeightedLootDrop("vesperite_warmaul", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("vesperite_recurve_bow", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("vesperite_bulwark", 1, 1, dropChance = 0.20f, weight = 9),
                WeightedLootDrop("vesperite_aegis_mail", 1, 1, dropChance = 0.20f, weight = 9),
                WeightedLootDrop("vesperite_resonance_necklace", 1, 1, dropChance = 0.18f, weight = 8),
                WeightedLootDrop("vesperite_catalyst_ring", 1, 1, dropChance = 0.18f, weight = 8)
            ),
            bonusWeightRolls = 2
        ),

        // --- TIER 6: Gorek Badlands War-Camp & Floor -6 The Gorek Labyrinth (Pinnacle Obsidian Beast-Kin) ---
        MonsterType.GOREK_RAIDER to MonsterLootEntry(
            minGp = 180, maxGp = 350,
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_ore", 1, 2, dropChance = 0.54f, weight = 30),
                WeightedLootDrop("gorek_obsidian_horn", 1, 1, dropChance = 0.30f, weight = 22),
                WeightedLootDrop("health_potion", 2, 3, dropChance = 0.45f, weight = 20),
                WeightedLootDrop("fireball_rune", 2, 3, dropChance = 0.36f, weight = 18),
                WeightedLootDrop("cinderite_greatsword", 1, 1, dropChance = 0.05f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GOREK_PHALANX to MonsterLootEntry(
            minGp = 210, maxGp = 400,
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_ore", 2, 3, dropChance = 0.60f, weight = 28),
                WeightedLootDrop("gorek_obsidian_horn", 1, 2, dropChance = 0.36f, weight = 24),
                WeightedLootDrop("cinderite_bar", 1, 1, dropChance = 0.24f, weight = 18),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.16f, weight = 12),
                WeightedLootDrop("cinderite_greataxe", 1, 1, dropChance = 0.06f, weight = 5),
                WeightedLootDrop("cinderite_worldbreaker", 1, 1, dropChance = 0.06f, weight = 5)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GOREK_HEX_CALLER to MonsterLootEntry(
            minGp = 225, maxGp = 430,
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_ore", 2, 3, dropChance = 0.58f, weight = 26),
                WeightedLootDrop("runic_slate", 2, 3, dropChance = 0.50f, weight = 22),
                WeightedLootDrop("gorek_obsidian_horn", 1, 2, dropChance = 0.36f, weight = 22),
                WeightedLootDrop("sd_rune", 2, 4, dropChance = 0.50f, weight = 18),
                WeightedLootDrop("cinderite_soulspire", 1, 1, dropChance = 0.06f, weight = 5),
                WeightedLootDrop("cinderite_ashen_bow", 1, 1, dropChance = 0.06f, weight = 5)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.VAELGOR_LABYRINTH_WARDEN to MonsterLootEntry(
            minGp = 560, maxGp = 980,
            guaranteedDrops = listOf(
                ItemStack("cinderite_bar", 2),
                ItemStack("gorek_obsidian_horn", 2),
                ItemStack("sd_rune", 3)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_ore", 3, 5, dropChance = 0.80f, weight = 20),
                WeightedLootDrop("health_potion", 3, 4, dropChance = 0.70f, weight = 16),
                WeightedLootDrop("mana_potion", 3, 4, dropChance = 0.65f, weight = 16),
                WeightedLootDrop("cinderite_greatsword", 1, 1, dropChance = 0.24f, weight = 10),
                WeightedLootDrop("cinderite_greataxe", 1, 1, dropChance = 0.24f, weight = 10),
                WeightedLootDrop("cinderite_worldbreaker", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("cinderite_ashen_bow", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("cinderite_soulspire", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("cinderite_bastion", 1, 1, dropChance = 0.20f, weight = 9),
                WeightedLootDrop("cinderite_warplate", 1, 1, dropChance = 0.20f, weight = 9),
                WeightedLootDrop("cinderheart_sovereign_torc", 1, 1, dropChance = 0.18f, weight = 8),
                WeightedLootDrop("cinderite_overlord_band", 1, 1, dropChance = 0.18f, weight = 8)
            ),
            bonusWeightRolls = 2
        )
    )

    fun entryFor(type: MonsterType): MonsterLootEntry =
        entries[type] ?: MonsterLootEntry(minGp = type.goldMin, maxGp = type.goldMax)

    /**
     * Rolls guaranteed gold, guaranteed items, independent drop-chance triggers, and weighted bonus picks.
     * Merges duplicate stackable items into clean [ItemStack] entries.
     */
    fun rollLoot(type: MonsterType, rng: Random = Random.Default): RolledLoot {
        val table = entryFor(type)
        val lo = minOf(table.minGp, table.maxGp).coerceAtLeast(0)
        val hi = maxOf(table.minGp, table.maxGp).coerceAtLeast(lo)
        val gold = if (lo == hi) lo else rng.nextInt(lo, hi + 1)

        val rolled = mutableListOf<ItemStack>()
        rolled.addAll(table.guaranteedDrops)

        // Independent chance evaluation per weighted entry
        val pickedIds = mutableSetOf<String>()
        for (drop in table.weightedDrops) {
            if (rng.nextFloat() <= drop.dropChance.coerceIn(0f, 1f)) {
                val qty = rollQty(drop.minQty, drop.maxQty, rng)
                if (qty > 0) {
                    rolled.add(ItemStack(drop.itemId, qty))
                    pickedIds.add(drop.itemId)
                }
            }
        }

        // Weighted bonus roll(s) so bosses and stronger foes reliably drop creature-themed loot
        if (table.weightedDrops.isNotEmpty() && table.bonusWeightRolls > 0) {
            val totalWeight = table.weightedDrops.sumOf { it.weight.coerceAtLeast(1) }
            val rollsNeeded = if (type.isBoss) table.bonusWeightRolls else {
                if (rolled.isEmpty()) 1 else 0
            }
            repeat(rollsNeeded) {
                var cursor = rng.nextInt(totalWeight)
                var chosen = table.weightedDrops.first()
                for (drop in table.weightedDrops) {
                    val w = drop.weight.coerceAtLeast(1)
                    if (cursor < w) {
                        chosen = drop
                        break
                    }
                    cursor -= w
                }
                val isEquip = ItemCatalog.all[chosen.itemId]?.stackable == false
                if (!isEquip || chosen.itemId !in pickedIds) {
                    val qty = rollQty(chosen.minQty, chosen.maxQty, rng)
                    if (qty > 0) {
                        rolled.add(ItemStack(chosen.itemId, qty))
                        pickedIds.add(chosen.itemId)
                    }
                }
            }
        }

        return RolledLoot(gold = gold, items = mergeStacks(rolled))
    }

    private fun rollQty(minQty: Int, maxQty: Int, rng: Random): Int {
        val lo = minOf(minQty, maxQty).coerceAtLeast(1)
        val hi = maxOf(minQty, maxQty).coerceAtLeast(lo)
        return if (lo == hi) lo else rng.nextInt(lo, hi + 1)
    }

    private fun mergeStacks(raw: List<ItemStack>): List<ItemStack> {
        val out = mutableListOf<ItemStack>()
        for (stack in raw) {
            if (stack.count <= 0) continue
            val def = ItemCatalog.all[stack.itemId]
            if (def?.stackable == true) {
                val idx = out.indexOfFirst { it.itemId == stack.itemId }
                if (idx >= 0) {
                    out[idx] = out[idx].copy(count = out[idx].count + stack.count)
                } else {
                    out.add(stack)
                }
            } else {
                out.add(stack)
            }
        }
        return out
    }
}
