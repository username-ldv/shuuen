package ldv.shuuen.features.training.chords.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import ldv.shuuen.core.music.generator.ChordStyles
import ldv.shuuen.core.ui.components.music.NoteRow
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.common.components.ScaleChooser
import ldv.shuuen.features.training.common.components.StylePickerSheet
import ldv.shuuen.features.training.domain.LevelConfig

@Composable
fun ChordsSetupScreen(
    viewModel: ChordsSetupScreenViewModel,
    onNavigateBack: () -> Unit,
    onOpenContext: () -> Unit,
    onSaveLevel: () -> Unit,
) {
  val saveableScreenState by viewModel.screenState.collectAsStateWithLifecycle()
  val isEditing = viewModel.isEditing
  StaticScreenFrame(
      verticalSpacing = 22.dp,
      topBar = {
        ShuuenTopAppBar(
            title = if (isEditing) "EDIT CHORDS" else "CHORDS SETUP",
            subtitle =
                if (isEditing) "Update this chord training level."
                else "Create a custom chord training level.",
            onBack = onNavigateBack,
            type = ShuuenTopAppBarType.Labeled,
        )
      },
  ) {
    val levelConfig = saveableScreenState.levelConfig
    val config =
        when (levelConfig) {
          is LevelConfig.Chords.Relative -> levelConfig.scaleConfig
          is LevelConfig.Chords.Absolute -> levelConfig.scales.first()
        }

    ScaleChooser(
        scaleConfig = config,
        onScaleChosen = viewModel::changeScale,
    )

    // Scale rotation only applies to a random (relative) scale — a fixed
    // tonic has nothing to rotate. Stepping the control below 5 turns it off.
    if (levelConfig is LevelConfig.Chords.Relative) {
      FlatSection(
          label = "SCALE ROTATION",
          supporting = "Off, or move to a new random tonic every few questions.",
      ) {
        SegmentedPlusMinus(value = levelConfig.rotateEveryQuestions, onChange = viewModel::changeRotateEveryQuestions, delta = 5, nullCondition = {
          (it.toIntOrNull() ?: 0) <= 0
        }, nullLabel = "Off")
      }
    }

    Hairline()

    NavigationSectionRow(
        label = "2 · CONTEXT",
        supporting =
            saveableScreenState.context?.let { "Using context ${it.id}" }
                ?: "Open context screen to configure.",
        onClick = onOpenContext,
    )

    Hairline()

    FlatSection(
        label = "3 · NUMBER OF QUESTIONS",
        supporting = "Set how many questions to include.",
    ) {
      SegmentedPlusMinus(
          value = saveableScreenState.questionsNumber,
          onChange = viewModel::changeQuestionsNumber,
          minimalNumber = 0,
      )
    }

    Hairline()

    FlatSection(
        label = "4 · NOTES PER CHORD",
        supporting = "How many notes sound at once; a range picks a random size each question.",
    ) {
      ChordSizeRow("Min", saveableScreenState.chordSize.min, viewModel::changeChordSizeMin)
      ChordSizeRow("Max", saveableScreenState.chordSize.max, viewModel::changeChordSizeMax)
    }

    Hairline()

    var showStyleSheet by remember { mutableStateOf(false) }
    NavigationSectionRow(
        label = "5 · CHORD SHAPES",
        supporting =
            "${saveableScreenState.levelConfig.chordStyle.name} · ${saveableScreenState.levelConfig.chordStyle.tier.label}",
        onClick = { showStyleSheet = true },
    )
    if (showStyleSheet) {
      StylePickerSheet(
          title = "Chord shapes",
          subtitle =
              "How the random chords are built: from strictly diatonic stacks to fully free note piles. Shapes that don't fit the chord-size range above are skipped.",
          icon = Icons.Rounded.MusicNote,
          presets = ChordStyles.presets,
          selectedId = saveableScreenState.levelConfig.chordStyle.id,
          onSelect = { style ->
            viewModel.changeChordStyle(style)
            showStyleSheet = false
          },
          onDismiss = { showStyleSheet = false },
      )
    }

    Hairline()

    FlatSection(label = "6 · PLAYBACK") {
      SoftControl(
          modifier = Modifier.fillMaxWidth(),
          selected = saveableScreenState.sustainNotes,
          onClick = { viewModel.changeSustainNotes(!saveableScreenState.sustainNotes) },
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              "Sustain chord",
              color = ShuuenUi.Text,
              style = MaterialTheme.typography.titleSmall,
          )
          Text(
              if (saveableScreenState.sustainNotes) "Hold until the next question or a replay."
              else "Release after about two seconds.",
              color = ShuuenUi.Muted,
              style = MaterialTheme.typography.bodySmall,
          )
        }
        ShuuenSwitch(
            checked = saveableScreenState.sustainNotes,
            onCheckedChange = null,
        )
      }
    }

    Hairline()

    FlatSection(
        label = "7 · ANSWER ORDER",
        supporting = "Which order the chord's notes must be answered in.",
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ChordAnswerOrder.entries.forEach { order ->
          PillControl(
              text = order.label,
              selected = saveableScreenState.answerOrder == order,
              onClick = { viewModel.changeAnswerOrder(order) },
              modifier = Modifier.weight(1f),
          )
        }
      }
    }

    Hairline()

    FlatSection(
        label = "8 · RANGE",
        supporting = "Select the note range.",
    ) {
      Text(
          text = "${saveableScreenState.range.from} - ${saveableScreenState.range.to}",
          style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 3.sp),
          modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      NoteRow(value = saveableScreenState.range.from) { viewModel.changeRangeStart(it) }
      NoteRow(value = saveableScreenState.range.to) { viewModel.changeRangeEnd(it) }
    }

    val scope = rememberCoroutineScope()
    PrimaryCta(
        text = if (isEditing) "SAVE CHANGES" else "SAVE LEVEL",
        onClick = {
          scope.launch {
            viewModel.upsertLevel()
            onSaveLevel()
          }
        },
        modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
        icon = Icons.Rounded.Save,
    )
  }
}

@Composable
private fun ChordSizeRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = label,
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
    )
    SegmentedPlusMinus(
        value = value,
        onChange = { it?.let(onChange) },
        delta = 1,
        minimalNumber = ChordSizeRange.MinSize,
    )
  }
}

@Composable
private fun NavigationSectionRow(
    label: String,
    supporting: String,
    onClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
          text = label,
          color = ShuuenUi.Muted,
          style =
              MaterialTheme.typography.labelLarge.copy(
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
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(26.dp),
    )
  }
}
