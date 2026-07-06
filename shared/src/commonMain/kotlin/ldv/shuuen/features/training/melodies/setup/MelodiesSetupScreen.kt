package ldv.shuuen.features.training.melodies.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.PillControl
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.SegmentedPlusMinus
import ldv.shuuen.core.ui.components.ShuuenSwitch
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.music.generator.MelodyStyles
import ldv.shuuen.core.ui.components.music.NoteRow
import ldv.shuuen.features.training.common.components.ScaleChooser
import ldv.shuuen.features.training.common.components.StylePickerSheet
import ldv.shuuen.features.training.domain.ScaleConfig

@Composable
fun MelodiesSetupScreen(
  onNavigateBack: () -> Unit,
  onOpenContext: () -> Unit,
  onSaveLevel: () -> Unit,
  viewModel: MelodiesSetupScreenViewModel,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val isEditing = viewModel.isEditing
  StaticScreenFrame(
    maxWidth = 920.dp,
    verticalSpacing = 22.dp,
    topBar = {
      ShuuenTopAppBar(
        title = if (isEditing) "EDIT MELODIES" else "MELODIES SETUP",
        subtitle =
          if (isEditing) "Update this melody training level."
          else "Create a custom melody training level.",
        onBack = onNavigateBack,
        type = ShuuenTopAppBarType.Labeled,
      )
    },
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      // The Midi mode only has two sections, so it never splits into columns.
      val twoColumn = maxWidth > 760.dp && state.sourceMode == MelodiesSourceMode.Random

      if (twoColumn) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(44.dp),
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(22.dp),
          ) {
            LeadingSections(state, viewModel, onOpenContext)
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(22.dp),
          ) {
            RandomTrailingSections(state, viewModel)
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
          LeadingSections(state, viewModel, onOpenContext)
          if (state.sourceMode == MelodiesSourceMode.Random) {
            Hairline()
            RandomTrailingSections(state, viewModel)
          }
        }
      }
    }

    val scope = rememberCoroutineScope()
    PrimaryCta(
      text = if (isEditing) "SAVE CHANGES" else "SAVE LEVEL",
      icon = Icons.Rounded.Save,
      onClick = { scope.launch { if (viewModel.upsertLevel()) onSaveLevel() } },
      modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
    )
  }
}

@Composable
private fun LeadingSections(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
  onOpenContext: () -> Unit,
) {
  when (state.sourceMode) {
    MelodiesSourceMode.Random -> {
      ScaleChooser(
        scaleConfig = state.scaleConfig,
        onScaleChosen = viewModel::changeScale,
      )

      // Scale rotation only applies to a random (relative) tonic, and only between finite
      // sequences — an endless stream keeps its tonic. Stepping the control below 5 turns it off.
      if (state.scaleConfig is ScaleConfig.RelativeScaleConfig && !state.endlessNotes) {
        FlatSection(
          label = "SCALE ROTATION",
          supporting = "Off, or move to a new random tonic every few questions.",
        ) {
          SegmentedPlusMinus(
            value = state.rotateEveryQuestions,
            onChange = viewModel::changeRotateEveryQuestions,
            delta = 5,
            nullCondition = { (it.toIntOrNull() ?: 0) <= 0 },
            nullLabel = "Off",
          )
        }
      }

      Hairline()
      ContextSection("2 · CONTEXT", state.context, onOpenContext)
      Hairline()
      SourceModeSection("3 · SOURCE MODE", state, viewModel)
      if (!state.endlessNotes) {
        Hairline()
        QuestionCountSection(state, viewModel)
      }
    }

    MelodiesSourceMode.Midi -> {
      SourceModeSection("1 · SOURCE MODE", state, viewModel)
      Hairline()
      ContextSection("2 · CONTEXT", state.context, onOpenContext)
    }
  }
}

@Composable
private fun RandomTrailingSections(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  NotesPerSequenceSection(state, viewModel)
  Hairline()
  TempoSection(state, viewModel)
  Hairline()
  RhythmSection(state, viewModel)
  Hairline()
  MelodyRangeSection(state, viewModel)
}

@Composable
private fun ContextSection(
  label: String,
  context: DegreeContext?,
  onOpenContext: () -> Unit,
) {
  SetupNavRow(
    label = label,
    supporting =
      context?.let { "Using context ${it.name ?: it.id}" }
        ?: "Open context screen to configure.",
    onClick = onOpenContext,
  ) {
    Icon(
      Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = ShuuenUi.Dim,
      modifier = Modifier.size(26.dp),
    )
  }
}

