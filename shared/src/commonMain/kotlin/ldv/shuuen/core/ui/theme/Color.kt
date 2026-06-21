package ldv.shuuen.core.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral ramp
val Neutral0 = Color(0xFF000000)
val Neutral4 = Color(0xFF0A0A0A)
val Neutral6 = Color(0xFF101010)
val Neutral8 = Color(0xFF141414)
val Neutral10 = Color(0xFF191919)
val Neutral12 = Color(0xFF1E1E1E)
val Neutral14 = Color(0xFF242424)
val Neutral17 = Color(0xFF2B2B2B)
val Neutral20 = Color(0xFF333333)
val Neutral24 = Color(0xFF3D3D3D)
val Neutral30 = Color(0xFF4D4D4D)
val Neutral40 = Color(0xFF666666)
val Neutral50 = Color(0xFF808080)
val Neutral60 = Color(0xFF999999)
val Neutral70 = Color(0xFFB3B3B3)
val Neutral80 = Color(0xFFCCCCCC)
val Neutral90 = Color(0xFFE5E5E5)
val Neutral95 = Color(0xFFF2F2F2)
val Neutral98 = Color(0xFFFAFAFA)
val Neutral100 = Color(0xFFFFFFFF)

// Optional muted icon accents only.
// These are not wired into MaterialTheme automatically.
val IconAccentMint = Color(0xFF9FB8A7)
val IconAccentBlue = Color(0xFFA2B4C8)
val IconAccentLavender = Color(0xFFB4ADC8)
val IconAccentGold = Color(0xFFC2B28A)
val IconAccentTeal = Color(0xFF92B4B1)

// Error colors
val ErrorDark = Color(0xFFFF8A80)
val ErrorDarkContainer = Color(0xFF2A0F0F)
val ErrorLight = Color(0xFFB3261E)
val ErrorLightContainer = Color(0xFFF9DEDC)

// Scrim
val AppScrim = Color(0xCC000000)

// Dark theme roles
val md_theme_dark_primary = Neutral60
val md_theme_dark_onPrimary = Neutral0
val md_theme_dark_primaryContainer = Neutral17
val md_theme_dark_onPrimaryContainer = Neutral60
val md_theme_dark_inversePrimary = Neutral20

val md_theme_dark_secondary = Neutral80
val md_theme_dark_onSecondary = Neutral0
val md_theme_dark_secondaryContainer = Neutral14
val md_theme_dark_onSecondaryContainer = Neutral95

val md_theme_dark_tertiary = Neutral70
val md_theme_dark_onTertiary = Neutral0
val md_theme_dark_tertiaryContainer = Neutral12
val md_theme_dark_onTertiaryContainer = Neutral95

val md_theme_dark_background = Neutral0
val md_theme_dark_onBackground = Neutral95

val md_theme_dark_surface = Neutral8
val md_theme_dark_onSurface = Neutral95
val md_theme_dark_surfaceVariant = Neutral17
val md_theme_dark_onSurfaceVariant = Neutral60
val md_theme_dark_surfaceTint = Color.Transparent
val md_theme_dark_inverseSurface = Neutral95
val md_theme_dark_inverseOnSurface = Neutral0

val md_theme_dark_error = ErrorDark
val md_theme_dark_onError = Neutral0
val md_theme_dark_errorContainer = ErrorDarkContainer
val md_theme_dark_onErrorContainer = Neutral95

val md_theme_dark_outline = Neutral30
val md_theme_dark_outlineVariant = Neutral17
val md_theme_dark_scrim = AppScrim

val md_theme_dark_surfaceBright = Neutral14
val md_theme_dark_surfaceDim = Neutral6
val md_theme_dark_surfaceContainer = Neutral10
val md_theme_dark_surfaceContainerHigh = Neutral12
val md_theme_dark_surfaceContainerHighest = Neutral17
val md_theme_dark_surfaceContainerLow = Neutral8
val md_theme_dark_surfaceContainerLowest = Neutral4

val md_theme_dark_primaryFixed = Neutral90
val md_theme_dark_primaryFixedDim = Neutral80
val md_theme_dark_onPrimaryFixed = Neutral0
val md_theme_dark_onPrimaryFixedVariant = Neutral20

val md_theme_dark_secondaryFixed = Neutral90
val md_theme_dark_secondaryFixedDim = Neutral80
val md_theme_dark_onSecondaryFixed = Neutral0
val md_theme_dark_onSecondaryFixedVariant = Neutral20

val md_theme_dark_tertiaryFixed = Neutral90
val md_theme_dark_tertiaryFixedDim = Neutral80
val md_theme_dark_onTertiaryFixed = Neutral0
val md_theme_dark_onTertiaryFixedVariant = Neutral20

// Light theme roles
val md_theme_light_primary = Neutral10
val md_theme_light_onPrimary = Neutral100
val md_theme_light_primaryContainer = Neutral90
val md_theme_light_onPrimaryContainer = Neutral10
val md_theme_light_inversePrimary = Neutral90

val md_theme_light_secondary = Neutral20
val md_theme_light_onSecondary = Neutral100
val md_theme_light_secondaryContainer = Neutral95
val md_theme_light_onSecondaryContainer = Neutral10

val md_theme_light_tertiary = Neutral20
val md_theme_light_onTertiary = Neutral100
val md_theme_light_tertiaryContainer = Neutral95
val md_theme_light_onTertiaryContainer = Neutral10

val md_theme_light_background = Color(0xFFF7F7F7)
val md_theme_light_onBackground = Neutral10

val md_theme_light_surface = Neutral100
val md_theme_light_onSurface = Neutral10
val md_theme_light_surfaceVariant = Neutral95
val md_theme_light_onSurfaceVariant = Neutral40
val md_theme_light_surfaceTint = Color.Transparent
val md_theme_light_inverseSurface = Neutral10
val md_theme_light_inverseOnSurface = Neutral95

val md_theme_light_error = ErrorLight
val md_theme_light_onError = Neutral100
val md_theme_light_errorContainer = ErrorLightContainer
val md_theme_light_onErrorContainer = Neutral10

val md_theme_light_outline = Neutral80
val md_theme_light_outlineVariant = Neutral90
val md_theme_light_scrim = AppScrim

val md_theme_light_surfaceBright = Neutral100
val md_theme_light_surfaceDim = Neutral90
val md_theme_light_surfaceContainer = Neutral98
val md_theme_light_surfaceContainerHigh = Neutral95
val md_theme_light_surfaceContainerHighest = Neutral90
val md_theme_light_surfaceContainerLow = Neutral100
val md_theme_light_surfaceContainerLowest = Neutral100

// Fixed roles intentionally stay visually stable across light/dark.
val md_theme_light_primaryFixed = Neutral90
val md_theme_light_primaryFixedDim = Neutral80
val md_theme_light_onPrimaryFixed = Neutral0
val md_theme_light_onPrimaryFixedVariant = Neutral20

val md_theme_light_secondaryFixed = Neutral90
val md_theme_light_secondaryFixedDim = Neutral80
val md_theme_light_onSecondaryFixed = Neutral0
val md_theme_light_onSecondaryFixedVariant = Neutral20

val md_theme_light_tertiaryFixed = Neutral90
val md_theme_light_tertiaryFixedDim = Neutral80
val md_theme_light_onTertiaryFixed = Neutral0
val md_theme_light_onTertiaryFixedVariant = Neutral20
