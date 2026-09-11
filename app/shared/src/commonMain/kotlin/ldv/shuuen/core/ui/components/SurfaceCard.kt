package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Borderless card with a subtle fill. Reserved for genuinely tappable items
 * (menu rows, level cards); static content should use flat sections instead.
 */
@Composable
fun SurfaceCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  color: Color = ldv.shuuen.core.ui.components.ShuuenUi.Surface,
  contentPadding: PaddingValues = PaddingValues(18.dp),
  verticalSpacing: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = MaterialTheme.shapes.large
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      color = color,
      contentColor = ldv.shuuen.core.ui.components.ShuuenUi.Text,
      shape = shape,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Column(
        modifier = Modifier.padding(contentPadding),
        verticalArrangement = verticalSpacing,
        content = content,
      )
    }
  } else {
    Surface(
      modifier = modifier.fillMaxWidth(),
      color = color,
      contentColor = ldv.shuuen.core.ui.components.ShuuenUi.Text,
      shape = shape,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Column(
        modifier = Modifier.padding(contentPadding),
        verticalArrangement = verticalSpacing,
        content = content,
      )
    }
  }
}
