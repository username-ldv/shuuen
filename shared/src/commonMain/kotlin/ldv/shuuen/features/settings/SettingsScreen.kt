package ldv.shuuen.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.music.effectiveDegreeNames
import ldv.shuuen.core.music.effectiveNoteNames
import ldv.shuuen.core.settings.InputComponent
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MaxLevelStatsWindow
import ldv.shuuen.core.settings.MinLevelStatsWindow
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.ThemeAppearance
import ldv.shuuen.core.settings.ThemeSettings
import ldv.shuuen.core.settings.ThemeStyle
import ldv.shuuen.core.ui.components.BackendStatusBadge
import ldv.shuuen.core.ui.components.BackendStatusIcon
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.IconBubble
import ldv.shuuen.core.ui.components.MidiKeyboardBadge
import ldv.shuuen.core.ui.components.PillControl
import ldv.shuuen.core.ui.components.ShuuenSwitch
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.label

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onNavigateBack: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  StaticScreenFrame(
    maxWidth = 920.dp,
    topBar = {
      ShuuenTopAppBar(
        title = "SETTINGS",
        onBack = onNavigateBack,
        trailingIcon = Icons.Rounded.Tune,
        statusContent = {
          BackendStatusBadge()
          MidiKeyboardBadge()
        },
        type = ShuuenTopAppBarType.Simple
      )
    },
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val twoColumn = maxWidth > 760.dp

      if (twoColumn) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(44.dp),
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(13.dp),
          ) {
            InputMethodSection(
              selected = state.inputMethod,
              onSelect = { viewModel.onAction(SettingsAction.SelectInputMethod(it)) },
            )
            Hairline()
            MidiKeyboardSection(state = state, onAction = viewModel::onAction)
            Hairline()
            ThemeSection(
              theme = state.theme,
              onSelect = { viewModel.onAction(SettingsAction.SetTheme(it)) },
            )
            Hairline()
            GeneralSection(
              state = state,
              onAction = viewModel::onAction,
            )
}
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(26.dp),
          ) {
            SoundfontSection(state = state, onAction = viewModel::onAction)
            OnlineSection(state = state, onAction = viewModel::onAction)
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
          InputMethodSection(
            selected = state.inputMethod,
            onSelect = { viewModel.onAction(SettingsAction.SelectInputMethod(it)) },
          )
          Hairline()
          MidiKeyboardSection(state = state, onAction = viewModel::onAction)
          Hairline()
          OnlineSection(state = state, onAction = viewModel::onAction)
          Hairline()
          SoundfontSection(state = state, onAction = viewModel::onAction)
          Hairline()
          ThemeSection(
            theme = state.theme,
            onSelect = { viewModel.onAction(SettingsAction.SetTheme(it)) },
          )
          Hairline()
          GeneralSection(
            state = state,
            onAction = viewModel::onAction,
          )
        }
      }
    }

    Text(
      text = "Changes are applied automatically.",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp, bottom = 18.dp),
      textAlign = TextAlign.Center,
    )
  }

  state.openPickerChannel?.let { channel ->
    PresetPickerSheet(
      title = channelLabel(channel),
      icon = channelIcon(channel),
      soundbanks = state.soundbanks,
      selectedPresets = state.resolvedChoices(channel),
      presetVolumes = state.presetVolumes,
      presetCutoffs = state.presetCutoffs,
      onTogglePreset = { viewModel.onAction(SettingsAction.TogglePreset(channel, it)) },
      onPreviewPreset = { viewModel.onAction(SettingsAction.PreviewPreset(channel, it)) },
      onPresetVolumeChange = { preset, percent ->
        viewModel.onAction(SettingsAction.SetPresetVolume(channel, preset, percent))
      },
      onPresetVolumeCommit = { preset, percent ->
        viewModel.onAction(SettingsAction.CommitPresetVolume(preset, percent))
      },
      onPresetCutoffChange = { preset, cutoff ->
        viewModel.onAction(SettingsAction.SetPresetCutoff(channel, preset, cutoff))
      },
      onPresetCutoffCommit = { preset, cutoff ->
        viewModel.onAction(SettingsAction.CommitPresetCutoff(preset, cutoff))
      },
      onPresetCutoffScopeChange = { preset, scope ->
        viewModel.onAction(SettingsAction.SetPresetCutoffScope(channel, preset, scope))
      },
      onDismiss = { viewModel.onAction(SettingsAction.ClosePicker) },
    )
  }

  state.openShuffleChannel?.let { channel ->
    PresetShuffleSheet(
      channelLabel = channelLabel(channel),
      selected = state.presetShuffle.forChannel(channel),
      perNoteApplies = channel == MidiChannel.Notes,
      onSelect = { viewModel.onAction(SettingsAction.SetPresetShuffleMode(channel, it)) },
      onDismiss = { viewModel.onAction(SettingsAction.CloseShuffleModePicker) },
    )
  }

  state.openLabelEditor?.let { editor ->
    LabelEditorSheet(
      editor = editor,
      labels =
        when (editor) {
          LabelEditor.Notes -> state.musicLabels.noteNames
          LabelEditor.Degrees -> state.musicLabels.degreeNames
        },
      savedCustomLabels =
        when (editor) {
          LabelEditor.Notes -> state.musicLabels.customNoteNamesPreset
          LabelEditor.Degrees -> state.musicLabels.customDegreeNamesPreset
        },
      onLabelChange = { index, value ->
        viewModel.onAction(
          when (editor) {
            LabelEditor.Notes -> SettingsAction.SetNoteName(index, value)
            LabelEditor.Degrees -> SettingsAction.SetDegreeName(index, value)
          },
        )
      },
      onLabelsChange = { values ->
        viewModel.onAction(
          when (editor) {
            LabelEditor.Notes -> SettingsAction.SetNoteNames(values)
            LabelEditor.Degrees -> SettingsAction.SetDegreeNames(values)
          },
        )
      },
      onSaveCustom = { values ->
        viewModel.onAction(
          when (editor) {
            LabelEditor.Notes -> SettingsAction.SaveCustomNoteNamesPreset(values)
            LabelEditor.Degrees -> SettingsAction.SaveCustomDegreeNamesPreset(values)
          },
        )
      },
      onDismiss = { viewModel.onAction(SettingsAction.CloseLabelEditor) },
    )
  }

  if (state.backendUrlDialogOpen) {
    BackendUrlDialog(state = state, onAction = viewModel::onAction)
  }
}

