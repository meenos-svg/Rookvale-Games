package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RookvaleColorScheme = darkColorScheme(
    primary = TorchGold,
    onPrimary = ObsidianVoid,
    primaryContainer = StoneElevated,
    onPrimaryContainer = ParchmentLight,
    secondary = TorchAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = StoneSurface,
    onSecondaryContainer = ParchmentLight,
    tertiary = ManaSapphire,
    onTertiary = ParchmentLight,
    background = ObsidianVoid,
    onBackground = ParchmentLight,
    surface = BasaltDark,
    onSurface = ParchmentLight,
    surfaceVariant = StoneSurface,
    onSurfaceVariant = ParchmentMuted,
    outline = StoneBorderLight,
    outlineVariant = StoneBorderDark,
    error = HealthCrimson,
    onError = ParchmentLight
)

@Composable
fun RookvaleTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RookvaleColorScheme,
        typography = Typography,
        content = content
    )
}
