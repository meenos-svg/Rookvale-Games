package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.cloud.CloudAuthUiState
import com.example.game.data.SaveSlotEntity
import com.example.game.model.AdventurerPalette
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.SpriteColorSwatch
import com.example.game.model.Vocation
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.update.UpdateCheckState
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchGold

private enum class AuthTab {
    CREATE,
    RECOVER
}

@Composable
fun TitleAndCreationScreen(
    savedSlots: List<SaveSlotEntity>,
    updateState: UpdateCheckState = UpdateCheckState(),
    cloudAuthState: CloudAuthUiState = CloudAuthUiState(),
    onDownloadUpdate: (String) -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onCreateCloudCharacter: (name: String, pin: String, tunicColorIndex: Int, hairColorIndex: Int) -> Unit,
    onLoginCloudCharacter: (name: String, pin: String) -> Unit,
    onResumeCachedSession: () -> Unit = {},
    onSignOutCachedSession: () -> Unit = {},
    onClearCloudError: () -> Unit = {},
    onLoadCharacter: (slotId: String) -> Unit,
    onDeleteSlot: (slotId: String) -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(AuthTab.CREATE) }
    var heroName by rememberSaveable { mutableStateOf("Aldric") }
    var createPin by rememberSaveable { mutableStateOf("") }
    var loginName by rememberSaveable { mutableStateOf("") }
    var loginPin by rememberSaveable { mutableStateOf("") }
    var pinVisible by rememberSaveable { mutableStateOf(false) }
    var tunicColorIndex by rememberSaveable { mutableIntStateOf(0) }
    var hairColorIndex by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    val previewSprite = remember(tunicColorIndex, hairColorIndex) {
        EntitySpriteAtlas.playerBitmap(Vocation.ADVENTURER, 0, tunicColorIndex, hairColorIndex)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TorchGold,
        unfocusedBorderColor = StoneBorderLight,
        focusedTextColor = ParchmentLight,
        unfocusedTextColor = ParchmentLight,
        focusedLabelColor = TorchGold,
        unfocusedLabelColor = ParchmentMuted,
        cursorColor = TorchGold,
        focusedContainerColor = StoneSurface,
        unfocusedContainerColor = StoneSurface
    )

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
                .height(215.dp)
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
            Surface(
                color = BasaltDark.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 14.dp)
                    .testTag("app_version_badge")
            ) {
                Text(
                    text = updateState.currentVersionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TorchGold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
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
            // Auto-login progress banner on startup
            AnimatedVisibility(visible = cloudAuthState.isAutoLoggingIn) {
                Surface(
                    color = StoneElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, ExpEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_autologin_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = TorchGold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = cloudAuthState.infoMessage ?: "Restoring your Rookvale cloud hero...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ParchmentLight
                        )
                    }
                }
            }

            // Dismissible GitHub Release Update Banner
            AnimatedVisibility(
                visible = updateState.updateAvailable,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = StoneElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, TorchGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_available_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Update Available",
                                tint = TorchGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "New Rookvale Build Available!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TorchGold
                                )
                                val sub = buildString {
                                    if (!updateState.releaseDateLabel.isNullOrBlank()) {
                                        append("Published ${updateState.releaseDateLabel} • ")
                                    }
                                    append("Installed ${updateState.currentVersionLabel}")
                                }
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ParchmentMuted
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onDownloadUpdate(updateState.downloadUrl) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TorchGold,
                                    contentColor = ObsidianVoid
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("download_update_button")
                            ) {
                                Text(
                                    text = "DOWNLOAD UPDATE",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = onDismissUpdate,
                                border = BorderStroke(1.dp, StoneBorderLight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("dismiss_update_button")
                            ) {
                                Text(
                                    text = "Later",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ParchmentLight
                                )
                            }
                        }
                    }
                }
            }

            // Cached Cloud Hero Quick-Resume Card (no PIN re-entry required!)
            val cached = cloudAuthState.cachedSession
            if (cached != null && !cloudAuthState.isAutoLoggingIn) {
                Surface(
                    color = StoneElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, ExpEmerald.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cached_cloud_hero_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud Hero Ready",
                            tint = ExpEmerald,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cached.characterName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ParchmentLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Cloud Save Linked • Auto-login ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpEmerald
                            )
                        }
                        Button(
                            onClick = onResumeCachedSession,
                            enabled = !cloudAuthState.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExpEmerald,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("resume_cloud_hero_button")
                        ) {
                            Text(
                                text = "CONTINUE",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(
                            onClick = onSignOutCachedSession,
                            enabled = !cloudAuthState.isBusy,
                            modifier = Modifier.testTag("signout_cached_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Forget cached hero on this device",
                                tint = ParchmentMuted
                            )
                        }
                    }
                }
            }

            // Cloud Character Portal: Create New Hero vs Load / Recover Existing Hero (Name + 4-Digit PIN)
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
                    // Segmented Tab Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuthTabButton(
                            icon = Icons.Default.PersonAdd,
                            label = "New Hero",
                            selected = activeTab == AuthTab.CREATE,
                            onClick = {
                                activeTab = AuthTab.CREATE
                                onClearCloudError()
                            },
                            testTag = "tab_create_hero",
                            modifier = Modifier.weight(1f)
                        )
                        AuthTabButton(
                            icon = Icons.Default.CloudDownload,
                            label = "Load / Recover",
                            selected = activeTab == AuthTab.RECOVER,
                            onClick = {
                                activeTab = AuthTab.RECOVER
                                onClearCloudError()
                            },
                            testTag = "tab_load_cloud_hero",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Error / Info Feedback Banner
                    if (!cloudAuthState.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = HealthCrimson.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, HealthCrimson),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_auth_error_banner")
                        ) {
                            Text(
                                text = cloudAuthState.errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ParchmentLight,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (!cloudAuthState.infoMessage.isNullOrBlank() && !cloudAuthState.isAutoLoggingIn) {
                        Surface(
                            color = StoneElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (cloudAuthState.isBusy) {
                                    CircularProgressIndicator(
                                        color = TorchGold,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = cloudAuthState.infoMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TorchGold
                                )
                            }
                        }
                    }

                    if (activeTab == AuthTab.CREATE) {
                        // --- CREATE NEW CLOUD CHARACTER (Name + 4-Digit PIN) ---
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
                                        text = "Lvl 1 Adventurer • Cloud Backed",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TorchGold
                                    )
                                    Text(
                                        text = "Pick a Hero Name & 4-digit PIN to save progress to Supabase across APK updates and reinstalls.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ParchmentMuted
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = heroName,
                            onValueChange = {
                                if (it.length <= 18) {
                                    heroName = it
                                    onClearCloudError()
                                }
                            },
                            label = { Text(stringResource(R.string.hero_name_label)) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hero_name_input")
                        )

                        OutlinedTextField(
                            value = createPin,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(4)
                                createPin = digits
                                onClearCloudError()
                            },
                            label = { Text("4-Digit Recovery PIN") },
                            placeholder = { Text("e.g. 4829", color = ParchmentMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = TorchGold)
                            },
                            trailingIcon = {
                                IconButton(onClick = { pinVisible = !pinVisible }) {
                                    Icon(
                                        imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (pinVisible) "Hide PIN" else "Show PIN",
                                        tint = ParchmentMuted
                                    )
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_pin_input")
                        )

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
                            onClick = {
                                onCreateCloudCharacter(heroName, createPin, tunicColorIndex, hairColorIndex)
                            },
                            enabled = !cloudAuthState.isBusy,
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
                    } else {
                        // --- LOAD / RECOVER EXISTING CHARACTER (Name + 4-Digit PIN) ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = TorchGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Recover Cloud Hero",
                                style = MaterialTheme.typography.titleLarge,
                                color = ParchmentLight
                            )
                        }

                        Text(
                            text = "Installed a new APK or switched phones? Enter your Hero Name and 4-digit PIN to restore your exact level, skills, gear, backpack, and coordinates from Rookvale Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentMuted
                        )

                        OutlinedTextField(
                            value = loginName,
                            onValueChange = {
                                if (it.length <= 18) {
                                    loginName = it
                                    onClearCloudError()
                                }
                            },
                            label = { Text("Character Name") },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_name_input")
                        )

                        OutlinedTextField(
                            value = loginPin,
                            onValueChange = { raw ->
                                loginPin = raw.filter { it.isDigit() }.take(4)
                                onClearCloudError()
                            },
                            label = { Text("4-Digit PIN") },
                            placeholder = { Text("4 digits", color = ParchmentMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = TorchGold)
                            },
                            trailingIcon = {
                                IconButton(onClick = { pinVisible = !pinVisible }) {
                                    Icon(
                                        imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (pinVisible) "Hide PIN" else "Show PIN",
                                        tint = ParchmentMuted
                                    )
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pin_input")
                        )

                        Button(
                            onClick = { onLoginCloudCharacter(loginName, loginPin) },
                            enabled = !cloudAuthState.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TorchGold,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("load_cloud_hero_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LOAD CHARACTER",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Existing Local Device Saves (Quick Offline Fallback Access)
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AuthTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) TorchGold else StoneSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, if (selected) TorchGold else StoneBorderLight),
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ObsidianVoid else TorchGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) ObsidianVoid else ParchmentLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