@Composable
private fun OnlineSection(
  state: SettingsUiState,
  onAction: (SettingsAction) -> Unit,
) {
  FlatSection(label = "ONLINE") {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(ShuuenUi.ControlShape)
          .clickable { onAction(SettingsAction.OpenBackendUrlDialog) }
          .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      BackendStatusIcon(state.backendStatus, size = 22.dp)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Backend",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = state.effectiveBackendUrl,
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = state.backendStatus.label().uppercase(),
        color = if (state.backendStatus == ldv.shuuen.core.online.BackendStatus.Available) {
          ShuuenUi.Correct
        } else {
          ShuuenUi.Dim
        },
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
      )
      Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = "Edit backend URL",
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun BackendUrlDialog(
  state: SettingsUiState,
  onAction: (SettingsAction) -> Unit,
) {
  AlertDialog(
    onDismissRequest = { onAction(SettingsAction.CloseBackendUrlDialog) },
    icon = { BackendStatusIcon(state.backendStatus, size = 28.dp) },
    title = { Text("Backend URL") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = state.backendUrlDraft,
          onValueChange = { onAction(SettingsAction.SetBackendUrlDraft(it)) },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("URL") },
          placeholder = { Text(state.defaultBackendUrl) },
          trailingIcon = { BackendStatusIcon(state.backendStatus) },
          supportingText = {
            Text(
              state.backendUrlError
                ?: "Leave blank to use the platform default: ${state.defaultBackendUrl}",
            )
          },
          isError = state.backendUrlError != null,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Text(
          text = "Backend status: ${state.backendStatus.label().lowercase()}",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onAction(SettingsAction.SaveBackendUrl) }) { Text("SAVE") }
    },
    dismissButton = {
      TextButton(onClick = { onAction(SettingsAction.CloseBackendUrlDialog) }) { Text("CANCEL") }
    },
  )
}

