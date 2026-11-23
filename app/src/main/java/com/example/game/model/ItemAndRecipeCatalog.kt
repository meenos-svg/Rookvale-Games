package com.example.game.model

enum class CraftingTab(val label: String) {
    KNIGHT("Knight"),
    PALADIN("Paladin"),
    MAGE("Mage"),
    AMMO("Ammo"),
    TOOLS("Pickaxes")
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
        ItemDefinition("quarry_mattock", "Quarry Mattock", ItemCategory.TOOL_PICKAXE, 22.0, buyPrice = 75, sellPrice = 25, pickaxeTier = 1, description = "Tier 1 pickaxe. Extracts Ochre Veins."),
        ItemDefinition("sunbrass_pick", "Sunbrass Pick", ItemCategory.TOOL_PICKAXE, 26.0, sellPrice = 70, pickaxeTier = 2, description = "Tier 2 forged pick. Extracts Ochre & Bogstone Veins."),
        ItemDefinition("duskmetal_breaker", "Duskmetal Breaker", ItemCategory.TOOL_PICKAXE, 30.0, sellPrice = 140, pickaxeTier = 3, description = "Tier 3 forged pick. Extracts up to Brimstone Seams (+15% yield)."),
        ItemDefinition("quenchsteel_delver", "Quenchsteel Delver", ItemCategory.TOOL_PICKAXE, 34.0, sellPrice = 260, pickaxeTier = 4, description = "Tier 4 master pick. Extracts Aetherium Lodes (+30% yield)."),

        // --- Original Rookvale Raw Ores & Smelted Ingots ---
        ItemDefinition("ochre_nugget", "Ochre Nugget", ItemCategory.RAW_ORE, 14.0, sellPrice = 10, stackable = true, description = "Warm bronze-rust ore chipped from shallow Ochre Veins."),
        ItemDefinition("bogstone_shard", "Bogstone Shard", ItemCategory.RAW_ORE, 18.0, sellPrice = 18, stackable = true, description = "Heavy dark ferrous ore mined from Bogstone Veins."),
        ItemDefinition("cinder_pitch", "Cinder Pitch", ItemCategory.RAW_ORE, 9.0, sellPrice = 22, stackable = true, description = "Smoldering volcanic seam fuel used to forge high-tier ingots."),
        ItemDefinition("pale_starstone", "Pale Starstone", ItemCategory.RAW_ORE, 22.0, sellPrice = 48, stackable = true, description = "Luminous subterranean ore pried from deep Aetherium Lodes."),

        ItemDefinition("sunbrass_ingot", "Sunbrass Ingot", ItemCategory.METAL_INGOT, 16.0, sellPrice = 26, stackable = true, description = "Refined Sunbrass bar smelted from 2 Ochre Nuggets."),
        ItemDefinition("duskmetal_ingot", "Duskmetal Ingot", ItemCategory.METAL_INGOT, 20.0, sellPrice = 46, stackable = true, description = "Dense Duskmetal bar smelted from 2 Bogstone Shards."),
        ItemDefinition("quenchsteel_ingot", "Quenchsteel Ingot", ItemCategory.METAL_INGOT, 22.0, sellPrice = 85, stackable = true, description = "Tempered alloy smelted from Bogstone Shards and Cinder Pitch."),
        ItemDefinition("moonforge_ingot", "Moonforge Ingot", ItemCategory.METAL_INGOT, 24.0, sellPrice = 165, stackable = true, description = "Radiant deep-forged bar smelted from Pale Starstone and Cinder Pitch."),

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
        ItemDefinition("colossus_tendon", "Colossus Tendon", ItemCategory.CRAFT_MATERIAL, 4.5, sellPrice = 68, stackable = true, description = "Sinewy giant cord dropped by Orc Warriors & Cyclopses."),
        ItemDefinition("runic_slate", "Runic Slate", ItemCategory.CRAFT_MATERIAL, 6.0, sellPrice = 80, stackable = true, description = "Etched obsidian tablet carried by Necromancers & Abyssal foes."),
        ItemDefinition("ember_heart", "Ember Heart", ItemCategory.CRAFT_MATERIAL, 2.6, sellPrice = 115, stackable = true, description = "Pulsing volcanic cinder harvested from Fire Devils & Demon Lords."),
        ItemDefinition("wyrmscale_plate", "Wyrmscale Plate", ItemCategory.CRAFT_MATERIAL, 5.5, sellPrice = 150, stackable = true, description = "Emerald fire-hardened scale from Dragon Hatchlings & Elder Dragons."),
        ItemDefinition("dragon_tooth", "Draketalon", ItemCategory.CRAFT_MATERIAL, 3.0, sellPrice = 190, stackable = true, description = "Curved boss trophy taken from the Demon Lord or Elder Dragon."),

