package ldv.shuuen.features.context

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aakira.napier.Napier
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.DirectedDegree
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.SetupMelody
import ldv.shuuen.core.music.SetupMelodyRepeat
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.music.Timing
import ldv.shuuen.core.music.stepLabels
import ldv.shuuen.core.ui.components.CompactDropdownMenu
import ldv.shuuen.core.ui.components.DashedAddButton
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenSwitch
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard
import ldv.shuuen.core.ui.components.music.DegreePalette
import ldv.shuuen.core.ui.components.music.DegreeSequenceChips
import ldv.shuuen.core.ui.components.music.DirectedDegreeSequenceEditor
import ldv.shuuen.core.ui.components.music.OctaveStepper
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val TimedSustain = Sustain.Finite(1.seconds)

private const val standardTempo = 90

private fun sequenceNode(
    firstDegree: DegreeWithOctave = DegreeWithOctave(Degree.D1, 2),
    extraDegrees: List<Degree> = listOf(),
    sustain: Sustain = Sustain.Endless,
    duration: ContextDuration = ContextDuration.SameAsScaleRotation,
    setupMelody: SetupMelody? = null,
) =
    DegreeContextNode(
        firstDegree = firstDegree,
        extraDegrees = extraDegrees,
        sustain = sustain,
        duration = duration,
        setupMelody = setupMelody,
    )

private data class SequencePreset(val label: String, val nodes: List<DegreeContextNode>)

private val sequencePresets =
    listOf(
        SequencePreset(
            label = "Drone",
            nodes =
                listOf(
                    sequenceNode(
                        firstDegree = DegreeWithOctave(Degree.D1, 2),
                        extraDegrees = emptyList(),
                        sustain = Sustain.Endless,
                        setupMelody =
                            SetupMelody(
                                melody =
                                    RelativeMelody(
                                        firstDegree = DegreeWithOctave(Degree.D1, 3),
                                        extraDegrees =
                                            listOf(
                                                DirectedDegree(Degree.D3, DegreeDirection.Up),
                                                DirectedDegree(Degree.D5, DegreeDirection.Up),
                                                DirectedDegree(Degree.D1, DegreeDirection.Up),
                                            ),
                                    ),
                                repeat = SetupMelodyRepeat.Once,
                            ),
                    ),
                ),
        ),
        SequencePreset(
            label = "I-IV-V-I",
            nodes =
                listOf(
                    sequenceNode(
                        DegreeWithOctave(Degree.D1, 3),
                        listOf(Degree.D3, Degree.D5),
                        Sustain.Finite(Timing(standardTempo).quarter()),
                        duration = ContextDuration.Immediate,
                    ),
                    sequenceNode(
                        DegreeWithOctave(Degree.D4, 3),
                        listOf(Degree.D6, Degree.D1),
                        Sustain.Finite(Timing(standardTempo).quarter()),
                        duration = ContextDuration.Immediate,
                    ),
                    sequenceNode(
                        DegreeWithOctave(Degree.D5, 3),
                        listOf(Degree.D7, Degree.D2),
                        Sustain.Finite(Timing(standardTempo).quarter()),
                        duration = ContextDuration.Immediate,
                    ),
                    sequenceNode(
                        DegreeWithOctave(Degree.D1, 3),
                        listOf(Degree.D3, Degree.D5),
                        Sustain.Finite(Timing(standardTempo).half()),
                        duration = ContextDuration.SameAsScaleRotation,
                    ),
                ),
        ),
        SequencePreset(
            label = "ii-V-I",
            nodes =
                listOf(
                    sequenceNode(
                        DegreeWithOctave(Degree.D2, 3),
                        listOf(Degree.D4, Degree.D6),
                        Sustain.Finite(Timing(standardTempo).quarter()),
                        duration = ContextDuration.Immediate,
                    ),
                    sequenceNode(
                        DegreeWithOctave(Degree.D5, 3),
                        listOf(Degree.D7, Degree.D2),
                        Sustain.Finite(Timing(standardTempo).quarter()),
                        duration = ContextDuration.Immediate,
                    ),
                    sequenceNode(
                        DegreeWithOctave(Degree.D1, 3),
                        listOf(Degree.D3, Degree.D5),
                        Sustain.Finite(Timing(standardTempo).half()),
                        duration = ContextDuration.SameAsScaleRotation,
                    ),
                ),
        ),
    )

@OptIn(ExperimentalUuidApi::class)
private fun editableContext(nodes: List<DegreeContextNode>): DegreeContext =
    DegreeContext(
        id = Uuid.generateV7().toString(),
        source = ContextSource.UserLocal,
        nodes = nodes,
    )

