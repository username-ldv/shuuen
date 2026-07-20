package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * Round soft icon button sized for the fifths circle's empty middle, where the play screens keep
 * their repeat/rewind controls when the circle input is active.
 */
@Composable
fun CircleCenterIconButton(
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = ShuuenUi.Text,
  onClick: () -> Unit,
) {
  Box(
    modifier = modifier
      .size(52.dp)
      .clip(CircleShape)
      .background(ShuuenUi.Ink.copy(alpha = 0.05f))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(24.dp),
    )
  }
}