        // --- Multi-Tier Forged Weapons: KNIGHT (Melee) ---
        ItemDefinition("wooden_club", "Wooden Club", ItemCategory.WEAPON_MELEE, 21.0, sellPrice = 12, attack = 11, defense = 6, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 10, description = "Rough knotted cudgel dropped by lowland pests."),
        ItemDefinition("sunbrass_dirk", "Sunbrass Dirk", ItemCategory.WEAPON_MELEE, 18.0, sellPrice = 55, attack = 15, defense = 8, attackIntervalMs = 800L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 10, description = "Swift 1H bronze-tinted dagger for agile strikes."),
        ItemDefinition("rookvale_gladius", "Rookvale Gladius", ItemCategory.WEAPON_MELEE, 29.0, sellPrice = 75, attack = 19, defense = 13, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 11, description = "Balanced 1H forged shortblade with steady guard."),
        ItemDefinition("duskmetal_falchion", "Duskmetal Falchion", ItemCategory.WEAPON_MELEE, 36.0, sellPrice = 145, attack = 26, defense = 17, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 14, description = "Curved 1H dark-ferrous chopper forged in Rookvale."),
        ItemDefinition("bog_cleaver_axe", "Bog-Cleaver Axe", ItemCategory.WEAPON_MELEE, 54.0, sellPrice = 175, attack = 34, defense = 12, attackIntervalMs = 1400L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 15, isTwoHanded = true, description = "Heavy 2H bearded axe delivering crushing blows without a shield."),
        ItemDefinition("quenchsteel_bastard", "Quenchsteel Bastard", ItemCategory.WEAPON_MELEE, 42.0, sellPrice = 260, attack = 33, defense = 21, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 18, preferredVocation = Vocation.KNIGHT, description = "1H blade tempered over Cinder Pitch. Knight Tier 3 mastery."),
        ItemDefinition("colossus_glaive", "Colossus Glaive", ItemCategory.WEAPON_MELEE, 68.0, sellPrice = 310, attack = 44, defense = 16, attackIntervalMs = 1400L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 19, isTwoHanded = true, preferredVocation = Vocation.KNIGHT, description = "Massive 2H polearm bound in Colossus Tendon. Knight Tier 3 mastery."),
        ItemDefinition("moonforge_rapier", "Moonforge Rapier", ItemCategory.WEAPON_MELEE, 26.0, sellPrice = 490, attack = 37, defense = 22, attackIntervalMs = 800L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 22, preferredVocation = Vocation.KNIGHT, description = "Feather-light 1H starstone dueling blade. Knight Tier 4 mastery."),
        ItemDefinition("wyrmtooth_claymore", "Wyrmtooth Claymore", ItemCategory.WEAPON_MELEE, 62.0, sellPrice = 620, attack = 52, defense = 25, attackIntervalMs = 1200L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 24, isTwoHanded = true, preferredVocation = Vocation.KNIGHT, description = "Serrated 2H greatsword edged with Draketalon. Knight Tier 4 mastery."),