@Composable
fun ContextScreen(
  onNavigateBack: () -> Unit,
  onContextChosen: (DegreeContext) -> Unit,
  viewModel: ContextViewModel,
) {
  var context by remember { mutableStateOf(editableContext(sequencePresets[0].nodes)) }
  val nodes = context.nodes
  val playingNodeNumber by viewModel.playingNodeNumber.collectAsStateWithLifecycle()
  val playingMelody by viewModel.playingMelody.collectAsStateWithLifecycle()
  val playingFullSequence by viewModel.playingFullSequence.collectAsStateWithLifecycle()

  StaticScreenFrame(
      verticalSpacing = 18.dp,
      topBar = {
        ShuuenTopAppBar(
            title = "CONTEXT",
            subtitle = "Configure the listening context.",
            onBack = onNavigateBack,
            type = ShuuenTopAppBarType.Labeled,
        )
      },
  ) {
    FlatSection(
        label = "SEQUENCE",
        supporting =
            "Build the progression played during training. After the last node, the sequence returns to the first node.",
        trailing = {
          Text(
              text = "${nodes.size} ${if (nodes.size == 1) "node" else "nodes"}",
              color = ShuuenUi.Muted,
              style = MaterialTheme.typography.labelLarge,
          )
        },
    ) {
      PresetRow(
          onApply = {
            viewModel.stopPreviewsUsingSequenceNodes()
            context = context.copy(nodes = it)
          },
      )
      PreviewFullSequence(
          playing = playingFullSequence,
          onPreview = { viewModel.toggleFullSequencePreview(nodes) },
      )
      nodes.forEachIndexed { index, node ->
        SequenceNodeCard(
            number = index + 1,
            isLast = index == nodes.lastIndex,
            node = node,
            onNodeChange = { updated ->
              viewModel.stopFullSequencePreview()
              context = context.copy(nodes = nodes.toMutableList().also { it[index] = updated })
            },
            onDelete =
                if (nodes.size > 1) {
                  {
                    viewModel.stopPreviewsUsingSequenceNodes()
                    context =
                        context.copy(nodes = nodes.toMutableList().also { it.removeAt(index) })
                  }
                } else null,
            nodePlaying = playingNodeNumber == index + 1,
            setupMelodyPlaying = playingMelody,
            onPreviewNode = { viewModel.toggleNodePreview(index + 1, node) },
            onPreviewSetupMelody = viewModel::previewSetupMelody,
        )
      }
      DashedAddButton(
          text = "ADD NODE",
          onClick = {
            viewModel.stopFullSequencePreview()
            context = context.copy(nodes = nodes + sequenceNode())
          },
      )
    }

    SequenceInfoBlock()

    PrimaryCta(
        text = "SAVE CONTEXT",
        onClick = {
          onContextChosen(context)
          Napier.v { "sending context: $context" }
          onNavigateBack()
        },
        modifier = Modifier.padding(bottom = 18.dp),
    )
  }
}

