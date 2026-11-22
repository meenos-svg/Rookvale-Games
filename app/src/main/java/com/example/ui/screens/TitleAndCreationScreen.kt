package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.data.SaveSlotEntity
import com.example.game.model.AdventurerPalette
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.SpriteColorSwatch
import com.example.game.model.Vocation
import com.example.game.sprites.EntitySpriteAtlas
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchGold

@Composable
fun TitleAndCreationScreen(
    savedSlots: List<SaveSlotEntity>,
    onCreateCharacter: (name: String, tunicColorIndex: Int, hairColorIndex: Int) -> Unit,
    onLoadCharacter: (slotId: String) -> Unit,
    onDeleteSlot: (slotId: String) -> Unit
) {
    var heroName by rememberSaveable { mutableStateOf("Aldric") }
    var tunicColorIndex by rememberSaveable { mutableIntStateOf(0) }
    var hairColorIndex by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    val previewSprite = remember(tunicColorIndex, hairColorIndex) {
        EntitySpriteAtlas.playerBitmap(Vocation.ADVENTURER, 0, tunicColorIndex, hairColorIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        // Atmospheric Pixel-Art Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(225.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_rookvale_hero_1795279155504),
                contentDescription = "Rookvale medieval city and dungeon entrance",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x880E0C0B),
                                Color(0x440E0C0B),
                                ObsidianVoid
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = TorchGold
                )
                Text(
                    text = stringResource(R.string.title_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ParchmentLight
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Existing Offline Saved Characters
            if (savedSlots.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.saved_characters),
                    style = MaterialTheme.typography.titleMedium,
                    color = TorchGold
                )
                savedSlots.forEach { slot ->
                    SavedCharacterCard(
                        slot = slot,
                        onLoad = { onLoadCharacter(slot.slotId) },
                        onDelete = { onDeleteSlot(slot.slotId) }
                    )
                }
            }

            // Blank-Slate Rookgaard Adventurer Creation Card (NO vocation presets!)
            Surface(
                color = BasaltDark,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, StoneBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = TorchGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.new_hero_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = ParchmentLight
                        )
                    }

                    // Live Pixel Sprite Preview + Blank-Slate Adventurer Overview
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StoneBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PixelSpritePreview(
                                bitmap = previewSprite,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianVoid)
                                    .border(1.5.dp, TorchGold, RoundedCornerShape(8.dp))
                                    .testTag("adventurer_sprite_preview")
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Lvl 1 Adventurer",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TorchGold
                                )
                                Text(
                                    text = "Start with identical balanced stats (150 HP, 55 MP, 400 oz) and train any weapon, shield, rune, or gathering skill freely.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ParchmentMuted
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = heroName,
                        onValueChange = { if (it.length <= 18) heroName = it },
                        label = { Text(stringResource(R.string.hero_name_label)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TorchGold,
                            unfocusedBorderColor = StoneBorderLight,
                            focusedTextColor = ParchmentLight,
                            unfocusedTextColor = ParchmentLight,
                            focusedLabelColor = TorchGold,
                            unfocusedLabelColor = ParchmentMuted,
                            cursorColor = TorchGold,
                            focusedContainerColor = StoneSurface,
                            unfocusedContainerColor = StoneSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_name_input")
                    )

                    // Cosmetic-Only Pixel Sprite Tunic & Hair Customization
                    ColorSwatchPickerRow(
                        label = "Tunic Color (Cosmetic)",
                        swatches = AdventurerPalette.tunicColors,
                        selectedIndex = tunicColorIndex,
                        tagPrefix = "tunic_swatch",
                        onSelect = { tunicColorIndex = it }
                    )

                    ColorSwatchPickerRow(
                        label = "Hair Color (Cosmetic)",
                        swatches = AdventurerPalette.hairColors,
                        selectedIndex = hairColorIndex,
                        tagPrefix = "hair_swatch",
                        onSelect = { hairColorIndex = it }
                    )

                    // Oracle Level 20 Progression Note
                    Surface(
                        color = StoneElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TorchGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Reach Level $ORACLE_MIN_LEVEL and speak with The Oracle in Rookvale Temple to choose Knight, Paladin, or Mage.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ParchmentLight
                            )
                        }
                    }

                    Button(
                        onClick = { onCreateCharacter(heroName, tunicColorIndex, hairColorIndex) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TorchGold,
                            contentColor = ObsidianVoid
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enter_rookvale_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.enter_rookvale).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorSwatchPickerRow(
    label: String,
    swatches: List<SpriteColorSwatch>,
    selectedIndex: Int,
    tagPrefix: String,
    onSelect: (Int) -> Unit
) {
    val activeName = swatches.getOrNull(selectedIndex)?.name ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = TorchGold)
            Text(text = activeName, style = MaterialTheme.typography.labelSmall, color = ParchmentMuted)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            swatches.forEachIndexed { idx, swatch ->
                val selected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) StoneElevated else StoneSurface)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) TorchGold else StoneBorderLight,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(idx) }
                        .testTag("${tagPrefix}_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(swatch.primaryArgb))
                            .border(1.5.dp, Color(swatch.highlightArgb), RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedCharacterCard(
    slot: SaveSlotEntity,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val voc = remember(slot.vocationName) {
        runCatching { Vocation.valueOf(slot.vocationName) }.getOrDefault(Vocation.ADVENTURER)
    }
    val tunicIdx = remember(slot.payloadJson) { extractJsonInt(slot.payloadJson, "tunicColorIndex") }
    val hairIdx = remember(slot.payloadJson) { extractJsonInt(slot.payloadJson, "hairColorIndex") }
    val sprite = remember(voc, tunicIdx, hairIdx) {
        EntitySpriteAtlas.playerBitmap(voc, 0, tunicIdx, hairIdx)
    }
    val floorLabel = if (slot.floor == 0) "Rookvale City" else "Floor ${slot.floor}"

    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
            .testTag("save_slot_${slot.slotId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelSpritePreview(
                bitmap = sprite,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianVoid)
                    .border(1.dp, StoneBorderDark, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.characterName,
                    style = MaterialTheme.typography.titleMedium,
                    color = ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Lvl ${slot.level} ${voc.displayName} • $floorLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = TorchGold
                )
            }
            Text(
                text = "PLAY",
                style = MaterialTheme.typography.labelLarge,
                color = ExpEmerald,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_slot_${slot.slotId}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = ParchmentMuted
                )
            }
        }
    }
}

private fun extractJsonInt(json: String, field: String): Int {
    val match = Regex("\"$field\"\\s*:\\s*(\\d+)").find(json) ?: return 0
    return match.groupValues[1].toIntOrNull() ?: 0
}

@Composable
fun PixelSpritePreview(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None
        )
    }
}
