package ldv.shuuen.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ldv.shuuen.core.ui.theme.LocalShuuenPalette

/**
 * Design tokens for the monotone look: neutrals carry the whole UI and
 * selection/primary states are rendered by inversion (background-opposite fill,
 * flipped content). [Correct]/[Incorrect] answer feedback is the only colored
 * exception. The color values resolve against the active theme variant via
 * [LocalShuuenPalette], so the same token reads correctly in every theme.
 */
object ShuuenUi {
  // Typography / iconography
  val Text: Color @Composable get() = LocalShuuenPalette.current.text
  val Muted: Color @Composable get() = LocalShuuenPalette.current.muted
  val Dim: Color @Composable get() = LocalShuuenPalette.current.dim

  // Borderless surfaces over the background
  val Surface: Color @Composable get() = LocalShuuenPalette.current.surface
  val SurfaceHigh: Color @Composable get() = LocalShuuenPalette.current.surfaceHigh

  // Hairlines instead of borders
  val Hairline: Color @Composable get() = LocalShuuenPalette.current.hairline
  val HairlineStrong: Color @Composable get() = LocalShuuenPalette.current.hairlineStrong

  // Inverted selection (background-opposite chip, flipped content)
  val Inverse: Color @Composable get() = LocalShuuenPalette.current.inverse
  val OnInverse: Color @Composable get() = LocalShuuenPalette.current.onInverse

  /**
   * Base for translucent chrome drawn as `Ink.copy(alpha = x)` over the
   * background: white on dark variants, black on light ones.
   */
  val Ink: Color @Composable get() = LocalShuuenPalette.current.ink

  /** True while a dark variant is active; for brightness-specific values. */
  val IsDark: Boolean @Composable get() = LocalShuuenPalette.current.isDark

  // Answer feedback — the only colored exception to the monotone rule.
  // Theme independent (also read from non-composable contexts).
  val Correct = Color(0xFF52E58A)
  val Incorrect = Color(0xFFFF5B57)

  val PillShape = RoundedCornerShape(50)
  val ControlShape = RoundedCornerShape(10.dp)

  val titlesSpacing = 2.sp
  val labelSpacing = 2.5.sp
}
