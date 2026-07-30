package com.dvote.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E604A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E9DF),
    onPrimaryContainer = Color(0xFF123B30),
    inversePrimary = Color(0xFFA9D5C5),
    secondary = Color(0xFFB94D2C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8D8CC),
    onSecondaryContainer = Color(0xFF9A3A20),
    tertiary = Color(0xFF6B5D2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEE5BA),
    onTertiaryContainer = Color(0xFF3A3007),
    background = Color(0xFFFFFCF8),
    onBackground = Color(0xFF241C18),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF241C18),
    surfaceVariant = Color(0xFFFFF8F2),
    onSurfaceVariant = Color(0xFF4B3D37),
    surfaceTint = Color(0xFF0E604A),
    inverseSurface = Color(0xFF392E29),
    inverseOnSurface = Color(0xFFFFF1E8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A7469),
    outlineVariant = Color(0xFFEBCFC4),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFFCF8),
    surfaceDim = Color(0xFFECDDD4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF8F2),
    surfaceContainer = Color(0xFFFAF1EB),
    surfaceContainerHigh = Color(0xFFF5EAE3),
    surfaceContainerHighest = Color(0xFFEFE2DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9D5C5),
    onPrimary = Color(0xFF0A392E),
    primaryContainer = Color(0xFF0E604A),
    onPrimaryContainer = Color(0xFFD7E9DF),
    inversePrimary = Color(0xFF0E604A),
    secondary = Color(0xFFFFB5A0),
    onSecondary = Color(0xFF5B1A0B),
    secondaryContainer = Color(0xFF7C3322),
    onSecondaryContainer = Color(0xFFFFDAD0),
    tertiary = Color(0xFFD8C894),
    onTertiary = Color(0xFF3A3007),
    tertiaryContainer = Color(0xFF534817),
    onTertiaryContainer = Color(0xFFF5E5AE),
    background = Color(0xFF17120F),
    onBackground = Color(0xFFF1E7E0),
    surface = Color(0xFF17120F),
    onSurface = Color(0xFFF1E7E0),
    surfaceVariant = Color(0xFF53443D),
    onSurfaceVariant = Color(0xFFDCC4B9),
    surfaceTint = Color(0xFFA9D5C5),
    inverseSurface = Color(0xFFF1E7E0),
    inverseOnSurface = Color(0xFF352B26),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA78E82),
    outlineVariant = Color(0xFF53443D),
    scrim = Color.Black,
    surfaceBright = Color(0xFF403630),
    surfaceDim = Color(0xFF17120F),
    surfaceContainerLowest = Color(0xFF110D0B),
    surfaceContainerLow = Color(0xFF201A17),
    surfaceContainer = Color(0xFF251E1B),
    surfaceContainerHigh = Color(0xFF302723),
    surfaceContainerHighest = Color(0xFF3B302B),
)

@Composable
fun DVoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DVoteTypography,
        content = content,
    )
}