@Composable
private fun Card(
  component: InputComponent,
  mode: InputMode,
  icon: ImageVector,
  selected: InputMethod,
  compact: Boolean,
  onSelect: (InputMethod) -> Unit,
  modifier: Modifier,
) {
  InputMethodCard(
    title = if (component == InputComponent.Piano) "Piano" else "Circle",
    mode = if (mode == InputMode.Absolute) "Absolute" else "Relative",
    icon = icon,
    selected = selected.component == component && selected.mode == mode,
    compact = compact,
    // Keep the circle orientation choice when switching methods.
    onClick = { onSelect(selected.copy(component = component, mode = mode)) },
    modifier = modifier,
  )
}

@Composable
private fun InputMethodSection(
  selected: InputMethod,
  onSelect: (InputMethod) -> Unit,
) {
  FlatSection(
    label = "INPUT METHOD",
    supporting = "Choose how answers are entered and interpreted.",
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val compact = maxWidth < 390.dp

      val spacing = 10.dp
      Column {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Card(InputComponent.Piano, InputMode.Absolute, Icons.Rounded.Keyboard, selected, compact, onSelect, Modifier.weight(1f))
          Card(InputComponent.Piano, InputMode.Relative, Icons.Rounded.Keyboard, selected, compact, onSelect, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(spacing))
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Card(InputComponent.Circle, InputMode.Absolute, Icons.Rounded.GraphicEq, selected, compact, onSelect, Modifier.weight(1f))
          Card(InputComponent.Circle, InputMode.Relative, Icons.Rounded.GraphicEq, selected, compact, onSelect, Modifier.weight(1f))
        }

        // Circle + Absolute has a sub-choice: keep the fixed C-at-top layout, or rotate so the
        // level's root sits at the top.
        AnimatedVisibility (selected.component == InputComponent.Circle && selected.mode == InputMode.Absolute) {
          Column {
            Spacer(modifier = Modifier.height(spacing))
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              PillControl(
                text = "C at the top",
                selected = !selected.circleAbsoluteRootAtTop,
                trailingCheck = true,
                onClick = { onSelect(selected.copy(circleAbsoluteRootAtTop = false)) },
                modifier = Modifier.weight(1f),
              )
              PillControl(
                text = "Root at the top",
                selected = selected.circleAbsoluteRootAtTop,
                trailingCheck = true,
                onClick = { onSelect(selected.copy(circleAbsoluteRootAtTop = true)) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Theme choice: appearance decides which brightness variant is active (System follows
 * the device), contrast switches between the standard and the softer monochrome look.
 */
@Composable
private fun ThemeSection(
  theme: ThemeSettings,
  onSelect: (ThemeSettings) -> Unit,
) {
  FlatSection(
    label = "THEME",
    supporting = "System follows the device's dark or light mode.",
  ) {
    ThemePickerRow("APPEARANCE") {
      PillControl(
        text = "System",
        selected = theme.appearance == ThemeAppearance.System,
        trailingCheck = true,
        onClick = { onSelect(theme.copy(appearance = ThemeAppearance.System)) },
        modifier = Modifier.weight(1f),
      )
      PillControl(
        text = "Dark",
        selected = theme.appearance == ThemeAppearance.Dark,
        trailingCheck = true,
        onClick = { onSelect(theme.copy(appearance = ThemeAppearance.Dark)) },
        modifier = Modifier.weight(1f),
      )
      PillControl(
        text = "Light",
        selected = theme.appearance == ThemeAppearance.Light,
        trailingCheck = true,
        onClick = { onSelect(theme.copy(appearance = ThemeAppearance.Light)) },
        modifier = Modifier.weight(1f),
      )
    }
    ThemePickerRow("CONTRAST") {
      PillControl(
        text = "Standard",
        selected = theme.style == ThemeStyle.Mono,
        trailingCheck = true,
        onClick = { onSelect(theme.copy(style = ThemeStyle.Mono)) },
        modifier = Modifier.weight(1f),
      )
      PillControl(
        text = "Soft",
        selected = theme.style == ThemeStyle.MonoSoft,
        trailingCheck = true,
        onClick = { onSelect(theme.copy(style = ThemeStyle.MonoSoft)) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ThemePickerRow(
  label: String,
  content: @Composable RowScope.() -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = label,
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth(),
      content = content,
    )
  }
}

/**
 * Hardware MIDI keyboard status and options. The keyboard always answers alongside the on-screen
 * input; the octave choice only appears while one is actually connected.
 */
@Composable
private fun MidiKeyboardSection(
  state: SettingsUiState,
  onAction: (SettingsAction) -> Unit,
) {
  val connected = state.midiKeyboardDevices.isNotEmpty()
  FlatSection(
    label = "MIDI KEYBOARD",
    supporting = "A connected MIDI keyboard answers alongside the on-screen input.",
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(
        Icons.Rounded.Piano,
        contentDescription = null,
        tint = if (connected) ShuuenUi.Correct else ShuuenUi.Muted,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (connected) "Connected" else "Not connected",
          color = if (connected) ShuuenUi.Text else ShuuenUi.Muted,
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text =
            if (connected) state.midiKeyboardDevices.joinToString()
            else "Plug in a MIDI keyboard to play answers on it.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }

    AnimatedVisibility(connected) {
      Column {
        Hairline()
        SwitchRow(
          icon = Icons.Rounded.MusicNote,
          title = "Respect octaves",
          subtitle = "Answers must match the exact octave; off grades any octave as equal.",
          checked = state.midiRespectOctaves,
          onCheckedChange = { onAction(SettingsAction.SetMidiRespectOctaves(it)) },
        )
      }
    }
  }
}

@Composable
private fun SoundfontSection(
  state: SettingsUiState,
  onAction: (SettingsAction) -> Unit,
) {
  FlatSection(
    label = "SOUNDFONT",
    supporting = "Use one MIDI soundfont for all playback categories.",
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val compact = maxWidth < 440.dp
      if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SoftControl(modifier = Modifier.fillMaxWidth()) {
            Icon(
              Icons.AutoMirrored.Rounded.Article,
              contentDescription = null,
              tint = ShuuenUi.Muted,
              modifier = Modifier.size(22.dp)
            )
            Text(
              "GeneralUser-GS",
              color = ShuuenUi.Text,
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.weight(1f),
              maxLines = 1
            )
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            PillControl(
              "Load",
              leadingIcon = Icons.Rounded.FolderOpen,
              selected = true,
              modifier = Modifier.weight(1f)
            )
            PillControl("Default", modifier = Modifier.weight(1f))
          }
        }
      } else {
        SoftControl(modifier = Modifier.fillMaxWidth()) {
          Icon(
            Icons.AutoMirrored.Rounded.Article,
            contentDescription = null,
            tint = ShuuenUi.Muted,
            modifier = Modifier.size(24.dp)
          )
          Text(
            "GeneralUser-GS",
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1
          )
          PillControl(
            "Load from storage",
            leadingIcon = Icons.Rounded.FolderOpen,
            selected = true
          )
          PillControl("Default")
        }
      }
    }

    MidiChannel.entries.forEachIndexed { index, channel ->
      if (index > 0) Hairline()
      val choices = state.resolvedChoices(channel)
      SoundCategoryRow(
        label = channelLabel(channel),
        icon = channelIcon(channel),
        soundbankLabel = soundbankChoicesLabel(choices),
        presetLabel = presetChoicesLabel(choices),
        volume = state.selectedVolumes.forChannel(channel),
        onOpen = { onAction(SettingsAction.OpenPicker(channel)) },
        onPreview = { onAction(SettingsAction.Preview(channel)) },
        onVolumeChange = { onAction(SettingsAction.SetVolume(channel, it)) },
        onVolumeCommit = { onAction(SettingsAction.CommitVolume(channel, it)) },
      )
      // The schedule only means something once there is more than one instrument to rotate.
      AnimatedVisibility(choices.size > 1) {
        PresetShuffleRow(
          mode = state.presetShuffle.forChannel(channel),
          perNoteApplies = channel == MidiChannel.Notes,
          onOpen = { onAction(SettingsAction.OpenShuffleModePicker(channel)) },
        )
      }
    }
    // Per-note is approximate on imported melodies, so it is opt-out on its own.
    AnimatedVisibility(
      state.selectedPresets.choicesFor(MidiChannel.Notes).size > 1 &&
        state.presetShuffle.notes == PresetShuffleMode.PerNote
    ) {
      Column {
        Hairline()
        SwitchRow(
          icon = Icons.Rounded.Shuffle,
          title = "Per note on imported melodies",
          subtitle =
            "Imported MIDI plays through the audio engine, so the instrument changes land up to " +
              "a moment late. Off keeps those levels on one instrument per question.",
          checked = state.presetShuffle.perNoteOnImportedMelodies,
          onCheckedChange = {
            onAction(SettingsAction.SetPerNoteShuffleOnImportedMelodies(it))
          },
        )
      }
    }
    Hairline()
    MelodyOriginalVolumeBoostRow(
      value = state.melodyOriginalVolumeBoost,
      onChange = { onAction(SettingsAction.SetMelodyOriginalVolumeBoost(it)) },
      onCommit = { onAction(SettingsAction.CommitMelodyOriginalVolumeBoost(it)) },
    )
    Hairline()
    BackingTrackVolumeRow(
      value = state.backingTrackVolume,
      onChange = { onAction(SettingsAction.SetBackingTrackVolume(it)) },
      onCommit = { onAction(SettingsAction.CommitBackingTrackVolume(it)) },
    )
    Hairline()
    SwitchRow(
      icon = Icons.Rounded.Audiotrack,
      title = "Backing track replaces melody",
      subtitle = "Silence the MIDI melody when a level has a backing track.",
      checked = state.backingTrackMutesMelody,
      onCheckedChange = { onAction(SettingsAction.SetBackingTrackMutesMelody(it)) },
    )

    if (state.errorMessage != null) {
      Text(
        text = state.errorMessage,
        color = ShuuenUi.Incorrect,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun GeneralSection(
  state: SettingsUiState,
  onAction: (SettingsAction) -> Unit,
) {
  FlatSection(label = "GENERAL") {
    SettingsRow(Icons.Rounded.Language, "Language", trailing = "English")
    Hairline()
    SettingsRow(
      Icons.Rounded.TextFields,
      "Note names",
      subtitle = labelPreview(effectiveNoteNames(state.musicLabels.noteNames), PreviewNaturalCount),
      onClick = { onAction(SettingsAction.OpenLabelEditor(LabelEditor.Notes)) },
    )
    Hairline()
    SettingsRow(
      Icons.Rounded.TextFields,
      "Degree names",
      subtitle = labelPreview(effectiveDegreeNames(state.musicLabels.degreeNames), PreviewDegreeCount),
      onClick = { onAction(SettingsAction.OpenLabelEditor(LabelEditor.Degrees)) },
    )
    Hairline()
    SwitchRow(
      icon = Icons.Rounded.MusicNote,
      title = "Allow 7♯/7♭ keys",
      subtitle = "Let C♯/C♭-type keys appear in note naming.",
      checked = state.allowSevenAccidentalKeys,
      onCheckedChange = { onAction(SettingsAction.SetAllowSevenAccidentalKeys(it)) },
    )
    Hairline()
    LevelStatsWindowRow(
      value = state.levelStatsWindow,
      onChange = { onAction(SettingsAction.SetLevelStatsWindow(it)) },
      onCommit = { onAction(SettingsAction.CommitLevelStatsWindow(it)) },
    )
    Hairline()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(
        Icons.Rounded.PlayArrow,
        contentDescription = null,
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(22.dp)
      )
      Text(
        text = "Play next question automatically",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      ShuuenSwitch(checked = true)
    }
  }
}

private const val PreviewNaturalCount = 5
private const val PreviewDegreeCount = 5

private fun labelPreview(labels: List<String>, visibleCount: Int): String {
  val visibleLabels = labels.take(visibleCount)
  val suffix = if (labels.size > visibleLabels.size) "..." else ""
  return visibleLabels.joinToString(", ") + suffix
}

@Composable
private fun SwitchRow(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(icon, contentDescription = null, tint = ShuuenUi.Muted, modifier = Modifier.size(22.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, color = ShuuenUi.Text, style = MaterialTheme.typography.titleMedium)
      if (subtitle != null) {
        Text(text = subtitle, color = ShuuenUi.Dim, style = MaterialTheme.typography.bodySmall)
      }
    }
    ShuuenSwitch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun InputMethodCard(
  title: String,
  mode: String,
  icon: ImageVector,
  selected: Boolean,
  compact: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  SoftControl(
    modifier = modifier
      .heightIn(min = if (compact) 92.dp else 86.dp),
        onClick = onClick,
    selected = selected,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          icon,
          contentDescription = null,
          tint = if (selected) ShuuenUi.Text else ShuuenUi.Muted,
          modifier = Modifier.size(if (compact) 22.dp else 24.dp)
        )
        Spacer(Modifier.weight(1f))
        Icon(
          imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (selected) ShuuenUi.Text else ShuuenUi.Dim,
          modifier = Modifier.size(if (compact) 20.dp else 22.dp),
        )
      }
      Text(
        text = title,
        color = if (selected) ShuuenUi.Text else ShuuenUi.Muted,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = mode,
        color = if (selected) ShuuenUi.Muted else ShuuenUi.Dim,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SoundCategoryRow(
  label: String,
  icon: ImageVector,
  soundbankLabel: String,
  presetLabel: String,
  volume: Int,
  onOpen: () -> Unit,
  onPreview: () -> Unit,
  onVolumeChange: (Int) -> Unit,
  onVolumeCommit: (Int) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val compact = maxWidth < 480.dp

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (compact) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            icon,
            contentDescription = null,
            tint = ShuuenUi.Muted,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = label,
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
          )
          PreviewBubble(onClick = onPreview, size = 36.dp)
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          SoundPickerColumn("SOUNDBANK", soundbankLabel, onOpen, Modifier.weight(1f))
          SoundPickerColumn("PRESET", presetLabel, onOpen, Modifier.weight(1f))
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            icon,
            contentDescription = null,
            tint = ShuuenUi.Muted,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = label,
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(74.dp),
          )
          SoundPickerColumn("SOUNDBANK", soundbankLabel, onOpen, Modifier.weight(1f))
          SoundPickerColumn("PRESET", presetLabel, onOpen, Modifier.weight(1f))
          PreviewBubble(onClick = onPreview, size = 40.dp)
        }
      }

      ValueSlider(
        value = volume,
        onChange = onVolumeChange,
        onCommit = onVolumeCommit,
        valueLabel = { "${(it * 100) / 127}%" },
      )
    }
  }
}

/**
 * How often a channel re-rolls among its chosen presets, as a summary row that opens the choices
 * in a sheet. Three labelled pills side by side don't survive a phone's width — see
 * [PresetShuffleSheet].
 */
@Composable
private fun PresetShuffleRow(
  mode: PresetShuffleMode,
  perNoteApplies: Boolean,
  onOpen: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .clickable(onClick = onOpen)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      Icons.Rounded.Shuffle,
      contentDescription = null,
      tint = ShuuenUi.Muted,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = "Change instrument",
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.weight(1f),
      // One line wherever it fits; on a narrow phone it wraps rather than losing a word to an
      // ellipsis, since the value pill beside it is what must stay readable.
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    PillControl(
      text = shuffleModeSummary(mode, perNoteApplies),
      trailingIcon = Icons.Rounded.ChevronRight,
      fillLabel = false,
      onClick = onOpen,
    )
  }
}

@Composable
private fun MelodyOriginalVolumeBoostRow(
  value: Int,
  onChange: (Int) -> Unit,
  onCommit: (Int) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "Melody volume boost",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          "Only for imported melodies with original velocities.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    ValueSlider(
      value = value,
      onChange = onChange,
      onCommit = onCommit,
      valueLabel = ::melodyVolumeBoostLabel,
      iconForValue = { Icons.AutoMirrored.Rounded.VolumeUp },
    )
  }
}

@Composable
private fun BackingTrackVolumeRow(
  value: Int,
  onChange: (Int) -> Unit,
  onCommit: (Int) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        Icons.Rounded.Audiotrack,
        contentDescription = null,
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "Backing track volume",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          "Audio played along imported melodies. Real recordings are mastered much louder " +
            "than the MIDI instruments, so low values are normal here.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    ValueSlider(
      value = value,
      onChange = onChange,
      onCommit = onCommit,
      valueLabel = { "$it" },
      iconForValue = { Icons.AutoMirrored.Rounded.VolumeUp },
    )
  }
}

@Composable
private fun LevelStatsWindowRow(
  value: Int,
  onChange: (Int) -> Unit,
  onCommit: (Int) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        Icons.Rounded.GraphicEq,
        contentDescription = null,
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "Level stats window",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          "Latest $value games on level cards.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    ValueSlider(
      value = value,
      onChange = onChange,
      onCommit = onCommit,
      valueLabel = { "$it" },
      iconForValue = { Icons.Rounded.GraphicEq },
      valueRange = MinLevelStatsWindow.toFloat()..MaxLevelStatsWindow.toFloat(),
      steps = MaxLevelStatsWindow - MinLevelStatsWindow - 1,
    )
  }
}