@Composable
private fun PresetRow(onApply: (List<DegreeContextNode>) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = "PRESETS",
        color = ShuuenUi.Dim,
        style =
            MaterialTheme.typography.labelSmall.copy(
                letterSpacing = ShuuenUi.labelSpacing,
                fontWeight = FontWeight.SemiBold,
            ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      sequencePresets.forEach { preset ->
        SmallPill(
            text = preset.label,
            onClick = { onApply(preset.nodes) },
            modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun PreviewFullSequence(
    playing: Boolean,
    onPreview: () -> Unit,
) {
  SoftControl(modifier = Modifier.fillMaxWidth(), onClick = onPreview) {
    PlayBubble(playing)
    Text(
        text = "Preview full sequence",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    //    MiniWaveform(Modifier.width(126.dp).height(30.dp), pieces = 4)
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
private fun SequenceNodeCard(
    number: Int,
    isLast: Boolean,
    node: DegreeContextNode,
    onNodeChange: (DegreeContextNode) -> Unit,
    onDelete: (() -> Unit)?,
    nodePlaying: Boolean,
    setupMelodyPlaying: Boolean,
    onPreviewNode: () -> Unit,
    onPreviewSetupMelody: (RelativeMelody) -> Unit,
) {
  val sustainEnabled = node.sustain is Sustain.Endless

  SurfaceCard {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(end = 30.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        NodeNumber(number = number)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "NODE $number",
            color = ShuuenUi.Text,
            style =
              MaterialTheme.typography.titleMedium.copy(
                letterSpacing = ShuuenUi.titlesSpacing,
                fontWeight = FontWeight.SemiBold,
              ),
          )
          Text(
            text = if (isLast) "Plays before restart" else "Plays before Node ${number + 1}",
            color = ShuuenUi.Muted,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (onDelete != null) {
          Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Delete node",
            tint = ShuuenUi.Dim,
            modifier =
              Modifier
                .size(24.dp)
                .clip(ShuuenUi.ControlShape)
                .clickable(onClick = onDelete),
          )
        }
      }
      val spacing = 14.dp
      Column(
//        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        NodeDegreesEditor(node = node, onNodeChange = onNodeChange)
        Spacer(modifier = Modifier.height(spacing))
        SustainRow(
          sustain = sustainEnabled,
          onChange = { enabled ->
            val updatedDuration =
                if (enabled && node.duration == ContextDuration.Immediate) {
                  ContextDuration.SameAsScaleRotation
                } else {
                  node.duration
                }

            onNodeChange(
              node.copy(
                sustain =
                  if (enabled) Sustain.Endless
                  else Sustain.Finite(Timing(standardTempo).quarter()),
                duration = updatedDuration,
              )
            )
          },
        )
        Spacer(modifier = Modifier.height(spacing))
        SetupMelodyRow(
          setupMelody = node.setupMelody,
          onChange = { onNodeChange(node.copy(setupMelody = it)) },
          playing = setupMelodyPlaying,
          onPreview = onPreviewSetupMelody,
        )
        Spacer(modifier = Modifier.height(spacing))
        NodePreviewRow(playing = nodePlaying, onPreview = onPreviewNode)
        Spacer(modifier = Modifier.height(spacing))
        DurationPicker(
            duration = node.duration,
            immediateVisible = !sustainEnabled,
            onDurationChange = { onNodeChange(node.copy(duration = it)) },
        )
        AnimatedVisibility(
            visible = node.duration is ContextDuration.Finite,
        ) {
          Column {
            Spacer(modifier = Modifier.height(spacing))
            InlineCounter(
              label = if (isLast) "QUESTIONS BEFORE RESTART" else "QUESTIONS BEFORE NEXT",
              value = (node.duration as? ContextDuration.Finite)?.durationInQuestions ?: 4,
              onChange = { onNodeChange(node.copy(duration = ContextDuration.Finite(it))) },
            )
          }
        }
      }
    }
  }
}

private val ContextDuration.durationLabel: String
  get() =
      when (this) {
        is ContextDuration.Finite -> "Finite"
        ContextDuration.Immediate -> "Immediate"
        ContextDuration.Endless -> "Endless"
        ContextDuration.SameAsScaleRotation -> "Rotate"
      }

private fun ContextDuration.sameModeAs(other: ContextDuration): Boolean =
    when {
      this is ContextDuration.Finite && other is ContextDuration.Finite -> true
      else -> this == other
    }

@Composable
private fun DurationPicker(
    duration: ContextDuration,
    immediateVisible: Boolean,
    onDurationChange: (ContextDuration) -> Unit,
) {
  val finiteDuration = duration as? ContextDuration.Finite ?: ContextDuration.Finite(4)
  val topDurations =
      if (immediateVisible) {
        listOf(ContextDuration.Immediate, finiteDuration)
      } else {
        listOf(finiteDuration)
      }

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(ShuuenUi.ControlShape)
              .background(Color.White.copy(alpha = 0.05f)),
  ) {
    DurationRow(
        durations = topDurations,
        selectedDuration = duration,
        onDurationChange = onDurationChange,
    )
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ShuuenUi.Hairline))
    DurationRow(
        durations = listOf(ContextDuration.SameAsScaleRotation, ContextDuration.Endless),
        selectedDuration = duration,
        onDurationChange = onDurationChange,
    )
  }
}

@Composable
private fun DurationRow(
    durations: List<ContextDuration>,
    selectedDuration: ContextDuration,
    onDurationChange: (ContextDuration) -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth().height(42.dp)) {
    durations.forEachIndexed { index, duration ->
      DurationSegment(
          duration = duration,
          selected = duration.sameModeAs(selectedDuration),
          onClick = { onDurationChange(duration) },
          modifier = Modifier.weight(1f),
      )
      if (index < durations.lastIndex) {
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(ShuuenUi.Hairline))
      }
    }
  }
}

