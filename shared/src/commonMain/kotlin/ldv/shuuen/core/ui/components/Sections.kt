package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Flat, typography-driven section header: a small uppercase label with an
 * optional supporting line and trailing slot. Replaces panel + icon-bubble
 * section titles.
 */
@Composable
fun SectionHeader(
  label: String,
  modifier: Modifier = Modifier,
  supporting: String? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = label,
        color = ldv.shuuen.core.ui.components.ShuuenUi.Muted,
        style = MaterialTheme.typography.labelLarge.copy(
          letterSpacing = ldv.shuuen.core.ui.components.ShuuenUi.labelSpacing,
          fontWeight = FontWeight.SemiBold,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (supporting != null) {
        Text(
          text = supporting,
          color = ldv.shuuen.core.ui.components.ShuuenUi.Dim,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailing != null) {
      Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
  }
}

/** 1px divider used between flat sections and list rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
  HorizontalDivider(modifier = modifier, thickness = 1.dp, color = ldv.shuuen.core.ui.components.ShuuenUi.Hairline)
}

/**
 * A flat content section: header, content, no card around it.
 * Sections are visually separated by spacing and hairlines at the call site.
 */
@Composable
fun FlatSection(
  label: String,
  modifier: Modifier = Modifier,
  supporting: String? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    ldv.shuuen.core.ui.components.SectionHeader(
      label = label,
      supporting = supporting,
      trailing = trailing
    )
    content()
  }
}