private fun melodyVolumeBoostLabel(value: Int): String {
  val tenths = 10 + (value.coerceIn(0, 127) * 30 + 63) / 127
  return "${tenths / 10}.${tenths % 10}x"
}

@Composable
private fun ValueSlider(
  value: Int,
  onChange: (Int) -> Unit,
  onCommit: (Int) -> Unit,
  valueLabel: (Int) -> String,
  iconForValue: (Int) -> ImageVector = ::volumeIcon,
  valueRange: ClosedFloatingPointRange<Float> = 0f..127f,
  steps: Int = 0,
) {
  // Local state drives the slider; live drags don't persist, so the incoming
  // [value] only changes on commit/load and re-syncs us without fighting the drag.
  val rangeStart = valueRange.start
  val rangeEnd = valueRange.endInclusive
  var sliderValue by remember { mutableFloatStateOf(value.toFloat().coerceIn(rangeStart, rangeEnd)) }
  LaunchedEffect(value, valueRange) { sliderValue = value.toFloat().coerceIn(rangeStart, rangeEnd) }
  val current = sliderValue.roundToInt()

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      iconForValue(current),
      contentDescription = null,
      tint = ShuuenUi.Muted,
      modifier = Modifier.size(20.dp),
    )
    Slider(
      value = sliderValue,
      onValueChange = {
        val coerced = it.coerceIn(rangeStart, rangeEnd)
        sliderValue = coerced
        onChange(coerced.roundToInt())
      },
      onValueChangeFinished = { onCommit(sliderValue.roundToInt()) },
      valueRange = valueRange,
      steps = steps,
      colors = SliderDefaults.colors(
        thumbColor = ShuuenUi.Text,
        activeTrackColor = ShuuenUi.Inverse,
        inactiveTrackColor = ShuuenUi.Ink.copy(alpha = 0.12f),
      ),
      modifier = Modifier.weight(1f),
    )
    Text(
      text = valueLabel(current),
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.labelLarge,
      textAlign = TextAlign.End,
      modifier = Modifier.width(48.dp),
    )
  }
}

