package com.leitorlocal.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF315D49),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEDE1),
    onPrimaryContainer = Color(0xFF153B29),
    secondary = Color(0xFF805A45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCA),
    onSecondaryContainer = Color(0xFF3A1708),
    tertiary = Color(0xFF53644D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E8CD),
    onTertiaryContainer = Color(0xFF132610),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFECE9E1),
    onSurfaceVariant = Color(0xFF454842),
    outline = Color(0xFF7A7E78),
    outlineVariant = Color(0xFFC5C8C1),
    surfaceTint = Color(0xFF315D49),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D3B6),
    onPrimary = Color(0xFF073921),
    primaryContainer = Color(0xFF214C39),
    onPrimaryContainer = Color(0xFFC0EED0),
    secondary = Color(0xFFE4B39C),
    onSecondary = Color(0xFF442A1D),
    secondaryContainer = Color(0xFF5B3F31),
    onSecondaryContainer = Color(0xFFFFDBCA),
    tertiary = Color(0xFFBBCBAD),
    onTertiary = Color(0xFF263422),
    tertiaryContainer = Color(0xFF3C4B37),
    onTertiaryContainer = Color(0xFFD7E8CD),
    background = Color(0xFF171A17),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF20231F),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF3F4540),
    onSurfaceVariant = Color(0xFFC1C8C0),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
    surfaceTint = Color(0xFFA5D3B6),
)

@Composable
internal fun ReaderTheme(content: @Composable () -> Unit) {
    val darkMode = if (LocalInspectionMode.current) {
        false
    } else {
        (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        content = content,
    )
}
