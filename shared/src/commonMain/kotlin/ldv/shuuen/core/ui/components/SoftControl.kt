package ldv.shuuen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Borderless pill-ish control with a subtle fill.
 * Selection is shown by a brighter fill instead of a colored border.
 */
@Composable
fun SoftControl(
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
  content: @Composable RowScope.() -> Unit,
) {
  val shape = ShuuenUi.ControlShape
  Row(
    modifier = modifier.clip(shape)
      .background(if (selected) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.05f))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 10.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(
      space = 6.dp, alignment = Alignment.CenterHorizontally
    ),
    content = content,
  )
}
