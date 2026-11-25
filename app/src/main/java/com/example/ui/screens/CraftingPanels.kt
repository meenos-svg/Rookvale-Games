package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.model.BiomeEquipmentClass
import com.example.game.model.CraftingRecipe
import com.example.game.model.CraftingStationType
import com.example.game.model.CraftingTab
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.RecipeCatalog
import com.example.game.sprites.EntitySpriteAtlas
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.SoulViolet
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold

private enum class BiomeRecipeFilter(val label: String) {
    ALL("All"),
    PICKAXES("Pickaxes"),
    SMELTING("Smelting"),
    WEAPONS("Weapons"),
    DEFENSE("Shield & Armor")
}

internal fun biomeStationAccent(station: CraftingStationType): androidx.compose.ui.graphics.Color = when (station) {
    CraftingStationType.ROOKVALE_TOWN_FORGE -> TorchAmber
    CraftingStationType.STILT_TOWN_MIRE_BENCH -> androidx.compose.ui.graphics.Color(0xFF5CE88A)
    CraftingStationType.SUNSCORCH_OASIS_SMELTERY -> androidx.compose.ui.graphics.Color(0xFFB48CFF)
    CraftingStationType.FROSTPEAK_GREAT_HEARTH -> androidx.compose.ui.graphics.Color(0xFF68E8FF)
    CraftingStationType.ASHEN_OBSIDIAN_FOUNDRY -> androidx.compose.ui.graphics.Color(0xFFFF6E24)
}

@Composable
fun BiomeCraftingStallContent(
    state: GameWorldState,
    initialStation: CraftingStationType?,
    isCodexBrowseMode: Boolean,
    onCraft: (String) -> Unit,
    onInspectItem: (String) -> Unit = {}
) {
    val nearbyStall = remember(state.floor, state.playerX, state.playerY) {
        CraftingAndMiningEngine.nearbyBiomeStation(state)
    }
    var selectedStation by remember(initialStation, nearbyStall) {
        mutableStateOf(initialStation ?: nearbyStall ?: CraftingStationType.ROOKVALE_TOWN_FORGE)
    }
    var selectedFilter by remember { mutableStateOf(BiomeRecipeFilter.ALL) }
    val atSelectedStall = remember(state.floor, state.playerX, state.playerY, selectedStation) {
        CraftingAndMiningEngine.isNearBiomeStation(state, selectedStation)
    }
    val recipes = remember(selectedStation, selectedFilter) {
        RecipeCatalog.recipesForBiomeStation(selectedStation).filter { recipe ->
            val outDef = ItemCatalog.get(recipe.outputItemId)
            when (selectedFilter) {
                BiomeRecipeFilter.ALL -> true
                BiomeRecipeFilter.PICKAXES -> outDef.category == ItemCategory.TOOL_PICKAXE
                BiomeRecipeFilter.SMELTING -> outDef.category == ItemCategory.METAL_INGOT
                BiomeRecipeFilter.WEAPONS -> outDef.equipSlot == EquipmentSlot.WEAPON || outDef.category == ItemCategory.AMMO
                BiomeRecipeFilter.DEFENSE -> outDef.equipSlot == EquipmentSlot.SHIELD || outDef.equipSlot == EquipmentSlot.ARMOR
            }
        }
    }
    val stationAccent = biomeStationAccent(selectedStation)

    Column(modifier = Modifier.fillMaxWidth()) {
        // 5 Biome Stall selector strip (browse any biome from Recipe Codex or inspect current stall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CraftingStationType.entries.forEach { station ->
                val active = station == selectedStation
                val isNearby = CraftingAndMiningEngine.isNearBiomeStation(state, station)
                val accent = biomeStationAccent(station)
                Surface(
                    color = if (active) accent else StoneSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = if (isNearby) 1.5.dp else 1.dp,
                        color = when {
                            active -> accent
                            isNearby -> ExpEmerald
                            else -> StoneBorderLight
                        }
                    ),
                    modifier = Modifier
                        .clickable { selectedStation = station }
                        .testTag("biome_stall_tab_${station.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = station.shortName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (active) ObsidianVoid else ParchmentLight
                        )
                        if (isNearby) {
                            Text(
                                text = "HERE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold),
                                color = if (active) ObsidianVoid else ExpEmerald
                            )
                        }
                    }
                }
            }
        }

        // Location-lock status banner
        Surface(
            color = if (atSelectedStall) ExpEmerald.copy(alpha = 0.14f) else TorchAmber.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (atSelectedStall) ExpEmerald.copy(alpha = 0.7f) else TorchAmber.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .testTag("biome_stall_location_banner")
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (atSelectedStall) {
                        "✓ AT ${selectedStation.displayName.uppercase()} • READY TO FORGE"
                    } else {
                        "🔒 ${selectedStation.displayName} (${selectedStation.biomeLabel})"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (atSelectedStall) ExpEmerald else TorchAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (atSelectedStall) {
                        "Mining Lv ${state.skills.mining.level} • Forge ${selectedStation.gearClassificationLabel} & next-tier pickaxes."
                    } else if (isCodexBrowseMode) {
                        "Browse recipes anywhere. Stand within 2 tiles of ${selectedStation.displayName} to craft."
                    } else {
                        "Must stand within 2 tiles of ${selectedStation.displayName} to forge ${selectedStation.gearClassificationLabel}."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Category sub-filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BiomeRecipeFilter.entries.forEach { filter ->
                val active = filter == selectedFilter
                Surface(
                    color = if (active) TorchGold else ObsidianVoid,
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, if (active) TorchGold else StoneBorderDark),
                    modifier = Modifier
                        .clickable { selectedFilter = filter }
                        .testTag("biome_filter_${filter.name.lowercase()}")
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (active) ObsidianVoid else ParchmentLight,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (recipes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ${selectedFilter.label.lowercase()} recipes at ${selectedStation.shortName}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ParchmentMuted
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(recipes, key = { it.id }) { recipe ->
                    val outDef = ItemCatalog.get(recipe.outputItemId)
                    val label = when {
                        !atSelectedStall -> "Locked"
                        outDef.category == ItemCategory.METAL_INGOT -> "Smelt"
                        else -> "Forge"
                    }
                    RecipeCraftCard(
                        state = state,
                        recipe = recipe,
                        actionLabel = label,
                        accentColor = stationAccent,
                        allowClickWhenLocked = true,
                        isAtRequiredStall = atSelectedStall,
                        onCraft = { onCraft(recipe.id) },
                        onInspect = { onInspectItem(recipe.outputItemId) }
                    )
                }
            }
        }
    }
}

@Composable
fun SmeltingFurnaceContent(
    state: GameWorldState,
    onCraft: (String) -> Unit,
    onInspectItem: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Refine heavy raw ores into lighter ingots. Smelting frees ~40% carrying Cap (oz).",
            style = MaterialTheme.typography.bodyMedium,
            color = ParchmentMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(RecipeCatalog.smeltingRecipes, key = { it.id }) { recipe ->
                RecipeCraftCard(
                    state = state,
                    recipe = recipe,
                    actionLabel = "Smelt",
                    accentColor = FireOrange,
                    onCraft = { onCraft(recipe.id) },
                    onInspect = { onInspectItem(recipe.outputItemId) }
                )
            }
        }
    }
}

