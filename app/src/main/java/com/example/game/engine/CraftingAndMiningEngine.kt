package com.example.game.engine

import com.example.game.model.ActivePanel
import com.example.game.model.BiomeEquipmentClass
import com.example.game.model.CraftingStationType
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.OreVeinKind
import com.example.game.model.ProjectileKind
import com.example.game.model.RecipeCatalog
import com.example.game.model.SkillType
import com.example.game.model.TREE_CHOP_COOLDOWN_MS
import com.example.game.model.TREE_REGROW_TICKS
import com.example.game.model.TileType
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.OverlandBiomeTier
import com.example.game.world.WorldMaps
import kotlin.random.Random

/**
 * Pure-Kotlin world-anchored Ore Mining, Tree Chopping, and location-locked Biome Crafting Stall
 * mechanics. Enforces Pickaxe tier & Mining skill requirements, faster gather speeds for higher-tier
 * pickaxes on lower-tier veins, Cap (oz) weight checks, vein depletion/regen, and 2-tile physical
 * proximity to each biome's dedicated crafting stall.
 */
object CraftingAndMiningEngine {

    const val BASE_MINE_SWING_COOLDOWN_MS: Long = 850L
    const val MIN_MINE_SWING_COOLDOWN_MS: Long = 260L
    const val BIOME_STALL_INTERACT_RADIUS: Int = 2

    /**
     * Higher-tier pickaxes swing faster on lower-tier ore veins (850ms at tier parity down to 260ms).
     */
    fun swingCooldownMs(pickaxeTier: Int, veinRequiredTier: Int): Long {
        val tierAdvantage = (pickaxeTier - veinRequiredTier).coerceAtLeast(0)
        return (BASE_MINE_SWING_COOLDOWN_MS - tierAdvantage * 130L).coerceAtLeast(MIN_MINE_SWING_COOLDOWN_MS)
    }

    /**
     * Rolls for bonus fishing worms unearthed while digging/mining an ore vein.
     * Returns (wormItemId, count) or null if none surfaced on this strike.
     */
    fun rollWormForageForOre(
        oreKind: OreVeinKind,
        roll: Double = Random.nextDouble(),
        countRoll: Double = Random.nextDouble()
    ): Pair<String, Int>? {
        val (wormId, chance) = when (oreKind) {
            OreVeinKind.COPPER, OreVeinKind.TIN -> "garden_worm" to 0.38
            OreVeinKind.BOG_IRON -> "dew_nightcrawler" to 0.40
            OreVeinKind.MIRE_SILVER, OreVeinKind.PEAT_COAL -> "mire_bloodworm" to 0.42
            OreVeinKind.VESPERITE -> "dune_sandworm" to 0.44
            OreVeinKind.GLACIER_QUARTZ, OreVeinKind.FROST_IRON -> "frost_glowworm" to 0.46
            OreVeinKind.CINDERITE -> "cinder_emberworm" to 0.48
        }
        if (roll > chance) return null
        val count = if (countRoll < 0.42) 2 else 1
        return wormId to count
    }

    /**
     * Rolls for bonus fishing worms/grubs shaken loose from roots and bark while chopping a tree.
     * Scales naturally with the overland biome (and upgrades Heartland finds to Dew Nightcrawlers
     * once the player reaches Fishing Lv 15).
     */
    fun rollWormForageForTree(
        floor: Int,
        tx: Int,
        ty: Int,
        fishingLevel: Int,
        roll: Double = Random.nextDouble(),
        countRoll: Double = Random.nextDouble()
    ): Pair<String, Int>? {
        val biome = if (floor == 0) {
            BiomeProgressionRules.biomeForGlobalTile(tx, ty)
        } else {
            BiomeProgressionRules.surfaceBiomeForDungeonFloor(floor)
        }
        val (wormId, chance) = when (biome) {
            OverlandBiomeTier.ROOKVALE_HEARTLAND -> {
                val id = if (fishingLevel >= 15 && countRoll >= 0.45) "dew_nightcrawler" else "garden_worm"
                id to 0.36
            }
            OverlandBiomeTier.WHISPERING_MARSH -> "mire_bloodworm" to 0.40
            OverlandBiomeTier.SUNSCORCH_BADLANDS -> "dune_sandworm" to 0.42
            OverlandBiomeTier.FROSTPEAK_HIGHLANDS -> "frost_glowworm" to 0.44
            OverlandBiomeTier.ASHEN_CALDERA -> "cinder_emberworm" to 0.46
        }
        if (roll > chance) return null
        val count = if (countRoll < 0.40) 2 else 1
        return wormId to count
    }

