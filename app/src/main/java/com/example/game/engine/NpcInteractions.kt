package com.example.game.engine

import com.example.game.model.ActivePanel
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.world.NpcRole
import com.example.game.world.NpcSpot
import kotlin.math.ceil

/**
 * Game-state rules for Rookvale's townsfolk (see [NpcServices] for the data):
 * opening their shops/panels, hunt contracts, bestiary claims, the town crier's news and the
 * kill hook that feeds all of them.
 */
object NpcInteractions {
    private const val HUNT_ACTIVE = "hunt_active"      // contract index + 1 (0 = none)
    private const val HUNT_KILLS = "hunt_kills"
    private const val HUNTS_DONE = "hunts_done"

    private fun closedMovement(s: GameWorldState) = s.copy(walkPath = emptyList(), longRangeGoal = null, longRangeWaypoints = emptyList())

    private fun vendorIdFor(npc: NpcSpot): String? = when (npc.name) {
        "Odra" -> VendorCatalog.MIRE_OUTFITTER
        "Tahir" -> VendorCatalog.SUN_CARAVANEER
        else -> vendorIdFor(npc.role)
    }

    private fun vendorIdFor(role: NpcRole): String? = when (role) {
        NpcRole.FISHERMAN -> VendorCatalog.FISHERMAN
        NpcRole.BLACKSMITH -> VendorCatalog.BLACKSMITH
        NpcRole.FLETCHER -> VendorCatalog.FLETCHER
        NpcRole.WANDERING_MERCHANT -> VendorCatalog.WANDERER
        else -> null
    }

    /** Handles a tap on one of the new townsfolk. Returns null for roles handled elsewhere. */
    fun talkTo(state: GameWorldState, npc: NpcSpot, wallClockMs: Long = System.currentTimeMillis()): GameWorldState? {
        vendorIdFor(npc)?.let { vid ->
            val v = VendorCatalog.vendor(vid) ?: return null
            var s = closedMovement(state).copy(activePanel = ActivePanel.SHOP, activeShopId = vid).addLogInternal(v.greeting)
            if (vid == VendorCatalog.WANDERER) {
                s = s.addLogInternal("Zephyr: 'I'll pack up and change my wares in about ${VendorCatalog.minutesUntilNextRotation(wallClockMs)} minutes.'")
            }
            if (vid == VendorCatalog.FISHERMAN) s = s.addLogInternal(fishingTip(state))
            return s
        }
        return when (npc.role) {
            NpcRole.HUNT_MASTER -> {
                val board = HuntBoards.byMaster(npc.name)
                val who = board.masterName
                val active = activeContract(state)
                val line = if (active != null) {
                    val kills = huntKills(state)
                    if (kills >= active.count) "$who: 'Well done! Claim your reward for ${active.title}.'"
                    else "$who: '${active.title}: ${kills}/${active.count} ${active.target.displayName}. Keep at it.'"
                } else {
                    "$who: 'Looking for work? Pick a contract from the board.'"
                }
                closedMovement(state).copy(activePanel = ActivePanel.HUNT_BOARD, activeShopId = HuntBoards.panelId(board.id)).addLogInternal(line)
            }
            NpcRole.BESTIARY_SAGE -> {
                val ready = completedUnclaimed(state).size
                val line = if (ready > 0) "Sage Ilsa: 'You have $ready bestiary ${if (ready == 1) "entry" else "entries"} ready to record!'"
                else "Sage Ilsa: 'Slay creatures and I'll record what you learn. Each finished entry makes you deadlier against that beast.'"
                closedMovement(state).copy(activePanel = ActivePanel.BESTIARY, activeShopId = null).addLogInternal(line)
            }
            NpcRole.TOWN_CRIER -> when (npc.name) {
                "Crier Pell" -> mirewatchCrier(closedMovement(state), wallClockMs)
                "Crier Nadim" -> sunspireCrier(closedMovement(state), wallClockMs)
                else -> crierNews(closedMovement(state), wallClockMs)
            }
            NpcRole.DUNGEON_GUIDE -> closedMovement(state).addLogInternal(DungeonGuides.messageFor(npc.name))
            else -> null
        }
    }

    private fun fishingTip(state: GameWorldState): String {
        val lv = state.skills.fishing.level
        val next = com.example.game.model.FishingCatalog.tiers
            .sortedBy { it.requiredFishingLevel }
            .firstOrNull { it.requiredFishingLevel > lv }
        return if (next == null) {
            "Old Finn: 'Fishing $lv! You've mastered every bait I sell.'"
        } else {
            val bait = com.example.game.model.ItemCatalog.get(next.baitId).name
            val fish = com.example.game.model.ItemCatalog.get(next.fishId).name
            "Old Finn: 'Fishing $lv. At level ${next.requiredFishingLevel}, $bait will start landing $fish.'"
        }
    }

    // ---------------------------------------------------------------- Town crier