@Composable
private fun SourceModeSection(
  label: String,
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  FlatSection(label = label) {
    PillControl(
      text = "Random",
      selected = state.sourceMode == MelodiesSourceMode.Random,
      leadingIcon = Icons.Rounded.Casino,
      trailingCheck = true,
      onClick = { viewModel.selectSourceMode(MelodiesSourceMode.Random) },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      PillControl(
        text =
          when {
            state.isLoadingMidi -> "Loading…"
            state.loadedMidiName != null -> state.loadedMidiName
            else -> "Load .midi file"
          },
        selected = state.sourceMode == MelodiesSourceMode.Midi,
        leadingIcon = Icons.Rounded.FolderOpen,
        onClick = {
          if (state.isLoadingMidi) return@PillControl
          // No file yet, or already active: open the picker. A loaded file with Random
          // active just switches back without re-picking.
          if (state.loadedMidi == null || state.sourceMode == MelodiesSourceMode.Midi) {
            viewModel.loadMidiFile()
          } else {
            viewModel.selectSourceMode(MelodiesSourceMode.Midi)
          }
        },
        modifier = Modifier.weight(1f),
      )
      PillControl(
        text = "Open library",
        leadingIcon = Icons.Rounded.FolderOpen,
        modifier = Modifier.weight(1f),
      )
    }
    val statusText =
      when {
        state.midiError != null -> state.midiError
        state.loadedMidiName != null -> "Loaded ${state.loadedMidiName}."
        else -> "Random sequences from a scale, or a .midi file's melody."
      }
    Text(
      text = statusText,
      color = if (state.midiError != null) ShuuenUi.Incorrect else ShuuenUi.Dim,
      style = MaterialTheme.typography.bodyMedium,
    )
    if (state.sourceMode == MelodiesSourceMode.Midi) {
      SoftControl(
        modifier = Modifier.fillMaxWidth(),
        selected = state.useOriginalVelocities,
        onClick = { viewModel.setUseOriginalVelocities(!state.useOriginalVelocities) },
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "Note velocities",
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            if (state.useOriginalVelocities) "Original file values" else "Full velocity (127)",
            color = ShuuenUi.Muted,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        ShuuenSwitch(
          checked = state.useOriginalVelocities,
          onCheckedChange = null,
        )
      }
    }
  }
}

@Composable
private fun QuestionCountSection(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  FlatSection(
    label = "4 · NUMBER OF QUESTIONS",
    supporting = "One question is one melody sequence.",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SegmentedPlusMinus(
        value = state.questionsNumber,
        onChange = viewModel::changeQuestionsNumber,
        nullLabel = "∞",
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "∞",
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.headlineMedium,
      )
      ShuuenSwitch(
        checked = state.questionsNumber == null,
        onCheckedChange = { unlimited ->
          viewModel.changeQuestionsNumber(if (unlimited) null else 20)
        },
      )
    }
  }
}

private val NotesPerSequencePresets = listOf(2, 4, 8, 12)