        // --- Multi-Tier Forged & Fletched Weapons: PALADIN (Distance) ---
        ItemDefinition("ashwood_shortbow", "Ashwood Shortbow", ItemCategory.WEAPON_DISTANCE, 20.0, sellPrice = 60, attack = 16, range = 4, attackIntervalMs = 1000L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 10, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, description = "Compact 2H bow firing wooden & forged Arrows/Shafts."),
        ItemDefinition("hunting_bow", "Hunting Bow", ItemCategory.WEAPON_DISTANCE, 22.0, sellPrice = 95, attack = 20, range = 5, attackIntervalMs = 950L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 11, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, description = "Seasoned hardwood 2H bow fletched with sinew and hide. Fires Arrows & Shafts."),
        ItemDefinition("ivory_javelin", "Ivory-Tipped Javelin", ItemCategory.WEAPON_DISTANCE, 24.0, sellPrice = 80, attack = 20, range = 4, attackIntervalMs = 1000L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 11, description = "1H throwing spear tipped with Jagged Ivory; pairs with a shield."),
        ItemDefinition("brindle_recurve", "Brindle Recurve", ItemCategory.WEAPON_DISTANCE, 25.0, sellPrice = 165, attack = 24, range = 5, attackIntervalMs = 900L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 14, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, description = "Snappy 2H sinew-backed bow firing Shafts at high cadence."),
        ItemDefinition("quenchsteel_arbalest", "Quenchsteel Arbalest", ItemCategory.WEAPON_DISTANCE, 44.0, sellPrice = 290, attack = 39, range = 5, attackIntervalMs = 1300L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 18, requiredAmmoKind = AmmoKind.QUARRELS, isTwoHanded = true, preferredVocation = Vocation.PALADIN, description = "Heavy 2H cranked crossbow loosing Quarrels. Paladin Tier 3 mastery."),
        ItemDefinition("moonforge_warbow", "Moonforge Warbow", ItemCategory.WEAPON_DISTANCE, 30.0, sellPrice = 520, attack = 36, range = 5, attackIntervalMs = 900L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 22, requiredAmmoKind = AmmoKind.SHAFTS, isTwoHanded = true, preferredVocation = Vocation.PALADIN, description = "Luminous 2H longbow prized by master marksmen. Paladin Tier 4 mastery."),
        ItemDefinition("draketalon_siegebow", "Draketalon Siegebow", ItemCategory.WEAPON_DISTANCE, 56.0, sellPrice = 640, attack = 54, range = 6, attackIntervalMs = 1300L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 24, requiredAmmoKind = AmmoKind.QUARRELS, isTwoHanded = true, preferredVocation = Vocation.PALADIN, description = "Colossal 2H dragon-limbed arbalest. Paladin Tier 4 mastery."),

        // --- Multi-Tier Forged Weapons: MAGE (Magic) ---
        ItemDefinition("sunbrass_scepter", "Sunbrass Scepter", ItemCategory.WEAPON_WAND, 18.0, sellPrice = 65, attack = 17, range = 3, attackIntervalMs = 800L, manaCost = 4, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 1, description = "Quick 1H spark-channeling wand. Pairs with a shield."),
        ItemDefinition("bilewood_conduit", "Bilewood Conduit", ItemCategory.WEAPON_WAND, 22.0, sellPrice = 155, attack = 25, range = 4, attackIntervalMs = 900L, manaCost = 6, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 6, description = "Venom-infused 1H focus rod firing corrosive bolts."),
        ItemDefinition("tallowbone_crosier", "Tallowbone Crosier", ItemCategory.WEAPON_WAND, 27.0, sellPrice = 285, attack = 34, range = 4, attackIntervalMs = 1000L, manaCost = 8, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 11, isTwoHanded = true, preferredVocation = Vocation.MAGE, description = "Crypt-carved 2H staff channeling Wraith Tallow. Mage Tier 3 mastery."),
        ItemDefinition("moonforge_spire", "Moonforge Spire", ItemCategory.WEAPON_WAND, 24.0, sellPrice = 510, attack = 39, range = 5, attackIntervalMs = 800L, manaCost = 9, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 14, preferredVocation = Vocation.MAGE, description = "Rapid 1H starstone wand pulsing with astral lightning. Mage Tier 4 mastery."),
        ItemDefinition("cinderheart_rod", "Cinderheart Rod", ItemCategory.WEAPON_WAND, 31.0, sellPrice = 640, attack = 52, range = 5, attackIntervalMs = 1100L, manaCost = 12, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 16, isTwoHanded = true, preferredVocation = Vocation.MAGE, description = "Volcanic 2H staff crowned with an Ember Heart. Mage Tier 4 mastery."),