    fun crierNews(state: GameWorldState, wallClockMs: Long = System.currentTimeMillis()): GameWorldState {
        val featured = TownCrier.featuredCreature(wallClockMs)
        val pct = (TownCrier.FEATURED_XP_BONUS * 100).toInt()
        val wares = VendorCatalog.wanderingStock(wallClockMs).joinToString(", ") { com.example.game.model.ItemCatalog.get(it.itemId).name }
        return state
            .addLogInternal("Crier Tobias: 'Hear ye! Today's hunt: every ${featured.displayName} slain gives +$pct% XP, for the next ${TownCrier.hoursUntilTomorrow(wallClockMs)}h!'")
            .addLogInternal("Crier Tobias: 'Zephyr's caravan in the Market Plaza is selling $wares.'")
            .addLogInternal("Crier Tobias: 'Captain Hale posts bounties by the plaza, and Sage Ilsa rewards monster lore at the Academy!'")
    }

    fun mirewatchCrier(state: GameWorldState, wallClockMs: Long = System.currentTimeMillis()): GameWorldState {
        val featured = TownCrier.featuredMarshCreature(wallClockMs)
        val pct = (TownCrier.FEATURED_XP_BONUS * 100).toInt()
        return state
            .addLogInternal("Crier Pell: 'Hear ye! Marsh hunt of the day: every ${featured.displayName} slain gives +$pct% XP, for the next ${TownCrier.hoursUntilTomorrow(wallClockMs)}h!'")
            .addLogInternal("Crier Pell: 'Warden Brisk posts marsh bounties on the moot platform, and Odra buys bog goods at the wharf.'")
            .addLogInternal("Crier Pell: 'Murkfen Hollow lies just west of town. Mind the wisps, they lead you into deep water.'")
    }

    fun sunspireCrier(state: GameWorldState, wallClockMs: Long = System.currentTimeMillis()): GameWorldState {
        val featured = TownCrier.featuredBadlandsCreature(wallClockMs)
        val pct = (TownCrier.FEATURED_XP_BONUS * 100).toInt()
        return state
            .addLogInternal("Crier Nadim: 'Hear ye! Desert hunt of the day: every ${featured.displayName} slain gives +$pct% XP, for the next ${TownCrier.hoursUntilTomorrow(wallClockMs)}h!'")
            .addLogInternal("Crier Nadim: 'Sergeant Kasim posts desert bounties in the Sun Plaza, and Tahir trades at the caravanserai.'")
            .addLogInternal("Crier Nadim: 'The Tomb, the Labyrinth and the Scorchglass Rift all lie to the south-west. Carry water!'")
    }

    // ---------------------------------------------------------------- Hunt contracts

    fun activeContract(state: GameWorldState): HuntContract? =
        HuntContracts.byIndex((state.npcProgress[HUNT_ACTIVE] ?: 0) - 1)

    fun huntKills(state: GameWorldState): Int = state.npcProgress[HUNT_KILLS] ?: 0
    fun huntsCompleted(state: GameWorldState): Int = state.npcProgress[HUNTS_DONE] ?: 0

    /** The hunt master behind the board the player has open (or the contract's own board). */
    private fun masterFor(state: GameWorldState, contract: HuntContract? = null): String =
        if (state.activeShopId?.startsWith("hunt:") == true) HuntBoards.fromPanelId(state.activeShopId).masterName
        else HuntBoards.byId(contract?.board).masterName

    fun acceptContract(state: GameWorldState, contractId: String): GameWorldState {
        val c = HuntContracts.byId(contractId) ?: return state
        val who = masterFor(state, c)
        if (activeContract(state) != null) return state.addLogInternal("$who: 'Finish or abandon your current contract first.'")
        if (state.level < c.minLevel) return state.addLogInternal("$who: 'Come back at level ${c.minLevel} for that one.'")
        val idx = HuntContracts.indexOf(c.id)
        return state.copy(npcProgress = state.npcProgress + (HUNT_ACTIVE to idx + 1) + (HUNT_KILLS to 0))
            .addLogInternal("Contract accepted: ${c.title}. Slay ${c.count} ${c.target.displayName}. ${c.hint}")
    }

    fun abandonContract(state: GameWorldState): GameWorldState {
        val c = activeContract(state) ?: return state
        return state.copy(npcProgress = state.npcProgress + (HUNT_ACTIVE to 0) + (HUNT_KILLS to 0))
            .addLogInternal("Contract abandoned: ${c.title}.")
    }

