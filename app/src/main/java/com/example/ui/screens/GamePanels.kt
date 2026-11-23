package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.game.model.ActivePanel
import com.example.game.model.CorpseEntity
import com.example.game.model.DeathReport
import com.example.game.model.DisplaySettings
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.GraphicsQuality
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemStack
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.SkillProgress
import com.example.game.model.Vocation
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.sprites.EntitySpriteAtlas
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import java.util.Locale

@Composable
fun SlideUpGamePanel(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState> = emptyList(),
    displaySettings: DisplaySettings = DisplaySettings(),
    onClose: () -> Unit,
    onUseItem: (String) -> Unit,
    onDropItem: (String) -> Unit,
    onUnequipSlot: (EquipmentSlot) -> Unit,
    onLootAll: (String) -> Unit,
    onLootCorpseItem: (String, Int) -> Unit = { _, _ -> },
    onLootCorpseGold: (String) -> Unit = {},
    onDepositItem: (String) -> Unit,
    onWithdrawItem: (String) -> Unit,
    onBuyItem: (String) -> Unit,
    onSellItem: (String) -> Unit,
    onCraftRecipe: (String) -> Unit,
    onBindRune: (String) -> Unit = {},
    onWalkToTile: (Int, Int) -> Unit = { _, _ -> },
    onSelectGraphicsQuality: (GraphicsQuality) -> Unit = {},
    onToggleMinimapSetting: (Boolean) -> Unit = {},
    onToggleFpsCounter: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val title = when (state.activePanel) {
        ActivePanel.EQUIPMENT -> stringResource(R.string.panel_equipment)
        ActivePanel.BACKPACK -> stringResource(R.string.panel_backpack)
        ActivePanel.SKILLS -> stringResource(R.string.panel_skills)
        ActivePanel.DEPOT -> stringResource(R.string.panel_depot)
        ActivePanel.SHOP -> stringResource(R.string.panel_shop)
        ActivePanel.FURNACE -> "Smelting Furnace"
        ActivePanel.ANVIL -> "Blacksmith Anvil"
        ActivePanel.FLETCHING_BENCH -> "Fletching Workbench"
        ActivePanel.RUNECRAFTING_TABLE -> stringResource(R.string.panel_runecrafting)
        ActivePanel.LOOT_CORPSE -> state.corpses.firstOrNull { it.id == state.inspectingCorpseId }?.label ?: "Corpse"
        ActivePanel.ORACLE -> "The Oracle"
        ActivePanel.WORLD_MAP -> "Regional World Map"
        ActivePanel.SETTINGS -> stringResource(R.string.panel_settings)
        ActivePanel.NONE -> ""
    }

    Surface(
        color = BasaltDark.copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, StoneBorderLight),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp, max = 318.dp)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps on panel surface so background map doesn't walk */ } }
            .testTag("slide_up_panel_${state.activePanel.name.lowercase()}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoneElevated)
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TorchGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                if (state.activePanel == ActivePanel.SETTINGS) {
                    Text(
                        text = "Tier: ${displaySettings.graphicsQuality.displayName}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TorchAmber
                    )
                } else {
                    Text(
                        text = "Cap ${formatOz(state.remainingCapOz())}/${formatOz(state.maxCapOz)} oz",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.remainingCapOz() < 20.0) HealthCrimson else ParchmentMuted
                    )
                }
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp).testTag("close_panel_button")
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = ParchmentLight)
                }
            }
            HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                when (state.activePanel) {
                    ActivePanel.EQUIPMENT -> EquipmentPanelContent(state, onUnequipSlot)
                    ActivePanel.BACKPACK -> BackpackPanelContent(state, onUseItem, onDropItem, onBindRune)
                    ActivePanel.SKILLS -> SkillsPanelContent(state)
                    ActivePanel.DEPOT -> DepotPanelContent(state, onDepositItem, onWithdrawItem)
                    ActivePanel.SHOP -> ShopPanelContent(state, onBuyItem, onSellItem)
                    ActivePanel.FURNACE -> SmeltingFurnaceContent(state, onCraftRecipe)
                    ActivePanel.ANVIL -> BlacksmithAnvilContent(state, onCraftRecipe)
                    ActivePanel.FLETCHING_BENCH -> FletchingBenchContent(state, onCraftRecipe)
                    ActivePanel.RUNECRAFTING_TABLE -> RunecraftingTableContent(state, onCraftRecipe)
                    ActivePanel.LOOT_CORPSE -> LootCorpseContent(
                        state = state,
                        corpse = state.corpses.firstOrNull { it.id == state.inspectingCorpseId },
                        onLootItem = onLootCorpseItem,
                        onLootGold = onLootCorpseGold,
                        onLootAll = onLootAll
                    )
                    ActivePanel.WORLD_MAP -> RegionalWorldMapContent(state, remotePlayers, onWalkToTile)
                    ActivePanel.SETTINGS -> SettingsPanelContent(
                        displaySettings = displaySettings,
                        onSelectGraphicsQuality = onSelectGraphicsQuality,
                        onToggleMinimap = onToggleMinimapSetting,
                        onToggleFpsCounter = onToggleFpsCounter
                    )
                    ActivePanel.ORACLE, ActivePanel.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun EquipmentPanelContent(
    state: GameWorldState,
    onUnequipSlot: (EquipmentSlot) -> Unit
) {
    val eq = state.equipment
    val weapon = eq.weaponDef()
    val weaponAtk = weapon?.attack ?: 6
    val speedTxt = weapon?.speedLabel() ?: "1.0s"
    val isTwoHanded = state.isUsingTwoHandedWeapon()
    val dodgePct = (state.shieldDodgeChance(isRanged = false) * 100).toInt()
    val offVocPenalty = state.weaponOffVocationMultiplier(weapon) < 1.0

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoneSurface, RoundedCornerShape(6.dp))
                .border(1.dp, StoneBorderLight, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ATK $weaponAtk", style = MaterialTheme.typography.labelMedium, color = TorchGold)
            Text(speedTxt, style = MaterialTheme.typography.labelSmall, color = ParchmentLight)
            Text(
                text = if (isTwoHanded) "DODGE $dodgePct% (2H)" else "DODGE $dodgePct%",
                style = MaterialTheme.typography.labelMedium,
                color = if (isTwoHanded) TorchAmber else ManaSapphire
            )
            Text("ARM ${eq.totalArmor()}", style = MaterialTheme.typography.labelMedium, color = ExpEmerald)
        }

        // Classic 3-column Tibia Paper Doll layout + Ammo Quiver Slot
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipSlotBox(EquipmentSlot.AMULET, eq.amuletId, null, null, onUnequipSlot, Modifier.weight(1f))
            EquipSlotBox(EquipmentSlot.HELMET, eq.helmetId, null, null, onUnequipSlot, Modifier.weight(1f))
            EquipSlotBox(EquipmentSlot.AMMO, eq.ammoId, state.activeAmmoCount(), null, onUnequipSlot, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipSlotBox(EquipmentSlot.WEAPON, eq.weaponId, null, null, onUnequipSlot, Modifier.weight(1f))
            EquipSlotBox(EquipmentSlot.ARMOR, eq.armorId, null, null, onUnequipSlot, Modifier.weight(1f))
            EquipSlotBox(
                slot = EquipmentSlot.SHIELD,
                itemId = eq.shieldId,
                ammoCount = null,
                lockedLabel = if (isTwoHanded) "2H Occupied" else null,
                onUnequip = onUnequipSlot,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            EquipSlotBox(EquipmentSlot.LEGS, eq.legsId, null, null, onUnequipSlot, Modifier.weight(1f))
            EquipSlotBox(EquipmentSlot.BOOTS, eq.bootsId, null, null, onUnequipSlot, Modifier.weight(1f))
        }
        val statusHint = buildString {
            if (isTwoHanded) append("2H weapon active: Shield locked & Shielding Dodge penalized. ")
            if (offVocPenalty && weapon?.preferredVocation != null) {
                append("Off-vocation Tier 3-4 weapon (-25% DMG; ${weapon.preferredVocation.displayName} mastery). ")
            }
            if (isEmpty()) {
                append("Tap any equipped piece to unequip. Load Shafts or Quarrels from Backpack into Ammo.")
            }
        }.trim()
        Text(
            text = statusHint,
            style = MaterialTheme.typography.labelSmall,
            color = if (isTwoHanded || offVocPenalty) TorchAmber else ParchmentMuted
        )
    }
}

