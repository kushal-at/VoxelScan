package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Strictly Non-Dark, Non-Gradient Blue / Yellow / White Light Color Scheme
private val VoxelScanLightColorScheme = lightColorScheme(
    primary = CobaltBluePrimary,
    onPrimary = PureWhite,
    primaryContainer = SkyBlueSoft,
    onPrimaryContainer = DeepNavyInk,
    secondary = SolarYellowAccent,
    onSecondary = DeepNavyInk,
    secondaryContainer = BrightYellowSurface,
    onSecondaryContainer = DeepAmberInk,
    tertiary = RoyalBlueAccent,
    onTertiary = PureWhite,
    tertiaryContainer = TranslucentIceSurface,
    onTertiaryContainer = DeepNavyInk,
    background = PorcelainWhite,
    onBackground = DeepNavyInk,
    surface = PureWhite,
    onSurface = DeepNavyInk,
    surfaceVariant = SkyBlueSoft,
    onSurfaceVariant = SlateBlueMuted,
    outline = CrispBlueBorder,
    outlineVariant = WarmYellowBorder,
    error = AlertRedSolid,
    onError = PureWhite,
    errorContainer = AlertRedSurface,
    onErrorContainer = AlertRedSolid
)

val VoxelScanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VoxelScanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoxelScanLightColorScheme,
        typography = Typography,
        shapes = VoxelScanShapes,
        content = content
    )
}
