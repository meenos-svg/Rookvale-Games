package com.example.game.engine

import com.example.game.model.ActivePanel
import com.example.game.model.CorpseEntity
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import kotlin.math.abs
import kotlin.math.max

/** How much the game picks up for you when you kill something nearby. */
enum class AutoLootMode(val label: String) {
    OFF("Off"),
    GOLD("Gold only"),
    SMART("Gold + picked items")
}

/**
 * Quality-of-life looting: open corpses from anywhere, loot everything nearby in one tap, auto-loot
 * on kill, and step through several corpses lying on the same tile.
 *
 * The auto-loot mode and the remembered item list live in `npcProgress`, so they're saved locally
 * and in the cloud with the rest of the character.
 */
object LootCollection {
    const val NEARBY_RADIUS = 2
    private const val MODE_KEY = "autoloot_mode"
    private const val ITEM_PREFIX = "autoloot:"

    private fun chebyshev(ax: Int, ay: Int, bx: Int, by: Int) = max(abs(ax - bx), abs(ay - by))

    private fun hasLoot(c: CorpseEntity) = c.gold > 0 || c.items.isNotEmpty()

    // ------------------------------------------------------------------ settings

    fun mode(state: GameWorldState): AutoLootMode =
        AutoLootMode.entries.getOrElse(state.npcProgress[MODE_KEY] ?: 0) { AutoLootMode.OFF }

    fun setMode(state: GameWorldState, mode: AutoLootMode): GameWorldState =
        state.copy(npcProgress = state.npcProgress + (MODE_KEY to mode.ordinal))
            .addLogInternal("Auto-loot: ${mode.label}.")

    fun rememberedItems(state: GameWorldState): Set<String> =
        state.npcProgress.filter { it.key.startsWith(ITEM_PREFIX) && it.value > 0 }.keys.map { it.removePrefix(ITEM_PREFIX) }.toSet()

    /** Items you pick by hand are remembered for the "Gold + picked items" auto-loot mode. */
    fun remember(state: GameWorldState, itemId: String): GameWorldState {
        val key = ITEM_PREFIX + itemId
        return if (state.npcProgress[key] == 1) state else state.copy(npcProgress = state.npcProgress + (key to 1))
    }

    fun forgetAll(state: GameWorldState): GameWorldState =
        state.copy(npcProgress = state.npcProgress.filterKeys { !it.startsWith(ITEM_PREFIX) })
            .addLogInternal("Auto-loot forgot every remembered item.")

    // ------------------------------------------------------------------ opening corpses

    /** The corpse a tap on (x, y) should open: one with loot you're allowed to take, newest first. */
    fun corpseToOpenAt(state: GameWorldState, x: Int, y: Int, nowMs: Long): CorpseEntity? {
        val here = state.corpses.filter { it.floor == state.floor && it.x == x && it.y == y }
        if (here.isEmpty()) return null
        return here.lastOrNull { hasLoot(it) && !it.isOwnerLockedFor(state.saveId, nowMs) }
            ?: here.lastOrNull { hasLoot(it) }
            ?: here.last()
    }

    /** Other corpses with loot on the same tile as [corpse]. */
    fun othersOnTile(state: GameWorldState, corpse: CorpseEntity): List<CorpseEntity> =
        state.corpses.filter { it.id != corpse.id && it.floor == corpse.floor && it.x == corpse.x && it.y == corpse.y && hasLoot(it) }

    /** After a corpse is emptied, keep the loot window open on the next corpse lying on the same tile. */
    fun openNextCorpseOnTile(state: GameWorldState, emptied: CorpseEntity, nowMs: Long): GameWorldState {
        val next = othersOnTile(state, emptied).lastOrNull { !it.isOwnerLockedFor(state.saveId, nowMs) } ?: return state
        return state.copy(activePanel = ActivePanel.LOOT_CORPSE, inspectingCorpseId = next.id)
    }

