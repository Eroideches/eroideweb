package com.eroideweb.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tema effettivo dell'app (scelto nelle Impostazioni, non per forza quello di
 * sistema). Da usare al posto di `isSystemInDarkTheme()` nei componenti.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkColors = darkColorScheme(
    primary = SkyLight,
    onPrimary = DeepBlueInk,
    primaryContainer = BlueContainer,
    onPrimaryContainer = Mist100,
    secondary = BrandCyan,
    onSecondary = DeepBlueInk,
    background = Ink950,
    onBackground = Mist100,
    surface = Ink900,
    onSurface = Mist100,
    surfaceVariant = Ink800,
    onSurfaceVariant = Mist300,
    outline = Ink500,
    outlineVariant = Ink700,
)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = PaperElevated,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = BrandBlueDeep,
    secondary = CyanDark,
    onSecondary = PaperElevated,
    background = Paper,
    onBackground = OnPaper,
    surface = PaperElevated,
    onSurface = OnPaper,
    surfaceVariant = PaperLine,
    onSurfaceVariant = Slate600,
    outline = OutlineLight,
)

/**
 * Tema dell'app: palette del marchio, niente dynamic color
 * (l'identità visiva deve restare coerente su tutti i dispositivi).
 */
@Composable
fun EroideWebTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = EroideWebTypography,
            content = content,
        )
    }
}
