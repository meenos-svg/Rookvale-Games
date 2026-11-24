package com.example.game.model

enum class CraftingTab(val label: String) {
    KNIGHT("Knight"),
    PALADIN("Paladin"),
    MAGE("Mage"),
    AMMO("Ammo"),
    TOOLS("Armor/Tools"),
    JEWELRY("Jewelry")
}

data class CraftingRecipe(
    val id: String,
    val outputItemId: String,
    val outputCount: Int = 1,
    val ingredients: List<ItemStack>,
    val station: ActivePanel,
    val tab: CraftingTab = CraftingTab.KNIGHT,
    val requiredMagicLevel: Int = 0,
    val manaCost: Int = 0,
    val soulCost: Int = 0
)

object ItemCatalog {
    val all: Map<String, ItemDefinition> = listOf(
        // --- Tools & Supplies (Alaric's Shop sells ONLY consumables & starter tools — ZERO weapons) ---
        ItemDefinition("rope", "Rope", ItemCategory.TOOL_ROPE, 18.0, buyPrice = 50, sellPrice = 15, description = "Essential to climb back up underground rope-holes."),
        ItemDefinition("fishing_rod", "Fishing Rod", ItemCategory.TOOL_FISHING_ROD, 8.5, buyPrice = 60, sellPrice = 20, description = "Cast into water at the Rookvale pier to catch fish."),
        ItemDefinition("hatchet", "Hatchet", ItemCategory.TOOL_HATCHET, 18.0, buyPrice = 65, sellPrice = 22, description = "Sturdy woodcutter's hatchet. Tap adjacent overland trees to chop Wood Logs."),
        ItemDefinition("health_potion", "Health Potion", ItemCategory.POTION_HP, 2.7, buyPrice = 45, sellPrice = 18, restoreAmount = 95, stackable = true, description = "Restores ~95 HP and eases poison."),
        ItemDefinition("mana_potion", "Mana Potion", ItemCategory.POTION_MP, 2.7, buyPrice = 50, sellPrice = 20, restoreAmount = 80, stackable = true, description = "Restores ~80 Mana instantly."),
        ItemDefinition("blank_rune", "Blank Rune", ItemCategory.RUNE_BLANK, 0.3, buyPrice = 15, sellPrice = 5, stackable = true, description = "Uninscribed runestone. Take to Rookvale's Runecrafting Table to imbue spells."),
        ItemDefinition("magic_dart_rune", "Arcane Dart Rune", ItemCategory.RUNE_ATTACK, 1.0, sellPrice = 8, attack = 28, range = 5, requiredMagicLevel = 1, runeTier = 1, manaCost = 8, stackable = true, description = "Tier 1 offensive rune (Magic Lv 1). Fires a crisp arcane bolt."),
        ItemDefinition("healing_rune", "Intense Healing Rune", ItemCategory.RUNE_HEAL, 1.1, sellPrice = 16, restoreAmount = 135, range = 5, requiredMagicLevel = 4, runeTier = 1, manaCost = 14, stackable = true, description = "Healing rune (Magic Lv 4). Restores strong HP on self or a tapped ally & eases poison."),
        ItemDefinition("hmm_rune", "Heavy Magic Missile", ItemCategory.RUNE_ATTACK, 1.2, sellPrice = 24, attack = 48, range = 5, requiredMagicLevel = 7, runeTier = 2, manaCost = 14, stackable = true, description = "Tier 2 offensive rune (Magic Lv 7). Piercing lightning bolt with arcane shock."),
        ItemDefinition("fireball_rune", "Great Fireball Rune", ItemCategory.RUNE_ATTACK, 1.3, sellPrice = 42, attack = 68, range = 5, requiredMagicLevel = 12, aoeRadius = 1, runeTier = 3, manaCost = 22, stackable = true, description = "Tier 3 AoE rune (Magic Lv 12). Unleashes a 3x3 fiery splash blast around your target."),
        ItemDefinition("sd_rune", "Sudden Death Rune", ItemCategory.RUNE_ATTACK, 1.4, sellPrice = 75, attack = 108, range = 5, requiredMagicLevel = 15, aoeRadius = 1, runeTier = 4, manaCost = 35, stackable = true, description = "Tier 4 master rune (Magic Lv 15). Devastating death blast & shockwave for bosses."),
        ItemDefinition("fresh_fish", "Fresh Fish", ItemCategory.FOOD, 5.2, buyPrice = 12, sellPrice = 6, restoreAmount = 35, stackable = true, description = "Caught at Rookvale Pier. Restores 35 HP and 20 MP."),
        ItemDefinition("fresh_meat", "Fresh Meat", ItemCategory.FOOD, 6.0, buyPrice = 0, sellPrice = 5, restoreAmount = 32, stackable = true, description = "Hearty wild game meat dropped by outskirts critters. Restores 32 HP."),
        ItemDefinition("dragon_ham", "Wyrm Haunch", ItemCategory.FOOD, 12.0, buyPrice = 0, sellPrice = 28, restoreAmount = 85, stackable = true, description = "Rich roasted meat. Restores 85 HP and 45 MP."),
        ItemDefinition("cheese", "Cellar Curd", ItemCategory.FOOD, 4.0, buyPrice = 6, sellPrice = 2, restoreAmount = 22, stackable = true, description = "Simple dungeon rations. Restores 22 HP."),

        // --- Original Rookvale Pickaxe Tiers ---
        ItemDefinition("quarry_mattock", "Quarry Mattock", ItemCategory.TOOL_PICKAXE, 22.0, buyPrice = 75, sellPrice = 25, pickaxeTier = 1, description = "Tier 1 pickaxe. Extracts Copper & Tin Veins."),
        ItemDefinition("sunbrass_pick", "Sunbrass Pick", ItemCategory.TOOL_PICKAXE, 26.0, sellPrice = 70, pickaxeTier = 2, description = "Tier 2 forged pick. Extracts up to Bog Iron Veins."),
        ItemDefinition("duskmetal_breaker", "Duskmetal Breaker", ItemCategory.TOOL_PICKAXE, 30.0, sellPrice = 140, pickaxeTier = 3, description = "Tier 3 forged pick. Extracts up to Vesperite Veins (+15% yield)."),
        ItemDefinition("quenchsteel_delver", "Quenchsteel Delver", ItemCategory.TOOL_PICKAXE, 34.0, sellPrice = 260, pickaxeTier = 4, description = "Tier 4 master pick. Extracts Cinderite Veins (+30% yield)."),

        // --- Five Original Rookvale Raw Ores & Smelted Bars ---
        ItemDefinition("copper_ore", "Copper Ore", ItemCategory.RAW_ORE, 12.0, sellPrice = 10, stackable = true, description = "Warm burnished copper ore chipped from outskirts limestone ridges."),
        ItemDefinition("tin_ore", "Tin Ore", ItemCategory.RAW_ORE, 12.0, sellPrice = 11, stackable = true, description = "Silvery-pewter ore mined from outer Rookvale shale outcrops."),
        ItemDefinition("bog_iron_ore", "Bog Iron Ore", ItemCategory.RAW_ORE, 17.0, sellPrice = 19, stackable = true, description = "Heavy oxidized ferrous nodules pried from marshes and outer crags."),
        ItemDefinition("vesperite_ore", "Vesperite Ore", ItemCategory.RAW_ORE, 20.0, sellPrice = 42, stackable = true, description = "Luminous violet-silver crystalline ore mined in Floor -1 and The Grimmaw Quarry."),
        ItemDefinition("cinderite_ore", "Cinderite Ore", ItemCategory.RAW_ORE, 23.0, sellPrice = 68, stackable = true, description = "Glowing volcanic-gold and obsidian ore extracted from Floor -2 and The Gorek Labyrinth."),

        ItemDefinition("sunbrass_ingot", "Sunbrass Bar", ItemCategory.METAL_INGOT, 16.0, sellPrice = 26, stackable = true, description = "Refined Sunbrass bronze bar smelted from Copper Ore and Tin Ore."),
        ItemDefinition("duskmetal_ingot", "Bog-Iron Bar", ItemCategory.METAL_INGOT, 20.0, sellPrice = 46, stackable = true, description = "Dense forged iron bar smelted from 2 Bog Iron Ore."),
        ItemDefinition("quenchsteel_ingot", "Quenchsteel Bar", ItemCategory.METAL_INGOT, 22.0, sellPrice = 85, stackable = true, description = "Tempered alloy bar smelted from Bog Iron Ore and Vesperite Ore."),
        ItemDefinition("vesperite_bar", "Vesperite Bar", ItemCategory.METAL_INGOT, 22.0, sellPrice = 115, stackable = true, description = "Crystalline violet-silver Tier 4 bar smelted from 2 Vesperite Ore."),
        ItemDefinition("cinderite_bar", "Cinderite Bar", ItemCategory.METAL_INGOT, 25.0, sellPrice = 195, stackable = true, description = "Volcanic obsidian-gold Tier 5 bar smelted from 2 Cinderite Ore."),
        ItemDefinition("moonforge_ingot", "Moonforge Bar", ItemCategory.METAL_INGOT, 24.0, sellPrice = 165, stackable = true, description = "Radiant deep-forged bar alloyed from Vesperite Ore and Cinderite Ore."),

        // --- Original Monster & Gathering Crafting Materials ---
        ItemDefinition("wood_log", "Wood Log", ItemCategory.CRAFT_MATERIAL, 2.0, sellPrice = 8, stackable = true, description = "Fresh timber chopped from overland trees. Fletched into bows & arrows at Rookvale's Fletching Workbench."),
        ItemDefinition("wild_feather", "Wild Feather", ItemCategory.CRAFT_MATERIAL, 0.1, sellPrice = 6, stackable = true, description = "Light fletching plume dropped by outskirts critters & Goblin Scouts."),
        ItemDefinition("brindle_hide", "Brindle Hide", ItemCategory.CRAFT_MATERIAL, 3.2, sellPrice = 16, stackable = true, description = "Tough grey-striped pelt stripped from Crag Wolves & Trolls."),
        ItemDefinition("jagged_ivory", "Jagged Ivory", ItemCategory.CRAFT_MATERIAL, 1.4, sellPrice = 14, stackable = true, description = "Razor predator fang used for blades and fletching."),
        ItemDefinition("spider_silk", "Gossamer Strand", ItemCategory.CRAFT_MATERIAL, 0.8, sellPrice = 24, stackable = true, description = "Tensile subterranean silk spun by Poison Spiders."),
        ItemDefinition("bile_gland", "Bile Gland", ItemCategory.CRAFT_MATERIAL, 1.2, sellPrice = 28, stackable = true, description = "Caustic venom sac harvested from Snakes & Poison Spiders."),
        ItemDefinition("vesper_wing", "Vesper Wing", ItemCategory.CRAFT_MATERIAL, 0.9, sellPrice = 18, stackable = true, description = "Nimble dark hide scraps carried by Goblin Scouts for fletching."),
        ItemDefinition("ancient_bone", "Marrow Shard", ItemCategory.CRAFT_MATERIAL, 3.8, sellPrice = 24, stackable = true, description = "Calcified crypt bone dropped by Skeletons & Ghouls."),
        ItemDefinition("ghoul_gem", "Wraith Tallow", ItemCategory.CRAFT_MATERIAL, 1.5, sellPrice = 52, stackable = true, description = "Eerie condensed grave-wax dropped by Ghouls & Necromancers."),
        ItemDefinition("colossus_tendon", "Colossus Tendon", ItemCategory.CRAFT_MATERIAL, 4.5, sellPrice = 68, stackable = true, description = "Sinewy giant cord dropped by Orc Warriors & Grimmaw quarry giants."),
        ItemDefinition("runic_slate", "Runic Slate", ItemCategory.CRAFT_MATERIAL, 6.0, sellPrice = 80, stackable = true, description = "Etched obsidian tablet carried by Necromancers & Gorek Hex-Callers."),
        ItemDefinition("ember_heart", "Ember Heart", ItemCategory.CRAFT_MATERIAL, 2.6, sellPrice = 115, stackable = true, description = "Pulsing volcanic cinder harvested from Fire Devils & Demon Lords."),
        ItemDefinition("wyrmscale_plate", "Wyrmscale Plate", ItemCategory.CRAFT_MATERIAL, 5.5, sellPrice = 150, stackable = true, description = "Emerald fire-hardened scale from Dragon Hatchlings & Elder Dragons."),
        ItemDefinition("dragon_tooth", "Draketalon", ItemCategory.CRAFT_MATERIAL, 3.0, sellPrice = 190, stackable = true, description = "Curved boss trophy taken from the Demon Lord or Elder Dragon."),
        ItemDefinition("grimmaw_stone_core", "Grimmaw Stone Core", ItemCategory.CRAFT_MATERIAL, 4.5, sellPrice = 145, stackable = true, description = "Resonant granite-violet heart taken from Grimmaw giants & Kragor the Stone-Eyed. Used to forge Vesperite weapons."),
        ItemDefinition("gorek_obsidian_horn", "Gorek Obsidian Horn", ItemCategory.CRAFT_MATERIAL, 4.8, sellPrice = 210, stackable = true, description = "Charred horn trophy claimed from Gorek warbands & Vaelgor the Labyrinth Warden. Used to forge Cinderite weapons."),

        // --- Multi-Tier Forged Weapons: KNIGHT (Melee — Sword, Axe, Club) ---
        // Tier 1 (Starter / Bronze / Sunbrass): Level 1, Skill 1
        ItemDefinition("wooden_club", "Wooden Club", ItemCategory.WEAPON_MELEE, 21.0, sellPrice = 12, attack = 11, defense = 6, attackIntervalMs = 1000L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.CLUB to 1, description = "Rough knotted cudgel dropped by lowland pests."),
        ItemDefinition("sunbrass_dirk", "Sunbrass Dirk", ItemCategory.WEAPON_MELEE, 18.0, sellPrice = 55, attack = 15, defense = 8, attackIntervalMs = 800L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SWORD to 1, description = "Swift 1H bronze-tinted dagger for agile strikes."),
        ItemDefinition("rookvale_gladius", "Rookvale Gladius", ItemCategory.WEAPON_MELEE, 29.0, sellPrice = 75, attack = 19, defense = 13, attackIntervalMs = 1000L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SWORD to 1, description = "Balanced 1H forged shortblade with steady guard."),
        // Tier 2 (Bog Iron / Duskmetal): Level 8, Skill 12
        ItemDefinition("duskmetal_falchion", "Duskmetal Falchion", ItemCategory.WEAPON_MELEE, 36.0, sellPrice = 145, attack = 26, defense = 17, attackIntervalMs = 1000L, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.SWORD to 12, description = "Curved 1H dark-ferrous chopper forged in Rookvale."),
        ItemDefinition("bog_cleaver_axe", "Bog-Cleaver Axe", ItemCategory.WEAPON_MELEE, 54.0, sellPrice = 175, attack = 34, defense = 12, attackIntervalMs = 1400L, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.AXE to 12, isTwoHanded = true, description = "Heavy 2H bearded axe delivering crushing blows without a shield."),
        // Tier 3 (Steel / Quenchsteel): Level 18, Skill 22
        ItemDefinition(
            "quenchsteel_bastard", "Quenchsteel Bastard", ItemCategory.WEAPON_MELEE, 42.0, sellPrice = 260,
            attack = 33, defense = 21, attackIntervalMs = 1000L, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.SWORD to 22, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Tempered Arc", triggerChancePercent = 15, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.5f, maxTargets = 3, damageMultiplier = 0.70f, elementColor = 0xFF8FD3FFL,
                bonusEffect = ProcBonusEffect.NONE, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Sweeps a tempered steel shockwave across nearby foes."
            ),
            description = "1H blade tempered over Vesperite sparks. Knight Tier 3 mastery."
        ),
        ItemDefinition(
            "colossus_glaive", "Colossus Glaive", ItemCategory.WEAPON_MELEE, 68.0, sellPrice = 310,
            attack = 44, defense = 16, attackIntervalMs = 1400L, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.AXE to 22, isTwoHanded = true, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Crushing Sweep", triggerChancePercent = 16, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.7f, maxTargets = 3, damageMultiplier = 0.75f, elementColor = 0xFFE6A24CL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Cleaves a wide horizontal arc that cracks monster armor."
            ),
            description = "Massive 2H polearm bound in Colossus Tendon. Knight Tier 3 mastery."
        ),
        // Tier 4 (Moonforge / Vesperite): Level 28, Skill 32
        ItemDefinition(
            "moonforge_rapier", "Moonforge Rapier", ItemCategory.WEAPON_MELEE, 26.0, sellPrice = 490,
            attack = 37, defense = 22, attackIntervalMs = 800L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.SWORD to 32, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Astral Cleave", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.7f, maxTargets = 3, damageMultiplier = 0.80f, elementColor = 0xFFB692FFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Flashes a silver-violet arc slicing adjacent monsters."
            ),
            description = "Feather-light 1H star-forged dueling blade. Knight Tier 4 mastery."
        ),
        ItemDefinition(
            "wyrmtooth_claymore", "Wyrmtooth Claymore", ItemCategory.WEAPON_MELEE, 62.0, sellPrice = 620,
            attack = 52, defense = 25, attackIntervalMs = 1200L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.SWORD to 32, isTwoHanded = true, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Astral Cleave", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.9f, maxTargets = 4, damageMultiplier = 0.88f, elementColor = 0xFFB692FFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Unleashes a wide serrated blade shockwave."
            ),
            description = "Serrated 2H greatsword edged with Draketalon. Knight Tier 4 mastery."
        ),

        // --- TIER 4 (VESPERITE) & TIER 5 (CINDERITE) ORIGINAL ROOKVALE WEAPONS (ALL 5 COMBAT CLASSES) ---
        // Sword
        ItemDefinition(
            "vesperite_longblade", "Vesperite Longblade", ItemCategory.WEAPON_MELEE, 34.0, sellPrice = 740,
            attack = 46, defense = 26, attackIntervalMs = 950L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.SWORD to 32, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Astral Cleave", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.8f, maxTargets = 4, damageMultiplier = 0.85f, elementColor = 0xFFB692FFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Sweeps a luminous violet blade arc through up to 4 foes, shattering armor."
            ),
            description = "Tier 4 crystalline violet-silver 1H longblade forged from Vesperite Bars. Pairs with a shield."
        ),
        ItemDefinition(
            "cinderite_greatsword", "Cinderite Greatsword", ItemCategory.WEAPON_MELEE, 64.0, sellPrice = 1180,
            attack = 64, defense = 30, attackIntervalMs = 1200L, equipmentTier = 5, requiredLevel = 40,
            requiredSkill = SkillType.SWORD to 42, isTwoHanded = true, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Cataclysm Wave", triggerChancePercent = 25, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.4f, maxTargets = 5, damageMultiplier = 1.15f, elementColor = 0xFFFF6B2BL,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Unleashes an expanding volcanic wave scorching up to 5 monsters."
            ),
            description = "Tier 5 obsidian-and-gold 2H greatsword blazing with volcanic Cinderite heat."
        ),
        // Axe
        ItemDefinition(
            "vesperite_cleaver", "Vesperite Cleaver", ItemCategory.WEAPON_MELEE, 38.0, sellPrice = 760,
            attack = 48, defense = 22, attackIntervalMs = 1050L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.AXE to 32, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Shatter Pulse", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.8f, maxTargets = 4, damageMultiplier = 0.90f, elementColor = 0xFFA282FFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Releases a resonant crystalline pulse that sunders enemy armor."
            ),
            description = "Tier 4 amethyst-silver 1H bearded battle-cleaver honed in the Grimmaw Quarry."
        ),
        ItemDefinition(
            "cinderite_greataxe", "Cinderite Greataxe", ItemCategory.WEAPON_MELEE, 70.0, sellPrice = 1240,
            attack = 68, defense = 24, attackIntervalMs = 1300L, equipmentTier = 5, requiredLevel = 40,
            requiredSkill = SkillType.AXE to 42, isTwoHanded = true, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Magma Eruption", triggerChancePercent = 24, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.3f, maxTargets = 5, damageMultiplier = 1.20f, elementColor = 0xFFFF5126L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Ruptures molten ground beneath nearby enemies, igniting up to 5 foes."
            ),
            description = "Tier 5 double-bit volcanic 2H greataxe prized by Gorek warlords."
        ),
        // Club / Mace
        ItemDefinition(
            "vesperite_warmaul", "Vesperite Warmaul", ItemCategory.WEAPON_MELEE, 58.0, sellPrice = 790,
            attack = 56, defense = 24, attackIntervalMs = 1250L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.CLUB to 32, isTwoHanded = true, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Thunderous Slam", triggerChancePercent = 22, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.0f, maxTargets = 4, damageMultiplier = 0.95f, elementColor = 0xFF7EC8FFL,
                bonusEffect = ProcBonusEffect.STAGGER_SLOW, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Smashes a seismic tremor that staggers and slows surrounding monsters."
            ),
            description = "Tier 4 faceted violet-quartz 2H warmaul built to shatter granite armor."
        ),
        ItemDefinition(
            "cinderite_worldbreaker", "Cinderite Worldbreaker", ItemCategory.WEAPON_MELEE, 76.0, sellPrice = 1320,
            attack = 72, defense = 28, attackIntervalMs = 1400L, equipmentTier = 5, requiredLevel = 40,
            requiredSkill = SkillType.CLUB to 42, isTwoHanded = true, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Meteor Impact", triggerChancePercent = 25, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.5f, maxTargets = 6, damageMultiplier = 1.25f, elementColor = 0xFFFF9E2CL,
                bonusEffect = ProcBonusEffect.STAGGER_SLOW, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Drops a cataclysmic molten shockwave staggering up to 6 monsters."
            ),
            description = "Tier 5 colossal obsidian-and-molten-gold 2H maul of earth-splitting force."
        ),

        // --- Multi-Tier Forged & Fletched Weapons: PALADIN (Distance) ---
        ItemDefinition("ashwood_shortbow", "Ashwood Shortbow", ItemCategory.WEAPON_DISTANCE, 20.0, sellPrice = 60, attack = 16, range = 4, attackIntervalMs = 1000L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.DISTANCE to 1, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, description = "Compact 2H bow firing wooden & forged Arrows/Shafts."),
        ItemDefinition("hunting_bow", "Hunting Bow", ItemCategory.WEAPON_DISTANCE, 22.0, sellPrice = 95, attack = 20, range = 5, attackIntervalMs = 950L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.DISTANCE to 1, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, description = "Seasoned hardwood 2H bow fletched with sinew and hide. Fires Arrows & Shafts."),
        ItemDefinition("ivory_javelin", "Ivory-Tipped Javelin", ItemCategory.WEAPON_DISTANCE, 24.0, sellPrice = 80, attack = 20, range = 4, attackIntervalMs = 1000L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.DISTANCE to 1, description = "1H throwing spear tipped with Jagged Ivory; pairs with a shield."),
        ItemDefinition("brindle_recurve", "Brindle Recurve", ItemCategory.WEAPON_DISTANCE, 25.0, sellPrice = 165, attack = 24, range = 5, attackIntervalMs = 900L, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.DISTANCE to 12, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, description = "Snappy 2H sinew-backed bow firing Shafts at high cadence."),
        ItemDefinition(
            "quenchsteel_arbalest", "Quenchsteel Arbalest", ItemCategory.WEAPON_DISTANCE, 44.0, sellPrice = 290,
            attack = 39, range = 5, attackIntervalMs = 1300L, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.DISTANCE to 22, requiredAmmoKind = AmmoKind.QUARRELS, isTwoHanded = true,
            preferredVocation = Vocation.PALADIN,
            proc = EquipmentProc(
                name = "Piercing Fan", triggerChancePercent = 15, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.6f, maxTargets = 3, damageMultiplier = 0.72f, elementColor = 0xFF9BE7FFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Splits a quarrel into steel shrapnel across nearby monsters."
            ),
            description = "Heavy 2H cranked crossbow loosing Quarrels. Paladin Tier 3 mastery."
        ),
        ItemDefinition(
            "moonforge_warbow", "Moonforge Warbow", ItemCategory.WEAPON_DISTANCE, 30.0, sellPrice = 520,
            attack = 36, range = 5, attackIntervalMs = 900L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.DISTANCE to 32, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true,
            preferredVocation = Vocation.PALADIN,
            proc = EquipmentProc(
                name = "Starfall Volley", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.9f, maxTargets = 4, damageMultiplier = 0.82f, elementColor = 0xFFC4A3FFL,
                bonusEffect = ProcBonusEffect.NONE, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Calls down luminous shafts across monsters near the target."
            ),
            description = "Luminous 2H longbow prized by master marksmen. Paladin Tier 4 mastery."
        ),
        ItemDefinition(
            "draketalon_siegebow", "Draketalon Siegebow", ItemCategory.WEAPON_DISTANCE, 56.0, sellPrice = 640,
            attack = 54, range = 6, attackIntervalMs = 1300L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.DISTANCE to 32, requiredAmmoKind = AmmoKind.QUARRELS, isTwoHanded = true,
            preferredVocation = Vocation.PALADIN,
            proc = EquipmentProc(
                name = "Starfall Volley", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.0f, maxTargets = 4, damageMultiplier = 0.88f, elementColor = 0xFFC4A3FFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Hails armor-piercing siege bolts over the impact zone."
            ),
            description = "Colossal 2H dragon-limbed arbalest. Paladin Tier 4 mastery."
        ),
        // Distance Tier 4 & Tier 5
        ItemDefinition(
            "vesperite_recurve_bow", "Vesperite Recurve Bow", ItemCategory.WEAPON_DISTANCE, 28.0, sellPrice = 770,
            attack = 46, range = 5, attackIntervalMs = 900L, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.DISTANCE to 32, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true,
            preferredVocation = Vocation.PALADIN,
            proc = EquipmentProc(
                name = "Starfall Volley", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.0f, maxTargets = 4, damageMultiplier = 0.85f, elementColor = 0xFFC8A6FFL,
                bonusEffect = ProcBonusEffect.NONE, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Rains crystalline violet arrows on up to 4 monsters around the target."
            ),
            description = "Tier 4 laminated silver-violet 2H recurve bow loosing rapid crystalline volleys."
        ),
        ItemDefinition(
            "cinderite_ashen_bow", "Cinderite Ashen Bow", ItemCategory.WEAPON_DISTANCE, 32.0, sellPrice = 1220,
            attack = 62, range = 6, attackIntervalMs = 950L, equipmentTier = 5, requiredLevel = 40,
            requiredSkill = SkillType.DISTANCE to 42, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true,
            preferredVocation = Vocation.PALADIN,
            proc = EquipmentProc(
                name = "Ember Rain", triggerChancePercent = 24, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.4f, maxTargets = 5, damageMultiplier = 1.10f, elementColor = 0xFFFF7A29L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Showers blazing ember arrows across 5 monsters, igniting burning flames."
            ),
            description = "Tier 5 charred obsidian 2H warbow strung with glowing ember-gold cord."
        ),

        // --- Multi-Tier Forged Weapons: MAGE (Magic) ---
        ItemDefinition("sunbrass_scepter", "Sunbrass Scepter", ItemCategory.WEAPON_WAND, 18.0, sellPrice = 65, attack = 17, range = 3, attackIntervalMs = 800L, manaCost = 4, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.MAGIC to 1, description = "Quick 1H spark-channeling wand. Pairs with a shield."),
        ItemDefinition("bilewood_conduit", "Bilewood Conduit", ItemCategory.WEAPON_WAND, 22.0, sellPrice = 155, attack = 25, range = 4, attackIntervalMs = 900L, manaCost = 6, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.MAGIC to 12, description = "Venom-infused 1H focus rod firing corrosive bolts."),
        ItemDefinition(
            "tallowbone_crosier", "Tallowbone Crosier", ItemCategory.WEAPON_WAND, 27.0, sellPrice = 285,
            attack = 34, range = 4, attackIntervalMs = 1000L, manaCost = 8, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.MAGIC to 22, isTwoHanded = true, preferredVocation = Vocation.MAGE,
            proc = EquipmentProc(
                name = "Grave Spark", triggerChancePercent = 16, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.7f, maxTargets = 3, damageMultiplier = 0.72f, elementColor = 0xFF7BE8A8L,
                bonusEffect = ProcBonusEffect.MANA_SIPHON, visualStyle = ProcVisualStyle.CHAIN_ARC,
                summary = "Arcs eerie spectral bolts across nearby foes, siphoning a touch of Mana."
            ),
            description = "Crypt-carved 2H staff channeling Wraith Tallow. Mage Tier 3 mastery."
        ),
        ItemDefinition(
            "moonforge_spire", "Moonforge Spire", ItemCategory.WEAPON_WAND, 24.0, sellPrice = 510,
            attack = 39, range = 5, attackIntervalMs = 800L, manaCost = 9, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.MAGIC to 32, preferredVocation = Vocation.MAGE,
            proc = EquipmentProc(
                name = "Void Chain", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.0f, maxTargets = 3, damageMultiplier = 0.85f, elementColor = 0xFFA47BFFL,
                bonusEffect = ProcBonusEffect.MANA_SIPHON, visualStyle = ProcVisualStyle.CHAIN_ARC,
                summary = "Chains astral bolts between nearby monsters and restores Mana."
            ),
            description = "Rapid 1H star-forged wand pulsing with astral lightning. Mage Tier 4 mastery."
        ),
        ItemDefinition(
            "cinderheart_rod", "Cinderheart Rod", ItemCategory.WEAPON_WAND, 31.0, sellPrice = 640,
            attack = 52, range = 5, attackIntervalMs = 1100L, manaCost = 12, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.MAGIC to 32, isTwoHanded = true, preferredVocation = Vocation.MAGE,
            proc = EquipmentProc(
                name = "Inferno Nova", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.2f, maxTargets = 4, damageMultiplier = 0.95f, elementColor = 0xFFFF6E2EL,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Detonates a roaring ember nova around the target."
            ),
            description = "Volcanic 2H staff crowned with an Ember Heart. Mage Tier 4 mastery."
        ),
        // Magic Tier 4 & Tier 5
        ItemDefinition(
            "vesperite_conduit_staff", "Vesperite Conduit Staff", ItemCategory.WEAPON_WAND, 26.0, sellPrice = 790,
            attack = 49, range = 5, attackIntervalMs = 950L, manaCost = 10, equipmentTier = 4, requiredLevel = 28,
            requiredSkill = SkillType.MAGIC to 32, isTwoHanded = true, preferredVocation = Vocation.MAGE,
            proc = EquipmentProc(
                name = "Void Chain", triggerChancePercent = 22, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.2f, maxTargets = 4, damageMultiplier = 0.95f, elementColor = 0xFF9F7BFFL,
                bonusEffect = ProcBonusEffect.MANA_SIPHON, visualStyle = ProcVisualStyle.CHAIN_ARC,
                summary = "Chains crackling violet void-lightning across up to 4 foes and siphons Mana."
            ),
            description = "Tier 4 twin-pronged silver 2H staff cradling a floating Vesperite crystal."
        ),
        ItemDefinition(
            "cinderite_soulspire", "Cinderite Soulspire", ItemCategory.WEAPON_WAND, 32.0, sellPrice = 1260,
            attack = 66, range = 6, attackIntervalMs = 1050L, manaCost = 14, equipmentTier = 5, requiredLevel = 40,
            requiredSkill = SkillType.MAGIC to 42, isTwoHanded = true, preferredVocation = Vocation.MAGE,
            proc = EquipmentProc(
                name = "Inferno Nova", triggerChancePercent = 25, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.5f, maxTargets = 6, damageMultiplier = 1.25f, elementColor = 0xFFFF8826L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Erupts a blazing ring of soulfire around the target, scorching up to 6 foes."
            ),
            description = "Tier 5 twisted obsidian 2H spire crowned with a blazing volcanic-gold soul orb."
        ),

        // --- Craftable Ammunition (Ammo Slot) ---
        ItemDefinition("wooden_arrows", "Wooden Arrows", ItemCategory.AMMO, 0.1, sellPrice = 1, attack = 4, ammoKind = AmmoKind.SHAFTS, stackable = true, description = "Hand-fletched hardwood arrows for Shortbows & Hunting Bows."),
        ItemDefinition("ochre_shafts", "Copper-Fletched Shafts", ItemCategory.AMMO, 0.12, sellPrice = 2, attack = 6, ammoKind = AmmoKind.SHAFTS, stackable = true, description = "Light forged arrows for Shortbows, Hunting Bows & Recurves."),
        ItemDefinition("venom_shafts", "Venom-Barbed Shafts", ItemCategory.AMMO, 0.15, sellPrice = 5, attack = 12, ammoKind = AmmoKind.SHAFTS, stackable = true, description = "Bile-coated arrows forged for Recurves & Warbows."),
        ItemDefinition("quenchsteel_quarrels", "Quenchsteel Quarrels", ItemCategory.AMMO, 0.2, sellPrice = 6, attack = 15, ammoKind = AmmoKind.QUARRELS, stackable = true, description = "Heavy steel bolts crafted for Arbalests."),
        ItemDefinition("starlight_quarrels", "Vesperite Quarrels", ItemCategory.AMMO, 0.18, sellPrice = 12, attack = 24, ammoKind = AmmoKind.QUARRELS, stackable = true, description = "Crystalline Vesperite-tipped bolts of lethal precision."),

        // --- Legacy V1 Weapons (Kept for 100% Save Backward Compatibility; not sold or dropped) ---
        ItemDefinition("short_sword", "Short Sword", ItemCategory.WEAPON_MELEE, 28.0, sellPrice = 18, attack = 14, defense = 10, attackIntervalMs = 1000L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SWORD to 1, description = "Heirloom blade from early Rookvale expeditions."),
        ItemDefinition("steel_sword", "Steel Longsword", ItemCategory.WEAPON_MELEE, 36.0, sellPrice = 95, attack = 24, defense = 16, attackIntervalMs = 1000L, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.SWORD to 12, description = "Legacy Rookvale longsword."),
        ItemDefinition(
            "fire_sword", "Fire Sword", ItemCategory.WEAPON_MELEE, 23.0, sellPrice = 450,
            attack = 38, defense = 22, attackIntervalMs = 1000L, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.SWORD to 22, preferredVocation = Vocation.KNIGHT,
            proc = EquipmentProc(
                name = "Tempered Arc", triggerChancePercent = 16, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.6f, maxTargets = 3, damageMultiplier = 0.78f, elementColor = 0xFFFF7A29L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Cleaves a fiery arc scorching nearby foes."
            ),
            description = "Legacy blazing blade."
        ),
        ItemDefinition(
            "halberd", "Quarry Halberd", ItemCategory.WEAPON_MELEE, 75.0, sellPrice = 240,
            attack = 33, defense = 14, attackIntervalMs = 1400L, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.AXE to 22, isTwoHanded = true,
            proc = EquipmentProc(
                name = "Crushing Sweep", triggerChancePercent = 15, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.6f, maxTargets = 3, damageMultiplier = 0.72f, elementColor = 0xFFE6A24CL,
                bonusEffect = ProcBonusEffect.NONE, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Sweeps heavy steel across adjacent enemies."
            ),
            description = "Legacy heavy 2H polearm."
        ),
        ItemDefinition("hunting_spear", "Hunting Spear", ItemCategory.WEAPON_DISTANCE, 20.0, sellPrice = 22, attack = 18, range = 4, attackIntervalMs = 1000L, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.DISTANCE to 1, description = "Starter 1H throwing spear."),
        ItemDefinition("crossbow", "Arbalest Bow", ItemCategory.WEAPON_DISTANCE, 34.0, sellPrice = 120, attack = 31, range = 5, attackIntervalMs = 1300L, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.DISTANCE to 12, requiredAmmoKind = AmmoKind.QUARRELS, isTwoHanded = true, description = "Legacy 2H arbalest crossbow."),
        ItemDefinition("wand_vortex", "Wand of Vortex", ItemCategory.WEAPON_WAND, 19.0, sellPrice = 25, attack = 17, range = 3, attackIntervalMs = 900L, manaCost = 4, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.MAGIC to 1, description = "Starter crackling energy wand."),
        ItemDefinition(
            "wand_inferno", "Wand of Inferno", ItemCategory.WEAPON_WAND, 26.0, sellPrice = 160,
            attack = 35, range = 4, attackIntervalMs = 1000L, manaCost = 8, equipmentTier = 3, requiredLevel = 18,
            requiredSkill = SkillType.MAGIC to 22,
            proc = EquipmentProc(
                name = "Grave Spark", triggerChancePercent = 16, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.7f, maxTargets = 3, damageMultiplier = 0.75f, elementColor = 0xFFFF7A29L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Sparks an explosive gout of flame around the target."
            ),
            description = "Legacy flame wand."
        ),

        // --- Shields & Armor (Tiers 1..5 with Defensive Procs) ---
        ItemDefinition("wooden_shield", "Wooden Shield", ItemCategory.SHIELD, 32.0, buyPrice = 35, sellPrice = 12, defense = 14, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SHIELDING to 1, description = "Basic oak buckler."),
        ItemDefinition(
            "steel_shield", "Dwarven Shield", ItemCategory.SHIELD, 48.0, buyPrice = 210, sellPrice = 90,
            defense = 24, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.SHIELDING to 12,
            proc = EquipmentProc(
                name = "Aegis Retaliation", triggerChancePercent = 16, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.5f, maxTargets = 3, damageMultiplier = 0.85f, elementColor = 0xFFF5D76EL,
                bonusEffect = ProcBonusEffect.AEGIS_WARD, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Releases a kinetic shield burst when struck, damaging adjacent foes & restoring guard HP."
            ),
            description = "Heavy iron-banded kite shield that retaliates against nearby attackers."
        ),
        ItemDefinition(
            "dragon_shield", "Wyrmscale Shield", ItemCategory.SHIELD, 60.0, sellPrice = 520,
            defense = 34, equipmentTier = 3, requiredLevel = 18, requiredSkill = SkillType.SHIELDING to 22,
            proc = EquipmentProc(
                name = "Aegis Retaliation", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.6f, maxTargets = 4, damageMultiplier = 1.00f, elementColor = 0xFFFFB84DL,
                bonusEffect = ProcBonusEffect.AEGIS_WARD, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Sends a golden wyrmscale blast against adjacent monsters and bolsters HP."
            ),
            description = "Forged from fire-hardened scales of the Ancient Dragon."
        ),
        ItemDefinition(
            "vesperite_bulwark", "Vesperite Bulwark", ItemCategory.SHIELD, 64.0, sellPrice = 840,
            defense = 40, equipmentTier = 4, requiredLevel = 28, requiredSkill = SkillType.SHIELDING to 32,
            proc = EquipmentProc(
                name = "Aegis Retaliation", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.7f, maxTargets = 4, damageMultiplier = 1.12f, elementColor = 0xFFB692FFL,
                bonusEffect = ProcBonusEffect.AEGIS_WARD, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Emits a violet crystalline shockwave when struck, striking up to 4 adjacent foes & restoring HP."
            ),
            description = "Tier 4 faceted Vesperite tower shield radiating a resonant ward."
        ),
        ItemDefinition(
            "cinderite_bastion", "Cinderite Bastion", ItemCategory.SHIELD, 72.0, sellPrice = 1360,
            defense = 48, equipmentTier = 5, requiredLevel = 40, requiredSkill = SkillType.SHIELDING to 42,
            proc = EquipmentProc(
                name = "Aegis Retaliation", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.9f, maxTargets = 5, damageMultiplier = 1.28f, elementColor = 0xFFFF7A29L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Triggers a blazing volcanic retaliation wave when struck, igniting up to 5 adjacent foes."
            ),
            description = "Tier 5 obsidian-and-molten-gold fortress shield forged in volcanic fire."
        ),

        ItemDefinition("leather_helmet", "Leather Cap", ItemCategory.HELMET, 15.0, buyPrice = 35, sellPrice = 14, armor = 2, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SHIELDING to 1, description = "Light hardened leather headgear."),
        ItemDefinition("viking_helmet", "Viking Helmet", ItemCategory.HELMET, 35.0, buyPrice = 150, sellPrice = 65, armor = 5, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.SHIELDING to 12, description = "Horned iron helm."),

        ItemDefinition("leather_armor", "Leather Tunic", ItemCategory.ARMOR, 38.0, buyPrice = 50, sellPrice = 20, armor = 4, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SHIELDING to 1, description = "Supple traveler's armor."),
        ItemDefinition("mystic_robe", "Mystic Robe", ItemCategory.ARMOR, 24.0, buyPrice = 85, sellPrice = 35, armor = 4, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.MAGIC to 1, description = "Light enchanted cloak favored by Mages."),
        ItemDefinition(
            "brass_armor", "Brass Armor", ItemCategory.ARMOR, 68.0, buyPrice = 260, sellPrice = 110,
            armor = 9, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.SHIELDING to 12,
            proc = EquipmentProc(
                name = "Ember Thorns", triggerChancePercent = 15, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.5f, maxTargets = 3, damageMultiplier = 0.90f, elementColor = 0xFFFF9642L,
                bonusEffect = ProcBonusEffect.NONE, visualStyle = ProcVisualStyle.THORNS_PULSE,
                summary = "Erupts heated brass spikes when struck, scorching adjacent monsters."
            ),
            description = "Solid riveted breastplate that punishes nearby attackers."
        ),
        ItemDefinition(
            "dragon_mail", "Wyrmscale Cuirass", ItemCategory.ARMOR, 94.0, sellPrice = 800,
            armor = 16, equipmentTier = 3, requiredLevel = 18, requiredSkill = SkillType.SHIELDING to 22,
            proc = EquipmentProc(
                name = "Ember Thorns", triggerChancePercent = 17, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.6f, maxTargets = 4, damageMultiplier = 1.05f, elementColor = 0xFFFF7A29L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.THORNS_PULSE,
                summary = "Flares searing dragonfire thorns around you when hit, burning up to 4 adjacent foes."
            ),
            description = "Legendary emerald scale cuirass wreathed in reactive fire."
        ),
        ItemDefinition(
            "vesperite_aegis_mail", "Vesperite Aegis Mail", ItemCategory.ARMOR, 92.0, sellPrice = 1040,
            armor = 21, equipmentTier = 4, requiredLevel = 28, requiredSkill = SkillType.SHIELDING to 32,
            proc = EquipmentProc(
                name = "Ember Thorns", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.7f, maxTargets = 4, damageMultiplier = 1.15f, elementColor = 0xFFA47BFFL,
                bonusEffect = ProcBonusEffect.ARMOR_SHATTER, visualStyle = ProcVisualStyle.THORNS_PULSE,
                summary = "Shatters violet crystal thorns outward when hit, weakening up to 4 adjacent foes."
            ),
            description = "Tier 4 articulated Vesperite plate mail lined with resonant crystal shards."
        ),
        ItemDefinition(
            "cinderite_warplate", "Cinderite Warplate", ItemCategory.ARMOR, 102.0, sellPrice = 1580,
            armor = 26, equipmentTier = 5, requiredLevel = 40, requiredSkill = SkillType.SHIELDING to 42,
            proc = EquipmentProc(
                name = "Ember Thorns", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.9f, maxTargets = 5, damageMultiplier = 1.30f, elementColor = 0xFFFF5522L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.THORNS_PULSE,
                summary = "Unleashes incandescent magma thorns when struck, igniting up to 5 adjacent monsters."
            ),
            description = "Tier 5 forged obsidian-and-gold warplate radiating volcanic fury."
        ),

        ItemDefinition("leather_legs", "Leather Legs", ItemCategory.LEGS, 18.0, buyPrice = 45, sellPrice = 18, armor = 2, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SHIELDING to 1, description = "Stitched greaves for dungeon travel."),
        ItemDefinition("brass_legs", "Brass Legs", ItemCategory.LEGS, 38.0, buyPrice = 190, sellPrice = 80, armor = 5, equipmentTier = 2, requiredLevel = 8, requiredSkill = SkillType.SHIELDING to 12, description = "Reinforced leg plates."),
        ItemDefinition("leather_boots", "Traveler Boots", ItemCategory.BOOTS, 9.0, buyPrice = 40, sellPrice = 16, armor = 1, equipmentTier = 1, requiredLevel = 1, requiredSkill = SkillType.SHIELDING to 1, description = "Sturdy dungeon boots."),

        // --- Tiered Necklaces / Amulets (AMULET Slot — Ability Amplifiers & Unique Procs) ---
        ItemDefinition(
            "life_amulet", "Amulet of Vitality", ItemCategory.AMULET, 4.2, buyPrice = 240, sellPrice = 100,
            armor = 2, equipmentTier = 1, requiredLevel = 1, bonusMaxHp = 25,
            description = "Warm ruby pendant granting +25 Max HP and steady protection."
        ),
        ItemDefinition(
            "bog_iron_ward_pendant", "Bog-Iron Ward Pendant", ItemCategory.AMULET, 4.8, sellPrice = 180,
            armor = 3, equipmentTier = 2, requiredLevel = 8, procChanceBonusPct = 3,
            proc = EquipmentProc(
                name = "Iron Ward Pulse", triggerChancePercent = 15, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 1.5f, maxTargets = 3, damageMultiplier = 0.72f, elementColor = 0xFF8BE2A8L,
                bonusEffect = ProcBonusEffect.AEGIS_WARD, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Pulses a protective ward when hit, harming adjacent foes and healing guard HP."
            ),
            description = "Tier 2 marsh-iron talisman boosting all proc rates (+3%) with reactive ward pulses."
        ),
        ItemDefinition(
            "stormglass_pendant", "Stormglass Pendant", ItemCategory.AMULET, 4.5, sellPrice = 410,
            armor = 4, equipmentTier = 3, requiredLevel = 18, procChanceBonusPct = 5, procPowerBonusPct = 15,
            proc = EquipmentProc(
                name = "Tempest Arc", triggerChancePercent = 16, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.0f, maxTargets = 3, damageMultiplier = 0.80f, elementColor = 0xFF5CC8FFL,
                bonusEffect = ProcBonusEffect.STAGGER_SLOW, visualStyle = ProcVisualStyle.CHAIN_ARC,
                summary = "Arcs storm lightning across up to 3 nearby monsters, briefly staggering them."
            ),
            description = "Tier 3 lightning-fused crystal pendant amplifying proc chance (+5%) and proc damage (+15%)."
        ),
        ItemDefinition(
            "vesperite_resonance_necklace", "Vesperite Resonance Necklace", ItemCategory.AMULET, 5.0, sellPrice = 860,
            armor = 6, equipmentTier = 4, requiredLevel = 28, procChanceBonusPct = 6, procPowerBonusPct = 20, procExtraTargets = 1,
            proc = EquipmentProc(
                name = "Astral Resonance", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.2f, maxTargets = 4, damageMultiplier = 0.90f, elementColor = 0xFFB692FFL,
                bonusEffect = ProcBonusEffect.MANA_SIPHON, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Echoes an astral pulse around the target that damages up to 4 foes and restores Mana."
            ),
            description = "Tier 4 resonant Vesperite necklace granting +6% Proc Chance, +20% Proc Power, and +1 AOE Target."
        ),
        ItemDefinition(
            "cinderheart_sovereign_torc", "Cinderheart Sovereign Torc", ItemCategory.AMULET, 5.5, sellPrice = 1420,
            armor = 8, equipmentTier = 5, requiredLevel = 40, procChanceBonusPct = 8, procPowerBonusPct = 25, procExtraTargets = 1, bonusMaxHp = 60,
            proc = EquipmentProc(
                name = "Sovereign Inferno", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_DEFEND,
                aoeRadiusTiles = 2.2f, maxTargets = 5, damageMultiplier = 1.10f, elementColor = 0xFFFF6B2BL,
                bonusEffect = ProcBonusEffect.AEGIS_WARD, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Erupts a protective sovereign flame ring when hit, scorching 5 foes and restoring HP."
            ),
            description = "Tier 5 volcanic gold torc granting +8% Proc Chance, +25% Proc Power, +1 AOE Target, and +60 Max HP."
        ),

        // --- Tiered Rings (RING Slot — Brand-New Jewelry Slot for Synergy & Procs) ---
        ItemDefinition(
            "sunbrass_signet", "Sunbrass Signet", ItemCategory.RING, 1.2, sellPrice = 75,
            attack = 2, equipmentTier = 1, requiredLevel = 1, procChanceBonusPct = 2,
            description = "Tier 1 burnished copper-tin ring adding +2 ATK and +2% Proc Chance."
        ),
        ItemDefinition(
            "duskmetal_vampire_band", "Duskmetal Vampire Band", ItemCategory.RING, 1.4, sellPrice = 210,
            attack = 3, equipmentTier = 2, requiredLevel = 8, procChanceBonusPct = 3,
            proc = EquipmentProc(
                name = "Siphon Bite", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 1.5f, maxTargets = 2, damageMultiplier = 0.75f, elementColor = 0xFFE84A6AL,
                bonusEffect = ProcBonusEffect.LIFE_SIPHON, visualStyle = ProcVisualStyle.SIPHON_WAVE,
                summary = "Drains crimson essence from up to 2 enemies, healing you for 35% of proc damage."
            ),
            description = "Tier 2 blood-etched band granting +3 ATK, +3% Proc Chance, and vampiric Siphon Bite."
        ),
        ItemDefinition(
            "frostfang_coil", "Frostfang Coil", ItemCategory.RING, 1.5, sellPrice = 440,
            attack = 4, equipmentTier = 3, requiredLevel = 18, procChanceBonusPct = 4, procPowerBonusPct = 15,
            proc = EquipmentProc(
                name = "Glacial Shatter", triggerChancePercent = 18, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.0f, maxTargets = 4, damageMultiplier = 0.85f, elementColor = 0xFF66E0FFL,
                bonusEffect = ProcBonusEffect.STAGGER_SLOW, visualStyle = ProcVisualStyle.SHOCKWAVE_RING,
                summary = "Shatters a biting frost ring across up to 4 enemies, slowing their pursuit."
            ),
            description = "Tier 3 rimed silver ring granting +4 ATK, +4% Proc Chance, +15% Proc Power, and Glacial Shatter."
        ),
        ItemDefinition(
            "vesperite_catalyst_ring", "Vesperite Catalyst Ring", ItemCategory.RING, 1.6, sellPrice = 890,
            attack = 6, equipmentTier = 4, requiredLevel = 28, procChanceBonusPct = 6, procPowerBonusPct = 20,
            proc = EquipmentProc(
                name = "Starlight Echo", triggerChancePercent = 20, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.2f, maxTargets = 4, damageMultiplier = 0.92f, elementColor = 0xFFC4A3FFL,
                bonusEffect = ProcBonusEffect.MANA_SIPHON, visualStyle = ProcVisualStyle.ARCANE_NOVA,
                summary = "Triggers a crystalline starlight nova on up to 4 foes and restores Mana."
            ),
            description = "Tier 4 faceted Vesperite ring granting +6 ATK, +6% Proc Chance, and +20% Proc Power."
        ),
        ItemDefinition(
            "cinderite_overlord_band", "Cinderite Overlord Band", ItemCategory.RING, 1.8, sellPrice = 1480,
            attack = 8, equipmentTier = 5, requiredLevel = 40, procChanceBonusPct = 8, procPowerBonusPct = 25, procExtraTargets = 1,
            proc = EquipmentProc(
                name = "Cataclysmic Flare", triggerChancePercent = 22, triggerType = ProcTriggerType.ON_ATTACK,
                aoeRadiusTiles = 2.4f, maxTargets = 5, damageMultiplier = 1.12f, elementColor = 0xFFFF7A29L,
                bonusEffect = ProcBonusEffect.IGNITE_BURN, visualStyle = ProcVisualStyle.METEOR_BURST,
                summary = "Detonates a blazing Cinderite flare scorching up to 5 enemies with burn DoT."
            ),
            description = "Tier 5 obsidian-and-gold signet granting +8 ATK, +8% Proc Chance, +25% Proc Power, and +1 AOE Target."
        )
    ).associateBy { it.id }

    // Alaric sells ONLY supplies, Blank Runes, tools (Quarry Mattock, Hatchet, Rope, Fishing Rod), potions & basic armor — NO finished runes or weapons!
    val shopInventoryIds: List<String> = listOf(
        "health_potion",
        "mana_potion",
        "blank_rune",
        "hatchet",
        "quarry_mattock",
        "rope",
        "fishing_rod",
        "steel_shield",
        "viking_helmet",
        "brass_armor",
        "brass_legs",
        "life_amulet"
    )

    fun migrateLegacyItemId(id: String): String = when (id) {
        "ochre_nugget" -> "copper_ore"
        "bogstone_shard" -> "bog_iron_ore"
        "cinder_pitch" -> "vesperite_ore"
        "pale_starstone" -> "cinderite_ore"
        else -> id
    }

    fun get(id: String): ItemDefinition = all[id] ?: all[migrateLegacyItemId(id)] ?: all.getValue("cheese")
}

