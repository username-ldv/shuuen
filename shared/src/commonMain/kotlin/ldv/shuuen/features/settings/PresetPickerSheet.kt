package ldv.shuuen.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * Combined soundbank + preset picker. The soundbank chips scope a searchable,
 * scrollable preset list; tapping a preset applies it immediately. One sheet
 * handles both dimensions because a preset is only meaningful as a (bank, id) pair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPickerSheet(
  title: String,
  icon: ImageVector,
  soundbanks: List<Soundbank>,
  selectedPreset: Preset,
  onSelectPreset: (Preset) -> Unit,
  onPreview: () -> Unit,
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
    PresetPickerContent(
      title = title,
      icon = icon,
      soundbanks = soundbanks,
      selectedPreset = selectedPreset,
      onSelectPreset = onSelectPreset,
      onPreview = onPreview,
    )
  }
}

@Composable
private fun ColumnScope.PresetPickerContent(
  title: String,
  icon: ImageVector,
  soundbanks: List<Soundbank>,
  selectedPreset: Preset,
  onSelectPreset: (Preset) -> Unit,
  onPreview: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  var selectedBank: Int? by rememberSaveable {
    mutableStateOf(
      selectedPreset.bank.takeIf { bank -> soundbanks.any { it.bank == bank } }
        ?: soundbanks.firstOrNull()?.bank,
    )
  }

  val visiblePresets = remember(query, selectedBank, soundbanks) {
    val scoped = when (val bank = selectedBank) {
      null -> soundbanks.flatMap { it.presets }
      else -> soundbanks.firstOrNull { it.bank == bank }?.presets.orEmpty()
    }
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      scoped
    } else {
      scoped.filter { preset ->
        presetName(preset).contains(trimmed, ignoreCase = true) ||
          presetNumber(preset).contains(trimmed) ||
          preset.id.toString() == trimmed
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .imePadding()
      .padding(horizontal = 20.dp)
      .padding(bottom = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(icon, contentDescription = null, tint = ShuuenUi.Text, modifier = Modifier.size(22.dp))
      Text(
        text = title.uppercase(),
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.SemiBold,
          letterSpacing = ShuuenUi.titlesSpacing,
        ),
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      PreviewButton(onClick = onPreview)
    }

    SearchField(query = query, onQueryChange = { query = it })

    if (soundbanks.size > 1) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          BankChip(label = "All", selected = selectedBank == null) { selectedBank = null }
        }
        items(soundbanks, key = { it.bank }) { bank ->
          BankChip(label = bank.label, selected = selectedBank == bank.bank) {
            selectedBank = bank.bank
          }
        }
      }
    }

    if (visiblePresets.isEmpty()) {
      Text(
        text = if (soundbanks.isEmpty()) "No presets available." else "No presets match \"$query\".",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(visiblePresets, key = { it.toPacked() }) { preset ->
          val selected = preset.bank == selectedPreset.bank && preset.id == selectedPreset.id
          PresetRow(
            number = presetNumber(preset),
            name = presetName(preset),
            selected = selected,
            onClick = { onSelectPreset(preset) },
          )
        }
      }
    }
  }
}

@Composable
private fun PickerDragHandle() {
  Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .size(width = 36.dp, height = 4.dp)
        .clip(RoundedCornerShape(50))
        .background(ShuuenUi.HairlineStrong),
    )
  }
}

@Composable
private fun PreviewButton(onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .clip(ShuuenUi.PillShape)
      .background(Color.White.copy(alpha = 0.06f))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      Icons.Rounded.PlayArrow,
      contentDescription = null,
      tint = ShuuenUi.Text,
      modifier = Modifier.size(18.dp),
    )
    Text("Preview", color = ShuuenUi.Muted, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(Color.White.copy(alpha = 0.05f))
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      Icons.Rounded.Search,
      contentDescription = null,
      tint = ShuuenUi.Muted,
      modifier = Modifier.size(18.dp),
    )
    Box(modifier = Modifier.weight(1f)) {
      if (query.isEmpty()) {
        Text(
          "Search presets…",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.titleSmall,
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = ShuuenUi.Text),
        cursorBrush = SolidColor(ShuuenUi.Text),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (query.isNotEmpty()) {
      Icon(
        Icons.Rounded.Close,
        contentDescription = "Clear search",
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(18.dp).clip(ShuuenUi.PillShape).clickable { onQueryChange("") },
      )
    }
  }
}

@Composable
private fun BankChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .clip(ShuuenUi.PillShape)
      .background(if (selected) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
    )
  }
}

@Composable
private fun PresetRow(
  number: String,
  name: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(if (selected) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = number,
      color = if (selected) ShuuenUi.OnInverse.copy(alpha = 0.55f) else ShuuenUi.Dim,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.widthIn(min = 30.dp),
    )
    Text(
      text = name,
      color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Text,
      style = MaterialTheme.typography.titleSmall.copy(
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      ),
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
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
