package ldv.shuuen.features.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ldv.shuuen.core.audio.midi.FullPresetVolume
import ldv.shuuen.core.audio.midi.MaximumPresetCutoff
import ldv.shuuen.core.audio.midi.NeutralPresetCutoff
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetCutoffScope
import ldv.shuuen.core.audio.midi.PresetCutoffs
import ldv.shuuen.core.audio.midi.PresetVolumes
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * Combined soundbank + preset picker. The soundbank chips scope a searchable,
 * scrollable preset list; tapping a preset adds it to (or drops it from) the channel's choices,
 * which take effect immediately. One sheet handles both dimensions because a preset is only
 * meaningful as a (bank, id) pair.
 *
 * Every row also carries its instrument's own settings: a loudness trim and optional brightness
 * compensation for velocity-dependent SoundFont filtering. They are editable on sliders the row
 * unfolds and auditionable on the spot. The settings belong to the preset — they are kept even
 * when it is not chosen and are independent of how many presets are selected.
 *
 * [selectedPresets] is never empty and its first entry is the channel's base instrument; the last
 * remaining choice cannot be dropped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPickerSheet(
  title: String,
  icon: ImageVector,
  soundbanks: List<Soundbank>,
  selectedPresets: List<Preset>,
  presetVolumes: PresetVolumes,
  presetCutoffs: PresetCutoffs,
  onTogglePreset: (Preset) -> Unit,
  onPreviewPreset: (Preset) -> Unit,
  onPresetVolumeChange: (Preset, Int) -> Unit,
  onPresetVolumeCommit: (Preset, Int) -> Unit,
  onPresetCutoffChange: (Preset, Int) -> Unit,
  onPresetCutoffCommit: (Preset, Int) -> Unit,
  onPresetCutoffScopeChange: (Preset, PresetCutoffScope) -> Unit,
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
      selectedPresets = selectedPresets,
      presetVolumes = presetVolumes,
      presetCutoffs = presetCutoffs,
      onTogglePreset = onTogglePreset,
      onPreviewPreset = onPreviewPreset,
      onPresetVolumeChange = onPresetVolumeChange,
      onPresetVolumeCommit = onPresetVolumeCommit,
      onPresetCutoffChange = onPresetCutoffChange,
      onPresetCutoffCommit = onPresetCutoffCommit,
      onPresetCutoffScopeChange = onPresetCutoffScopeChange,
    )
  }
}

@Composable
private fun ColumnScope.PresetPickerContent(
  title: String,
  icon: ImageVector,
  soundbanks: List<Soundbank>,
  selectedPresets: List<Preset>,
  presetVolumes: PresetVolumes,
  presetCutoffs: PresetCutoffs,
  onTogglePreset: (Preset) -> Unit,
  onPreviewPreset: (Preset) -> Unit,
  onPresetVolumeChange: (Preset, Int) -> Unit,
  onPresetVolumeCommit: (Preset, Int) -> Unit,
  onPresetCutoffChange: (Preset, Int) -> Unit,
  onPresetCutoffCommit: (Preset, Int) -> Unit,
  onPresetCutoffScopeChange: (Preset, PresetCutoffScope) -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  // At most one row shows its preset settings; the rest stay compact. Keyed by packed preset so it
  // survives the list scrolling the row out of view.
  var expandedPreset: Int? by rememberSaveable { mutableStateOf(null) }
  var selectedBank: Int? by rememberSaveable {
    mutableStateOf(
      selectedPresets.firstOrNull()?.bank?.takeIf { bank -> soundbanks.any { it.bank == bank } }
        ?: soundbanks.firstOrNull()?.bank,
    )
  }
  val selectedPacked = remember(selectedPresets) { selectedPresets.map { it.toPacked() }.toSet() }

  // Which presets ride at the top — snapshotted when the sheet opens, and refreshed only by the
  // re-sort button. Choosing one must not slide the list out from under the finger that tapped it.
  var pinned by remember(title) { mutableStateOf(selectedPacked) }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  val visiblePresets = remember(query, selectedBank, soundbanks, pinned) {
    val scoped = when (val bank = selectedBank) {
      null -> soundbanks.flatMap { it.presets }
      else -> soundbanks.firstOrNull { it.bank == bank }?.presets.orEmpty()
    }
    val trimmed = query.trim()
    val matching =
      if (trimmed.isEmpty()) {
        scoped
      } else {
        scoped.filter { preset ->
          presetName(preset).contains(trimmed, ignoreCase = true) ||
            presetNumber(preset).contains(trimmed) ||
            preset.id.toString() == trimmed
        }
      }
    // A stable sort on nothing but "is it chosen", so both halves keep the soundbank's own order:
    // the chosen ones rise to the top still in ascending preset number, and one dropped here lands
    // back in its numbered slot.
    matching.sortedBy { if (it.toPacked() in pinned) 0 else 1 }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .imePadding()
      .padding(horizontal = 20.dp)
      .padding(bottom = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        HeaderIconButton(
          icon = Icons.AutoMirrored.Rounded.Sort,
          contentDescription = "Move the chosen presets back to the top",
          onClick = {
            pinned = selectedPacked
            scope.launch { listState.animateScrollToItem(0) }
          },
        )
      }
      Text(
        text =
          if (selectedPresets.size > 1) {
            "${selectedPresets.size} chosen — a level picks among them."
          } else {
            "Tap more presets to let a level pick among them."
          },
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
      )
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
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(visiblePresets, key = { it.toPacked() }) { preset ->
          val packed = preset.toPacked()
          PresetRow(
            number = presetNumber(preset),
            name = presetName(preset),
            selected = packed in selectedPacked,
            volumePercent = presetVolumes.forPreset(preset),
            cutoff = presetCutoffs.forPreset(preset) ?: NeutralPresetCutoff,
            cutoffScope = presetCutoffs.scopeForPreset(preset),
            settingsExpanded = expandedPreset == packed,
            onClick = { onTogglePreset(preset) },
            onToggleSettings = { expandedPreset = if (expandedPreset == packed) null else packed },
            onPreview = { onPreviewPreset(preset) },
            onVolumeChange = { onPresetVolumeChange(preset, it) },
            onVolumeCommit = { onPresetVolumeCommit(preset, it) },
            onCutoffChange = { onPresetCutoffChange(preset, it) },
            onCutoffCommit = { onPresetCutoffCommit(preset, it) },
            onCutoffScopeChange = { onPresetCutoffScopeChange(preset, it) },
          )
        }
      }
    }
  }
}