object RecipeCatalog {
    val smeltingRecipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "smelt_sunbrass",
            outputItemId = "sunbrass_ingot",
            ingredients = listOf(ItemStack("copper_ore", 1), ItemStack("tin_ore", 1)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_duskmetal",
            outputItemId = "duskmetal_ingot",
            ingredients = listOf(ItemStack("bog_iron_ore", 2)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_quenchsteel",
            outputItemId = "quenchsteel_ingot",
            ingredients = listOf(ItemStack("bog_iron_ore", 2), ItemStack("vesperite_ore", 1)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_vesperite",
            outputItemId = "vesperite_bar",
            ingredients = listOf(ItemStack("vesperite_ore", 2)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_cinderite",
            outputItemId = "cinderite_bar",
            ingredients = listOf(ItemStack("cinderite_ore", 2)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_moonforge",
            outputItemId = "moonforge_ingot",
            ingredients = listOf(ItemStack("vesperite_ore", 1), ItemStack("cinderite_ore", 1)),
            station = ActivePanel.FURNACE
        )
    )

    val forgingRecipes: List<CraftingRecipe> = listOf(
        // --- Knight Melee Weapons (Tiers 1-5) ---
        CraftingRecipe("forge_sunbrass_dirk", "sunbrass_dirk", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("jagged_ivory", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_rookvale_gladius", "rookvale_gladius", 1, listOf(ItemStack("sunbrass_ingot", 2), ItemStack("brindle_hide", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_duskmetal_falchion", "duskmetal_falchion", 1, listOf(ItemStack("duskmetal_ingot", 2), ItemStack("ancient_bone", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_bog_cleaver", "bog_cleaver_axe", 1, listOf(ItemStack("duskmetal_ingot", 3), ItemStack("brindle_hide", 3)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_quenchsteel_bastard", "quenchsteel_bastard", 1, listOf(ItemStack("quenchsteel_ingot", 2), ItemStack("ghoul_gem", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_colossus_glaive", "colossus_glaive", 1, listOf(ItemStack("quenchsteel_ingot", 3), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_moonforge_rapier", "moonforge_rapier", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("runic_slate", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_wyrmtooth_claymore", "wyrmtooth_claymore", 1, listOf(ItemStack("moonforge_ingot", 3), ItemStack("dragon_tooth", 1), ItemStack("wyrmscale_plate", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        // Tier 4 Vesperite Melee (Sword, Axe, Club/Maul)
        CraftingRecipe("forge_vesperite_longblade", "vesperite_longblade", 1, listOf(ItemStack("vesperite_bar", 3), ItemStack("grimmaw_stone_core", 1)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_vesperite_cleaver", "vesperite_cleaver", 1, listOf(ItemStack("vesperite_bar", 3), ItemStack("grimmaw_stone_core", 1), ItemStack("colossus_tendon", 1)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_vesperite_warmaul", "vesperite_warmaul", 1, listOf(ItemStack("vesperite_bar", 4), ItemStack("grimmaw_stone_core", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        // Tier 5 Cinderite Melee (Sword, Axe, Club/Maul)
        CraftingRecipe("forge_cinderite_greatsword", "cinderite_greatsword", 1, listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_cinderite_greataxe", "cinderite_greataxe", 1, listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 2), ItemStack("ember_heart", 1)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_cinderite_worldbreaker", "cinderite_worldbreaker", 1, listOf(ItemStack("cinderite_bar", 5), ItemStack("gorek_obsidian_horn", 2), ItemStack("grimmaw_stone_core", 1)), ActivePanel.ANVIL, CraftingTab.KNIGHT),

        // --- Paladin Distance Weapons (Tiers 1-5) ---
        CraftingRecipe("forge_ashwood_shortbow", "ashwood_shortbow", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_ivory_javelin", "ivory_javelin", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("jagged_ivory", 3)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_brindle_recurve", "brindle_recurve", 1, listOf(ItemStack("duskmetal_ingot", 2), ItemStack("brindle_hide", 2), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_quenchsteel_arbalest", "quenchsteel_arbalest", 1, listOf(ItemStack("quenchsteel_ingot", 2), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_moonforge_warbow", "moonforge_warbow", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("colossus_tendon", 2), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_draketalon_siegebow", "draketalon_siegebow", 1, listOf(ItemStack("moonforge_ingot", 3), ItemStack("dragon_tooth", 1), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_vesperite_recurve_bow", "vesperite_recurve_bow", 1, listOf(ItemStack("vesperite_bar", 3), ItemStack("grimmaw_stone_core", 1), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_cinderite_ashen_bow", "cinderite_ashen_bow", 1, listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 2), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),

        // --- Mage Arcane Weapons (Tiers 1-5) ---
        CraftingRecipe("forge_sunbrass_scepter", "sunbrass_scepter", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("vesper_wing", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_bilewood_conduit", "bilewood_conduit", 1, listOf(ItemStack("duskmetal_ingot", 1), ItemStack("bile_gland", 3)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_tallowbone_crosier", "tallowbone_crosier", 1, listOf(ItemStack("quenchsteel_ingot", 1), ItemStack("ancient_bone", 2), ItemStack("ghoul_gem", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_moonforge_spire", "moonforge_spire", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("runic_slate", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_cinderheart_rod", "cinderheart_rod", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("ember_heart", 2), ItemStack("wyrmscale_plate", 1)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_vesperite_conduit_staff", "vesperite_conduit_staff", 1, listOf(ItemStack("vesperite_bar", 3), ItemStack("grimmaw_stone_core", 1), ItemStack("runic_slate", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_cinderite_soulspire", "cinderite_soulspire", 1, listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 2), ItemStack("ember_heart", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),

        // --- Forged Ammunition Batches ---
        CraftingRecipe("forge_ochre_shafts", "ochre_shafts", 20, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("vesper_wing", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),
        CraftingRecipe("forge_venom_shafts", "venom_shafts", 20, listOf(ItemStack("duskmetal_ingot", 1), ItemStack("bile_gland", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),
        CraftingRecipe("forge_quenchsteel_quarrels", "quenchsteel_quarrels", 25, listOf(ItemStack("quenchsteel_ingot", 1), ItemStack("ancient_bone", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),
        CraftingRecipe("forge_starlight_quarrels", "starlight_quarrels", 25, listOf(ItemStack("vesperite_bar", 1), ItemStack("ember_heart", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),

        // --- Pickaxe Upgrades & High-Tier Shields / Armor ---
        CraftingRecipe("forge_sunbrass_pick", "sunbrass_pick", 1, listOf(ItemStack("sunbrass_ingot", 2), ItemStack("brindle_hide", 1)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_duskmetal_breaker", "duskmetal_breaker", 1, listOf(ItemStack("duskmetal_ingot", 2), ItemStack("ancient_bone", 2)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_quenchsteel_delver", "quenchsteel_delver", 1, listOf(ItemStack("quenchsteel_ingot", 3), ItemStack("colossus_tendon", 1)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_wyrmscale_shield", "dragon_shield", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("wyrmscale_plate", 2)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_wyrmscale_cuirass", "dragon_mail", 1, listOf(ItemStack("moonforge_ingot", 3), ItemStack("wyrmscale_plate", 3)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_vesperite_bulwark", "vesperite_bulwark", 1, listOf(ItemStack("vesperite_bar", 3), ItemStack("grimmaw_stone_core", 1)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_vesperite_aegis_mail", "vesperite_aegis_mail", 1, listOf(ItemStack("vesperite_bar", 4), ItemStack("grimmaw_stone_core", 2)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_cinderite_bastion", "cinderite_bastion", 1, listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 2)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_cinderite_warplate", "cinderite_warplate", 1, listOf(ItemStack("cinderite_bar", 5), ItemStack("gorek_obsidian_horn", 2), ItemStack("ember_heart", 1)), ActivePanel.ANVIL, CraftingTab.TOOLS),

        // --- Enchanted Necklaces & Rings (Tiers 1..5) ---
        CraftingRecipe("forge_sunbrass_signet", "sunbrass_signet", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("jagged_ivory", 1)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_bog_iron_ward_pendant", "bog_iron_ward_pendant", 1, listOf(ItemStack("duskmetal_ingot", 1), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_duskmetal_vampire_band", "duskmetal_vampire_band", 1, listOf(ItemStack("duskmetal_ingot", 1), ItemStack("bile_gland", 2)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_stormglass_pendant", "stormglass_pendant", 1, listOf(ItemStack("quenchsteel_ingot", 2), ItemStack("ghoul_gem", 2)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_frostfang_coil", "frostfang_coil", 1, listOf(ItemStack("quenchsteel_ingot", 2), ItemStack("ancient_bone", 2), ItemStack("ghoul_gem", 1)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_vesperite_resonance_necklace", "vesperite_resonance_necklace", 1, listOf(ItemStack("vesperite_bar", 2), ItemStack("grimmaw_stone_core", 1), ItemStack("runic_slate", 1)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_vesperite_catalyst_ring", "vesperite_catalyst_ring", 1, listOf(ItemStack("vesperite_bar", 2), ItemStack("grimmaw_stone_core", 1)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_cinderheart_sovereign_torc", "cinderheart_sovereign_torc", 1, listOf(ItemStack("cinderite_bar", 3), ItemStack("gorek_obsidian_horn", 1), ItemStack("ember_heart", 2)), ActivePanel.ANVIL, CraftingTab.JEWELRY),
        CraftingRecipe("forge_cinderite_overlord_band", "cinderite_overlord_band", 1, listOf(ItemStack("cinderite_bar", 3), ItemStack("gorek_obsidian_horn", 2)), ActivePanel.ANVIL, CraftingTab.JEWELRY)
    )

    val fletchingRecipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "fletch_wooden_arrows",
            outputItemId = "wooden_arrows",
            outputCount = 20,
            ingredients = listOf(ItemStack("wood_log", 1), ItemStack("wild_feather", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.AMMO
        ),
        CraftingRecipe(
            id = "fletch_ochre_shafts",
            outputItemId = "ochre_shafts",
            outputCount = 20,
            ingredients = listOf(ItemStack("wood_log", 1), ItemStack("jagged_ivory", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.AMMO
        ),
        CraftingRecipe(
            id = "fletch_venom_shafts",
            outputItemId = "venom_shafts",
            outputCount = 20,
            ingredients = listOf(ItemStack("wood_log", 1), ItemStack("bile_gland", 1), ItemStack("wild_feather", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.AMMO
        ),
        CraftingRecipe(
            id = "fletch_ashwood_shortbow",
            outputItemId = "ashwood_shortbow",
            outputCount = 1,
            ingredients = listOf(ItemStack("wood_log", 2), ItemStack("spider_silk", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.PALADIN
        ),
        CraftingRecipe(
            id = "fletch_hunting_bow",
            outputItemId = "hunting_bow",
            outputCount = 1,
            ingredients = listOf(ItemStack("wood_log", 3), ItemStack("spider_silk", 2), ItemStack("brindle_hide", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.PALADIN
        ),
        CraftingRecipe(
            id = "fletch_vesperite_recurve_bow",
            outputItemId = "vesperite_recurve_bow",
            outputCount = 1,
            ingredients = listOf(ItemStack("vesperite_bar", 3), ItemStack("wood_log", 2), ItemStack("spider_silk", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.PALADIN
        ),
        CraftingRecipe(
            id = "fletch_cinderite_ashen_bow",
            outputItemId = "cinderite_ashen_bow",
            outputCount = 1,
            ingredients = listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 1), ItemStack("colossus_tendon", 2)),
            station = ActivePanel.FLETCHING_BENCH,
            tab = CraftingTab.PALADIN
        )
    )

    val runecraftingRecipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "rune_magic_dart",
            outputItemId = "magic_dart_rune",
            outputCount = 3,
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("copper_ore", 1)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 1,
            manaCost = 20,
            soulCost = 2
        ),
        CraftingRecipe(
            id = "rune_healing",
            outputItemId = "healing_rune",
            outputCount = 2,
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("wild_feather", 1)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 4,
            manaCost = 30,
            soulCost = 3
        ),
        CraftingRecipe(
            id = "rune_hmm",
            outputItemId = "hmm_rune",
            outputCount = 2,
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("bile_gland", 1)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 7,
            manaCost = 45,
            soulCost = 4
        ),
        CraftingRecipe(
            id = "rune_fireball",
            outputItemId = "fireball_rune",
            outputCount = 2,
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("ghoul_gem", 1), ItemStack("vesperite_ore", 1)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 12,
            manaCost = 70,
            soulCost = 6
        ),
        CraftingRecipe(
            id = "rune_sd",
            outputItemId = "sd_rune",
            outputCount = 2,
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("runic_slate", 1), ItemStack("ember_heart", 1)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 15,
            manaCost = 100,
            soulCost = 10
        ),
        CraftingRecipe(
            id = "rune_vesperite_conduit_staff",
            outputItemId = "vesperite_conduit_staff",
            outputCount = 1,
            ingredients = listOf(ItemStack("vesperite_bar", 3), ItemStack("grimmaw_stone_core", 1), ItemStack("blank_rune", 2)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 15,
            manaCost = 110,
            soulCost = 8
        ),
        CraftingRecipe(
            id = "rune_cinderite_soulspire",
            outputItemId = "cinderite_soulspire",
            outputCount = 1,
            ingredients = listOf(ItemStack("cinderite_bar", 4), ItemStack("gorek_obsidian_horn", 2), ItemStack("blank_rune", 3)),
            station = ActivePanel.RUNECRAFTING_TABLE,
            tab = CraftingTab.MAGE,
            requiredMagicLevel = 18,
            manaCost = 150,
            soulCost = 12
        )
    )

    fun findById(recipeId: String): CraftingRecipe? =
        smeltingRecipes.firstOrNull { it.id == recipeId }
            ?: forgingRecipes.firstOrNull { it.id == recipeId }
            ?: fletchingRecipes.firstOrNull { it.id == recipeId }
            ?: runecraftingRecipes.firstOrNull { it.id == recipeId }
}
