package ldv.shuuen.features.training.melodies.level_select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard

/**
 * Minimal level-select for Melodies. There is no persistence for melody levels yet, so this only
 * offers "create new" plus an empty-state hint; saved melody levels are a future task.
 */
@Composable
fun MelodiesLevelSelectScreen(
  onNavigateBack: () -> Unit,
  onCreateNewLevel: () -> Unit,
) {
  StaticScreenFrame(
    topBar = {
      ShuuenTopAppBar(
        title = "LEVEL SELECT",
        subtitle = "Transcribe melodies from MIDI or random sequences.",
        onBack = onNavigateBack,
        type = ShuuenTopAppBarType.Labeled,
      )
    },
  ) {
    PrimaryCta(
      text = "CREATE NEW",
      icon = Icons.Rounded.Create,
      onClick = onCreateNewLevel,
      modifier = Modifier.padding(top = 8.dp),
    )

    SurfaceCard(verticalSpacing = Arrangement.spacedBy(6.dp)) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "No saved melody levels yet",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "Create a new level to load a .midi file and start transcribing.",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}