/** Shared by the picker sheets in this package. */
@Composable
internal fun PickerDragHandle() {
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
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(ShuuenUi.Ink.copy(alpha = 0.05f))
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
      .background(if (selected) ShuuenUi.Inverse else ShuuenUi.Ink.copy(alpha = 0.05f))
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

/**
 * One preset: tapping the row chooses it, while the trailing controls belong to the instrument's
 * settings and are live whether or not the preset is chosen. The sliders only appear once
 * [settingsExpanded] — a list this long has no room to show them per row.
 */
@Composable
private fun PresetRow(
  number: String,
  name: String,
  selected: Boolean,
  volumePercent: Int,
  cutoff: Int,
  cutoffScope: PresetCutoffScope,
  settingsExpanded: Boolean,
  onClick: () -> Unit,
  onToggleSettings: () -> Unit,
  onPreview: () -> Unit,
  onVolumeChange: (Int) -> Unit,
  onVolumeCommit: (Int) -> Unit,
  onCutoffChange: (Int) -> Unit,
  onCutoffCommit: (Int) -> Unit,
  onCutoffScopeChange: (PresetCutoffScope) -> Unit,
) {
  val content = if (selected) ShuuenUi.OnInverse else ShuuenUi.Text
  val quiet = if (selected) ShuuenUi.OnInverse.copy(alpha = 0.55f) else ShuuenUi.Dim

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(if (selected) ShuuenUi.Inverse else ShuuenUi.Ink.copy(alpha = 0.05f)),
  ) {
    // Instrument names are long and the trailing controls are fixed, so the row gives them every
    // dp it can: tight gaps, no check mark (the inverted fill already says "chosen"), and a
    // second line where a name still won't fit on one.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = number,
        color = quiet,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.widthIn(min = 26.dp),
      )
      Text(
        text = name,
        color = content,
        style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "$volumePercent%",
        color = if (volumePercent == FullPresetVolume) quiet else content,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(min = 30.dp),
      )
      // The two buttons read as one control cluster, so they sit closer than the row's own gap.
      Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        RowIconButton(
          icon = if (settingsExpanded) Icons.Rounded.Close else Icons.Rounded.Settings,
          contentDescription = if (settingsExpanded) "Close settings" else "Settings for $name",
          tint = content,
          onClick = onToggleSettings,
        )
        RowIconButton(
          icon = Icons.Rounded.PlayArrow,
          contentDescription = "Preview $name",
          tint = content,
          onClick = onPreview,
        )
      }
    }

    AnimatedVisibility(settingsExpanded) {
      Column {
        PresetVolumeSlider(
          percent = volumePercent,
          tint = content,
          onChange = onVolumeChange,
          onCommit = onVolumeCommit,
        )
        PresetCutoffSlider(
          cutoff = cutoff,
          tint = content,
          onChange = onCutoffChange,
          onCommit = onCutoffCommit,
        )
        PresetCutoffScopeSelector(
          scope = cutoffScope,
          tint = content,
          onSelect = onCutoffScopeChange,
        )
      }
    }
  }
}