private fun volumeIcon(value: Int): ImageVector =
  when {
    value <= 0 -> Icons.AutoMirrored.Rounded.VolumeOff
    value < 64 -> Icons.AutoMirrored.Rounded.VolumeDown
    else -> Icons.AutoMirrored.Rounded.VolumeUp
  }

@Composable
private fun PreviewBubble(onClick: () -> Unit, size: Dp) {
  Box(modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
    IconBubble(Icons.Rounded.PlayArrow, tint = ShuuenUi.Text, size = size)
  }
}

@Composable
private fun SoundPickerColumn(
  label: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      label,
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    PillControl(value, onClick = onClick)
  }
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  trailing: String? = null,
  onClick: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(
        if (onClick != null) {
          Modifier.clip(ShuuenUi.ControlShape).clickable(onClick = onClick)
        } else {
          Modifier
        }
      )
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(icon, contentDescription = null, tint = ShuuenUi.Muted, modifier = Modifier.size(22.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleMedium,
      )
      if (subtitle != null) {
        Text(text = subtitle, color = ShuuenUi.Dim, style = MaterialTheme.typography.bodySmall)
      }
    }
    if (trailing != null) {
      PillControl(trailing, modifier = Modifier.width(170.dp))
    } else {
      Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

private fun channelLabel(channel: MidiChannel): String =
  when (channel) {
    MidiChannel.Notes -> "Notes"
    MidiChannel.Drone -> "Drone"
    MidiChannel.Cadence -> "Cadence"
  }

private fun channelIcon(channel: MidiChannel): ImageVector =
  when (channel) {
    MidiChannel.Notes -> Icons.Rounded.MusicNote
    MidiChannel.Drone -> Icons.Rounded.Waves
    MidiChannel.Cadence -> Icons.Rounded.GraphicEq
  }