@Composable
private fun DurationSegment(
    duration: ContextDuration,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxHeight()
              .background(if (selected) ShuuenUi.Inverse else Color.Transparent)
              .clickable(onClick = onClick)
              .padding(horizontal = 10.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = duration.durationLabel,
        color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Muted,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Degree editor for a node: the first degree picks its own octave; further degrees are appended
 * above it in ascending order (e.g. first 5·oct3 + 1 3 5 → G3 C4 E4 G4 in C major).
 */
@Composable
private fun NodeDegreesEditor(
    node: DegreeContextNode,
    onNodeChange: (DegreeContextNode) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    GroupLabel("FIRST DEGREE")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      DegreeChooser(
          node.firstDegree.degree,
          { onNodeChange(node.copy(firstDegree = node.firstDegree.copy(degree = it))) },
          modifier = Modifier.weight(1f),
      )
      OctaveStepper(
          node.firstDegree.octave,
          { onNodeChange(node.copy(firstDegree = node.firstDegree.copy(octave = it))) },
      )
    }

    GroupLabel("THEN, ASCENDING")
    DegreeSequenceChips(
        labels =
            listOf("${node.firstDegree.degree.label} · ${node.firstDegree.octave}") +
                node.extraDegrees.map { it.label },
        onBackspace = {
          if (node.extraDegrees.isNotEmpty()) {
            onNodeChange(node.copy(extraDegrees = node.extraDegrees.dropLast(1)))
          }
        },
    )
    DegreePalette(
        onPick = { onNodeChange(node.copy(extraDegrees = node.extraDegrees + it)) },
    )
  }
}

@Composable
fun DegreeChooser(
    degree: Degree,
    onSelectedDegree: (Degree) -> Unit,
    modifier: Modifier = Modifier,
) {
  CompactDropdownMenu(
      items = Degree.chromaticOrder.map { it.label },
      selectedItem = degree.label,
      onItemSelected = { name ->
        onSelectedDegree(Degree.fromName(name))
      },
      modifier = modifier,
  )
}

@Composable
private fun SustainRow(
    sustain: Boolean,
    onChange: (Boolean) -> Unit,
) {
  SoftControl(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = "Sustain",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleSmall,
      )
      Text(
          text = "Hold continuously like a drone instead of a timed chord.",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    ShuuenSwitch(checked = sustain, onCheckedChange = onChange)
  }
}

@Composable
private fun SetupMelodyRow(
    setupMelody: SetupMelody?,
    onChange: (SetupMelody?) -> Unit,
    playing: Boolean,
    onPreview: (RelativeMelody) -> Unit,
) {
  var editing by rememberSaveable { mutableStateOf(false) }
  val melody = setupMelody?.melody

  Column {
    SoftControl(
        modifier = Modifier.fillMaxWidth(),
        onClick = { editing = !editing },
    ) {
      Icon(
          imageVector = Icons.Rounded.Edit,
          contentDescription = null,
          tint = ShuuenUi.Muted,
          modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = "Setup melody",
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = melody?.stepLabels()?.joinToString(" ") ?: "None",
            color = ShuuenUi.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
      }
      Icon(
          imageVector = if (editing) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = null,
          tint = ShuuenUi.Dim,
          modifier = Modifier.size(22.dp),
      )
    }
    AnimatedVisibility(
        visible = editing,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        setupMelody?.let { setupMelody ->
          val melody = setupMelody.melody

          GroupLabel("FIRST DEGREE OCTAVE")
          OctaveStepper(
              melody.firstDegree.octave,
              {
                onChange(
                    setupMelody.copy(
                        melody = melody.copy(firstDegree = melody.firstDegree.copy(octave = it))
                    )
                )
              },
          )
        }

        DirectedDegreeSequenceEditor(
            steps = melody,
            onChange = { updatedMelody ->
              onChange(
                  updatedMelody?.let {
                    SetupMelody(melody = it, repeat = setupMelody?.repeat ?: SetupMelodyRepeat.Once)
                  }
              )
            },
            modifier = Modifier.padding(top = 2.dp),
        )
        setupMelody?.let { setupMelody ->
          GroupLabel("REPEAT")
          SetupMelodyRepeatPicker(
              repeat = setupMelody.repeat,
              onRepeatChange = { onChange(setupMelody.copy(repeat = it)) },
          )
        }
        SetupMelodyPreviewRow(
            onPreview = setupMelody?.let { { onPreview(it.melody) } },
            playing = playing,
        )
      }
    }
  }
}

private val SetupMelodyRepeat.repeatLabel: String
  get() =
      when (this) {
        SetupMelodyRepeat.Once -> "Once"
        SetupMelodyRepeat.EveryTime -> "Every time"
      }

