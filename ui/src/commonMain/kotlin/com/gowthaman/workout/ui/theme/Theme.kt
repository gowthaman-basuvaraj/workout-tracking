package com.gowthaman.workout.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A5C8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF0B2641),
    secondary = Color(0xFF4F6071),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3EF),
    onSecondaryContainer = Color(0xFF131C25),
    tertiary = Color(0xFF196B5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEFE0),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFB3261E),
    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF191C1F),
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFFE0E3E8),
    onSurfaceVariant = Color(0xFF43474C),
    outline = Color(0xFF73777D),
    outlineVariant = Color(0xFFC3C7CD),
    // The container ramp has to be stated. Material's baseline is purple-tinted, so
    // anything left unset here (the navigation bar reads surfaceContainer) comes out
    // lavender next to the blue-grey everything else uses.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F9),
    surfaceContainer = Color(0xFFEEF1F5),
    surfaceContainerHigh = Color(0xFFE8EBF0),
    surfaceContainerHighest = Color(0xFFE2E6EC),
    surfaceDim = Color(0xFFDADEE4),
    surfaceBright = Color(0xFFFAFBFD),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFF0F1F4),
    inversePrimary = Color(0xFF9FCAF4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCAF4),
    onPrimary = Color(0xFF02314F),
    primaryContainer = Color(0xFF13456E),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFFBDC7D3),
    onSecondary = Color(0xFF28323B),
    secondaryContainer = Color(0xFF3E4852),
    onSecondaryContainer = Color(0xFFD9E3EF),
    tertiary = Color(0xFFA2D3C4),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005144),
    onTertiaryContainer = Color(0xFFBDEFE0),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF43474C),
    onSurfaceVariant = Color(0xFFC3C7CD),
    outline = Color(0xFF8D9197),
    outlineVariant = Color(0xFF43474C),
    surfaceContainerLowest = Color(0xFF0C0F11),
    surfaceContainerLow = Color(0xFF191C1F),
    surfaceContainer = Color(0xFF1D2023),
    surfaceContainerHigh = Color(0xFF272A2E),
    surfaceContainerHighest = Color(0xFF323539),
    surfaceDim = Color(0xFF111416),
    surfaceBright = Color(0xFF373A3D),
    inverseSurface = Color(0xFFE2E2E5),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF2A5C8A),
)

/** Numbers sit next to each other constantly here, so they get a monospace style. */
val NumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
)

private val AppTypography = Typography()

@Composable
fun UncomplicatedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
