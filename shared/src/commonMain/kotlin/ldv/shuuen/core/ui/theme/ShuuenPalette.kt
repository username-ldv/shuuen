package ldv.shuuen.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ldv.shuuen.core.settings.ThemeStyle

/**
 * The resolved monochrome token set behind `ShuuenUi`. One instance per theme
 * variant; components never pick a palette themselves — [LocalShuuenPalette]
 * carries the active one, chosen from settings in ShuuenTheme.
 */
@Immutable
data class ShuuenPalette(
  /** Full-bleed screen background. */
  val background: Color,
  // Typography / iconography
  val text: Color,
  val muted: Color,
  val dim: Color,
  // Borderless surfaces over the background
  val surface: Color,
  val surfaceHigh: Color,
  // Hairlines instead of borders
  val hairline: Color,
  val hairlineStrong: Color,
  // Inverted selection (opposite-extreme chip with flipped content)
  val inverse: Color,
  val onInverse: Color,
  /**
   * Base for translucent chrome — fills, tracks, and lines drawn as
   * `ink.copy(alpha = x)` over the background. White on dark variants,
   * black on light ones.
   */
  val ink: Color,
  /** True for the dark variants; lets components pick brightness-specific values. */
  val isDark: Boolean,
)

/** The original theme: pure black with occasional white. */
val MonoDarkPalette = ShuuenPalette(
  background = Color(0xFF000000),
  text = Color(0xFFF2F2F2),
  muted = Color(0xFF9A9AA0),
  dim = Color(0xFF606066),
  surface = Color(0xFF111113),
  surfaceHigh = Color(0xFF1A1A1C),
  hairline = Color.White.copy(alpha = 0.08f),
  hairlineStrong = Color.White.copy(alpha = 0.16f),
  inverse = Color(0xFFEDEDED),
  onInverse = Color(0xFF0A0A0A),
  ink = Color.White,
  isDark = true,
)

/** [MonoDarkPalette] mirrored: pure white with occasional black. */
val MonoLightPalette = ShuuenPalette(
  background = Color(0xFFFFFFFF),
  text = Color(0xFF0D0D0F),
  muted = Color(0xFF65656B),
  dim = Color(0xFF9F9FA5),
  surface = Color(0xFFECECEE),
  surfaceHigh = Color(0xFFE3E3E6),
  hairline = Color.Black.copy(alpha = 0.08f),
  hairlineStrong = Color.Black.copy(alpha = 0.16f),
  inverse = Color(0xFF121214),
  onInverse = Color(0xFFF5F5F5),
  ink = Color.Black,
  isDark = false,
)

/** Lower-contrast dark: black lifted towards grey, white dimmed towards grey. */
val SoftDarkPalette = ShuuenPalette(
  background = Color(0xFF1A1A1D),
  text = Color(0xFFD8D8DC),
  muted = Color(0xFF94949B),
  dim = Color(0xFF62626A),
  surface = Color(0xFF232327),
  surfaceHigh = Color(0xFF2B2B30),
  hairline = Color.White.copy(alpha = 0.08f),
  hairlineStrong = Color.White.copy(alpha = 0.16f),
  inverse = Color(0xFFD4D4D8),
  onInverse = Color(0xFF1A1A1D),
  ink = Color.White,
  isDark = true,
)

/** Lower-contrast light: white dimmed towards grey, black lifted towards grey. */
val SoftLightPalette = ShuuenPalette(
  background = Color(0xFFE6E6E9),
  text = Color(0xFF303035),
  muted = Color(0xFF6B6B73),
  dim = Color(0xFF9A9AA0),
  surface = Color(0xFFDCDCE0),
  surfaceHigh = Color(0xFFD2D2D7),
  hairline = Color.Black.copy(alpha = 0.08f),
  hairlineStrong = Color.Black.copy(alpha = 0.16f),
  inverse = Color(0xFF3A3A40),
  onInverse = Color(0xFFE9E9EC),
  ink = Color.Black,
  isDark = false,
)

fun shuuenPalette(style: ThemeStyle, darkTheme: Boolean): ShuuenPalette =
  when (style) {
    ThemeStyle.Mono -> if (darkTheme) MonoDarkPalette else MonoLightPalette
    ThemeStyle.MonoSoft -> if (darkTheme) SoftDarkPalette else SoftLightPalette
  }

val LocalShuuenPalette = staticCompositionLocalOf { MonoDarkPalette }
