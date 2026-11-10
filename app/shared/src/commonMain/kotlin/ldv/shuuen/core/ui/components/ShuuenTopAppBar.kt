package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ShuuenTopAppBarType(val height: Dp) {
  Simple(50.dp),
  Labeled(TopAppBarDefaults.TopAppBarExpandedHeight),
}

@Composable
fun ShuuenTopAppBar(
  modifier: Modifier = Modifier,
  title: String? = null,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  trailingIcon: ImageVector? = null,
  onTrailingClick: (() -> Unit)? = null,
  /** Passive status content (e.g. the MIDI-keyboard badge) shown before the trailing icon. */
  statusContent: @Composable () -> Unit = {},
  /** Extra action buttons, sitting between the status content and the trailing icon. */
  actions: @Composable RowScope.() -> Unit = {},
  type: ShuuenTopAppBarType = ShuuenTopAppBarType.Simple,
  /** Replaces the standard title/subtitle column when the app bar needs interactive title UI. */
  titleContent: (@Composable () -> Unit)? = null,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val compact = maxWidth < 430.dp
    val titleSize = if (compact) 25.sp else 32.sp

    CenterAlignedTopAppBar(
      expandedHeight = type.height,
      title = {
        if (titleContent != null) {
          titleContent()
        } else {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            if (title != null) {
              Text(
                text = title,
                color = ShuuenUi.Text,
                style =
                  MaterialTheme.typography.displayMedium.copy(
                    fontSize = titleSize,
                    lineHeight = if (compact) 31.sp else 38.sp,
                    letterSpacing = ShuuenUi.titlesSpacing,
                    fontWeight = FontWeight.SemiBold,
                  ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (subtitle != null) {
              Text(
                text = subtitle,
                color = ShuuenUi.Muted,
                style =
                  if (compact) MaterialTheme.typography.bodyMedium
                  else MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
      navigationIcon = {
        if (onBack != null) {
          CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
          )
        }
      },
      actions = {
        statusContent()
        actions()
        if (trailingIcon != null) {
          CircleIconButton(
            icon = trailingIcon,
            contentDescription = null,
            onClick = onTrailingClick ?: {},
          )
        }
      },
      colors =
        TopAppBarDefaults.topAppBarColors(
          containerColor = Color.Transparent,
          scrolledContainerColor = Color.Transparent,
          navigationIconContentColor = ShuuenUi.Text,
          titleContentColor = ShuuenUi.Text,
          actionIconContentColor = ShuuenUi.Text,
        ),
    )
  }
}