@Composable
private fun EquipSlotBox(
    slot: EquipmentSlot,
    itemId: String?,
    ammoCount: Int?,
    lockedLabel: String?,
    onUnequip: (EquipmentSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val def = itemId?.let { ItemCatalog.get(it) }
    val isLocked = lockedLabel != null && def == null
    Surface(
        color = if (isLocked) ObsidianVoid.copy(alpha = 0.72f) else StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            when {
                def != null -> TorchGold.copy(alpha = 0.65f)
                isLocked -> TorchAmber.copy(alpha = 0.55f)
                else -> StoneBorderLight
            }
        ),
        modifier = modifier
            .height(58.dp)
            .clickable(enabled = def != null) { onUnequip(slot) }
            .testTag("equip_slot_${slot.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (def != null) {
                val icon = remember(def.id) { EntitySpriteAtlas.itemIcon(def.id) }
                PixelSpritePreview(icon, Modifier.size(28.dp).background(ObsidianVoid, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(5.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (slot == EquipmentSlot.AMMO && ammoCount != null && def != null) "Ammo x$ammoCount" else slot.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        slot == EquipmentSlot.AMMO && (ammoCount ?: 0) > 0 -> TorchGold
                        isLocked -> TorchAmber
                        else -> ParchmentMuted
                    }
                )
                Text(
                    text = def?.name ?: lockedLabel ?: "Empty",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        def != null -> ParchmentLight
                        isLocked -> TorchAmber.copy(alpha = 0.85f)
                        else -> ParchmentMuted.copy(alpha = 0.55f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BackpackPanelContent(
    state: GameWorldState,
    onUseItem: (String) -> Unit,
    onDropItem: (String) -> Unit,
    onBindRune: (String) -> Unit
) {
    var bagTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(StoneSurface).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Gold: ${state.gold} gp (${formatOz(state.gold * 0.1)} oz)", style = MaterialTheme.typography.labelMedium, color = TorchGold)
            Text(
                text = "Used: ${formatOz(state.usedCapOz())} / ${formatOz(state.maxCapOz)} oz",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.remainingCapOz() < 20.0) HealthCrimson else ParchmentLight
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelTabChip(
                text = "Main Bag (${state.backpack.size})",
                selected = bagTab == 0,
                onClick = { bagTab = 0 },
                modifier = Modifier.weight(1f).testTag("bag_tab_main")
            )
            PanelTabChip(
                text = "Tool Bag (${state.toolBag.size})",
                selected = bagTab == 1,
                onClick = { bagTab = 1 },
                modifier = Modifier.weight(1f).testTag("bag_tab_tools")
            )
        }

        if (bagTab == 0) {
            val mainBagEntries = remember(state.backpack) { state.backpack.withStableKeys("bag_main") }
            if (mainBagEntries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Your main backpack is empty.", style = MaterialTheme.typography.bodyLarge, color = ParchmentMuted)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(mainBagEntries, key = { it.key }) { entry ->
                        val stack = entry.stack
                        val def = ItemCatalog.get(stack.itemId)
                        val isCastableRune = def.category == ItemCategory.RUNE_ATTACK || def.category == ItemCategory.RUNE_HEAL
                        val isBound = (def.category == ItemCategory.RUNE_ATTACK && state.boundAttackRuneId == def.id) ||
                            (def.category == ItemCategory.RUNE_HEAL && state.boundHealRuneId == def.id)
                        val actionLabel = when {
                            isCastableRune -> "Cast"
                            def.category == ItemCategory.AMMO -> "Load"
                            def.equipSlot != null -> "Equip"
                            else -> "Use"
                        }
                        val sub = buildString {
                            append("${formatOz(def.weightOz * stack.count)} oz")
                            if (isCastableRune) {
                                append(" • MLv ${def.requiredMagicLevel}")
                                if (isBound) append(" • BOUND")
                            }
                            if (def.attack > 0 && def.equipSlot == EquipmentSlot.WEAPON) {
                                append(" • ATK ${def.attack}")
                                if (def.isTwoHanded) append(" • 2H")
                                append(" • ${def.speedLabel()}")
                            }
                            if (def.requiredSkillType != null) {
                                append(" • Req ${def.requiredSkillLevel}")
                            }
                            if (def.preferredVocation != null && state.vocation != def.preferredVocation) {
                                append(" • -25% (${def.preferredVocation.displayName})")
                            }
                            append(" • ${def.description}")
                        }
                        ItemActionRow(
                            stack = stack,
                            subtitleOverride = sub,
                            primaryLabel = actionLabel,
                            onPrimary = { onUseItem(stack.itemId) },
                            secondaryLabel = if (isCastableRune) (if (isBound) "Bound" else "Bind") else "Drop",
                            onSecondary = if (isCastableRune) ({ onBindRune(stack.itemId) }) else ({ onDropItem(stack.itemId) })
                        )
                    }
                }
            }
        } else {
            val toolBagEntries = remember(state.toolBag) { state.toolBag.withStableKeys("bag_tool") }
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, ExpEmerald.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Strapped Tool Bag • 50% weight (${formatOz(state.toolBagWeightOz())} oz) • Kept safe on death • Auto-used for Rope, Mining, Chopping & Fishing",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            if (toolBagEntries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No tools in your Tool Bag. Buy a Rope or forge a Pickaxe.", style = MaterialTheme.typography.bodyMedium, color = ParchmentMuted)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(toolBagEntries, key = { it.key }) { entry ->
                        val stack = entry.stack
                        val def = ItemCatalog.get(stack.itemId)
                        val sub = buildString {
                            append("${formatOz(def.carriedWeightOz() * stack.count)} oz (50% off)")
                            if (def.pickaxeTier > 0) append(" • Tier ${def.pickaxeTier} Pickaxe")
                            append(" • ${def.description}")
                        }
                        ItemActionRow(
                            stack = stack,
                            subtitleOverride = sub,
                            primaryLabel = if (def.id == "rope") "Climb" else "Use",
                            onPrimary = { onUseItem(stack.itemId) },
                            secondaryLabel = "Drop",
                            onSecondary = { onDropItem(stack.itemId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsPanelContent(state: GameWorldState) {
    val meleeMult = String.format(Locale.US, "x%.2f DMG", state.attackSkillMultiplier(state.skills.melee.level))
    val distMult = String.format(Locale.US, "x%.2f DMG", state.attackSkillMultiplier(state.skills.distance.level))
    val magicMult = String.format(Locale.US, "x%.2f MAG", state.magicSkillMultiplier())
    val dodgePct = (state.shieldDodgeChance(isRanged = false) * 100).toInt()
    val twoHanded = state.isUsingTwoHandedWeapon()
    val dodgeBadge = if (twoHanded) "$dodgePct% DODGE (2H)" else "$dodgePct% DODGE"

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Character Level ${state.level}", style = MaterialTheme.typography.titleMedium, color = TorchGold)
                        Text(state.vocation.displayName, style = MaterialTheme.typography.labelLarge, color = ParchmentLight)
                    }
                    Text("XP: ${state.xp} / ${state.nextLevelXp()}", style = MaterialTheme.typography.labelMedium, color = ParchmentMuted)
                    LinearProgressIndicator(
                        progress = { state.levelProgressFraction() },
                        color = ExpEmerald,
                        trackColor = ObsidianVoid,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    if (state.vocation == Vocation.ADVENTURER) {
                        Text(
                            text = if (state.level >= ORACLE_MIN_LEVEL) {
                                "Vocation Ceremony ready! Speak to The Oracle in Rookvale Temple."
                            } else {
                                "The Oracle in Rookvale Temple awaits at Level $ORACLE_MIN_LEVEL (${state.level}/$ORACLE_MIN_LEVEL)."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.level >= ORACLE_MIN_LEVEL) TorchGold else ParchmentMuted
                        )
                    }
                }
            }
        }
        item {
            SkillBarRow(
                name = "Magic Level",
                progress = state.skills.magic,
                barColor = Color(0xFFB37BFF),
                hint = "Magic XP: ${state.magicXp.toInt()} / ${state.magicXpToNext().toInt()} (spend MP casting & runecrafting) • Runes: Lv 1 Dart • Lv 4 Heal • Lv 7 HMM • Lv 12 GFB • Lv 15 SD",
                badgeText = magicMult,
                badgeColor = Color(0xFFB37BFF),
                progressFractionOverride = state.skills.magic.magicProgressFraction()
            )
        }
        item { SkillBarRow("Fist Fighting", state.skills.fist, Color(0xFFE8A468), "Unarmed combat mastery (+6% DMG per level above 10)", String.format(Locale.US, "x%.2f DMG", state.attackSkillMultiplier(state.skills.fist.level)), Color(0xFFE8A468)) }
        item { SkillBarRow("Club Fighting", state.skills.club, Color(0xFFD68C45), "Clubs & cudgels mastery (+6% DMG per level above 10)", String.format(Locale.US, "x%.2f DMG", state.attackSkillMultiplier(state.skills.club.level)), Color(0xFFD68C45)) }
        item { SkillBarRow("Sword Fighting", state.skills.sword, Color(0xFFE05A47), "Blades, dirks, longswords & claymores (+6% DMG per level above 10)", String.format(Locale.US, "x%.2f DMG", state.attackSkillMultiplier(state.skills.sword.level)), Color(0xFFE05A47)) }
        item { SkillBarRow("Axe Fighting", state.skills.axe, Color(0xFFEF7B45), "Axes, halberds, glaives & hatchets (+6% DMG per level above 10)", String.format(Locale.US, "x%.2f DMG", state.attackSkillMultiplier(state.skills.axe.level)), Color(0xFFEF7B45)) }
        item { SkillBarRow("Distance Fighting", state.skills.distance, Color(0xFF47B86E), "Multiplies bow, arbalest & javelin damage (+6% per level above 10)", distMult, Color(0xFF47B86E)) }
        item {
            SkillBarRow(
                name = "Shielding",
                progress = state.skills.shielding,
                barColor = Color(0xFF5CA8FF),
                hint = if (twoHanded) "Active Dodge stat • Penalized while wielding a 2H weapon (equip 1H + Shield for full Dodge)"
                else "Active Dodge stat • Full power with a Shield equipped; halved & penalized with 2H weapons",
                badgeText = dodgeBadge,
                badgeColor = if (twoHanded) TorchAmber else Color(0xFF5CA8FF)
            )
        }
        item { SkillBarRow("Fishing", state.skills.fishing, Color(0xFF4AD2D9), "Advances per line cast at the Fishing Pier") }
        item { SkillBarRow("Mining", state.skills.mining, TorchAmber, "Advances per Ore Vein strike; boosts ore yield") }
    }
}

@Composable
private fun SkillBarRow(
    name: String,
    progress: SkillProgress,
    barColor: Color,
    hint: String,
    badgeText: String? = null,
    badgeColor: Color = TorchGold,
    progressFractionOverride: Float? = null
) {
    val frac = progressFractionOverride ?: progress.progressFraction()
    val pct = (frac * 100).toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StoneSurface, RoundedCornerShape(6.dp))
            .border(1.dp, StoneBorderLight.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = ParchmentLight)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                            .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text("Lvl ${progress.level} ($pct%)", style = MaterialTheme.typography.labelLarge, color = TorchGold)
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { frac },
            color = barColor,
            trackColor = ObsidianVoid,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(2.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = ParchmentMuted)
    }
}

@Composable
private fun DepotPanelContent(
    state: GameWorldState,
    onDeposit: (String) -> Unit,
    onWithdraw: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val depotEntries = remember(state.depot) { state.depot.withStableKeys("depot_in") }
    val carriedEntries = remember(state.toolBag, state.backpack) {
        state.allCarriedItems().withStableKeys("depot_pack")
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelTabChip(
                text = "In Depot (${depotEntries.size})",
                selected = tab == 0,
                onClick = { tab = 0 },
                modifier = Modifier.weight(1f).testTag("depot_tab_in")
            )
            PanelTabChip(
                text = "Deposit Pack (${carriedEntries.size})",
                selected = tab == 1,
                onClick = { tab = 1 },
                modifier = Modifier.weight(1f).testTag("depot_tab_pack")
            )
        }
        val activeEntries = if (tab == 0) depotEntries else carriedEntries
        if (activeEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (tab == 0) "Your Depot Chest is empty." else "No carried items to deposit.", color = ParchmentMuted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(activeEntries, key = { it.key }) { entry ->
                    val stack = entry.stack
                    val def = ItemCatalog.get(stack.itemId)
                    val sub = if (def.isTool) {
                        "Tool Bag • ${formatOz(def.carriedWeightOz() * stack.count)} oz • ${def.description}"
                    } else {
                        "${formatOz(def.weightOz * stack.count)} oz • ${def.description}"
                    }
                    ItemActionRow(
                        stack = stack,
                        subtitleOverride = sub,
                        primaryLabel = if (tab == 0) "Take" else "Store",
                        onPrimary = { if (tab == 0) onWithdraw(stack.itemId) else onDeposit(stack.itemId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopPanelContent(
    state: GameWorldState,
    onBuy: (String) -> Unit,
    onSell: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelTabChip("Buy Supplies", tab == 0, { tab = 0 }, Modifier.weight(1f))
            PanelTabChip("Sell Loot", tab == 1, { tab = 1 }, Modifier.weight(1f))
            Text("${state.gold} gp", style = MaterialTheme.typography.labelLarge, color = TorchGold)
        }
        if (tab == 0) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ItemCatalog.shopInventoryIds, key = { it }) { itemId ->
                    val def = ItemCatalog.get(itemId)
                    val wt = if (def.isTool) "${formatOz(def.carriedWeightOz())} oz (Tool Bag)" else "${formatOz(def.weightOz)} oz"
                    ItemActionRow(
                        stack = ItemStack(itemId, 1),
                        subtitleOverride = "${def.buyPrice} gp • $wt • ${def.description}",
                        primaryLabel = "Buy ${def.buyPrice}g",
                        onPrimary = { onBuy(itemId) }
                    )
                }
            }
        } else {
            val sellableEntries = remember(state.toolBag, state.backpack) {
                state.allCarriedItems()
                    .filter { ItemCatalog.get(it.itemId).sellPrice > 0 }
                    .withStableKeys("shop_sell")
            }
            if (sellableEntries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No sellable loot in your packs.", color = ParchmentMuted)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sellableEntries, key = { it.key }) { entry ->
                        val stack = entry.stack
                        val def = ItemCatalog.get(stack.itemId)
                        val bagTag = if (def.isTool) "Tool Bag • " else ""
                        ItemActionRow(
                            stack = stack,
                            subtitleOverride = "${bagTag}Sells for ${def.sellPrice} gp • ${formatOz(def.carriedWeightOz())} oz",
                            primaryLabel = "Sell +${def.sellPrice}g",
                            onPrimary = { onSell(stack.itemId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LootCorpseContent(
    state: GameWorldState,
    corpse: CorpseEntity?,
    onLootItem: (String, Int) -> Unit,
    onLootGold: (String) -> Unit,
    onLootAll: (String) -> Unit
) {
    if (corpse == null || (corpse.gold <= 0 && corpse.items.isEmpty())) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Corpse has already been looted.", color = ParchmentMuted)
        }
        return
    }
    val remainingCap = state.remainingCapOz()
    val totalStacks = corpse.items.size + if (corpse.gold > 0) 1 else 0

    Column(modifier = Modifier.fillMaxWidth()) {
        // Scrollable list of tappable loot items (plus tappable Gold Coins row)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (corpse.gold > 0) {
                item(key = "corpse_gold_${corpse.id}") {
                    val goldWeight = corpse.gold * 0.1
                    val canTakeAnyGold = remainingCap + 1e-6 >= 0.1
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            1.dp,
                            if (canTakeAnyGold) TorchGold.copy(alpha = 0.75f) else HealthCrimson.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onLootGold(corpse.id) }
                            .testTag("corpse_gold_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(ObsidianVoid, RoundedCornerShape(4.dp))
                                    .border(1.dp, TorchGold.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = "Gold Coins",
                                    tint = TorchGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gold Coins x${corpse.gold}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TorchGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatOz(goldWeight)} oz • Tap to loot",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canTakeAnyGold) ParchmentMuted else HealthCrimson
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (canTakeAnyGold) TorchGold else StoneBorderDark,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (canTakeAnyGold) "Take" else "Heavy",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (canTakeAnyGold) ObsidianVoid else ParchmentMuted
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = corpse.items,
                key = { idx, stack -> "corpse_${corpse.id}_${idx}_${stack.itemId}_${stack.count}" }
            ) { index, stack ->
                val def = ItemCatalog.get(stack.itemId)
                val icon = remember(stack.itemId) { EntitySpriteAtlas.itemIcon(stack.itemId) }
                val unitWeight = def.carriedWeightOz()
                val stackWeight = unitWeight * stack.count
                val canTakeOne = remainingCap + 1e-6 >= unitWeight
                val canTakeAll = remainingCap + 1e-6 >= stackWeight

                Surface(
                    color = StoneSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            canTakeAll -> StoneBorderLight.copy(alpha = 0.85f)
                            canTakeOne -> TorchAmber.copy(alpha = 0.7f)
                            else -> HealthCrimson.copy(alpha = 0.55f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clickable { onLootItem(corpse.id, index) }
                        .testTag("corpse_item_row_$index")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelSpritePreview(
                            bitmap = icon,
                            modifier = Modifier
                                .size(34.dp)
                                .background(ObsidianVoid, RoundedCornerShape(4.dp))
                                .border(1.dp, StoneBorderDark, RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (stack.count > 1) "${def.name} x${stack.count}" else def.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (canTakeOne) ParchmentLight else ParchmentMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("${formatOz(stackWeight)} oz")
                                    if (def.isTool) append(" (Tool Bag)")
                                    append(
                                        when {
                                            canTakeAll -> " • Tap to take"
                                            canTakeOne -> " • Partial fit"
                                            else -> " • Too heavy"
                                        }
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    canTakeAll -> ParchmentMuted
                                    canTakeOne -> TorchAmber
                                    else -> HealthCrimson
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (canTakeOne) TorchAmber else StoneBorderDark,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (canTakeOne) "Take" else "Heavy",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (canTakeOne) ObsidianVoid else ParchmentMuted
                            )
                        }
                    }
                }
            }
        }

        // Sticky Footer Bar — always visible even when Fallen Backpack holds 15+ items
        HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoneElevated)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onLootAll(corpse.id) },
                colors = ButtonDefaults.buttonColors(containerColor = TorchGold, contentColor = ObsidianVoid),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("loot_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = ObsidianVoid,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (corpse.isPlayerCorpse) "LOOT ALL ($totalStacks) — RECOVER BACKPACK" else "LOOT ALL ($totalStacks)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PanelTabChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (selected) TorchGold else StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) TorchGold else StoneBorderLight),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) ObsidianVoid else ParchmentLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ItemActionRow(
    stack: ItemStack,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    subtitleOverride: String? = null
) {
    val def = ItemCatalog.get(stack.itemId)
    val icon = remember(stack.itemId) { EntitySpriteAtlas.itemIcon(stack.itemId) }
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelSpritePreview(icon, Modifier.size(34.dp).background(ObsidianVoid, RoundedCornerShape(4.dp)).border(1.dp, StoneBorderDark, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (stack.count > 1) "${def.name} x${stack.count}" else def.name, style = MaterialTheme.typography.titleMedium, color = ParchmentLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitleOverride ?: "${formatOz(def.weightOz * stack.count)} oz • ${def.description}", style = MaterialTheme.typography.labelSmall, color = ParchmentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onPrimary,
                colors = ButtonDefaults.buttonColors(containerColor = TorchAmber, contentColor = ObsidianVoid),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(primaryLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onSecondary,
                    border = BorderStroke(1.dp, StoneBorderLight),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(secondaryLabel, style = MaterialTheme.typography.labelSmall, color = ParchmentMuted)
                }
            }
        }
    }
}

@Composable
fun DeathPenaltyDialog(report: DeathReport, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = BasaltDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, HealthCrimson),
            modifier = Modifier.fillMaxWidth().testTag("death_penalty_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.WarningAmber, null, tint = HealthCrimson, modifier = Modifier.size(40.dp))
                Text("YOU ARE DEAD", style = MaterialTheme.typography.headlineMedium, color = HealthCrimson)
                Text("You awakened inside the Rookvale Temple.", style = MaterialTheme.typography.bodyLarge, color = ParchmentLight)
                Surface(color = StoneSurface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, StoneBorderLight), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• Penalty: -10% XP (-${report.xpLost} XP) & -10% skill progress", style = MaterialTheme.typography.labelMedium, color = HealthCrimson)
                        Text("• Dropped Main Bag (${report.droppedItemsCount} items) left at Floor ${report.floorDropped} (${report.corpseX}, ${report.corpseY})", style = MaterialTheme.typography.labelMedium, color = TorchGold)
                        Text("• Your strapped Tool Bag (Rope, Pickaxes, Hatchet, Rod) remained safe with you so you can retrieve your corpse.", style = MaterialTheme.typography.labelSmall, color = ParchmentMuted)
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TorchGold, contentColor = ObsidianVoid),
                    modifier = Modifier.fillMaxWidth().testTag("death_respawn_continue_button")
                ) {
                    Text("RISE AT TEMPLE", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private data class KeyedItemStack(
    val key: String,
    val stack: ItemStack
)

/**
 * Produces an immutable list snapshot with deterministic, guaranteed-unique LazyColumn keys
 * even when duplicate non-stackable items exist or when the last item of a stack is removed.
 */
private fun List<ItemStack>.withStableKeys(prefix: String): List<KeyedItemStack> {
    val snapshot = this.toList()
    val seenPerId = HashMap<String, Int>(snapshot.size)
    val result = ArrayList<KeyedItemStack>(snapshot.size)
    for (stack in snapshot) {
        if (stack.itemId.isBlank() || stack.count <= 0) continue
        val occurrence = seenPerId[stack.itemId] ?: 0
        seenPerId[stack.itemId] = occurrence + 1
        val defName = ItemCatalog.get(stack.itemId).name
        result.add(KeyedItemStack(key = "${prefix}_${stack.itemId}_${defName}_$occurrence", stack = stack))
    }
    return result.toList()
}

fun formatOz(value: Double): String = String.format(Locale.US, "%.1f", value)
