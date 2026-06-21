package ldv.shuuen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Quiet circular icon container — subtle fill, no border, monotone tint. */
@Composable
fun IconBubble(
  icon: ImageVector,
  modifier: Modifier = Modifier.Companion,
  tint: Color = ldv.shuuen.core.ui.components.ShuuenUi.Muted,
  background: Color = Color.White.copy(alpha = 0.06f),
  size: Dp = 58.dp,
) {
  Box(
    modifier = modifier.size(size).clip(CircleShape).background(background),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(size * 0.5f),
    )
  }
}