@Composable
fun BlacksmithAnvilContent(
    state: GameWorldState,
    onCraft: (String) -> Unit,
    onInspectItem: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(CraftingTab.KNIGHT) }
    val filtered = remember(selectedTab) {
        RecipeCatalog.forgingRecipes.filter { it.tab == selectedTab }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CraftingTab.entries.forEach { tab ->
                val active = tab == selectedTab
                Surface(
                    color = if (active) TorchGold else StoneSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (active) TorchGold else StoneBorderLight),
                    modifier = Modifier
                        .clickable { selectedTab = tab }
                        .testTag("anvil_tab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (active) ObsidianVoid else ParchmentLight,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.id }) { recipe ->
                RecipeCraftCard(
                    state = state,
                    recipe = recipe,
                    actionLabel = "Forge",
                    accentColor = TorchAmber,
                    onCraft = { onCraft(recipe.id) },
                    onInspect = { onInspectItem(recipe.outputItemId) }
                )
            }
        }
    }
}

@Composable
fun FletchingBenchContent(
    state: GameWorldState,
    onCraft: (String) -> Unit,
    onInspectItem: (String) -> Unit = {}
) {
    val logCount = state.itemCount("wood_log")
    val featherCount = state.itemCount("wild_feather")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoneSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Carve Bows & Fletch Arrows from Wood Logs.",
                style = MaterialTheme.typography.labelSmall,
                color = ParchmentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Logs: $logCount • Feathers: $featherCount",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TorchGold,
                modifier = Modifier.testTag("fletching_materials_summary")
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(RecipeCatalog.fletchingRecipes, key = { it.id }) { recipe ->
                RecipeCraftCard(
                    state = state,
                    recipe = recipe,
                    actionLabel = "Fletch",
                    accentColor = ExpEmerald,
                    onCraft = { onCraft(recipe.id) },
                    onInspect = { onInspectItem(recipe.outputItemId) }
                )
            }
        }
    }
}

