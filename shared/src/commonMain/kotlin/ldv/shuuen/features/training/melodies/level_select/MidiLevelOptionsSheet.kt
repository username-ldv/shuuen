package ldv.shuuen.features.training.melodies.level_select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.components.LevelSettingsSheet
import ldv.shuuen.features.training.melodies.domain.MaximumMidiTransposition
import ldv.shuuen.features.training.melodies.domain.MidiTransposition
import ldv.shuuen.features.training.melodies.domain.MidiTranspositionMode
import ldv.shuuen.features.training.melodies.domain.MinimumMidiTransposition

@Composable
internal fun MidiLevelOptionsSheet(
  levelName: String,
  levelReference: String,
  stats: LevelAccuracyStats,
  onStart: (MidiTransposition) -> Unit,
  onDeleteLastPlayStatistics: () -> Unit,
  onDeleteAllStatistics: () -> Unit,
  onDismiss: () -> Unit,
) {
  var mode by rememberSaveable(levelReference) { mutableStateOf(MidiTranspositionMode.Defined) }
  var semitones by rememberSaveable(levelReference) { mutableIntStateOf(0) }

  LevelSettingsSheet(
    levelName = levelName,
    hasStatistics = stats.games > 0,
    onDeleteLastPlayStatistics = onDeleteLastPlayStatistics,
    onDeleteAllStatistics = onDeleteAllStatistics,
    onDismiss = onDismiss,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = "TRANSPOSITION",
        color = ShuuenUi.Muted,
        style =
          MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = ShuuenUi.titlesSpacing,
          ),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TranspositionModeChoice(
          label = "Random",
          icon = Icons.Rounded.Casino,
          selected = mode == MidiTranspositionMode.Random,
          onClick = { mode = MidiTranspositionMode.Random },
          modifier = Modifier.weight(1f),
        )
        TranspositionModeChoice(
          label = "Defined",
          icon = Icons.Rounded.Tune,
          selected = mode == MidiTranspositionMode.Defined,
          onClick = { mode = MidiTranspositionMode.Defined },
          modifier = Modifier.weight(1f),
        )
      }

      if (mode == MidiTranspositionMode.Random) {
        Text(
          text = "Picks any value from −6 to +6 when the level starts, including the original.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = transpositionLabel(semitones),
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("−6", color = ShuuenUi.Dim, style = MaterialTheme.typography.labelMedium)
            Slider(
              value = semitones.toFloat(),
              onValueChange = {
                semitones =
                  it.roundToInt().coerceIn(
                    MinimumMidiTransposition,
                    MaximumMidiTransposition,
                  )
              },
              valueRange = MinimumMidiTransposition.toFloat()..MaximumMidiTransposition.toFloat(),
              steps = MaximumMidiTransposition - MinimumMidiTransposition - 1,
              modifier = Modifier.weight(1f),
            )
            Text("+6", color = ShuuenUi.Dim, style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }

    PrimaryCta(
      text = "START LEVEL",
      onClick = {
        onStart(
          MidiTransposition(
            mode = mode,
            semitones = semitones,
          )
        )
      },
    )
  }
}

@Composable
private fun TranspositionModeChoice(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier,
    color = if (selected) ShuuenUi.Inverse else ShuuenUi.Ink.copy(alpha = 0.05f),
    contentColor = if (selected) ShuuenUi.OnInverse else ShuuenUi.Text,
    shape = ShuuenUi.ControlShape,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      )
    }
  }
}

private fun transpositionLabel(semitones: Int): String =
  when (semitones) {
    0 -> "Original pitch"
    1 -> "+1 semitone"
    -1 -> "−1 semitone"
    in 2..MaximumMidiTransposition -> "+$semitones semitones"
    else -> "−${-semitones} semitones"
  }