    fun turnInContract(state: GameWorldState, nowMs: Long): GameWorldState {
        val c = activeContract(state) ?: return state
        val who = masterFor(state, c)
        if (huntKills(state) < c.count) return state.addLogInternal("$who: 'Not done yet: ${huntKills(state)}/${c.count}.'")
        var s = state.copy(
            gold = state.gold + c.goldReward,
            npcProgress = state.npcProgress + (HUNT_ACTIVE to 0) + (HUNT_KILLS to 0) + (HUNTS_DONE to huntsCompleted(state) + 1)
        )
        s = GameEngine.grantExperience(s, c.xpReward, s.playerX.toFloat(), s.playerY.toFloat(), nowMs)
        return s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "+${c.goldReward} gp", FloatingTextKind.HEAL, nowMs)
            .addLogInternal("$who: 'Contract complete!' (+${c.goldReward} gp, +${c.xpReward} XP)")
    }

    // ---------------------------------------------------------------- Bestiary

    fun kills(state: GameWorldState, type: MonsterType): Int = state.npcProgress[Bestiary.killKey(type)] ?: 0
    fun isClaimed(state: GameWorldState, type: MonsterType): Boolean = (state.npcProgress[Bestiary.claimedKey(type)] ?: 0) > 0

    fun completedUnclaimed(state: GameWorldState): List<MonsterType> =
        MonsterType.entries.filter { !isClaimed(state, it) && kills(state, it) >= Bestiary.killsNeeded(it) }

    /** Creatures you've fought, most-progressed first. */
    fun bestiaryEntries(state: GameWorldState): List<MonsterType> =
        MonsterType.entries.filter { kills(state, it) > 0 }
            .sortedWith(compareByDescending<MonsterType> { kills(state, it) >= Bestiary.killsNeeded(it) && !isClaimed(state, it) }
                .thenByDescending { kills(state, it).toDouble() / Bestiary.killsNeeded(it) })

    fun claimBestiary(state: GameWorldState, typeName: String, nowMs: Long): GameWorldState {
        val type = MonsterType.entries.firstOrNull { it.name == typeName } ?: return state
        if (isClaimed(state, type)) return state
        if (kills(state, type) < Bestiary.killsNeeded(type)) return state.addLogInternal("Sage Ilsa: 'I need more on the ${type.displayName}.'")
        val gold = Bestiary.goldReward(type)
        val pct = (Bestiary.DAMAGE_BONUS * 100).toInt()
        return state.copy(
            gold = state.gold + gold,
            npcProgress = state.npcProgress + (Bestiary.claimedKey(type) to 1)
        ).addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+${gold} gp", FloatingTextKind.HEAL, nowMs)
            .addLogInternal("Sage Ilsa records the ${type.displayName}! You now deal +$pct% damage to it. (+$gold gp)")
    }

    /** Damage after the bestiary bonus for a claimed creature. */
    fun applyBestiaryBonus(state: GameWorldState, type: MonsterType, damage: Int): Int =
        if (damage > 0 && isClaimed(state, type)) ceil(damage * (1.0 + Bestiary.DAMAGE_BONUS)).toInt() else damage

    // ---------------------------------------------------------------- Kill hook

    /**
     * Called whenever the local player is credited with a kill: grants XP (with the town crier's
     * featured-creature bonus) and advances the bestiary and any hunt contract.
     */
    fun onKillCredited(state: GameWorldState, m: MonsterEntity, nowMs: Long, wallClockMs: Long = System.currentTimeMillis()): GameWorldState {
        var s = GameEngine.grantExperience(state, m.scaledXpReward.toLong(), m.x.toFloat(), m.y.toFloat(), nowMs)
        if (m.isSummon) return s

        if (m.type in TownCrier.featuredCreatures(wallClockMs)) {
            val bonus = (m.scaledXpReward * TownCrier.FEATURED_XP_BONUS).toLong()
            if (bonus > 0) {
                s = GameEngine.grantExperience(s, bonus, m.x.toFloat(), m.y.toFloat() - 0.4f, nowMs)
                    .addLogInternal("Today's hunt bonus: +$bonus XP for the ${m.type.displayName}!")
            }
        }

        val key = Bestiary.killKey(m.type)
        val newKills = (s.npcProgress[key] ?: 0) + 1
        var progress = s.npcProgress + (key to newKills)
        if (newKills == Bestiary.killsNeeded(m.type) && !isClaimed(s, m.type)) {
            s = s.addLogInternal("Bestiary: ${m.type.displayName} entry complete! Visit Sage Ilsa at the Academy.")
        }

        val contract = activeContract(s)
        if (contract != null && contract.target == m.type) {
            val k = huntKills(s)
            if (k < contract.count) {
                progress = progress + (HUNT_KILLS to k + 1)
                s = if (k + 1 >= contract.count) {
                    s.addLogInternal("Contract complete: ${contract.title}! Report to any hunt master (${HuntBoards.byId(contract.board).masterName} posted it).")
                } else if ((k + 1) % 5 == 0) {
                    s.addLogInternal("${contract.title}: ${k + 1}/${contract.count}")
                } else s
            }
        }
        return s.copy(npcProgress = progress)
    }

    /** Handles commands from the townsfolk panels, e.g. `hunt_accept:rats`. */
    fun handleAction(state: GameWorldState, action: String, nowMs: Long): GameWorldState {
        val cmd = action.substringBefore(':')
        val arg = action.substringAfter(':', "")
        return when (cmd) {
            "hunt_accept" -> acceptContract(state, arg)
            "hunt_abandon" -> abandonContract(state)
            "hunt_turn_in" -> turnInContract(state, nowMs)
            "bestiary_claim" -> claimBestiary(state, arg, nowMs)
            else -> state
        }
    }
}
