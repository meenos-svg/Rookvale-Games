package com.example.game.engine

import com.example.game.model.ActivePanel
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.ProjectileKind
import com.example.game.model.RecipeCatalog
import com.example.game.model.SkillType
import com.example.game.model.TREE_CHOP_COOLDOWN_MS
import com.example.game.model.TREE_REGROW_TICKS
import com.example.game.model.TileType
import com.example.game.world.WorldMaps
import kotlin.random.Random

/**
 * Pure-Kotlin world-anchored Ore Mining, Smelting Furnace, and Blacksmith Anvil mechanics.
 * Enforces Pickaxe tier requirements, Cap (oz) weight checks, vein depletion/regen, and
 * Mining skill progression.
 */
object CraftingAndMiningEngine {

    fun mineOreNode(state: GameWorldState, tx: Int, ty: Int, nowMs: Long): GameWorldState {
        val key = Triple(state.floor, tx, ty)
        val node = state.oreNodes[key]
            ?: return state.addLogInternal("There is no ore vein there.")
        if (node.isDepleted) {
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "DEPLETED", FloatingTextKind.INFO, nowMs)
                .addLogInternal("This ${node.kind.displayName} is spent. It will regenerate soon.")
        }

        val pickaxe = state.bestPickaxe()
            ?: return state.addLogInternal("You need a ${node.kind.requiredPickaxeName} in your backpack to mine!")
        if (pickaxe.pickaxeTier < node.kind.requiredPickaxeTier) {
            return state
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "NEED T${node.kind.requiredPickaxeTier} PICK", FloatingTextKind.INFO, nowMs)
                .addLogInternal("${node.kind.displayName} requires at least a ${node.kind.requiredPickaxeName} (Tier ${node.kind.requiredPickaxeTier}).")
        }

        val oreDef = ItemCatalog.get(node.kind.oreItemId)
        if (state.remainingCapOz() < oreDef.weightOz) {
            return state
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "CAP FULL", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Too heavy to mine ${oreDef.name} (${oreDef.weightOz} oz needed)!")
        }

        // Visual spark effect + advance Mining skill
        var next = state.addProjectileInternal(
            state.floor,
            state.playerX.toFloat(),
            state.playerY.toFloat(),
            tx.toFloat(),
            ty.toFloat(),
            ProjectileKind.MINING_SPARK,
            nowMs,
            durationMs = 280L
        )
        next = GameEngine.advanceSkillPublic(next, SkillType.MINING, node.kind.miningXpPoints, nowMs)

        // Success & bonus double-ore chance scale with Mining skill and Pickaxe tier advantage
        val miningLvl = next.skills.mining.level
        val tierBonus = (pickaxe.pickaxeTier - node.kind.requiredPickaxeTier).coerceAtLeast(0) * 0.12
        val successChance = (0.58 + (miningLvl - 10) * 0.025 + tierBonus).coerceAtMost(0.95)

        if (Random.nextDouble() > successChance) {
            return next
                .addFloatingInternal(state.floor, tx.toFloat(), ty.toFloat(), "Clink!", FloatingTextKind.BLOCK, nowMs)
                .addLogInternal("Struck ${node.kind.displayName} with ${pickaxe.name}... chips fell loose.")
        }

        val bonusChance = ((miningLvl - 10) * 0.015 + (pickaxe.pickaxeTier - 1) * 0.08).coerceIn(0.0, 0.45)
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

        val depleteSuffix = if (newCharges == 0) " Vein depleted." else ""
        return next.addLogInternal("Mined ${actualYield}x ${oreDef.name} (Mining ${next.skills.mining.level}).$depleteSuffix")
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

    fun chopTree(state: GameWorldState, tx: Int, ty: Int, nowMs: Long): GameWorldState {
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

        return state.copy(
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
            .addLogInternal("Chopped ${actualYield}x ${logDef.name} with your Hatchet. Tree will regrow in ${TREE_REGROW_TICKS}s.")
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

    fun executeRecipe(state: GameWorldState, recipeId: String, nowMs: Long): GameWorldState {
        val recipe = RecipeCatalog.findById(recipeId) ?: return state
        // Strictly enforce physical station presence — no crafting without matching open station!
        if (state.activePanel != recipe.station) {
            val stationName = when (recipe.station) {
                ActivePanel.FURNACE -> "Smelting Furnace"
                ActivePanel.FLETCHING_BENCH -> "Fletching Workbench"
                ActivePanel.RUNECRAFTING_TABLE -> "Runecrafting Table"
                else -> "Blacksmith Anvil"
            }
            return state.addLogInternal("You must stand beside a $stationName to craft this.")
        }

        val outDef = ItemCatalog.get(recipe.outputItemId)

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

        // Verify all required ingredients are in backpack
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
            .addLogInternal("$verb $qtyLabel$extraNote")
    }

    fun isNearStation(state: GameWorldState, stationTile: com.example.game.model.TileType): Boolean {
        val fl = WorldMaps.getFloor(state.floor)
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (fl.tileAt(state.playerX + dx, state.playerY + dy) == stationTile) return true
            }
        }
        return false
    }
}
