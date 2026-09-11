package ldv.shuuen.features.training.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import ldv.shuuen.core.ui.components.CircleIconButton

/**
 * Top-bar button that rolls the level's channels onto other instruments out of the ones chosen in
 * settings. Hidden when there is only one choice everywhere — there would be nothing to roll to.
 */
@Composable
fun ShufflePresetsAction(visible: Boolean, onClick: () -> Unit) {
  if (!visible) return
  CircleIconButton(
    icon = Icons.Rounded.Shuffle,
    contentDescription = "Change instrument",
    onClick = onClick,
  )
}