@Composable
private fun NotesPerSequenceSection(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  FlatSection(label = "5 · NOTES PER SEQUENCE") {
    if (!state.endlessNotes) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        NotesPerSequencePresets.forEach { value ->
          PillControl(
            text = "$value",
            selected = state.notesPerSequence == value,
            onClick = { viewModel.changeNotesPerSequence(value) },
            modifier = Modifier.weight(1f),
          )
        }
        Text("Custom", color = ShuuenUi.Dim, style = MaterialTheme.typography.bodySmall)
        // Recreated whenever the VM value changes so preset taps refresh the field, while
        // invalid input in-between (e.g. an empty field mid-edit) stays local.
        var customText by remember(state.notesPerSequence) {
          mutableStateOf(state.notesPerSequence.toString())
        }
        SoftControl(
          modifier = Modifier.width(54.dp),
          selected = state.notesPerSequence !in NotesPerSequencePresets,
        ) {
          BasicTextField(
            value = customText,
            onValueChange = { text ->
              customText = text
              text.toIntOrNull()?.let(viewModel::changeNotesPerSequence)
            },
            textStyle =
              MaterialTheme.typography.titleSmall.copy(
                color = ShuuenUi.Text,
                textAlign = TextAlign.Center,
              ),
            singleLine = true,
            cursorBrush = SolidColor(ShuuenUi.Text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    SoftControl(
      modifier = Modifier.fillMaxWidth(),
      selected = state.endlessNotes,
      onClick = { viewModel.setEndlessNotes(!state.endlessNotes) },
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "Endless note mode",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          "Ignore sequence length and keep playing.",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      ShuuenSwitch(
        checked = state.endlessNotes,
        onCheckedChange = null,
      )
    }
  }
}

private const val TempoSliderStep = 5

@Composable
private fun TempoSection(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  val tempoRange = MelodiesSetupScreenViewModel.TempoRange
  FlatSection(
    label = "6 · TEMPO",
    supporting = "Playback speed of the sequences.",
    trailing = { TempoInputBox(state.tempo, viewModel::changeTempo) },
  ) {
    Slider(
      value = state.tempo.toFloat(),
      // The slider snaps to 5-BPM steps over the wide 20–360 span; the box above takes exact
      // values.
      onValueChange = {
        viewModel.changeTempo((it / TempoSliderStep).roundToInt() * TempoSliderStep)
      },
      valueRange = tempoRange.first.toFloat()..tempoRange.last.toFloat(),
      colors =
        SliderDefaults.colors(
          thumbColor = ShuuenUi.Text,
          activeTrackColor = ShuuenUi.Text,
          inactiveTrackColor = ShuuenUi.Hairline,
        ),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        "${tempoRange.first}",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall
      )
      Text(
        "${tempoRange.last}",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}

@Composable
private fun TempoInputBox(tempo: Int, onTempoChange: (Int) -> Unit) {
  val tempoRange = MelodiesSetupScreenViewModel.TempoRange
  // Recreated whenever the VM value changes (e.g. slider drags); incomplete input like "3" while
  // typing "360" stays local until it becomes a valid tempo.
  var text by remember(tempo) { mutableStateOf(tempo.toString()) }
  SoftControl(modifier = Modifier.width(110.dp)) {
    BasicTextField(
      value = text,
      onValueChange = { newText ->
        text = newText
        val value = newText.toIntOrNull() ?: return@BasicTextField
        when {
          value in tempoRange -> onTempoChange(value)
          value > tempoRange.last -> onTempoChange(tempoRange.last)
        }
      },
      textStyle =
        MaterialTheme.typography.titleSmall.copy(
          color = ShuuenUi.Text,
          textAlign = TextAlign.End,
        ),
      singleLine = true,
      cursorBrush = SolidColor(ShuuenUi.Text),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.weight(1f),
    )
    Text(
      "BPM",
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
    )
  }
}

@Composable
private fun RhythmSection(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  var showSheet by remember { mutableStateOf(false) }
  SetupNavRow(
    label = "7 · RHYTHM",
    supporting = "${state.melodyStyle.name} · ${state.melodyStyle.tier.label}",
    onClick = { showSheet = true },
  ) {
    Icon(
      Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = ShuuenUi.Dim,
      modifier = Modifier.size(26.dp),
    )
  }
  if (showSheet) {
    StylePickerSheet(
      title = "Rhythm",
      subtitle =
        "How the random notes flow: each style mixes rhythm figures with a weighted note picker. Context-aware styles also follow the chord the context is playing.",
      icon = Icons.Rounded.MusicNote,
      presets = MelodyStyles.presets,
      selectedId = state.melodyStyle.id,
      onSelect = { style ->
        viewModel.changeMelodyStyle(style)
        showSheet = false
      },
      onDismiss = { showSheet = false },
    )
  }
}

@Composable
private fun MelodyRangeSection(
  state: MelodiesSetupState,
  viewModel: MelodiesSetupScreenViewModel,
) {
  FlatSection(
    label = "8 · RANGE",
    supporting = "Select the note range.",
  ) {
    Text(
      text = "${state.range.from} - ${state.range.to}",
      style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 3.sp),
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    NoteRow(value = state.range.from) { viewModel.changeRangeStart(it) }
    NoteRow(value = state.range.to) { viewModel.changeRangeEnd(it) }
  }
}

@Composable
private fun SetupNavRow(
  label: String,
  supporting: String,
  onClick: (() -> Unit)? = null,
  trailing: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = label,
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.labelLarge.copy(
          letterSpacing = ShuuenUi.labelSpacing,
          fontWeight = FontWeight.SemiBold,
        ),
      )
      Text(
        text = supporting,
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    trailing()
  }
}