@Composable
private fun SetupMelodyRepeatPicker(
    repeat: SetupMelodyRepeat,
    onRepeatChange: (SetupMelodyRepeat) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(42.dp)
              .clip(ShuuenUi.ControlShape)
              .background(Color.White.copy(alpha = 0.05f)),
  ) {
    SetupMelodyRepeat.entries.forEachIndexed { index, option ->
      SetupMelodyRepeatSegment(
          repeat = option,
          selected = option == repeat,
          onClick = { onRepeatChange(option) },
          modifier = Modifier.weight(1f),
      )
      if (index < SetupMelodyRepeat.entries.lastIndex) {
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(ShuuenUi.Hairline))
      }
    }
  }
}

@Composable
private fun SetupMelodyRepeatSegment(
    repeat: SetupMelodyRepeat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxHeight()
              .background(if (selected) ShuuenUi.Inverse else Color.Transparent)
              .clickable(onClick = onClick)
              .padding(horizontal = 10.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = repeat.repeatLabel,
        color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Muted,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun NodePreviewRow(
    playing: Boolean,
    onPreview: () -> Unit,
) {
  SoftControl(modifier = Modifier.fillMaxWidth(), onClick = onPreview) {
    PlayBubble(playing)
    Text(
        text = "Preview node",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    //    MiniWaveform(Modifier.width(120.dp).height(28.dp), pieces = 3)
  }
}

@Composable
private fun SetupMelodyPreviewRow(
    onPreview: (() -> Unit)?,
    playing: Boolean,
) {
  SoftControl(modifier = Modifier.fillMaxWidth(), onClick = onPreview) {
    PlayBubble(playing)
    Text(
        text = "Preview setup melody",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    //    MiniWaveform(Modifier.width(120.dp).height(28.dp), pieces = 3)
  }
}

@Composable
private fun GroupLabel(text: String) {
  Text(
      text = text,
      color = ShuuenUi.Dim,
      style =
          MaterialTheme.typography.labelSmall.copy(
              letterSpacing = ShuuenUi.labelSpacing,
              fontWeight = FontWeight.SemiBold,
          ),
  )
}

@Composable
private fun NodeNumber(number: Int) {
  Box(
      modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = number.toString(),
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
  }
}

@Composable
private fun InlineCounter(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = label,
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = ShuuenUi.labelSpacing),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    CompactCounter(
        value = value,
        onChange = onChange,
        modifier = Modifier.weight(1.15f),
    )
  }
}

@Composable
private fun CompactCounter(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .height(38.dp)
              .clip(ShuuenUi.PillShape)
              .background(Color.White.copy(alpha = 0.05f)),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CounterPiece("-", onClick = { if (value > 1) onChange(value - 1) })
    CounterPiece(value.toString(), modifier = Modifier.weight(1.35f))
    CounterPiece("+", onClick = { onChange(value + 1) })
  }
}

@Composable
private fun RowScope.CounterPiece(
    text: String,
    modifier: Modifier = Modifier.weight(1f),
    onClick: (() -> Unit)? = null,
) {
  Box(
      modifier =
          modifier
              .fillMaxHeight()
              .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = text, color = ShuuenUi.Text, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun SequenceInfoBlock() {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Hairline()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
          Icons.Rounded.Info,
          contentDescription = null,
          tint = ShuuenUi.Dim,
          modifier = Modifier.size(22.dp),
      )
      Text(
          text =
              "A node's first degree sets the starting octave; added degrees stack above it in " +
                  "ascending order. Example: first degree 5 · oct 3 plus 1 3 5 plays G3 C4 E4 G4 in C major. " +
                  "Sustained nodes hold like a drone; others play as timed chords.",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun SmallPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
  Box(
      modifier =
          modifier
              .height(30.dp)
              .clip(ShuuenUi.PillShape)
              .background(if (selected) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
              .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = text,
        color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Muted,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun PlayBubble(playing: Boolean = false) {
  Box(
      modifier = Modifier.size(34.dp).clip(CircleShape).background(ShuuenUi.Inverse),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = if (!playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
        contentDescription = null,
        tint = ShuuenUi.OnInverse,
        modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
private fun MiniWaveform(
    modifier: Modifier = Modifier,
    pieces: Int = 3,
) {
  Canvas(modifier = modifier) {
    val segmentWidth = size.width / (pieces * 5f)
    val centerY = size.height / 2f
    var x = segmentWidth

    repeat(pieces) {
      listOf(0.35f, 0.7f, 1f, 0.55f).forEach { heightFraction ->
        val lineHeight = size.height * heightFraction
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(x, centerY - lineHeight / 2f),
            end = Offset(x, centerY + lineHeight / 2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        x += segmentWidth
      }
      if (it < pieces - 1) {
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(x, centerY),
            end = Offset(x + segmentWidth * 0.8f, centerY),
            strokeWidth = 1.dp.toPx(),
        )
        x += segmentWidth * 1.2f
      }
    }
  }
}
