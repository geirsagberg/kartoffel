package net.sagberg.kartoffel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8EF8D5),
    onPrimaryContainer = Color(0xFF002018),
    secondary = Color(0xFF4B635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DD),
    onSecondaryContainer = Color(0xFF082019),
    tertiary = Color(0xFF805500),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB5),
    onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF72DBBC),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFF8EF8D5),
    secondary = Color(0xFFB3CCC1),
    onSecondary = Color(0xFF1F352E),
    secondaryContainer = Color(0xFF354B43),
    onSecondaryContainer = Color(0xFFCEE9DD),
    tertiary = Color(0xFFF8BD6B),
    onTertiary = Color(0xFF462B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFDEE5E0),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFDEE5E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
)

@Composable
fun KartoffelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
