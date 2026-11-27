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
                WeightedLootDrop("brass_boots", 1, 1, dropChance = 0.10f, weight = 8),
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
                WeightedLootDrop("colossus_tendon", 1, 1, dropChance = 0.14f, weight = 12),
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
                WeightedLootDrop("frost_iron_ore", 1, 2, dropChance = 0.30f, weight = 18),
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
                WeightedLootDrop("frost_iron_ore", 1, 2, dropChance = 0.30f, weight = 18),
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
                WeightedLootDrop("frost_iron_ore", 1, 2, dropChance = 0.30f, weight = 18),
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
                WeightedLootDrop("frost_iron_ore", 2, 4, dropChance = 0.70f, weight = 18),
                WeightedLootDrop("rime_plate_longsword", 1, 1, dropChance = 0.14f, weight = 8),
                WeightedLootDrop("rime_plate_cuirass", 1, 1, dropChance = 0.14f, weight = 8),
                WeightedLootDrop("rime_plate_helm", 1, 1, dropChance = 0.12f, weight = 7),
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
            minGp = 90, maxGp = 190,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.50f, weight = 30),
                WeightedLootDrop("gorek_obsidian_horn", 1, 1, dropChance = 0.32f, weight = 24),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.42f, weight = 20),
                WeightedLootDrop("fireball_rune", 1, 2, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.04f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GOREK_PHALANX to MonsterLootEntry(
            minGp = 100, maxGp = 210,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.52f, weight = 28),
                WeightedLootDrop("gorek_obsidian_horn", 1, 1, dropChance = 0.36f, weight = 24),
                WeightedLootDrop("colossus_tendon", 1, 1, dropChance = 0.26f, weight = 18),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.10f, weight = 10),
                WeightedLootDrop("vesperite_bulwark", 1, 1, dropChance = 0.04f, weight = 4),
                WeightedLootDrop("vesperite_warmaul", 1, 1, dropChance = 0.04f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GOREK_HEX_CALLER to MonsterLootEntry(
            minGp = 105, maxGp = 220,
            weightedDrops = listOf(
                WeightedLootDrop("runic_slate", 1, 2, dropChance = 0.48f, weight = 26),
                WeightedLootDrop("gorek_obsidian_horn", 1, 1, dropChance = 0.34f, weight = 22),
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.40f, weight = 20),
                WeightedLootDrop("sd_rune", 1, 3, dropChance = 0.42f, weight = 18),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.04f, weight = 4),
                WeightedLootDrop("vesperite_recurve_bow", 1, 1, dropChance = 0.04f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.VAELGOR_LABYRINTH_WARDEN to MonsterLootEntry(
            minGp = 320, maxGp = 560,
            guaranteedDrops = listOf(
                ItemStack("vesperite_bar", 2),
                ItemStack("gorek_obsidian_horn", 2),
                ItemStack("sd_rune", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_sun_helm", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("vesperite_greaves", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("vesperite_ore", 2, 4, dropChance = 0.80f, weight = 20),
                WeightedLootDrop("health_potion", 2, 4, dropChance = 0.70f, weight = 16),
                WeightedLootDrop("mana_potion", 2, 4, dropChance = 0.65f, weight = 16),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.20f, weight = 10),
                WeightedLootDrop("vesperite_warmaul", 1, 1, dropChance = 0.18f, weight = 10),
                WeightedLootDrop("vesperite_recurve_bow", 1, 1, dropChance = 0.18f, weight = 10),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.18f, weight = 10),
                WeightedLootDrop("vesperite_bulwark", 1, 1, dropChance = 0.16f, weight = 9),
                WeightedLootDrop("vesperite_aegis_mail", 1, 1, dropChance = 0.16f, weight = 9),
                WeightedLootDrop("vesperite_resonance_necklace", 1, 1, dropChance = 0.14f, weight = 8),
                WeightedLootDrop("vesperite_catalyst_ring", 1, 1, dropChance = 0.14f, weight = 8)
            ),
            bonusWeightRolls = 2
        ),

        // --- NEW DUNGEON-SPECIFIC MONSTERS & LOWER-FLOOR BOSSES ---
        MonsterType.BRIAR_CRYPT_CRAWLER to MonsterLootEntry(
            minGp = 18, maxGp = 46,
            weightedDrops = listOf(
                WeightedLootDrop("spider_silk", 1, 2, dropChance = 0.56f, weight = 34),
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.44f, weight = 26),
                WeightedLootDrop("ancient_bone", 1, 2, dropChance = 0.32f, weight = 20),
                WeightedLootDrop("venom_shafts", 3, 6, dropChance = 0.24f, weight = 14),
                WeightedLootDrop("healing_rune", 1, 1, dropChance = 0.14f, weight = 6)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.CATACOMB_BONE_ARCHER to MonsterLootEntry(
            minGp = 20, maxGp = 46,
            weightedDrops = listOf(
                WeightedLootDrop("ancient_bone", 1, 2, dropChance = 0.62f, weight = 34),
                WeightedLootDrop("ochre_shafts", 5, 10, dropChance = 0.52f, weight = 28),
                WeightedLootDrop("ashwood_shortbow", 1, 1, dropChance = 0.16f, weight = 14),
                WeightedLootDrop("magic_dart_rune", 1, 2, dropChance = 0.20f, weight = 14),
                WeightedLootDrop("viking_helmet", 1, 1, dropChance = 0.12f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.TUNNEL_KOBOLD_SAPPER to MonsterLootEntry(
            minGp = 18, maxGp = 40,
            weightedDrops = listOf(
                WeightedLootDrop("copper_ore", 1, 3, dropChance = 0.58f, weight = 34),
                WeightedLootDrop("tin_ore", 1, 2, dropChance = 0.44f, weight = 28),
                WeightedLootDrop("ochre_shafts", 4, 8, dropChance = 0.36f, weight = 20),
                WeightedLootDrop("sunbrass_dirk", 1, 1, dropChance = 0.14f, weight = 10),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.16f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.QUARRY_MOLE_BRUTE to MonsterLootEntry(
            minGp = 22, maxGp = 50,
            weightedDrops = listOf(
                WeightedLootDrop("copper_ore", 1, 3, dropChance = 0.54f, weight = 30),
                WeightedLootDrop("bog_iron_ore", 1, 2, dropChance = 0.40f, weight = 26),
                WeightedLootDrop("brindle_hide", 1, 2, dropChance = 0.42f, weight = 22),
                WeightedLootDrop("wooden_shield", 1, 1, dropChance = 0.16f, weight = 12),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.18f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.CRAG_HARPY to MonsterLootEntry(
            minGp = 20, maxGp = 48,
            weightedDrops = listOf(
                WeightedLootDrop("wild_feather", 2, 4, dropChance = 0.65f, weight = 36),
                WeightedLootDrop("jagged_ivory", 1, 2, dropChance = 0.38f, weight = 24),
                WeightedLootDrop("ochre_shafts", 4, 9, dropChance = 0.34f, weight = 20),
                WeightedLootDrop("leather_boots", 1, 1, dropChance = 0.16f, weight = 12),
                WeightedLootDrop("mana_potion", 1, 1, dropChance = 0.16f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SHALE_GOLEM to MonsterLootEntry(
            minGp = 24, maxGp = 54,
            weightedDrops = listOf(
                WeightedLootDrop("bog_iron_ore", 1, 3, dropChance = 0.56f, weight = 34),
                WeightedLootDrop("tin_ore", 1, 3, dropChance = 0.44f, weight = 24),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.14f, weight = 14),
                WeightedLootDrop("steel_shield", 1, 1, dropChance = 0.12f, weight = 12),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.24f, weight = 16)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.THORN_BRAMBLE_STALKER to MonsterLootEntry(
            minGp = 22, maxGp = 52,
            weightedDrops = listOf(
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.52f, weight = 32),
                WeightedLootDrop("venom_shafts", 4, 8, dropChance = 0.40f, weight = 24),
                WeightedLootDrop("brindle_hide", 1, 2, dropChance = 0.34f, weight = 20),
                WeightedLootDrop("bilewood_conduit", 1, 1, dropChance = 0.10f, weight = 12),
                WeightedLootDrop("healing_rune", 1, 2, dropChance = 0.18f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SPORELING_LOBBER to MonsterLootEntry(
            minGp = 22, maxGp = 50,
            weightedDrops = listOf(
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.54f, weight = 32),
                WeightedLootDrop("magic_dart_rune", 1, 2, dropChance = 0.34f, weight = 24),
                WeightedLootDrop("mana_potion", 1, 1, dropChance = 0.26f, weight = 20),
                WeightedLootDrop("healing_rune", 1, 2, dropChance = 0.22f, weight = 16),
                WeightedLootDrop("blank_rune", 1, 2, dropChance = 0.18f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.FOREMAN_IRONJAW to MonsterLootEntry(
            minGp = 100, maxGp = 200,
            guaranteedDrops = listOf(
                ItemStack("copper_ore", 4),
                ItemStack("bog_iron_ore", 3),
                ItemStack("health_potion", 1)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("tin_ore", 3, 5, dropChance = 0.65f, weight = 26),
                WeightedLootDrop("rookvale_gladius", 1, 1, dropChance = 0.34f, weight = 18),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("steel_shield", 1, 1, dropChance = 0.28f, weight = 14),
                WeightedLootDrop("duskmetal_falchion", 1, 1, dropChance = 0.24f, weight = 12),
                WeightedLootDrop("bog_iron_ward_pendant", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("life_amulet", 1, 1, dropChance = 0.16f, weight = 4)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.SKREE_RIDGE_MATRIARCH to MonsterLootEntry(
            minGp = 110, maxGp = 215,
            guaranteedDrops = listOf(
                ItemStack("wild_feather", 4),
                ItemStack("jagged_ivory", 2),
                ItemStack("mana_potion", 1)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("ochre_shafts", 10, 18, dropChance = 0.68f, weight = 26),
                WeightedLootDrop("ashwood_shortbow", 1, 1, dropChance = 0.34f, weight = 18),
                WeightedLootDrop("healing_rune", 2, 3, dropChance = 0.52f, weight = 20),
                WeightedLootDrop("hmm_rune", 2, 3, dropChance = 0.48f, weight = 18),
                WeightedLootDrop("duskmetal_vampire_band", 1, 1, dropChance = 0.22f, weight = 10),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.18f, weight = 8)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.GNARLROOT_THE_BLIGHTED to MonsterLootEntry(
            minGp = 125, maxGp = 245,
            guaranteedDrops = listOf(
                ItemStack("bile_gland", 3),
                ItemStack("spider_silk", 2),
                ItemStack("health_potion", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("venom_shafts", 10, 16, dropChance = 0.66f, weight = 26),
                WeightedLootDrop("bilewood_conduit", 1, 1, dropChance = 0.36f, weight = 18),
                WeightedLootDrop("healing_rune", 2, 4, dropChance = 0.56f, weight = 20),
                WeightedLootDrop("duskmetal_falchion", 1, 1, dropChance = 0.26f, weight = 14),
                WeightedLootDrop("bog_iron_ward_pendant", 1, 1, dropChance = 0.24f, weight = 12),
                WeightedLootDrop("life_amulet", 1, 1, dropChance = 0.18f, weight = 10)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.VAULT_SENTINEL to MonsterLootEntry(
            minGp = 50, maxGp = 110,
            weightedDrops = listOf(
                WeightedLootDrop("bog_iron_ore", 2, 3, dropChance = 0.54f, weight = 30),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.20f, weight = 18),
                WeightedLootDrop("steel_shield", 1, 1, dropChance = 0.20f, weight = 18),
                WeightedLootDrop("duskmetal_falchion", 1, 1, dropChance = 0.16f, weight = 16),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.34f, weight = 18)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.HOLLOW_CROSSBOWMAN to MonsterLootEntry(
            minGp = 48, maxGp = 106,
            weightedDrops = listOf(
                WeightedLootDrop("quenchsteel_quarrels", 6, 12, dropChance = 0.56f, weight = 32),
                WeightedLootDrop("ochre_shafts", 8, 14, dropChance = 0.44f, weight = 24),
                WeightedLootDrop("ashwood_shortbow", 1, 1, dropChance = 0.18f, weight = 16),
                WeightedLootDrop("health_potion", 1, 1, dropChance = 0.28f, weight = 16),
                WeightedLootDrop("mana_potion", 1, 1, dropChance = 0.22f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.CINDER_IMP to MonsterLootEntry(
            minGp = 48, maxGp = 108,
            weightedDrops = listOf(
                WeightedLootDrop("ember_heart", 1, 1, dropChance = 0.36f, weight = 28),
                WeightedLootDrop("fireball_rune", 1, 2, dropChance = 0.38f, weight = 26),
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.34f, weight = 22),
                WeightedLootDrop("mana_potion", 1, 1, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("mystic_robe", 1, 1, dropChance = 0.12f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.WATCHWOOD_DREAD_KNIGHT to MonsterLootEntry(
            minGp = 56, maxGp = 122,
            weightedDrops = listOf(
                WeightedLootDrop("ancient_bone", 2, 3, dropChance = 0.52f, weight = 28),
                WeightedLootDrop("quenchsteel_bastard", 1, 1, dropChance = 0.16f, weight = 16),
                WeightedLootDrop("steel_shield", 1, 1, dropChance = 0.22f, weight = 18),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.38f, weight = 22),
                WeightedLootDrop("hmm_rune", 1, 2, dropChance = 0.26f, weight = 16)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.MIRE_LURKER to MonsterLootEntry(
            minGp = 54, maxGp = 118,
            weightedDrops = listOf(
                WeightedLootDrop("mire_silver_ore", 1, 2, dropChance = 0.50f, weight = 30),
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.46f, weight = 26),
                WeightedLootDrop("venom_shafts", 6, 12, dropChance = 0.36f, weight = 20),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.32f, weight = 14),
                WeightedLootDrop("bog_iron_ward_pendant", 1, 1, dropChance = 0.12f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.BOG_LEECH_WEAVER to MonsterLootEntry(
            minGp = 52, maxGp = 116,
            weightedDrops = listOf(
                WeightedLootDrop("mire_silver_ore", 1, 2, dropChance = 0.46f, weight = 28),
                WeightedLootDrop("spider_silk", 2, 3, dropChance = 0.48f, weight = 26),
                WeightedLootDrop("hmm_rune", 1, 2, dropChance = 0.32f, weight = 20),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.32f, weight = 16),
                WeightedLootDrop("bilewood_conduit", 1, 1, dropChance = 0.14f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.TEMPLE_SERPENT_GUARD to MonsterLootEntry(
            minGp = 60, maxGp = 130,
            weightedDrops = listOf(
                WeightedLootDrop("jagged_ivory", 1, 2, dropChance = 0.48f, weight = 28),
                WeightedLootDrop("peat_coal_ore", 1, 2, dropChance = 0.44f, weight = 24),
                WeightedLootDrop("quenchsteel_bastard", 1, 1, dropChance = 0.16f, weight = 16),
                WeightedLootDrop("venom_shafts", 6, 12, dropChance = 0.36f, weight = 18),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.34f, weight = 14)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SUN_PRIEST_ACOLYTE to MonsterLootEntry(
            minGp = 58, maxGp = 128,
            weightedDrops = listOf(
                WeightedLootDrop("runic_slate", 1, 2, dropChance = 0.48f, weight = 28),
                WeightedLootDrop("fireball_rune", 1, 3, dropChance = 0.42f, weight = 26),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.36f, weight = 20),
                WeightedLootDrop("tallowbone_crosier", 1, 1, dropChance = 0.14f, weight = 14),
                WeightedLootDrop("mystic_robe", 1, 1, dropChance = 0.14f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.WARDEN_STONEHELM to MonsterLootEntry(
            minGp = 165, maxGp = 310,
            guaranteedDrops = listOf(
                ItemStack("bog_iron_ore", 4),
                ItemStack("health_potion", 2),
                ItemStack("hmm_rune", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("quenchsteel_bastard", 1, 1, dropChance = 0.32f, weight = 18),
                WeightedLootDrop("steel_shield", 1, 1, dropChance = 0.34f, weight = 18),
                WeightedLootDrop("brass_armor", 1, 1, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("quenchsteel_quarrels", 10, 16, dropChance = 0.54f, weight = 20),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.22f, weight = 14),
                WeightedLootDrop("duskmetal_vampire_band", 1, 1, dropChance = 0.22f, weight = 14)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.MIREMAW_THE_DROWNED to MonsterLootEntry(
            minGp = 195, maxGp = 365,
            guaranteedDrops = listOf(
                ItemStack("mire_silver_ore", 4),
                ItemStack("bile_gland", 3),
                ItemStack("health_potion", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("mire_woven_leggings", 1, 1, dropChance = 0.16f, weight = 9),
                WeightedLootDrop("mire_silver_ingot", 2, 3, dropChance = 0.50f, weight = 14),
                WeightedLootDrop("bog_steel_mace", 1, 1, dropChance = 0.14f, weight = 10),
                WeightedLootDrop("mire_woven_brigandine", 1, 1, dropChance = 0.14f, weight = 10),
                WeightedLootDrop("mire_woven_longbow", 1, 1, dropChance = 0.12f, weight = 8),
                WeightedLootDrop("venom_shafts", 12, 20, dropChance = 0.68f, weight = 22),
                WeightedLootDrop("healing_rune", 2, 4, dropChance = 0.60f, weight = 20),
                WeightedLootDrop("quenchsteel_bastard", 1, 1, dropChance = 0.28f, weight = 16),
                WeightedLootDrop("tallowbone_crosier", 1, 1, dropChance = 0.26f, weight = 14),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.20f, weight = 14),
                WeightedLootDrop("frostfang_coil", 1, 1, dropChance = 0.22f, weight = 14)
            ),
            bonusWeightRolls = 2
        ),
        // --- Murkfen Hollow (-29) ---
        MonsterType.BOG_HAG to MonsterLootEntry(
            minGp = 48, maxGp = 108,
            weightedDrops = listOf(
                WeightedLootDrop("mire_woven_hood", 1, 1, dropChance = 0.03f, weight = 3),
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.48f, weight = 28),
                WeightedLootDrop("hmm_rune", 1, 2, dropChance = 0.36f, weight = 24),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.34f, weight = 18),
                WeightedLootDrop("peat_coal_ore", 1, 2, dropChance = 0.40f, weight = 18),
                WeightedLootDrop("bog_iron_ward_pendant", 1, 1, dropChance = 0.10f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.GATOR_BRUTE to MonsterLootEntry(
            minGp = 46, maxGp = 104,
            weightedDrops = listOf(
                WeightedLootDrop("jagged_ivory", 1, 2, dropChance = 0.50f, weight = 30),
                WeightedLootDrop("mire_silver_ore", 1, 2, dropChance = 0.42f, weight = 26),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.36f, weight = 20),
                WeightedLootDrop("venom_shafts", 6, 10, dropChance = 0.30f, weight = 14)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.FEN_WISP to MonsterLootEntry(
            minGp = 42, maxGp = 98,
            weightedDrops = listOf(
                WeightedLootDrop("ember_heart", 1, 1, dropChance = 0.30f, weight = 24),
                WeightedLootDrop("peat_coal_ore", 1, 2, dropChance = 0.46f, weight = 26),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.40f, weight = 22),
                WeightedLootDrop("fireball_rune", 1, 2, dropChance = 0.20f, weight = 12)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.HAGMOTHER_VESSA to MonsterLootEntry(
            minGp = 170, maxGp = 320,
            guaranteedDrops = listOf(
                ItemStack("bile_gland", 3),
                ItemStack("mire_silver_ore", 3),
                ItemStack("mana_potion", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("mire_woven_hood", 1, 1, dropChance = 0.16f, weight = 9),
                WeightedLootDrop("mire_woven_waders", 1, 1, dropChance = 0.16f, weight = 9),
                WeightedLootDrop("mire_woven_staff", 1, 1, dropChance = 0.14f, weight = 10),
                WeightedLootDrop("bog_steel_sabre", 1, 1, dropChance = 0.14f, weight = 10),
                WeightedLootDrop("bog_steel_shield", 1, 1, dropChance = 0.12f, weight = 8),
                WeightedLootDrop("hmm_rune", 3, 6, dropChance = 0.66f, weight = 22),
                WeightedLootDrop("healing_rune", 2, 4, dropChance = 0.58f, weight = 20),
                WeightedLootDrop("tallowbone_crosier", 1, 1, dropChance = 0.26f, weight = 16),
                WeightedLootDrop("bog_iron_ward_pendant", 1, 1, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.16f, weight = 10)
            ),
            bonusWeightRolls = 2
        ),
        // --- Tomb of Akhet (-30) ---
        MonsterType.DUNE_SCORPION to MonsterLootEntry(
            minGp = 70, maxGp = 150,
            weightedDrops = listOf(
                WeightedLootDrop("bile_gland", 1, 2, dropChance = 0.48f, weight = 28),
                WeightedLootDrop("jagged_ivory", 1, 2, dropChance = 0.44f, weight = 26),
                WeightedLootDrop("venom_shafts", 8, 14, dropChance = 0.36f, weight = 20),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.36f, weight = 14)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SAND_WRAITH to MonsterLootEntry(
            minGp = 68, maxGp = 146,
            weightedDrops = listOf(
                WeightedLootDrop("ancient_bone", 2, 3, dropChance = 0.52f, weight = 28),
                WeightedLootDrop("runic_slate", 1, 2, dropChance = 0.40f, weight = 24),
                WeightedLootDrop("sd_rune", 1, 2, dropChance = 0.32f, weight = 20),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.40f, weight = 16)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SUNSCALE_BASILISK to MonsterLootEntry(
            minGp = 74, maxGp = 158,
            weightedDrops = listOf(
                WeightedLootDrop("colossus_tendon", 1, 1, dropChance = 0.30f, weight = 20),
                WeightedLootDrop("ember_heart", 1, 2, dropChance = 0.44f, weight = 28),
                WeightedLootDrop("jagged_ivory", 1, 2, dropChance = 0.42f, weight = 24),
                WeightedLootDrop("fireball_rune", 1, 2, dropChance = 0.26f, weight = 18),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.36f, weight = 16)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.AKHET_THE_SAND_PHARAOH to MonsterLootEntry(
            minGp = 250, maxGp = 450,
            guaranteedDrops = listOf(
                ItemStack("ancient_bone", 4),
                ItemStack("ember_heart", 2),
                ItemStack("sd_rune", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_sun_helm", 1, 1, dropChance = 0.12f, weight = 8),
                WeightedLootDrop("gorek_obsidian_horn", 1, 2, dropChance = 0.40f, weight = 14),
                WeightedLootDrop("quenchsteel_arbalest", 1, 1, dropChance = 0.16f, weight = 10),
                WeightedLootDrop("fireball_rune", 2, 4, dropChance = 0.68f, weight = 22),
                WeightedLootDrop("starlight_quarrels", 10, 18, dropChance = 0.54f, weight = 20),
                WeightedLootDrop("dragon_mail", 1, 1, dropChance = 0.26f, weight = 16),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.18f, weight = 14),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.24f, weight = 12)
            ),
            bonusWeightRolls = 2
        ),
        // --- Scorchglass Rift (-31) ---
        MonsterType.SCORCHGLASS_STALKER to MonsterLootEntry(
            minGp = 80, maxGp = 168,
            weightedDrops = listOf(
                WeightedLootDrop("colossus_tendon", 1, 1, dropChance = 0.34f, weight = 24),
                WeightedLootDrop("jagged_ivory", 1, 2, dropChance = 0.44f, weight = 22),
                WeightedLootDrop("gorek_obsidian_horn", 1, 1, dropChance = 0.20f, weight = 16),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.38f, weight = 16),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.04f, weight = 4)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.DUST_DEVIL to MonsterLootEntry(
            minGp = 76, maxGp = 160,
            weightedDrops = listOf(
                WeightedLootDrop("runic_slate", 1, 2, dropChance = 0.44f, weight = 26),
                WeightedLootDrop("vesperite_ore", 1, 2, dropChance = 0.40f, weight = 22),
                WeightedLootDrop("sd_rune", 1, 2, dropChance = 0.30f, weight = 18),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.40f, weight = 16),
                WeightedLootDrop("vesperite_catalyst_ring", 1, 1, dropChance = 0.03f, weight = 3)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SALTGLASS_GOLEM to MonsterLootEntry(
            minGp = 84, maxGp = 176,
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_ore", 2, 3, dropChance = 0.52f, weight = 28),
                WeightedLootDrop("colossus_tendon", 1, 1, dropChance = 0.26f, weight = 18),
                WeightedLootDrop("bog_iron_ore", 1, 2, dropChance = 0.40f, weight = 16),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.36f, weight = 14),
                WeightedLootDrop("vesperite_aegis_mail", 1, 1, dropChance = 0.03f, weight = 3)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.SIROCCO_THE_STORM_DJINN to MonsterLootEntry(
            minGp = 300, maxGp = 520,
            guaranteedDrops = listOf(
                ItemStack("vesperite_bar", 2),
                ItemStack("gorek_obsidian_horn", 2),
                ItemStack("runic_slate", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("vesperite_sandstriders", 1, 1, dropChance = 0.18f, weight = 9),
                WeightedLootDrop("vesperite_greaves", 1, 1, dropChance = 0.14f, weight = 8),
                WeightedLootDrop("sd_rune", 2, 4, dropChance = 0.66f, weight = 18),
                WeightedLootDrop("colossus_tendon", 1, 2, dropChance = 0.60f, weight = 16),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.26f, weight = 12),
                WeightedLootDrop("vesperite_cleaver", 1, 1, dropChance = 0.18f, weight = 10),
                WeightedLootDrop("vesperite_recurve_bow", 1, 1, dropChance = 0.18f, weight = 10),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.18f, weight = 10),
                WeightedLootDrop("vesperite_resonance_necklace", 1, 1, dropChance = 0.16f, weight = 9),
                WeightedLootDrop("vesperite_catalyst_ring", 1, 1, dropChance = 0.16f, weight = 9)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.BARROW_WIGHT to MonsterLootEntry(
            minGp = 100, maxGp = 205,
            weightedDrops = listOf(
                WeightedLootDrop("rime_plate_legguards", 1, 1, dropChance = 0.04f, weight = 3),
                WeightedLootDrop("ancient_bone", 2, 4, dropChance = 0.56f, weight = 30),
                WeightedLootDrop("glacier_quartz_ore", 1, 2, dropChance = 0.44f, weight = 26),
                WeightedLootDrop("frost_iron_ore", 1, 2, dropChance = 0.42f, weight = 24),
                WeightedLootDrop("health_potion", 1, 2, dropChance = 0.36f, weight = 12),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.12f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.FROST_BANSHEE to MonsterLootEntry(
            minGp = 108, maxGp = 215,
            weightedDrops = listOf(
                WeightedLootDrop("glacier_quartz_ore", 1, 2, dropChance = 0.50f, weight = 28),
                WeightedLootDrop("runic_slate", 1, 2, dropChance = 0.48f, weight = 26),
                WeightedLootDrop("sd_rune", 1, 2, dropChance = 0.38f, weight = 22),
                WeightedLootDrop("mana_potion", 1, 2, dropChance = 0.40f, weight = 16),
                WeightedLootDrop("mystic_robe", 1, 1, dropChance = 0.16f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.XOLATL_THE_SUN_FANG to MonsterLootEntry(
            minGp = 240, maxGp = 430,
            guaranteedDrops = listOf(
                ItemStack("peat_coal_ore", 4),
                ItemStack("ember_heart", 2),
                ItemStack("sd_rune", 2)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("fireball_rune", 2, 4, dropChance = 0.70f, weight = 22),
                WeightedLootDrop("starlight_quarrels", 10, 18, dropChance = 0.54f, weight = 20),
                WeightedLootDrop("dragon_mail", 1, 1, dropChance = 0.28f, weight = 16),
                WeightedLootDrop("dragon_shield", 1, 1, dropChance = 0.30f, weight = 16),
                WeightedLootDrop("vesperite_longblade", 1, 1, dropChance = 0.18f, weight = 14),
                WeightedLootDrop("stormglass_pendant", 1, 1, dropChance = 0.24f, weight = 12)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.LORD_MALGRATH_THE_UNDYING to MonsterLootEntry(
            minGp = 290, maxGp = 520,
            guaranteedDrops = listOf(
                ItemStack("glacier_quartz_ore", 3),
                ItemStack("frost_iron_ore", 3),
                ItemStack("sd_rune", 3)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("rime_plate_helm", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("rime_plate_sabatons", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("runic_slate", 2, 4, dropChance = 0.68f, weight = 22),
                WeightedLootDrop("mana_potion", 2, 4, dropChance = 0.62f, weight = 20),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.22f, weight = 16),
                WeightedLootDrop("vesperite_aegis_mail", 1, 1, dropChance = 0.20f, weight = 14),
                WeightedLootDrop("frostfang_coil", 1, 1, dropChance = 0.26f, weight = 14),
                WeightedLootDrop("vesperite_resonance_necklace", 1, 1, dropChance = 0.20f, weight = 14)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.SLAG_AUTOMATON to MonsterLootEntry(
            minGp = 155, maxGp = 305,
            weightedDrops = listOf(
                WeightedLootDrop("molten_core", 1, 1, dropChance = 0.30f, weight = 18),
                WeightedLootDrop("cinderite_ore", 1, 2, dropChance = 0.56f, weight = 30),
                WeightedLootDrop("vesperite_ore", 2, 3, dropChance = 0.52f, weight = 26),
                WeightedLootDrop("ember_heart", 1, 2, dropChance = 0.42f, weight = 20),
                WeightedLootDrop("health_potion", 2, 3, dropChance = 0.40f, weight = 14),
                WeightedLootDrop("vesperite_warmaul", 1, 1, dropChance = 0.08f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.ASH_PYROMANCER to MonsterLootEntry(
            minGp = 148, maxGp = 290,
            weightedDrops = listOf(
                WeightedLootDrop("molten_core", 1, 1, dropChance = 0.22f, weight = 18),
                WeightedLootDrop("cinderite_ore", 1, 2, dropChance = 0.50f, weight = 28),
                WeightedLootDrop("fireball_rune", 2, 4, dropChance = 0.56f, weight = 26),
                WeightedLootDrop("sd_rune", 1, 3, dropChance = 0.42f, weight = 22),
                WeightedLootDrop("mana_potion", 2, 3, dropChance = 0.44f, weight = 16),
                WeightedLootDrop("vesperite_conduit_staff", 1, 1, dropChance = 0.08f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.EMBER_DRAKE to MonsterLootEntry(
            minGp = 160, maxGp = 315,
            weightedDrops = listOf(
                WeightedLootDrop("molten_core", 1, 1, dropChance = 0.24f, weight = 18),
                WeightedLootDrop("wyrmscale_plate", 1, 2, dropChance = 0.54f, weight = 28),
                WeightedLootDrop("cinderite_ore", 1, 2, dropChance = 0.52f, weight = 26),
                WeightedLootDrop("dragon_ham", 1, 2, dropChance = 0.48f, weight = 22),
                WeightedLootDrop("dragon_tooth", 1, 2, dropChance = 0.34f, weight = 14),
                WeightedLootDrop("dragon_mail", 1, 1, dropChance = 0.14f, weight = 10)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.OBSIDIAN_CULTIST to MonsterLootEntry(
            minGp = 152, maxGp = 298,
            weightedDrops = listOf(
                WeightedLootDrop("molten_core", 1, 1, dropChance = 0.22f, weight = 18),
                WeightedLootDrop("runic_slate", 2, 3, dropChance = 0.54f, weight = 28),
                WeightedLootDrop("cinderite_ore", 1, 2, dropChance = 0.48f, weight = 26),
                WeightedLootDrop("sd_rune", 2, 3, dropChance = 0.46f, weight = 22),
                WeightedLootDrop("mana_potion", 2, 3, dropChance = 0.40f, weight = 16),
                WeightedLootDrop("vesperite_catalyst_ring", 1, 1, dropChance = 0.08f, weight = 8)
            ),
            bonusWeightRolls = 1
        ),
        MonsterType.VULKARIS_THE_FORGE_TITAN to MonsterLootEntry(
            minGp = 395, maxGp = 720,
            guaranteedDrops = listOf(
                ItemStack("cinderite_bar", 2),
                ItemStack("ember_heart", 3),
                ItemStack("health_potion", 3)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_tassets", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("cinderite_treads", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("molten_core", 2, 3, dropChance = 0.80f, weight = 20),
                WeightedLootDrop("cinderite_greataxe", 1, 1, dropChance = 0.20f, weight = 10),
                WeightedLootDrop("cinderite_overlord_band", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("cinderite_ore", 3, 5, dropChance = 0.78f, weight = 22),
                WeightedLootDrop("vesperite_bar", 2, 3, dropChance = 0.65f, weight = 18),
                WeightedLootDrop("cinderite_worldbreaker", 1, 1, dropChance = 0.22f, weight = 12),
                WeightedLootDrop("cinderite_bastion", 1, 1, dropChance = 0.22f, weight = 12),
                WeightedLootDrop("cinderite_warplate", 1, 1, dropChance = 0.20f, weight = 12),
                WeightedLootDrop("vesperite_warmaul", 1, 1, dropChance = 0.24f, weight = 12),
                WeightedLootDrop("cinderheart_sovereign_torc", 1, 1, dropChance = 0.18f, weight = 12)
            ),
            bonusWeightRolls = 2
        ),
        MonsterType.IGNIVAR_ASH_WING_WYRM to MonsterLootEntry(
            minGp = 415, maxGp = 750,
            guaranteedDrops = listOf(
                ItemStack("wyrmscale_plate", 3),
                ItemStack("cinderite_bar", 2),
                ItemStack("dragon_tooth", 3)
            ),
            weightedDrops = listOf(
                WeightedLootDrop("cinderite_crown", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("molten_core", 2, 3, dropChance = 0.80f, weight = 20),
                WeightedLootDrop("cinderite_bastion", 1, 1, dropChance = 0.18f, weight = 9),
                WeightedLootDrop("cinderite_warplate", 1, 1, dropChance = 0.18f, weight = 9),
                WeightedLootDrop("cinderheart_sovereign_torc", 1, 1, dropChance = 0.16f, weight = 8),
                WeightedLootDrop("cinderite_ore", 3, 5, dropChance = 0.80f, weight = 22),
                WeightedLootDrop("sd_rune", 2, 4, dropChance = 0.70f, weight = 18),
                WeightedLootDrop("cinderite_greatsword", 1, 1, dropChance = 0.22f, weight = 12),
                WeightedLootDrop("cinderite_ashen_bow", 1, 1, dropChance = 0.22f, weight = 12),
                WeightedLootDrop("cinderite_soulspire", 1, 1, dropChance = 0.22f, weight = 12),
                WeightedLootDrop("dragon_mail", 1, 1, dropChance = 0.36f, weight = 12),
                WeightedLootDrop("cinderite_overlord_band", 1, 1, dropChance = 0.18f, weight = 12)
            ),
            bonusWeightRolls = 2
        )
    )

    fun entryFor(type: MonsterType): MonsterLootEntry =
        entries[type] ?: MonsterLootEntry(minGp = type.goldMin, maxGp = type.goldMax)

    /**
     * Common gold-only loot chance for non-boss monsters (~48% of regular kills drop just gold
     * instead of rolling the monster's valuable item table, so not every kill is guaranteed valuable).
     */
    const val COMMON_GOLD_ONLY_CHANCE: Float = 0.48f

    /** Upper bound on a boss dropping any gear piece in one kill (shared across its gear entries). */
    const val BOSS_GEAR_CHANCE_PER_KILL: Double = 0.35

    /**
     * Universal rare Health Potion drop chance for any monster species whose table
     * does not already explicitly include `health_potion` in its guaranteed or weighted drops.
     * Scales gently with monster strength (5% low-tier, 7% mid-tier, 9% high-tier, 14% boss).
     */
    fun rareHealthPotionChance(type: MonsterType): Float = when {
        type.isBoss -> 0.14f
        type.maxHp >= 300 -> 0.09f
        type.maxHp >= 100 -> 0.07f
        else -> 0.05f
    }

    fun hasExplicitHealthPotion(table: MonsterLootEntry): Boolean =
        table.guaranteedDrops.any { it.itemId == "health_potion" } ||
            table.weightedDrops.any { it.itemId == "health_potion" }

    /**
     * Rolls gold plus either a common gold-only outcome on regular monsters (~48% chance) or
     * the species' guaranteed, independent weighted, and boss bonus drops, alongside the
     * universal rare Health Potion roll for monsters without an explicit potion entry.
     * Merges duplicate stackable items into clean [ItemStack] entries.
     */
    fun rollLoot(type: MonsterType, rng: Random = Random.Default, monsterLevel: Int = type.threatLevel): RolledLoot {
        val table = entryFor(type)
        val level = monsterLevel.coerceAtLeast(1)
        fun allowed(itemId: String) = LootLevels.canDrop(itemId, level)
        fun rarityMult(itemId: String): Float = LootLevels.rarityOf(itemId).let { if (type.isBoss) it.bossMultiplier else it.regularMultiplier }
        val lo = minOf(table.minGp, table.maxGp).coerceAtLeast(0)
        val hi = maxOf(table.minGp, table.maxGp).coerceAtLeast(lo)
        val gold = if (lo == hi) lo else rng.nextInt(lo, hi + 1)

        val rolled = mutableListOf<ItemStack>()
        val isCommonGoldOnly = !type.isBoss && rng.nextFloat() < COMMON_GOLD_ONLY_CHANCE

        if (!isCommonGoldOnly) {
            rolled.addAll(table.guaranteedDrops.filter { allowed(it.itemId) })

            // Independent chance evaluation per weighted entry
            val pickedIds = mutableSetOf<String>()
            val eligible = table.weightedDrops.filter { allowed(it.itemId) }
            fun isGearDrop(drop: WeightedLootDrop) = ItemCatalog.all[drop.itemId]?.let { LootLevels.isGear(it) } == true
            // Bosses share one gear budget across all their gear entries, so a boss with many gear
            // pieces doesn't hand one out almost every kill.
            val gearScale = if (type.isBoss) {
                val total = eligible.filter { isGearDrop(it) }.sumOf { (it.dropChance * rarityMult(it.itemId)).toDouble() }
                if (total > BOSS_GEAR_CHANCE_PER_KILL) (BOSS_GEAR_CHANCE_PER_KILL / total).toFloat() else 1f
            } else 1f
            for (drop in eligible) {
                val chance = drop.dropChance * rarityMult(drop.itemId) * (if (isGearDrop(drop)) gearScale else 1f)
                if (rng.nextFloat() <= chance.coerceIn(0f, 1f)) {
                    // Regular monsters far above a piece of gear's level salvage it into a local ore instead
                    // (a Lv 33 badlands skeleton no longer hands out a Lv 1 starter dirk).
                    if (!type.isBoss && isGearDrop(drop) && LootLevels.isOutgrown(drop.itemId, level)) {
                        rolled.add(ItemStack(LootLevels.salvageOreFor(level), 1))
                        continue
                    }
                    val qty = rollQty(drop.minQty, drop.maxQty, rng)
                    if (qty > 0) {
                        rolled.add(ItemStack(drop.itemId, qty))
                        pickedIds.add(drop.itemId)
                    }
                }
            }

            // Weighted bonus roll(s) reserved for bosses so regular monsters are never forced to drop items
            // Boss bonus picks come from supplies and materials only (gear is governed by the budget above);
            // uncommon materials get proportionally less weight.
            val bonusPool = eligible.filter { !isGearDrop(it) }
            fun bonusWeight(drop: WeightedLootDrop): Int = (drop.weight.coerceAtLeast(1) * 20 * rarityMult(drop.itemId)).toInt().coerceAtLeast(1)
            if (type.isBoss && bonusPool.isNotEmpty() && table.bonusWeightRolls > 0) {
                val totalWeight = bonusPool.sumOf { bonusWeight(it) }
                repeat(table.bonusWeightRolls) {
                    var cursor = rng.nextInt(totalWeight)
                    var chosen = bonusPool.first()
                    for (drop in bonusPool) {
                        val w = bonusWeight(drop)
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
        }

        // Universal rare Health Potion drop for any monster species without an explicit potion entry
        if (!hasExplicitHealthPotion(table)) {
            val rareChance = rareHealthPotionChance(type)
            if (rng.nextFloat() <= rareChance) {
                val qty = if (type.isBoss || type.maxHp >= 300) rollQty(1, 2, rng) else 1
                rolled.add(ItemStack("health_potion", qty))
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

/**
 * How likely an item is to drop, relative to the chance written in a monster's loot table.
 * Everyday supplies keep their odds; runes and valuable materials are uncommon; gear gets rarer
 * the higher its tier, so the best pieces take real grinding (and bosses stay the best source).
 */
enum class LootRarity(val regularMultiplier: Float, val bossMultiplier: Float) {
    /** Ores, basic materials, potions, ammo, food. */
    COMMON(1.0f, 1.0f),
    /** Attack/heal runes, ingots and valuable materials (sell 100+ gp). */
    UNCOMMON(0.5f, 0.75f),
    /** Tier 1-2 gear: roughly 1 in 40-80 kills from regular monsters. */
    RARE(0.2f, 0.3f),
    /** Tier 3 gear: roughly 1 in 150-300 kills from regular monsters. */
    VERY_RARE(0.08f, 0.2f),
    /** Tier 4+ gear: boss-hunting rewards. */
    LEGENDARY(0.04f, 0.1f)
}

/**
 * Level gating for loot: every item gets an "item level" (gear: its required level; runes: the
 * level of their tier; ores, ingots and crafting materials: the level of the lowest gear they are
 * crafted into), and a monster only drops items up to [LOOT_LEVEL_SLACK] levels above its own.
 * Low-level monsters therefore never hand out materials for high-level equipment.
 */
object LootLevels {
    const val LOOT_LEVEL_SLACK = 3

    private val gearCategories = setOf(
        ItemCategory.WEAPON_MELEE, ItemCategory.WEAPON_DISTANCE, ItemCategory.WEAPON_WAND,
        ItemCategory.SHIELD, ItemCategory.ARMOR, ItemCategory.HELMET, ItemCategory.LEGS,
        ItemCategory.BOOTS, ItemCategory.AMULET, ItemCategory.RING,
        ItemCategory.TOOL_PICKAXE, ItemCategory.TOOL_HATCHET, ItemCategory.TOOL_FISHING_ROD
    )

    fun isGear(def: ItemDefinition): Boolean = def.category in gearCategories

    /** Lowest required level of gear at each equipment tier (T1 -> 1, T2 -> 8, T3 -> 18, ...). */
    private val levelForTier: Map<Int, Int> by lazy {
        ItemCatalog.all.values
            .filter { isGear(it) && it.equipmentTier > 0 }
            .groupBy { it.equipmentTier }
            .mapValues { (_, defs) -> defs.minOf { it.requiredLevel } }
    }

    fun tierLevel(tier: Int): Int {
        if (tier <= 1) return 1
        return levelForTier[tier] ?: levelForTier.filterKeys { it < tier }.maxByOrNull { it.key }?.value ?: 1
    }

    private val allRecipes: List<CraftingRecipe> by lazy {
        RecipeCatalog.smeltingRecipes + RecipeCatalog.forgingRecipes + RecipeCatalog.fletchingRecipes + RecipeCatalog.runecraftingRecipes
    }

    private val usedIn: Map<String, List<CraftingRecipe>> by lazy {
        val map = HashMap<String, MutableList<CraftingRecipe>>()
        for (r in allRecipes) for (ing in r.ingredients) map.getOrPut(ing.itemId) { mutableListOf() }.add(r)
        map
    }

    private val cache = HashMap<String, Int>()

    /** Item level used for drop gating (see class docs). */
    fun itemLevel(itemId: String): Int = cache[itemId] ?: computeLevel(itemId, HashSet()).also { cache[itemId] = it }

    private fun ownLevel(def: ItemDefinition): Int = when {
        def.pickaxeTier > 0 -> maxOf(def.requiredLevel, tierLevel(def.pickaxeTier))
        def.fishingTier > 0 -> maxOf(def.requiredLevel, tierLevel(def.fishingTier))
        isGear(def) -> maxOf(def.requiredLevel, tierLevel(def.equipmentTier))
        def.runeTier > 0 -> tierLevel(def.runeTier)
        else -> def.requiredLevel.coerceAtLeast(1)
    }

    private fun computeLevel(itemId: String, visiting: MutableSet<String>): Int {
        val def = ItemCatalog.all[itemId] ?: return 1
        val own = ownLevel(def)
        if (isGear(def) || def.runeTier > 0 || def.category == ItemCategory.RUNE_BLANK) return own
        if (!visiting.add(itemId)) return own
        // Crafting inputs (ores, ingots, hides, scales...) inherit the level of the cheapest gear they lead to.
        // Ammo is a by-product of most materials, not what they exist for, so it never lowers a material's level.
        val downstream = usedIn[itemId].orEmpty()
            .filter { it.outputItemId != itemId && ItemCatalog.all[it.outputItemId]?.category != ItemCategory.AMMO }
            .map { computeLevel(it.outputItemId, visiting) }
        visiting.remove(itemId)
        return if (downstream.isEmpty()) own else maxOf(own, downstream.min())
    }

    fun rarityOf(itemId: String): LootRarity {
        val def = ItemCatalog.all[itemId] ?: return LootRarity.COMMON
        if (isGear(def)) {
            val tier = if (def.equipmentTier > 0) def.equipmentTier else 2
            return when {
                tier >= 4 -> LootRarity.LEGENDARY
                tier == 3 -> LootRarity.VERY_RARE
                else -> LootRarity.RARE
            }
        }
        return when {
            def.category == ItemCategory.RUNE_ATTACK || def.category == ItemCategory.RUNE_HEAL -> LootRarity.UNCOMMON
            def.category == ItemCategory.METAL_INGOT -> LootRarity.UNCOMMON
            (def.category == ItemCategory.CRAFT_MATERIAL || def.category == ItemCategory.LOOT_VALUABLE) && def.sellPrice >= 100 -> LootRarity.UNCOMMON
            else -> LootRarity.COMMON
        }
    }

    fun canDrop(itemId: String, monsterLevel: Int): Boolean = itemLevel(itemId) <= monsterLevel + LOOT_LEVEL_SLACK

    /** Gear this many levels below the monster is "outgrown" and gets salvaged instead of dropped. */
    const val OUTGROWN_GEAR_GAP = 20

    fun isOutgrown(itemId: String, monsterLevel: Int): Boolean {
        val def = ItemCatalog.all[itemId] ?: return false
        return isGear(def) && !def.isTool && itemLevel(itemId) + OUTGROWN_GEAR_GAP < monsterLevel
    }

    /** Ore of the monster's own level band: marsh mire-silver, badlands vesperite, highland frost-iron, caldera cinderite. */
    fun salvageOreFor(monsterLevel: Int): String = when {
        monsterLevel >= 55 -> "cinderite_ore"
        monsterLevel >= 42 -> "frost_iron_ore"
        monsterLevel >= 30 -> "vesperite_ore"
        monsterLevel >= 20 -> "mire_silver_ore"
        else -> "bog_iron_ore"
    }
}
