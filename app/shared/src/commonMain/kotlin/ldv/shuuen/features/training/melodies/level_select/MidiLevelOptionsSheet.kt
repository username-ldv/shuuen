package ldv.shuuen.features.training.melodies.level_select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ldv.shuuen.core.music.MaximumMidiTransposition
import ldv.shuuen.core.music.MidiTransposition
import ldv.shuuen.core.music.MidiTranspositionMode
import ldv.shuuen.core.music.MinimumMidiTransposition
import ldv.shuuen.core.settings.MidiLevelOptions
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.components.LevelSettingsSheet

/**
 * Level settings for an imported MIDI melody. Transposition and context are one shared setting
 * ([MidiLevelOptions]): starting the level saves them, and every later MIDI level — including
 * "next" and "retry" from the results screen — plays with them until they are changed here.
 */
@Composable
internal fun MidiLevelOptionsSheet(
  levelName: String,
  levelReference: String,
  hasKey: Boolean,
  options: MidiLevelOptions,
  stats: LevelAccuracyStats,
  onOpenContext: () -> Unit,
  onClearContext: () -> Unit,
  onStart: (MidiLevelOptions) -> Unit,
  onDeleteLastPlayStatistics: () -> Unit,
  onDeleteAllStatistics: () -> Unit,
  onDismiss: () -> Unit,
) {
  var mode by rememberSaveable(levelReference) { mutableStateOf(options.transposition.mode) }
  var semitones by rememberSaveable(levelReference) { mutableIntStateOf(options.transposition.semitones) }

  LevelSettingsSheet(
    levelName = levelName,
    hasStatistics = stats.games > 0,
    onDeleteLastPlayStatistics = onDeleteLastPlayStatistics,
    onDeleteAllStatistics = onDeleteAllStatistics,
    onDismiss = onDismiss,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      SectionLabel("TRANSPOSITION")
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
          text = "Picks any value from −6 to +6 when a level starts, including the original.",
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      SectionLabel("CONTEXT")
      val context = options.context
      if (!hasKey) {
        Text(
          text =
            "This melody has no labelled key, so no context can frame it. Label its key on the " +
              "website to enable one.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      } else {
        Surface(
          onClick = onOpenContext,
          modifier = Modifier.fillMaxWidth(),
          color = ShuuenUi.Ink.copy(alpha = 0.05f),
          contentColor = ShuuenUi.Text,
          shape = ShuuenUi.ControlShape,
        ) {
          Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = context?.let { it.name ?: it.id } ?: "No context",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text =
                  if (context != null) "Shared by every MIDI level. Tap to change."
                  else "Tap to pick a drone or cadence played from the melody's key.",
                color = ShuuenUi.Dim,
                style = MaterialTheme.typography.bodySmall,
              )
            }
            if (context != null) {
              IconButton(onClick = onClearContext) {
                Icon(
                  Icons.Rounded.Close,
                  contentDescription = "Remove context",
                  tint = ShuuenUi.Muted,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }

    PrimaryCta(
      text = "START LEVEL",
      onClick = {
        onStart(
          options.copy(transposition = MidiTransposition(mode = mode, semitones = semitones))
        )
      },
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    color = ShuuenUi.Muted,
    style =
      MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = ShuuenUi.titlesSpacing,
      ),
  )
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