        // --- Craftable Ammunition (Ammo Slot) ---
        ItemDefinition("wooden_arrows", "Wooden Arrows", ItemCategory.AMMO, 0.1, sellPrice = 1, attack = 4, ammoKind = AmmoKind.SHAFTS, stackable = true, description = "Hand-fletched hardwood arrows for Shortbows & Hunting Bows."),
        ItemDefinition("ochre_shafts", "Ochre-Fletched Shafts", ItemCategory.AMMO, 0.12, sellPrice = 2, attack = 6, ammoKind = AmmoKind.SHAFTS, stackable = true, description = "Light forged arrows for Shortbows, Hunting Bows & Recurves."),
        ItemDefinition("venom_shafts", "Venom-Barbed Shafts", ItemCategory.AMMO, 0.15, sellPrice = 5, attack = 12, ammoKind = AmmoKind.SHAFTS, stackable = true, description = "Bile-coated arrows forged for Recurves & Warbows."),
        ItemDefinition("quenchsteel_quarrels", "Quenchsteel Quarrels", ItemCategory.AMMO, 0.2, sellPrice = 6, attack = 15, ammoKind = AmmoKind.QUARRELS, stackable = true, description = "Heavy steel bolts crafted for Arbalests."),
        ItemDefinition("starlight_quarrels", "Starlight Quarrels", ItemCategory.AMMO, 0.18, sellPrice = 12, attack = 24, ammoKind = AmmoKind.QUARRELS, stackable = true, description = "Moonforge-tipped bolts of lethal precision."),

        // --- Legacy V1 Weapons (Kept for 100% Save Backward Compatibility; not sold or dropped) ---
        ItemDefinition("short_sword", "Short Sword", ItemCategory.WEAPON_MELEE, 28.0, sellPrice = 18, attack = 14, defense = 10, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 10, description = "Heirloom blade from early Rookvale expeditions."),
        ItemDefinition("steel_sword", "Steel Longsword", ItemCategory.WEAPON_MELEE, 36.0, sellPrice = 95, attack = 24, defense = 16, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 13, description = "Legacy Rookvale longsword."),
        ItemDefinition("fire_sword", "Fire Sword", ItemCategory.WEAPON_MELEE, 23.0, sellPrice = 450, attack = 38, defense = 22, attackIntervalMs = 1000L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 18, preferredVocation = Vocation.KNIGHT, description = "Legacy blazing blade."),
        ItemDefinition("halberd", "Cyclops Halberd", ItemCategory.WEAPON_MELEE, 75.0, sellPrice = 240, attack = 33, defense = 14, attackIntervalMs = 1400L, requiredSkillType = SkillType.MELEE, requiredSkillLevel = 16, isTwoHanded = true, description = "Legacy heavy 2H polearm."),
        ItemDefinition("hunting_spear", "Hunting Spear", ItemCategory.WEAPON_DISTANCE, 20.0, sellPrice = 22, attack = 18, range = 4, attackIntervalMs = 1000L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 10, description = "Starter 1H throwing spear."),
        ItemDefinition("crossbow", "Arbalest Bow", ItemCategory.WEAPON_DISTANCE, 34.0, sellPrice = 120, attack = 31, range = 5, attackIntervalMs = 1300L, requiredSkillType = SkillType.DISTANCE, requiredSkillLevel = 14, requiredAmmoKind = AmmoKind.QUARRELS, isTwoHanded = true, description = "Legacy 2H arbalest crossbow."),
        ItemDefinition("wand_vortex", "Wand of Vortex", ItemCategory.WEAPON_WAND, 19.0, sellPrice = 25, attack = 17, range = 3, attackIntervalMs = 900L, manaCost = 4, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 1, description = "Starter crackling energy wand."),
        ItemDefinition("wand_inferno", "Wand of Inferno", ItemCategory.WEAPON_WAND, 26.0, sellPrice = 160, attack = 35, range = 4, attackIntervalMs = 1000L, manaCost = 8, requiredSkillType = SkillType.MAGIC, requiredSkillLevel = 11, description = "Legacy flame wand."),

