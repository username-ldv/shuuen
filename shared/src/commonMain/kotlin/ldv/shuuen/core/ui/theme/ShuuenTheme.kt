package ldv.shuuen.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val DarkColorScheme = darkColorScheme(
  primary = md_theme_dark_primary,
  onPrimary = md_theme_dark_onPrimary,
  primaryContainer = md_theme_dark_primaryContainer,
  onPrimaryContainer = md_theme_dark_onPrimaryContainer,
  inversePrimary = md_theme_dark_inversePrimary,
  secondary = md_theme_dark_secondary,
  onSecondary = md_theme_dark_onSecondary,
  secondaryContainer = md_theme_dark_secondaryContainer,
  onSecondaryContainer = md_theme_dark_onSecondaryContainer,
  tertiary = md_theme_dark_tertiary,
  onTertiary = md_theme_dark_onTertiary,
  tertiaryContainer = md_theme_dark_tertiaryContainer,
  onTertiaryContainer = md_theme_dark_onTertiaryContainer,
  background = md_theme_dark_background,
  onBackground = md_theme_dark_onBackground,
  surface = md_theme_dark_surface,
  onSurface = md_theme_dark_onSurface,
  surfaceVariant = md_theme_dark_surfaceVariant,
  onSurfaceVariant = md_theme_dark_onSurfaceVariant,
  surfaceTint = md_theme_dark_surfaceTint,
  inverseSurface = md_theme_dark_inverseSurface,
  inverseOnSurface = md_theme_dark_inverseOnSurface,
  error = md_theme_dark_error,
  onError = md_theme_dark_onError,
  errorContainer = md_theme_dark_errorContainer,
  onErrorContainer = md_theme_dark_onErrorContainer,
  outline = md_theme_dark_outline,
  outlineVariant = md_theme_dark_outlineVariant,
  scrim = md_theme_dark_scrim,
  surfaceBright = md_theme_dark_surfaceBright,
  surfaceDim = md_theme_dark_surfaceDim,
  surfaceContainer = md_theme_dark_surfaceContainer,
  surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
  surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
  surfaceContainerLow = md_theme_dark_surfaceContainerLow,
  surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
  primaryFixed = md_theme_dark_primaryFixed,
  primaryFixedDim = md_theme_dark_primaryFixedDim,
  onPrimaryFixed = md_theme_dark_onPrimaryFixed,
  onPrimaryFixedVariant = md_theme_dark_onPrimaryFixedVariant,
  secondaryFixed = md_theme_dark_secondaryFixed,
  secondaryFixedDim = md_theme_dark_secondaryFixedDim,
  onSecondaryFixed = md_theme_dark_onSecondaryFixed,
  onSecondaryFixedVariant = md_theme_dark_onSecondaryFixedVariant,
  tertiaryFixed = md_theme_dark_tertiaryFixed,
  tertiaryFixedDim = md_theme_dark_tertiaryFixedDim,
  onTertiaryFixed = md_theme_dark_onTertiaryFixed,
  onTertiaryFixedVariant = md_theme_dark_onTertiaryFixedVariant,
)

private val LightColorScheme = lightColorScheme(
  primary = md_theme_light_primary,
  onPrimary = md_theme_light_onPrimary,
  primaryContainer = md_theme_light_primaryContainer,
  onPrimaryContainer = md_theme_light_onPrimaryContainer,
  inversePrimary = md_theme_light_inversePrimary,
  secondary = md_theme_light_secondary,
  onSecondary = md_theme_light_onSecondary,
  secondaryContainer = md_theme_light_secondaryContainer,
  onSecondaryContainer = md_theme_light_onSecondaryContainer,
  tertiary = md_theme_light_tertiary,
  onTertiary = md_theme_light_onTertiary,
  tertiaryContainer = md_theme_light_tertiaryContainer,
  onTertiaryContainer = md_theme_light_onTertiaryContainer,
  background = md_theme_light_background,
  onBackground = md_theme_light_onBackground,
  surface = md_theme_light_surface,
  onSurface = md_theme_light_onSurface,
  surfaceVariant = md_theme_light_surfaceVariant,
  onSurfaceVariant = md_theme_light_onSurfaceVariant,
  surfaceTint = md_theme_light_surfaceTint,
  inverseSurface = md_theme_light_inverseSurface,
  inverseOnSurface = md_theme_light_inverseOnSurface,
  error = md_theme_light_error,
  onError = md_theme_light_onError,
  errorContainer = md_theme_light_errorContainer,
  onErrorContainer = md_theme_light_onErrorContainer,
  outline = md_theme_light_outline,
  outlineVariant = md_theme_light_outlineVariant,
  scrim = md_theme_light_scrim,
  surfaceBright = md_theme_light_surfaceBright,
  surfaceDim = md_theme_light_surfaceDim,
  surfaceContainer = md_theme_light_surfaceContainer,
  surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
  surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
  surfaceContainerLow = md_theme_light_surfaceContainerLow,
  surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
  primaryFixed = md_theme_light_primaryFixed,
  primaryFixedDim = md_theme_light_primaryFixedDim,
  onPrimaryFixed = md_theme_light_onPrimaryFixed,
  onPrimaryFixedVariant = md_theme_light_onPrimaryFixedVariant,
  secondaryFixed = md_theme_light_secondaryFixed,
  secondaryFixedDim = md_theme_light_secondaryFixedDim,
  onSecondaryFixed = md_theme_light_onSecondaryFixed,
  onSecondaryFixedVariant = md_theme_light_onSecondaryFixedVariant,
  tertiaryFixed = md_theme_light_tertiaryFixed,
  tertiaryFixedDim = md_theme_light_tertiaryFixedDim,
  onTertiaryFixed = md_theme_light_onTertiaryFixed,
  onTertiaryFixedVariant = md_theme_light_onTertiaryFixedVariant
)

@Composable
fun ShuuenTheme(
  modifier: Modifier = Modifier,
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = AppTypography,
    shapes = AppShapes,
  ) {
    Surface(
      color = MaterialTheme.colorScheme.background,
      modifier = modifier,
    ) {
      content()
    }
  }
}
