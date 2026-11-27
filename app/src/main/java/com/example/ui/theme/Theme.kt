package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HookedDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralContainer,
    onPrimaryContainer = CoralOnContainer,
    secondary = AmberSecondary,
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = MintTertiary,
    onTertiary = Color(0xFF00201C),
    tertiaryContainer = MintContainer,
    onTertiaryContainer = MintOnContainer,
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = StudioSurfaceElevated,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = Color(0xFF23283A),
    error = Color(0xFFFF6B6B),
    onError = Color.White
)

private val HookedLightColorScheme = lightColorScheme(
    primary = CoralPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E5),
    onPrimaryContainer = Color(0xFF6B0B0F),
    secondary = Color(0xFFD98A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF1CC),
    onSecondaryContainer = Color(0xFF4D3200),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF043B35),
    background = StudioLightBackground,
    onBackground = TextPrimaryLight,
    surface = StudioLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = StudioLightBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val HookedShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HookedTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HookedDarkColorScheme else HookedLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HookedTypography,
        shapes = HookedShapes,
        content = content
    )
}