        // --- Shields & Armor ---
        ItemDefinition("wooden_shield", "Wooden Shield", ItemCategory.SHIELD, 32.0, buyPrice = 35, sellPrice = 12, defense = 14, description = "Basic oak buckler."),
        ItemDefinition("steel_shield", "Dwarven Shield", ItemCategory.SHIELD, 48.0, buyPrice = 210, sellPrice = 90, defense = 24, description = "Heavy iron-banded kite shield."),
        ItemDefinition("dragon_shield", "Wyrmscale Shield", ItemCategory.SHIELD, 60.0, sellPrice = 520, defense = 34, description = "Forged from scales of the Ancient Dragon."),
        ItemDefinition("leather_helmet", "Leather Cap", ItemCategory.HELMET, 15.0, buyPrice = 35, sellPrice = 14, armor = 2, description = "Light hardened leather headgear."),
        ItemDefinition("viking_helmet", "Viking Helmet", ItemCategory.HELMET, 35.0, buyPrice = 150, sellPrice = 65, armor = 5, description = "Horned iron helm."),
        ItemDefinition("leather_armor", "Leather Tunic", ItemCategory.ARMOR, 38.0, buyPrice = 50, sellPrice = 20, armor = 4, description = "Supple traveler's armor."),
        ItemDefinition("mystic_robe", "Mystic Robe", ItemCategory.ARMOR, 24.0, buyPrice = 85, sellPrice = 35, armor = 4, description = "Light enchanted cloak favored by Mages."),
        ItemDefinition("brass_armor", "Brass Armor", ItemCategory.ARMOR, 68.0, buyPrice = 260, sellPrice = 110, armor = 9, description = "Solid riveted breastplate."),
        ItemDefinition("dragon_mail", "Wyrmscale Cuirass", ItemCategory.ARMOR, 94.0, sellPrice = 800, armor = 16, description = "Legendary emerald scale cuirass."),
        ItemDefinition("leather_legs", "Leather Legs", ItemCategory.LEGS, 18.0, buyPrice = 45, sellPrice = 18, armor = 2, description = "Stitched greaves for dungeon travel."),
        ItemDefinition("brass_legs", "Brass Legs", ItemCategory.LEGS, 38.0, buyPrice = 190, sellPrice = 80, armor = 5, description = "Reinforced leg plates."),
        ItemDefinition("leather_boots", "Traveler Boots", ItemCategory.BOOTS, 9.0, buyPrice = 40, sellPrice = 16, armor = 1, description = "Sturdy dungeon boots."),
        ItemDefinition("life_amulet", "Amulet of Vitality", ItemCategory.AMULET, 4.2, buyPrice = 240, sellPrice = 100, armor = 2, description = "Warm ruby pendant granting extra protection.")
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

    fun get(id: String): ItemDefinition = all[id] ?: all.getValue("cheese")
}

object RecipeCatalog {
    val smeltingRecipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "smelt_sunbrass",
            outputItemId = "sunbrass_ingot",
            ingredients = listOf(ItemStack("ochre_nugget", 2)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_duskmetal",
            outputItemId = "duskmetal_ingot",
            ingredients = listOf(ItemStack("bogstone_shard", 2)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_quenchsteel",
            outputItemId = "quenchsteel_ingot",
            ingredients = listOf(ItemStack("bogstone_shard", 2), ItemStack("cinder_pitch", 1)),
            station = ActivePanel.FURNACE
        ),
        CraftingRecipe(
            id = "smelt_moonforge",
            outputItemId = "moonforge_ingot",
            ingredients = listOf(ItemStack("pale_starstone", 2), ItemStack("cinder_pitch", 2)),
            station = ActivePanel.FURNACE
        )
    )

