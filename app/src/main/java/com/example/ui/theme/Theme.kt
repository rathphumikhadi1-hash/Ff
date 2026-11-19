package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = ConsoleSurfaceElevated,
    onPrimaryContainer = ElectricCyan,
    secondary = ElectricMint,
    onSecondary = ObsidianBg,
    secondaryContainer = ConsoleSurfaceHighest,
    onSecondaryContainer = ElectricMint,
    tertiary = WarmAmber,
    onTertiary = ObsidianBg,
    error = PeakCoral,
    onError = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = ConsoleSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ConsoleBorder,
    outlineVariant = ConsoleBorder,
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SonicMixTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = SonicMixTypography,
        shapes = StudioShapes,
        content = content,
    )
}