    /** Called when a walk ends while heading for a corpse: open it if we're next to it. */
    fun onArrival(state: GameWorldState, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == state.pendingLootCorpseId }
        val cleared = state.copy(pendingLootCorpseId = null)
        if (corpse == null || corpse.floor != state.floor) return cleared
        if (chebyshev(state.playerX, state.playerY, corpse.x, corpse.y) > 1) return cleared
        return cleared.copy(activePanel = ActivePanel.LOOT_CORPSE, inspectingCorpseId = corpse.id)
    }

    // ------------------------------------------------------------------ loot nearby

    fun lootableNearby(state: GameWorldState, nowMs: Long, radius: Int = NEARBY_RADIUS): List<CorpseEntity> =
        state.corpses.filter {
            it.floor == state.floor && hasLoot(it) && !it.isOwnerLockedFor(state.saveId, nowMs) &&
                chebyshev(state.playerX, state.playerY, it.x, it.y) <= radius
        }

    /** Takes everything carrying capacity allows from every corpse within [NEARBY_RADIUS] tiles. */
    fun lootNearby(state: GameWorldState, nowMs: Long): GameWorldState {
        val targets = lootableNearby(state, nowMs)
        if (targets.isEmpty()) return state.addLogInternal("Nothing to loot nearby.")
        var s = state
        var gold = 0
        var units = 0
        var emptied = 0
        for (c in targets) {
            val (next, g, u) = GameEngine.takeLootFromCorpse(s, c.id, nowMs)
            s = next; gold += g; units += u
            if (s.corpses.none { it.id == c.id }) emptied++
        }
        val inspectedGone = s.inspectingCorpseId != null && s.corpses.none { it.id == s.inspectingCorpseId }
        if (inspectedGone) s = s.copy(inspectingCorpseId = null, activePanel = if (s.activePanel == ActivePanel.LOOT_CORPSE) ActivePanel.NONE else s.activePanel)
        if (gold > 0) s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat() - 0.3f, "+$gold gp", FloatingTextKind.XP, nowMs)
        val leftSome = emptied < targets.size
        val msg = when {
            gold == 0 && units == 0 -> "Too heavy to loot! Smelt ores or store items in your Depot."
            leftSome -> "Looted ${targets.size} corpse(s): +$gold gp, $units item(s). Ran out of Cap for the rest!"
            else -> "Looted ${targets.size} corpse(s): +$gold gp, $units item(s)."
        }
        return s.addLogInternal(msg)
    }

    // ------------------------------------------------------------------ auto-loot

    /**
     * Right after a kill: if auto-loot is on and the corpse is within [NEARBY_RADIUS] tiles, take its
     * gold (and remembered items in SMART mode). The outgoing multiplayer copy of the corpse is
     * updated too, so other players never see loot that's already gone.
     */
    fun autoLootOnKill(state: GameWorldState, corpseId: String, nowMs: Long): GameWorldState {
        val mode = mode(state)
        if (mode == AutoLootMode.OFF) return state
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        if (chebyshev(state.playerX, state.playerY, corpse.x, corpse.y) > NEARBY_RADIUS) return state
        val wanted = if (mode == AutoLootMode.SMART) rememberedItems(state) else emptySet()
        val (s0, gold, units) = GameEngine.takeLootFromCorpse(state, corpseId, nowMs, takeGold = true) { it in wanted }
        if (gold == 0 && units == 0) return state
        val after = s0.corpses.firstOrNull { it.id == corpseId }
        var s = s0.copy(
            outboundLootSpawns = if (after == null) s0.outboundLootSpawns.filterNot { it.id == corpseId }
            else s0.outboundLootSpawns.map { if (it.id == corpseId) after else it }
        )
        val label = buildList {
            if (gold > 0) add("+$gold gp")
            if (units > 0) add("$units item(s)")
        }.joinToString(", ")
        s = s.addFloatingInternal(corpse.floor, corpse.x.toFloat(), corpse.y.toFloat() - 0.3f, label, FloatingTextKind.XP, nowMs)
        return s
    }

    /** Commands from the settings drawer: `autoloot_mode:<OFF|GOLD|SMART>`, `autoloot_forget`, `loot_nearby`. */
    fun handleAction(state: GameWorldState, cmd: String, arg: String, nowMs: Long): GameWorldState? = when (cmd) {
        "autoloot_mode" -> AutoLootMode.entries.firstOrNull { it.name == arg }?.let { setMode(state, it) } ?: state
        "autoloot_forget" -> forgetAll(state)
        "loot_nearby" -> lootNearby(state, nowMs)
        else -> null
    }

    fun rememberedNames(state: GameWorldState): List<String> =
        rememberedItems(state).mapNotNull { ItemCatalog.all[it]?.name }.sorted()
}
