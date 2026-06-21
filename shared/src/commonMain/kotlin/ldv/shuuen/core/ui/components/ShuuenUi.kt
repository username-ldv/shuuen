package ldv.shuuen.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for the monotone look: neutrals carry the whole UI and
 * selection/primary states are rendered by inversion (white fill, black content).
 * [Correct]/[Incorrect] answer feedback is the only colored exception.
 */
object ShuuenUi {
  // Typography / iconography
  val Text = Color(0xFFF2F2F2)
  val Muted = Color(0xFF9A9AA0)
  val Dim = Color(0xFF606066)

  // Borderless surfaces over the pure black background
  val Surface = Color(0xFF111113)
  val SurfaceHigh = Color(0xFF1A1A1C)

  // Hairlines instead of borders
  val Hairline = Color.White.copy(alpha = 0.08f)
  val HairlineStrong = Color.White.copy(alpha = 0.16f)

  // Inverted selection (white chip, black content)
  val Inverse = Color(0xFFEDEDED)
  val OnInverse = Color(0xFF0A0A0A)

  // Answer feedback — the only colored exception to the monotone rule
  val Correct = Color(0xFF52E58A)
  val Incorrect = Color(0xFFFF5B57)

  val PillShape = RoundedCornerShape(50)
  val ControlShape = RoundedCornerShape(10.dp)

  val titlesSpacing = 2.sp
  val labelSpacing = 2.5.sp
}
