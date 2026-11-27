package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.model.GOLD_COIN_WEIGHT_OZ
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.engine.GameEngine
import com.example.ui.theme.ExpEmerald
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

private enum class DepotTransferQty(val label: String, val tagSuffix: String) {
    ONE("1", "1"),
    FIVE("5", "5"),
    ALL("All", "all");

    fun resolveForStack(stackCount: Int): Int = when (this) {
        ONE -> 1
        FIVE -> minOf(5, stackCount.coerceAtLeast(1))
        ALL -> stackCount.coerceAtLeast(1)
    }
}

@Composable
internal fun DepotPanelContent(
    state: GameWorldState,
    onDepositItem: (String, Int) -> Unit,
    onWithdrawItem: (String, Int) -> Unit,
    onDepositGold: (Int) -> Unit,
    onDepositAllGold: () -> Unit,
    onWithdrawGold: (Long) -> Unit,
    onWithdrawAllGold: () -> Unit,
    onDepositAllMaterials: () -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    var transferQty by remember { mutableStateOf(DepotTransferQty.ONE) }
    val depotEntries = remember(state.depot) { state.depot.withStableKeys("depot_in") }
    val carriedEntries = remember(state.toolBag, state.backpack) {
        state.allCarriedItems().withStableKeys("depot_pack")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PanelTabChip(
                text = "Depot (${depotEntries.size})",
                selected = tab == 0,
                onClick = { tab = 0 },
                modifier = Modifier.weight(1f).testTag("depot_tab_in")
            )
            PanelTabChip(
                text = "Backpack (${carriedEntries.size})",
                selected = tab == 1,
                onClick = { tab = 1 },
                modifier = Modifier.weight(1f).testTag("depot_tab_pack")
            )
            PanelTabChip(
                text = stringResource(R.string.depot_tab_vault),
                selected = tab == 2,
                onClick = { tab = 2 },
                modifier = Modifier.weight(1f).testTag("depot_tab_bank")
            )
        }

        if (tab == 2) {
            BankVaultTabContent(
                state = state,
                onDepositGold = onDepositGold,
                onDepositAllGold = onDepositAllGold,
                onWithdrawGold = onWithdrawGold,
                onWithdrawAllGold = onWithdrawAllGold
            )
        } else {
            DepotQuantityToggleBar(
                selected = transferQty,
                isDepositTab = tab == 1,
                onSelect = { transferQty = it }
            )
            val activeEntries = if (tab == 0) depotEntries else carriedEntries
            if (tab == 1) {
                val materialStacks = state.backpack.count { it.count > 0 && GameEngine.isDepositMaterial(ItemCatalog.get(it.itemId)) }
                BankQuickActionButton(
                    label = if (materialStacks > 0) "Deposit all materials ($materialStacks)" else "No materials to deposit",
                    enabled = materialStacks > 0,
                    isDeposit = true,
                    onClick = onDepositAllMaterials,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("depot_deposit_all_materials")
                )
            }
            if (activeEntries.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tab == 0) "Your Depot Chest is empty." else "No carried items to deposit.",
                        color = ParchmentMuted
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(activeEntries, key = { it.key }) { entry ->
                        val stack = entry.stack
                        val def = ItemCatalog.get(stack.itemId)
                        val moveCount = transferQty.resolveForStack(stack.count)
                        val sub = if (def.isTool) {
                            "Tool Bag • ${formatOz(def.carriedWeightOz() * stack.count)} oz • ${def.description}"
                        } else {
                            "${formatOz(def.weightOz * stack.count)} oz • ${def.description}"
                        }
                        val actionVerb = if (tab == 0) "Take" else "Store"
                        val primaryLabel = when (transferQty) {
                            DepotTransferQty.ONE -> actionVerb
                            DepotTransferQty.FIVE -> if (moveCount > 1) "$actionVerb $moveCount" else actionVerb
                            DepotTransferQty.ALL -> if (stack.count > 1) "$actionVerb All (${stack.count})" else actionVerb
                        }
                        ItemActionRow(
                            stack = stack,
                            subtitleOverride = sub,
                            primaryLabel = primaryLabel,
                            onPrimary = {
                                if (tab == 0) {
                                    onWithdrawItem(stack.itemId, moveCount)
                                } else {
                                    onDepositItem(stack.itemId, moveCount)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepotQuantityToggleBar(
    selected: DepotTransferQty,
    isDepositTab: Boolean,
    onSelect: (DepotTransferQty) -> Unit
) {
    Surface(
        color = StoneElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, StoneBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isDepositTab) "Deposit Qty:" else "Withdraw Qty:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ParchmentMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DepotTransferQty.entries.forEach { option ->
                    val isActive = option == selected
                    Surface(
                        color = if (isActive) TorchAmber else StoneSurface,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, if (isActive) TorchGold else StoneBorderLight),
                        modifier = Modifier
                            .clickable { onSelect(option) }
                            .testTag("depot_qty_${option.tagSuffix}")
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 28.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isActive) ObsidianVoid else ParchmentLight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankVaultTabContent(
    state: GameWorldState,
    onDepositGold: (Int) -> Unit,
    onDepositAllGold: () -> Unit,
    onWithdrawGold: (Long) -> Unit,
    onWithdrawAllGold: () -> Unit
) {
    var customAmountText by remember { mutableStateOf("") }
    val parsedAmount = customAmountText.toLongOrNull()?.takeIf { it > 0L }
    val carriedGoldOz = state.gold * GOLD_COIN_WEIGHT_OZ

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Dual Balance Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StoneBorderLight),
                modifier = Modifier
                    .weight(1f)
                    .testTag("bank_carried_gold_card")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = stringResource(R.string.bank_carried_gold),
                            tint = TorchGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.bank_carried_gold),
                            style = MaterialTheme.typography.labelSmall,
                            color = ParchmentMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${formatGoldNumber(state.gold.toLong())} gp",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TorchGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatOz(carriedGoldOz)} oz carried",
                        style = MaterialTheme.typography.labelSmall,
                        color = ParchmentMuted
                    )
                }
            }

            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ExpEmerald.copy(alpha = 0.65f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("bank_balance_card")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = stringResource(R.string.bank_vault_balance),
                            tint = ExpEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.bank_vault_balance),
                            style = MaterialTheme.typography.labelSmall,
                            color = ParchmentMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${formatGoldNumber(state.bankGold)} gp",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ParchmentLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ExpEmerald,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = stringResource(R.string.bank_safe_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Quick Deposit Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BankQuickActionButton(
                label = "Deposit 100",
                enabled = state.gold >= 100,
                isDeposit = true,
                onClick = { onDepositGold(100) },
                modifier = Modifier.weight(1f).testTag("bank_deposit_100")
            )
            BankQuickActionButton(
                label = "Deposit 1,000",
                enabled = state.gold >= 1000,
                isDeposit = true,
                onClick = { onDepositGold(1000) },
                modifier = Modifier.weight(1f).testTag("bank_deposit_1000")
            )
            BankQuickActionButton(
                label = "Deposit All",
                enabled = state.gold > 0,
                isDeposit = true,
                onClick = onDepositAllGold,
                modifier = Modifier.weight(1f).testTag("bank_deposit_all")
            )
        }

        // Quick Withdraw Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BankQuickActionButton(
                label = "Withdraw 100",
                enabled = state.bankGold >= 100L,
                isDeposit = false,
                onClick = { onWithdrawGold(100L) },
                modifier = Modifier.weight(1f).testTag("bank_withdraw_100")
            )
            BankQuickActionButton(
                label = "Withdraw 1,000",
                enabled = state.bankGold >= 1000L,
                isDeposit = false,
                onClick = { onWithdrawGold(1000L) },
                modifier = Modifier.weight(1f).testTag("bank_withdraw_1000")
            )
            BankQuickActionButton(
                label = "Withdraw All",
                enabled = state.bankGold > 0L,
                isDeposit = false,
                onClick = onWithdrawAllGold,
                modifier = Modifier.weight(1f).testTag("bank_withdraw_all")
            )
        }

        // Custom Amount Transfer Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = customAmountText,
                onValueChange = { raw ->
                    customAmountText = raw.filter { it.isDigit() }.take(10)
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.bank_custom_amount_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = ParchmentMuted
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = ParchmentLight),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianVoid,
                    unfocusedContainerColor = ObsidianVoid,
                    focusedBorderColor = TorchGold,
                    unfocusedBorderColor = StoneBorderLight,
                    cursorColor = TorchGold
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("bank_custom_amount_input")
            )
            Button(
                onClick = {
                    val amt = parsedAmount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: return@Button
                    onDepositGold(amt)
                    customAmountText = ""
                },
                enabled = parsedAmount != null && state.gold > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TorchAmber,
                    contentColor = ObsidianVoid,
                    disabledContainerColor = StoneSurface,
                    disabledContentColor = ParchmentMuted.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("bank_custom_deposit_button")
            ) {
                Text(
                    text = stringResource(R.string.action_deposit),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            OutlinedButton(
                onClick = {
                    val amt = parsedAmount ?: return@OutlinedButton
                    onWithdrawGold(amt)
                    customAmountText = ""
                },
                enabled = parsedAmount != null && state.bankGold > 0L,
                border = BorderStroke(
                    1.dp,
                    if (parsedAmount != null && state.bankGold > 0L) TorchGold else StoneBorderDark
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("bank_custom_withdraw_button")
            ) {
                Text(
                    text = stringResource(R.string.action_withdraw),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (parsedAmount != null && state.bankGold > 0L) TorchGold else ParchmentMuted.copy(alpha = 0.5f)
                )
            }
        }

        // Shop clarity note
        Text(
            text = stringResource(R.string.bank_shop_carried_note),
            style = MaterialTheme.typography.labelSmall,
            color = ParchmentMuted,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BankQuickActionButton(
    label: String,
    enabled: Boolean,
    isDeposit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isDeposit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = TorchAmber,
                contentColor = ObsidianVoid,
                disabledContainerColor = StoneSurface,
                disabledContentColor = ParchmentMuted.copy(alpha = 0.45f)
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = modifier.height(36.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            border = BorderStroke(1.dp, if (enabled) TorchGold else StoneBorderDark),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = StoneSurface,
                contentColor = TorchGold,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = ParchmentMuted.copy(alpha = 0.45f)
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = modifier.height(36.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatGoldNumber(value: Long): String = String.format(Locale.US, "%,d", value)