/** Icon-only action beside the sheet's title. */
@Composable
private fun HeaderIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(36.dp)
      .clip(ShuuenUi.PillShape)
      .background(ShuuenUi.Ink.copy(alpha = 0.06f))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      icon,
      contentDescription = contentDescription,
      tint = ShuuenUi.Text,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun RowIconButton(
  icon: ImageVector,
  contentDescription: String,
  tint: Color,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
  }
}

/**
 * The trim slider a row unfolds. Local state drives the thumb so a drag isn't fought by the
 * incoming value, which the ViewModel updates on every step to keep the row's label in step.
 */
@Composable
private fun PresetVolumeSlider(
  percent: Int,
  tint: Color,
  onChange: (Int) -> Unit,
  onCommit: (Int) -> Unit,
) {
  var value by remember { mutableFloatStateOf(percent.toFloat()) }
  var dragging by remember { mutableStateOf(false) }
  LaunchedEffect(percent) { if (!dragging) value = percent.toFloat() }

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = "VOLUME",
      color = tint.copy(alpha = 0.55f),
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
    Slider(
      value = value,
      onValueChange = {
        dragging = true
        value = it.coerceIn(0f, FullPresetVolume.toFloat())
        onChange(value.roundToInt())
      },
      onValueChangeFinished = {
        dragging = false
        onCommit(value.roundToInt())
      },
      valueRange = 0f..FullPresetVolume.toFloat(),
      colors = SliderDefaults.colors(
        thumbColor = tint,
        activeTrackColor = tint.copy(alpha = 0.75f),
        inactiveTrackColor = tint.copy(alpha = 0.18f),
      ),
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * Optional CC74 compensation for a preset's velocity-dependent low-pass filter. The neutral end
 * is displayed as OFF because it removes the persisted override rather than storing a value.
 */
@Composable
private fun PresetCutoffSlider(
  cutoff: Int,
  tint: Color,
  onChange: (Int) -> Unit,
  onCommit: (Int) -> Unit,
) {
  var value by remember { mutableFloatStateOf(cutoff.toFloat()) }
  var dragging by remember { mutableStateOf(false) }
  LaunchedEffect(cutoff) { if (!dragging) value = cutoff.toFloat() }

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = "BRIGHTNESS",
      color = tint.copy(alpha = 0.55f),
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
    Slider(
      value = value,
      onValueChange = {
        dragging = true
        value = it.coerceIn(NeutralPresetCutoff.toFloat(), MaximumPresetCutoff.toFloat())
        onChange(value.roundToInt())
      },
      onValueChangeFinished = {
        dragging = false
        onCommit(value.roundToInt())
      },
      valueRange = NeutralPresetCutoff.toFloat()..MaximumPresetCutoff.toFloat(),
      colors = SliderDefaults.colors(
        thumbColor = tint,
        activeTrackColor = tint.copy(alpha = 0.75f),
        inactiveTrackColor = tint.copy(alpha = 0.18f),
      ),
      modifier = Modifier.weight(1f),
    )
    Text(
      text = if (value.roundToInt() == NeutralPresetCutoff) "OFF" else value.roundToInt().toString(),
      color = tint.copy(alpha = 0.7f),
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.End,
      modifier = Modifier.widthIn(min = 24.dp),
    )
  }
}

@Composable
private fun PresetCutoffScopeSelector(
  scope: PresetCutoffScope,
  tint: Color,
  onSelect: (PresetCutoffScope) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "SCOPE",
      color = tint.copy(alpha = 0.55f),
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
    PresetCutoffScopeOption(
      label = "ORIGINAL VELOCITY",
      selected = scope == PresetCutoffScope.OriginalVelocityMelodies,
      tint = tint,
      onClick = { onSelect(PresetCutoffScope.OriginalVelocityMelodies) },
    )
    PresetCutoffScopeOption(
      label = "EVERYWHERE",
      selected = scope == PresetCutoffScope.AllPlayback,
      tint = tint,
      onClick = { onSelect(PresetCutoffScope.AllPlayback) },
    )
  }
}

@Composable
private fun PresetCutoffScopeOption(
  label: String,
  selected: Boolean,
  tint: Color,
  onClick: () -> Unit,
) {
  Text(
    text = label,
    color = if (selected) tint else tint.copy(alpha = 0.55f),
    style = MaterialTheme.typography.labelSmall.copy(
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    ),
    modifier = Modifier
      .clip(ShuuenUi.PillShape)
      .background(if (selected) tint.copy(alpha = 0.14f) else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = 9.dp, vertical = 6.dp),
  )
}
