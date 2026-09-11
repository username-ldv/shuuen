package ldv.shuuen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Selectable chip. Selected state inverts to a white pill with black content;
 * unselected stays a quiet translucent fill.
 */
@Composable
fun PillControl(
  text: String,
  modifier: Modifier = Modifier.Companion,
  selected: Boolean = false,
  leadingIcon: ImageVector? = null,
  trailingCheck: Boolean = false,
  /** Shown at the end whatever the selection; a chevron marks a pill that opens a picker. */
  trailingIcon: ImageVector? = null,
  /**
   * Whether the label stretches to the pill's width, pushing any trailing icon to the far edge.
   * Off makes the pill only as wide as its content — necessary beside a flexible sibling, which
   * is measured after the pill and would otherwise be left with nothing.
   */
  fillLabel: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val shape = ldv.shuuen.core.ui.components.ShuuenUi.ControlShape
  val contentColor = if (selected) ldv.shuuen.core.ui.components.ShuuenUi.OnInverse else ldv.shuuen.core.ui.components.ShuuenUi.Muted
  Row(
    modifier = modifier.clip(shape)
      .background(if (selected) ldv.shuuen.core.ui.components.ShuuenUi.Inverse else ldv.shuuen.core.ui.components.ShuuenUi.Ink.copy(alpha = 0.05f))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.Companion)
      .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (leadingIcon != null) {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(20.dp),
      )
    }
    Text(
      text = text,
      color = contentColor,
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.weight(1f, fill = fillLabel),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (trailingCheck && selected) {
      Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
    }
    if (trailingIcon != null) {
      Icon(
        imageVector = trailingIcon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
