package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The single inverted (white) primary action on a screen. */
@Composable
fun PrimaryCta(
  text: String,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Rounded.PlayArrow,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().height(60.dp),
    color = ldv.shuuen.core.ui.components.ShuuenUi.Inverse,
    contentColor = ldv.shuuen.core.ui.components.ShuuenUi.OnInverse,
    shape = ldv.shuuen.core.ui.components.ShuuenUi.PillShape,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.CenterHorizontally),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
          letterSpacing = 4.sp,
          fontWeight = FontWeight.Bold,
        ),
      )
    }
  }
}