    val forgingRecipes: List<CraftingRecipe> = listOf(
        // --- Knight Melee Weapons ---
        CraftingRecipe("forge_sunbrass_dirk", "sunbrass_dirk", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("jagged_ivory", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_rookvale_gladius", "rookvale_gladius", 1, listOf(ItemStack("sunbrass_ingot", 2), ItemStack("brindle_hide", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_duskmetal_falchion", "duskmetal_falchion", 1, listOf(ItemStack("duskmetal_ingot", 2), ItemStack("ancient_bone", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_bog_cleaver", "bog_cleaver_axe", 1, listOf(ItemStack("duskmetal_ingot", 3), ItemStack("brindle_hide", 3)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_quenchsteel_bastard", "quenchsteel_bastard", 1, listOf(ItemStack("quenchsteel_ingot", 2), ItemStack("ghoul_gem", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_colossus_glaive", "colossus_glaive", 1, listOf(ItemStack("quenchsteel_ingot", 3), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_moonforge_rapier", "moonforge_rapier", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("runic_slate", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),
        CraftingRecipe("forge_wyrmtooth_claymore", "wyrmtooth_claymore", 1, listOf(ItemStack("moonforge_ingot", 3), ItemStack("dragon_tooth", 1), ItemStack("wyrmscale_plate", 2)), ActivePanel.ANVIL, CraftingTab.KNIGHT),

        // --- Paladin Distance Weapons ---
        CraftingRecipe("forge_ashwood_shortbow", "ashwood_shortbow", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_ivory_javelin", "ivory_javelin", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("jagged_ivory", 3)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_brindle_recurve", "brindle_recurve", 1, listOf(ItemStack("duskmetal_ingot", 2), ItemStack("brindle_hide", 2), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_quenchsteel_arbalest", "quenchsteel_arbalest", 1, listOf(ItemStack("quenchsteel_ingot", 2), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_moonforge_warbow", "moonforge_warbow", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("colossus_tendon", 2), ItemStack("spider_silk", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),
        CraftingRecipe("forge_draketalon_siegebow", "draketalon_siegebow", 1, listOf(ItemStack("moonforge_ingot", 3), ItemStack("dragon_tooth", 1), ItemStack("colossus_tendon", 2)), ActivePanel.ANVIL, CraftingTab.PALADIN),

        // --- Mage Arcane Weapons ---
        CraftingRecipe("forge_sunbrass_scepter", "sunbrass_scepter", 1, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("vesper_wing", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_bilewood_conduit", "bilewood_conduit", 1, listOf(ItemStack("duskmetal_ingot", 1), ItemStack("bile_gland", 3)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_tallowbone_crosier", "tallowbone_crosier", 1, listOf(ItemStack("quenchsteel_ingot", 1), ItemStack("ancient_bone", 2), ItemStack("ghoul_gem", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_moonforge_spire", "moonforge_spire", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("runic_slate", 2)), ActivePanel.ANVIL, CraftingTab.MAGE),
        CraftingRecipe("forge_cinderheart_rod", "cinderheart_rod", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("ember_heart", 2), ItemStack("wyrmscale_plate", 1)), ActivePanel.ANVIL, CraftingTab.MAGE),

        // --- Forged Ammunition Batches ---
        CraftingRecipe("forge_ochre_shafts", "ochre_shafts", 20, listOf(ItemStack("sunbrass_ingot", 1), ItemStack("vesper_wing", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),
        CraftingRecipe("forge_venom_shafts", "venom_shafts", 20, listOf(ItemStack("duskmetal_ingot", 1), ItemStack("bile_gland", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),
        CraftingRecipe("forge_quenchsteel_quarrels", "quenchsteel_quarrels", 25, listOf(ItemStack("quenchsteel_ingot", 1), ItemStack("ancient_bone", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),
        CraftingRecipe("forge_starlight_quarrels", "starlight_quarrels", 25, listOf(ItemStack("moonforge_ingot", 1), ItemStack("ember_heart", 1)), ActivePanel.ANVIL, CraftingTab.AMMO),

        // --- Pickaxe Upgrades & Dragon Scale Defense ---
        CraftingRecipe("forge_sunbrass_pick", "sunbrass_pick", 1, listOf(ItemStack("sunbrass_ingot", 2), ItemStack("brindle_hide", 1)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_duskmetal_breaker", "duskmetal_breaker", 1, listOf(ItemStack("duskmetal_ingot", 2), ItemStack("ancient_bone", 2)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_quenchsteel_delver", "quenchsteel_delver", 1, listOf(ItemStack("quenchsteel_ingot", 3), ItemStack("colossus_tendon", 1)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_wyrmscale_shield", "dragon_shield", 1, listOf(ItemStack("moonforge_ingot", 2), ItemStack("wyrmscale_plate", 2)), ActivePanel.ANVIL, CraftingTab.TOOLS),
        CraftingRecipe("forge_wyrmscale_cuirass", "dragon_mail", 1, listOf(ItemStack("moonforge_ingot", 3), ItemStack("wyrmscale_plate", 3)), ActivePanel.ANVIL, CraftingTab.TOOLS)
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
        )
    )

    val runecraftingRecipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "rune_magic_dart",
            outputItemId = "magic_dart_rune",
            outputCount = 3,
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("ochre_nugget", 1)),
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
            ingredients = listOf(ItemStack("blank_rune", 1), ItemStack("ghoul_gem", 1), ItemStack("cinder_pitch", 1)),
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
        )
    )

    fun findById(recipeId: String): CraftingRecipe? =
        smeltingRecipes.firstOrNull { it.id == recipeId }
            ?: forgingRecipes.firstOrNull { it.id == recipeId }
            ?: fletchingRecipes.firstOrNull { it.id == recipeId }
            ?: runecraftingRecipes.firstOrNull { it.id == recipeId }
}
