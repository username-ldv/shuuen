package ldv.shuuen.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * The three schedules a channel can re-roll its instrument on. A sheet rather than a row of pills:
 * the labels don't fit side by side on a phone, and each one wants a line of explanation anyway.
 *
 * [perNoteApplies] is true only for the Notes channel — nothing else plays its notes one at a
 * time, so offering "each note" there would be offering the same thing as "each question".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetShuffleSheet(
  channelLabel: String,
  selected: PresetShuffleMode,
  perNoteApplies: Boolean,
  onSelect: (PresetShuffleMode) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ShuuenUi.Surface,
    contentColor = ShuuenUi.Text,
    scrimColor = Color.Black.copy(alpha = 0.6f),
    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    dragHandle = { PickerDragHandle() },
  ) {
    ShuffleSheetContent(
      channelLabel = channelLabel,
      selected = selected,
      perNoteApplies = perNoteApplies,
      onSelect = onSelect,
    )
  }
}

@Composable
private fun ColumnScope.ShuffleSheetContent(
  channelLabel: String,
  selected: PresetShuffleMode,
  perNoteApplies: Boolean,
  onSelect: (PresetShuffleMode) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          Icons.Rounded.Shuffle,
          contentDescription = null,
          tint = ShuuenUi.Text,
          modifier = Modifier.size(22.dp),
        )
        Text(
          text = "CHANGE INSTRUMENT",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = ShuuenUi.titlesSpacing,
          ),
        )
      }
      Text(
        text = "How often the ${channelLabel.lowercase()} channel picks another of its instruments.",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      shuffleModeChoices(perNoteApplies).forEach { mode ->
        ShuffleModeRow(
          label = shuffleModeLabel(mode),
          description = shuffleModeDescription(mode),
          selected = mode == selected,
          onClick = { onSelect(mode) },
        )
      }
    }
  }
}

@Composable
private fun ShuffleModeRow(
  label: String,
  description: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(if (selected) ShuuenUi.Inverse else ShuuenUi.Ink.copy(alpha = 0.05f))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
      )
      Text(
        text = description,
        color = if (selected) ShuuenUi.OnInverse.copy(alpha = 0.7f) else ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    if (selected) {
      Icon(
        Icons.Rounded.Check,
        contentDescription = null,
        tint = ShuuenUi.OnInverse,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** The schedules on offer; only the melody notes of the Notes channel can use the per-note one. */
fun shuffleModeChoices(perNoteApplies: Boolean): List<PresetShuffleMode> =
  PresetShuffleMode.entries.filter { perNoteApplies || it != PresetShuffleMode.PerNote }

fun shuffleModeLabel(mode: PresetShuffleMode): String =
  when (mode) {
    PresetShuffleMode.PerLevel -> "Each level"
    PresetShuffleMode.PerQuestion -> "Each question"
    PresetShuffleMode.PerNote -> "Each note"
  }

private fun shuffleModeDescription(mode: PresetShuffleMode): String =
  when (mode) {
    PresetShuffleMode.PerLevel -> "One instrument, held until the level ends."
    PresetShuffleMode.PerQuestion -> "A different instrument on every question."
    PresetShuffleMode.PerNote ->
      "A different instrument on every melody note. Elsewhere this acts like each question."
  }

/**
 * What the settings row shows. A channel that cannot use the per-note schedule falls back to the
 * per-question one, so that is what it reports.
 */
fun shuffleModeSummary(mode: PresetShuffleMode, perNoteApplies: Boolean): String =
  shuffleModeLabel(
    if (mode == PresetShuffleMode.PerNote && !perNoteApplies) PresetShuffleMode.PerQuestion
    else mode
  )
