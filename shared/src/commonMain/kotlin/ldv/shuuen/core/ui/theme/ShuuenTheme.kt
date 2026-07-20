package ldv.shuuen.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import ldv.shuuen.core.settings.ThemeStyle

/**
 * Maps a [ShuuenPalette] onto the Material roles. Direct colorScheme reads are
 * rare (the design tokens in ShuuenUi carry the look), but Material components
 * (text fields, menus, ripples) still pull their defaults from here, so every
 * colored role must stay monochrome. All roles are passed explicitly, which
 * makes the light/dark builder choice irrelevant — a ColorScheme is only a bag
 * of colors.
 */
private fun shuuenColorScheme(palette: ShuuenPalette): ColorScheme {
  // Stepped neutral containers: background tinted with progressively more ink.
  fun container(inkFraction: Float): Color = lerp(palette.background, palette.ink, inkFraction)

  val error = if (palette.isDark) ErrorDark else ErrorLight
  val errorContainer = if (palette.isDark) ErrorDarkContainer else ErrorLightContainer

  return lightColorScheme(
    // Primary follows the app's inverted-selection language.
    primary = palette.inverse,
    onPrimary = palette.onInverse,
    primaryContainer = palette.surfaceHigh,
    onPrimaryContainer = palette.text,
    inversePrimary = palette.surfaceHigh,
    secondary = palette.muted,
    onSecondary = palette.background,
    secondaryContainer = palette.surface,
    onSecondaryContainer = palette.text,
    tertiary = palette.dim,
    onTertiary = palette.background,
    tertiaryContainer = palette.surface,
    onTertiaryContainer = palette.text,
    background = palette.background,
    onBackground = palette.text,
    surface = palette.surface,
    onSurface = palette.text,
    surfaceVariant = palette.surfaceHigh,
    onSurfaceVariant = palette.muted,
    surfaceTint = Color.Transparent,
    inverseSurface = palette.inverse,
    inverseOnSurface = palette.onInverse,
    error = error,
    onError = palette.background,
    errorContainer = errorContainer,
    onErrorContainer = palette.text,
    outline = palette.dim,
    outlineVariant = container(0.16f),
    scrim = AppScrim,
    surfaceBright = container(0.14f),
    surfaceDim = container(0.05f),
    surfaceContainer = container(0.08f),
    surfaceContainerHigh = container(0.11f),
    surfaceContainerHighest = container(0.15f),
    surfaceContainerLow = container(0.06f),
    surfaceContainerLowest = container(0.04f),
    // Fixed roles intentionally stay visually stable across variants.
    primaryFixed = Neutral90,
    primaryFixedDim = Neutral80,
    onPrimaryFixed = Neutral0,
    onPrimaryFixedVariant = Neutral20,
    secondaryFixed = Neutral90,
    secondaryFixedDim = Neutral80,
    onSecondaryFixed = Neutral0,
    onSecondaryFixedVariant = Neutral20,
    tertiaryFixed = Neutral90,
    tertiaryFixedDim = Neutral80,
    onTertiaryFixed = Neutral0,
    onTertiaryFixedVariant = Neutral20,
  )
}

@Composable
fun ShuuenTheme(
  modifier: Modifier = Modifier,
  style: ThemeStyle = ThemeStyle.Mono,
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val palette = shuuenPalette(style, darkTheme)
  val colorScheme = remember(palette) { shuuenColorScheme(palette) }
  SyncSystemBarsWithTheme(darkTheme)
  CompositionLocalProvider(LocalShuuenPalette provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = palette.text,
        modifier = modifier,
      ) {
        content()
      }
    }
  }
}