@Composable
fun RunecraftingTableContent(
    state: GameWorldState,
    onCraft: (String) -> Unit,
    onInspectItem: (String) -> Unit = {}
) {
    val blankCount = state.itemCount("blank_rune")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoneSurface)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MLv ${state.magicLevel}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = SoulViolet,
                modifier = Modifier.testTag("runecraft_magic_level")
            )
            Text(
                text = "MP ${state.mp}/${state.maxMp}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ManaSapphire
            )
            Text(
                text = "Soul ${state.soul}/${state.maxSoul}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SoulViolet
            )
            Text(
                text = "Blanks: $blankCount",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TorchGold,
                modifier = Modifier.testTag("runecraft_blanks_summary")
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(RecipeCatalog.runecraftingRecipes, key = { it.id }) { recipe ->
                RunecraftingRecipeCard(
                    state = state,
                    recipe = recipe,
                    onCraft = { onCraft(recipe.id) },
                    onInspect = { onInspectItem(recipe.outputItemId) }
                )
            }
        }
    }
}

@Composable
private fun RunecraftingRecipeCard(
    state: GameWorldState,
    recipe: CraftingRecipe,
    onCraft: () -> Unit,
    onInspect: () -> Unit
) {
    val outDef = ItemCatalog.get(recipe.outputItemId)
    val icon = remember(outDef.id) { EntitySpriteAtlas.itemIcon(outDef.id) }
    val magicMet = state.magicLevel >= recipe.requiredMagicLevel
    val manaMet = state.mp >= recipe.manaCost
    val soulMet = state.soul >= recipe.soulCost
    val ingredientsMet = recipe.ingredients.all { state.hasItem(it.itemId, it.count) }
    val canInscribe = magicMet && manaMet && soulMet && ingredientsMet

    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            when {
                canInscribe -> SoulViolet.copy(alpha = 0.9f)
                !magicMet -> HealthCrimson.copy(alpha = 0.55f)
                else -> StoneBorderLight.copy(alpha = 0.55f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recipe_${recipe.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onInspect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelSpritePreview(
                bitmap = icon,
                modifier = Modifier
                    .size(38.dp)
                    .background(ObsidianVoid, RoundedCornerShape(4.dp))
                    .border(1.dp, if (magicMet) SoulViolet.copy(alpha = 0.6f) else StoneBorderDark, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${outDef.name} x${recipe.outputCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (magicMet) ParchmentLight else ParchmentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "MLv ${recipe.requiredMagicLevel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (magicMet) ExpEmerald else HealthCrimson,
                        modifier = Modifier
                            .background(
                                (if (magicMet) ExpEmerald else HealthCrimson).copy(alpha = 0.16f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                (if (magicMet) ExpEmerald else HealthCrimson).copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = "${recipe.manaCost} MP • ${recipe.soulCost} Soul • ${outDef.description}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (manaMet && soulMet) ManaSapphire else TorchAmber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val reqText = recipe.ingredients.joinToString(" + ") { req ->
                    val have = state.itemCount(req.itemId)
                    "${ItemCatalog.get(req.itemId).name} ($have/${req.count})"
                }
                Text(
                    text = reqText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ingredientsMet) ExpEmerald else ParchmentMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            // Keep clickable even when missing requirements so tapping logs exact missing MP/Soul/MLv/Reagent feedback
            Button(
                onClick = onCraft,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canInscribe) SoulViolet else StoneBorderDark,
                    contentColor = if (canInscribe) ObsidianVoid else ParchmentMuted
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("craft_btn_${recipe.id}")
            ) {
                Text(
                    text = if (magicMet) "Inscribe" else "Locked",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun RecipeCraftCard(
    state: GameWorldState,
    recipe: CraftingRecipe,
    actionLabel: String,
    accentColor: androidx.compose.ui.graphics.Color,
    allowClickWhenLocked: Boolean = false,
    isAtRequiredStall: Boolean = true,
    onCraft: () -> Unit,
    onInspect: () -> Unit
) {
    val outDef = ItemCatalog.get(recipe.outputItemId)
    val icon = remember(outDef.id) { EntitySpriteAtlas.itemIcon(outDef.id) }
    val hasAllIngredients = recipe.ingredients.all { state.hasItem(it.itemId, it.count) }
    val equipReqsMet = state.meetsEquipRequirements(outDef)
    val tierColor = if (outDef.equipmentTier > 0) tierBadgeColor(outDef.equipmentTier) else accentColor
    val canExecuteNow = hasAllIngredients && isAtRequiredStall

    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            when {
                canExecuteNow -> accentColor.copy(alpha = 0.85f)
                !isAtRequiredStall -> TorchAmber.copy(alpha = 0.45f)
                else -> StoneBorderLight.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recipe_${recipe.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onInspect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelSpritePreview(
                bitmap = icon,
                modifier = Modifier
                    .size(38.dp)
                    .background(ObsidianVoid, RoundedCornerShape(4.dp))
                    .border(1.dp, if (outDef.equipmentTier >= 3 || outDef.biomeClass != BiomeEquipmentClass.NONE) tierColor.copy(alpha = 0.8f) else StoneBorderDark, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (recipe.outputCount > 1) "${outDef.name} x${recipe.outputCount}" else outDef.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = ParchmentLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (outDef.biomeClass != BiomeEquipmentClass.NONE) {
                        val bColor = androidx.compose.ui.graphics.Color(outDef.biomeClass.accentColorHex)
                        Text(
                            text = outDef.biomeClass.displayName.substringBefore(" /"),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = bColor,
                            modifier = Modifier
                                .background(bColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                .border(1.dp, bColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    } else if (outDef.equipmentTier > 0) {
                        Text(
                            text = "T${outDef.equipmentTier}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tierColor,
                            modifier = Modifier
                                .background(tierColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                .border(1.dp, tierColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                // Required biome stall pill (shown clearly in Recipe Codex or when browsing stalls)
                if (!isAtRequiredStall) {
                    Text(
                        text = "Stall: ${recipe.requiredBiomeStation.displayName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
                        color = TorchAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Stats + Requirements strip
                val statsLine = buildString {
                    if (outDef.attack > 0) {
                        append("ATK ${outDef.attack}")
                        if (outDef.isTwoHanded) append(" • 2H")
                        if (outDef.equipSlot == EquipmentSlot.WEAPON) append(" • ${outDef.speedLabel()}")
                        append(" • ")
                    }
                    if (outDef.defense > 0) append("DEF ${outDef.defense} • ")
                    if (outDef.armor > 0) append("ARM ${outDef.armor} • ")
                    if (outDef.poisonResistPercent > 0) append("+${outDef.poisonResistPercent}% Poison Res • ")
                    if (outDef.bonusMaxHp > 0) append("+${outDef.bonusMaxHp} HP • ")
                    if (outDef.procChanceBonusPct > 0) append("+${outDef.procChanceBonusPct}% Proc • ")
                    if (outDef.procPowerBonusPct > 0) append("+${outDef.procPowerBonusPct}% Pwr • ")
                    if (outDef.procExtraTargets > 0) append("+${outDef.procExtraTargets} Tgt • ")
                    if (outDef.pickaxeTier > 0) {
                        append("Tier ${outDef.pickaxeTier} Pick • Mining ${outDef.requiredMiningLevel} • ")
                    }
                    if (outDef.equipSlot != null) {
                        append("Req Lv ${outDef.requiredLevel}")
                        outDef.requiredSkill?.let { (sk, lv) ->
                            append("/${sk.displayName.take(4)} $lv")
                        }
                        append(" • ")
                    }
                    if (outDef.isTool) {
                        append("${formatOz(outDef.carriedWeightOz() * recipe.outputCount)} oz (Tool Bag)")
                    } else {
                        append("${formatOz(outDef.weightOz * recipe.outputCount)} oz")
                    }
                }
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (equipReqsMet) TorchGold else HealthCrimson,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                outDef.proc?.let { proc ->
                    Text(
                        text = "⚡ ${proc.name} (${proc.triggerChancePercent}% ${if (proc.triggerType == com.example.game.model.ProcTriggerType.ON_DEFEND) "Defend" else "Attack"}) • Tap to inspect",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = androidx.compose.ui.graphics.Color(proc.elementColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Ingredients with live backpack availability counts
                val reqText = recipe.ingredients.joinToString(" + ") { req ->
                    val have = state.itemCount(req.itemId)
                    "${ItemCatalog.get(req.itemId).name} ($have/${req.count})"
                }
                Text(
                    text = reqText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasAllIngredients) ExpEmerald else ParchmentMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onCraft,
                enabled = if (allowClickWhenLocked) true else canExecuteNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canExecuteNow) accentColor else StoneBorderDark,
                    contentColor = if (canExecuteNow) ObsidianVoid else ParchmentMuted,
                    disabledContainerColor = StoneBorderDark,
                    disabledContentColor = ParchmentMuted
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("craft_btn_${recipe.id}")
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