    fun mineOreNode(
        state: GameWorldState,
        tx: Int,
        ty: Int,
        nowMs: Long,
        mineRollOverride: Double? = null,
        wormRollOverride: Double? = null
    ): GameWorldState {
        val key = Triple(state.floor, tx, ty)
        val node = state.oreNodes[key]
            ?: return state.addLogInternal("There is no ore vein there.")
        if (node.isDepleted) {
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "DEPLETED", FloatingTextKind.INFO, nowMs)
                .addLogInternal("This ${node.kind.displayName} is spent. It will regenerate soon.")
        }

        val miningLvl = state.skills.mining.level
        val anyPickaxe = state.bestPickaxe()
            ?: return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "NEED PICKAXE", FloatingTextKind.INFO, nowMs)
                .addLogInternal("You need a ${node.kind.requiredPickaxeName} (Mining ${node.kind.requiredMiningLevel}) in your backpack or hand to mine!")

        if (miningLvl < node.kind.requiredMiningLevel) {
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "MINING ${node.kind.requiredMiningLevel}", FloatingTextKind.INFO, nowMs)
                .addLogInternal("${node.kind.displayName} requires Mining level ${node.kind.requiredMiningLevel} (you have $miningLvl).")
        }

        val pickaxe = state.bestValidPickaxeFor(miningLvl)
        if (pickaxe == null || pickaxe.pickaxeTier < node.kind.requiredPickaxeTier) {
            if (anyPickaxe.pickaxeTier >= node.kind.requiredPickaxeTier && miningLvl < anyPickaxe.requiredMiningLevel) {
                return state
                    .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "MINING ${anyPickaxe.requiredMiningLevel}", FloatingTextKind.INFO, nowMs)
                    .addLogInternal("${anyPickaxe.name} requires Mining level ${anyPickaxe.requiredMiningLevel} (you have $miningLvl).")
            }
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "NEED T${node.kind.requiredPickaxeTier} PICK", FloatingTextKind.INFO, nowMs)
                .addLogInternal("${node.kind.displayName} requires at least a ${node.kind.requiredPickaxeName} (Tier ${node.kind.requiredPickaxeTier}, Mining ${node.kind.requiredMiningLevel}).")
        }

        val cooldownMs = swingCooldownMs(pickaxe.pickaxeTier, node.kind.requiredPickaxeTier)
        if (nowMs > 0L && state.lastMineSwingMs > 0L && (nowMs - state.lastMineSwingMs) < cooldownMs) {
            return state
        }

        val oreDef = ItemCatalog.get(node.kind.oreItemId)
        if (state.remainingCapOz() < oreDef.weightOz) {
            return state
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "CAP FULL", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Too heavy to mine ${oreDef.name} (${oreDef.weightOz} oz needed)!")
        }

        // Visual spark effect + advance Mining skill + track swing timestamp
        var next = state.copy(
            playerFacing = GameEngine.directionToward(state.playerX, state.playerY, tx, ty),
            lastMineSwingMs = nowMs
        ).addProjectileInternal(
            state.floor,
            state.playerX.toFloat(),
            state.playerY.toFloat(),
            tx.toFloat(),
            ty.toFloat(),
            ProjectileKind.MINING_SPARK,
            nowMs,
            durationMs = (cooldownMs * 0.45).toLong().coerceIn(140L, 300L)
        )
        next = GameEngine.advanceSkillPublic(next, SkillType.MINING, node.kind.miningXpPoints, nowMs)

        // Success & bonus double-ore chance scale with Mining skill and Pickaxe tier advantage
        val updatedMiningLvl = next.skills.mining.level
        val tierAdvantage = (pickaxe.pickaxeTier - node.kind.requiredPickaxeTier).coerceAtLeast(0)
        val tierBonus = tierAdvantage * 0.14
        val skillDelta = (updatedMiningLvl - node.kind.requiredMiningLevel).coerceAtLeast(0)
        val successChance = (0.62 + skillDelta * 0.02 + tierBonus).coerceAtMost(0.98)

        if ((mineRollOverride ?: Random.nextDouble()) > successChance) {
            return next
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "Clink!", FloatingTextKind.BLOCK, nowMs)
                .addLogInternal("Struck ${node.kind.displayName} with ${pickaxe.name}... chips fell loose.")
        }

        val bonusChance = (skillDelta * 0.015 + tierAdvantage * 0.10 + (pickaxe.pickaxeTier - 1) * 0.05).coerceIn(0.0, 0.55)
        val desiredYield = if (Random.nextDouble() < bonusChance) 2 else 1
        val actualYield = if (desiredYield == 2 && next.remainingCapOz() >= oreDef.weightOz * 2) 2 else 1

        val newCharges = (node.chargesLeft - 1).coerceAtLeast(0)
        val updatedNode = node.copy(
            chargesLeft = newCharges,
            regenTicksLeft = if (newCharges == 0) node.kind.regenTicks else 0
        )
        val updatedNodes = next.oreNodes.toMutableMap().apply { put(key, updatedNode) }

        next = next.copy(
            backpack = GameEngine.addItemPublic(next.backpack, oreDef.id, actualYield),
            oreNodes = updatedNodes
        ).addFloatingInternal(
            state.floor,
            tx.toFloat(),
            ty.toFloat(),
            "+$actualYield ${oreDef.name}",
            FloatingTextKind.XP,
            nowMs
        )

        val wormFind = rollWormForageForOre(
            oreKind = node.kind,
            roll = wormRollOverride ?: Random.nextDouble()
        )
        val wormNote = if (wormFind != null) {
            val (wormId, wormCount) = wormFind
            val wormDef = ItemCatalog.get(wormId)
            val fitCount = (next.remainingCapOz() / wormDef.weightOz).toInt().coerceIn(0, wormCount)
            if (fitCount > 0) {
                next = next.copy(
                    backpack = GameEngine.addItemPublic(next.backpack, wormId, fitCount)
                ).addFloatingInternal(
                    state.floor,
                    state.playerX.toFloat(),
                    state.playerY.toFloat(),
                    "+$fitCount ${wormDef.name}",
                    FloatingTextKind.HEAL,
                    nowMs
                )
                " Unearthed ${fitCount}x ${wormDef.name}!"
            } else ""
        } else ""

        val depleteSuffix = if (newCharges == 0) " Vein depleted." else ""
        return next.addLogInternal("Mined ${actualYield}x ${oreDef.name} with ${pickaxe.name} (Mining ${next.skills.mining.level}).$wormNote$depleteSuffix")
    }

    fun regenerateOreNodesTick(state: GameWorldState): GameWorldState {
        var changed = false
        val updated = HashMap<Triple<Int, Int, Int>, com.example.game.model.OreNodeState>(state.oreNodes.size)
        for ((key, node) in state.oreNodes) {
            if (node.isDepleted) {
                changed = true
                if (node.regenTicksLeft <= 1) {
                    updated[key] = node.copy(chargesLeft = node.kind.maxCharges, regenTicksLeft = 0)
                } else {
                    updated[key] = node.copy(regenTicksLeft = node.regenTicksLeft - 1)
                }
            } else {
                updated[key] = node
            }
        }
        return if (changed) state.copy(oreNodes = updated) else state
    }

    fun chopTree(
        state: GameWorldState,
        tx: Int,
        ty: Int,
        nowMs: Long,
        wormRollOverride: Double? = null
    ): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        if (floorMap.tileAt(tx, ty) != TileType.TREE) {
            return state.addLogInternal("There is no choppable tree there.")
        }
        val key = Triple(state.floor, tx, ty)
        val regrowLeft = state.choppedTrees[key]
        if (regrowLeft != null && regrowLeft > 0) {
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "STUMP (${regrowLeft}s)", FloatingTextKind.INFO, nowMs)
                .addLogInternal("This tree stump is regrowing (${regrowLeft}s remaining).")
        }

        if (!state.hasHatchet()) {
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "NEED HATCHET", FloatingTextKind.INFO, nowMs)
                .addLogInternal("You need a Hatchet to chop trees. Buy one from Alaric's Shop!")
        }

        if (state.lastTreeChopMs > 0L && nowMs - state.lastTreeChopMs < TREE_CHOP_COOLDOWN_MS) {
            return state
        }

        val logDef = ItemCatalog.get("wood_log")
        if (state.remainingCapOz() < logDef.weightOz) {
            return state
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "CAP FULL", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Too heavy to carry a ${logDef.name} (${logDef.weightOz} oz needed)!")
        }

        val desiredYield = if (Random.nextDouble() < 0.35) 2 else 1
        val actualYield = if (desiredYield == 2 && state.remainingCapOz() >= logDef.weightOz * 2) 2 else 1
        val updatedTrees = state.choppedTrees.toMutableMap().apply { put(key, TREE_REGROW_TICKS) }

        var next = state.copy(
            playerFacing = GameEngine.directionToward(state.playerX, state.playerY, tx, ty),
            backpack = GameEngine.addItemPublic(state.backpack, logDef.id, actualYield),
            choppedTrees = updatedTrees,
            lastTreeChopMs = nowMs
        )
            .addProjectileInternal(
                state.floor,
                state.playerX.toFloat(),
                state.playerY.toFloat(),
                tx.toFloat(),
                ty.toFloat(),
                ProjectileKind.WOOD_CHIP,
                nowMs,
                durationMs = 300L
            )
            .addFloatingInternal(
                state.floor,
                tx.toFloat(),
                ty.toFloat(),
                "+$actualYield Wood Log",
                FloatingTextKind.XP,
                nowMs
            )

        val wormFind = rollWormForageForTree(
            floor = state.floor,
            tx = tx,
            ty = ty,
            fishingLevel = state.skills.fishing.level,
            roll = wormRollOverride ?: Random.nextDouble()
        )
        val wormNote = if (wormFind != null) {
            val (wormId, wormCount) = wormFind
            val wormDef = ItemCatalog.get(wormId)
            val fitCount = (next.remainingCapOz() / wormDef.weightOz).toInt().coerceIn(0, wormCount)
            if (fitCount > 0) {
                next = next.copy(
                    backpack = GameEngine.addItemPublic(next.backpack, wormId, fitCount)
                ).addFloatingInternal(
                    state.floor,
                    state.playerX.toFloat(),
                    state.playerY.toFloat(),
                    "+$fitCount ${wormDef.name}",
                    FloatingTextKind.HEAL,
                    nowMs
                )
                " Found ${fitCount}x ${wormDef.name} beneath the bark!"
            } else ""
        } else ""

        return next.addLogInternal("Chopped ${actualYield}x ${logDef.name} with your Hatchet.$wormNote Tree will regrow in ${TREE_REGROW_TICKS}s.")
    }

    fun regenerateTreesTick(state: GameWorldState): GameWorldState {
        if (state.choppedTrees.isEmpty()) return state
        val updated = LinkedHashMap<Triple<Int, Int, Int>, Int>(state.choppedTrees.size)
        for ((key, ticksLeft) in state.choppedTrees) {
            if (ticksLeft > 1) {
                updated[key] = ticksLeft - 1
            }
        }
        return state.copy(choppedTrees = updated)
    }

    fun locationLockMessage(recipeId: String): String {
        val recipe = RecipeCatalog.findById(recipeId) ?: return "Must be at the matching Biome Crafting Stall."
        val outDef = ItemCatalog.get(recipe.outputItemId)
        val gearLabel = when (outDef.biomeClass) {
            BiomeEquipmentClass.HEARTLAND -> "Heartland gear"
            BiomeEquipmentClass.MIRE_WOVEN -> "Mire-Woven gear"
            BiomeEquipmentClass.VESPERITE_SUNSCORCH -> "Vesperite gear"
            BiomeEquipmentClass.RIME_PLATE -> "Rime-Plate gear"
            BiomeEquipmentClass.CINDERITE_ASHEN -> "Cinderite gear"
            BiomeEquipmentClass.NONE -> outDef.name
        }
        return "Must be at the ${recipe.requiredBiomeStation.displayName} to forge $gearLabel"
    }

    fun executeRecipe(state: GameWorldState, recipeId: String, nowMs: Long): GameWorldState {
        val recipe = RecipeCatalog.findById(recipeId) ?: return state
        val outDef = ItemCatalog.get(recipe.outputItemId)

        // Strictly enforce physical proximity (within 2 tiles) to the recipe's required Biome Crafting Stall!
        val atRequiredStall = isNearBiomeStation(state, recipe.requiredBiomeStation, BIOME_STALL_INTERACT_RADIUS)
        if (!atRequiredStall) {
            val msg = locationLockMessage(recipeId)
            return state
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "WRONG STALL", FloatingTextKind.INFO, nowMs)
                .addLogInternal(msg)
        }

        // Also ensure an appropriate crafting panel (or Recipe Codex while standing beside the stall) is open
        val panelAllowed = state.activePanel == recipe.station ||
            state.activePanel == ActivePanel.BIOME_STALL ||
            state.activePanel == ActivePanel.RECIPE_CODEX
        if (!panelAllowed) {
            return state.addLogInternal(locationLockMessage(recipeId))
        }

        // Validate Magic Level, Mana & Soul requirements for Runecrafting recipes
        if (recipe.requiredMagicLevel > 0 && state.magicLevel < recipe.requiredMagicLevel) {
            return state.addLogInternal("You need Magic Level ${recipe.requiredMagicLevel} to inscribe ${outDef.name}.")
        }
        if (recipe.manaCost > 0 && state.mp < recipe.manaCost) {
            return state.addLogInternal("Not enough Mana (${recipe.manaCost} MP required to inscribe ${outDef.name}).")
        }
        if (recipe.soulCost > 0 && state.soul < recipe.soulCost) {
            return state.addLogInternal("Not enough Soul (${recipe.soulCost} Soul required). Rest at the Temple or hunt monsters to recover Soul.")
        }

        // Verify all required ingredients are in backpack / tool bag
        for (req in recipe.ingredients) {
            if (!state.hasItem(req.itemId, req.count)) {
                val missing = ItemCatalog.get(req.itemId).name
                return state.addLogInternal("Missing $missing (need ${req.count}).")
            }
        }

        // Check net Cap (oz) delta: smelting ores -> ingot frees Cap; forged tools stow in Tool Bag at 50% oz!
        val consumedWeight = recipe.ingredients.sumOf { ItemCatalog.get(it.itemId).carriedWeightOz() * it.count }
        val producedWeight = outDef.carriedWeightOz() * recipe.outputCount
        val netDelta = producedWeight - consumedWeight
        if (netDelta > 0 && state.remainingCapOz() < netDelta) {
            return state.addLogInternal("Too heavy! Need ${String.format("%.1f", netDelta)} oz extra Cap.")
        }

        var next = state
        for (req in recipe.ingredients) {
            next = GameEngine.removeCarriedItem(next, req.itemId, req.count)
        }
        if (recipe.manaCost > 0 || recipe.soulCost > 0) {
            next = next.copy(
                mp = (next.mp - recipe.manaCost).coerceAtLeast(0),
                soul = (next.soul - recipe.soulCost).coerceAtLeast(0)
            )
        }
        next = GameEngine.addCarriedItem(next, recipe.outputItemId, recipe.outputCount)

        if (recipe.manaCost > 0) {
            next = GameEngine.awardMagicXpFromMana(next, recipe.manaCost, nowMs)
            next = next.queuePlayerFx(null, null, com.example.game.multiplayer.PlayerActionState.CASTING)
        }

        val verb = when (recipe.station) {
            ActivePanel.FURNACE -> "Smelted"
            ActivePanel.FLETCHING_BENCH -> "Fletched"
            ActivePanel.RUNECRAFTING_TABLE -> "Inscribed"
            else -> "Forged"
        }
        val qtyLabel = if (recipe.outputCount > 1) "${recipe.outputCount}x ${outDef.name}" else outDef.name
        val extraNote = when {
            recipe.station == ActivePanel.RUNECRAFTING_TABLE -> " (-${recipe.manaCost} MP, -${recipe.soulCost} Soul)"
            outDef.isTool -> " (packed in Tool Bag!)"
            netDelta < -0.5 -> " (saved ${String.format("%.1f", -netDelta)} oz Cap!)"
            else -> ""
        }

        return next
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "$verb $qtyLabel", FloatingTextKind.SKILL_UP, nowMs)
            .addLogInternal("$verb $qtyLabel at ${recipe.requiredBiomeStation.displayName}$extraNote")
    }

    /**
     * Checks whether the player is physically standing within [radiusTiles] (Chebyshev distance)
     * of the specified [CraftingStationType]. Rookvale Town Forge also encompasses the original
     * Rookvale Smithy Furnace, Blacksmith Anvil, Fletching Workbench, and Runecrafting Table
     * tiles so existing Rookvale workshops function seamlessly as the Heartland crafting hub.
     */
    fun isNearBiomeStation(
        state: GameWorldState,
        station: CraftingStationType,
        radiusTiles: Int = BIOME_STALL_INTERACT_RADIUS
    ): Boolean {
        val fl = WorldMaps.getFloor(state.floor)
        val stallTile = station.stallTileType()
        for (dy in -radiusTiles..radiusTiles) {
            for (dx in -radiusTiles..radiusTiles) {
                val tile = fl.tileAt(state.playerX + dx, state.playerY + dy)
                if (tile == stallTile) return true
                if (station == CraftingStationType.ROOKVALE_TOWN_FORGE &&
                    (tile == TileType.SMELTING_FURNACE ||
                        tile == TileType.BLACKSMITH_ANVIL ||
                        tile == TileType.FLETCHING_BENCH ||
                        tile == TileType.RUNECRAFTING_TABLE)
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns the Biome Crafting Stall the player is currently standing within [radiusTiles] of,
     * or null if away from all biome crafting stalls.
     */
    fun nearbyBiomeStation(
        state: GameWorldState,
        radiusTiles: Int = BIOME_STALL_INTERACT_RADIUS
    ): CraftingStationType? {
        for (station in CraftingStationType.entries) {
            if (isNearBiomeStation(state, station, radiusTiles)) return station
        }
        return null
    }

    fun isNearStation(state: GameWorldState, stationTile: TileType, radius: Int = 1): Boolean {
        val fl = WorldMaps.getFloor(state.floor)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (fl.tileAt(state.playerX + dx, state.playerY + dy) == stationTile) return true
            }
        }
        return false
    }
}
