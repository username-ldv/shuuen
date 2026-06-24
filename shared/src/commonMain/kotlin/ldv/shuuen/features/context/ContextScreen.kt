package ldv.shuuen.features.context

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
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
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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

private val TimedSustain = Sustain.Finite(1.seconds)

private const val standardTempo = 90

private fun sequenceNode(
    firstDegree: DegreeWithOctave = DegreeWithOctave(Degree.D1, 3),
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
fun ContextScreen(onNavigateBack: () -> Unit, onContextChosen: (DegreeContext) -> Unit) {
  var context by remember { mutableStateOf(editableContext(sequencePresets[0].nodes)) }
  val nodes = context.nodes

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
      PresetRow(onApply = { context = context.copy(nodes = it) })
      PreviewFullSequence()
      nodes.forEachIndexed { index, node ->
        SequenceNodeCard(
            number = index + 1,
            isLast = index == nodes.lastIndex,
            node = node,
            onNodeChange = { updated ->
              context = context.copy(nodes = nodes.toMutableList().also { it[index] = updated })
            },
            onDelete =
                if (nodes.size > 1) {
                  {
                    context =
                        context.copy(nodes = nodes.toMutableList().also { it.removeAt(index) })
                  }
                } else null,
        )
      }
      DashedAddButton(
          text = "ADD NODE",
          onClick = { context = context.copy(nodes = nodes + sequenceNode()) },
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
private fun PreviewFullSequence() {
  SoftControl(modifier = Modifier.fillMaxWidth()) {
    PlayBubble()
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
) {
  SurfaceCard {
    Box(modifier = Modifier.fillMaxWidth()) {
      if (onDelete != null) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Delete node",
            tint = ShuuenUi.Dim,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(ShuuenUi.ControlShape)
                    .clickable(onClick = onDelete),
        )
      }
      Row(
          modifier = Modifier.fillMaxWidth().padding(end = 30.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        NodeNumber(number = number)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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

          NodeDegreesEditor(node = node, onNodeChange = onNodeChange)
          SustainRow(
              sustain = node.sustain is Sustain.Endless,
              onChange = {
                onNodeChange(node.copy(sustain = if (it) Sustain.Endless else TimedSustain))
              },
          )
          SetupMelodyRow(
              melody = node.setupMelody?.melody,
              onChange = {
                onNodeChange(
                    node.copy(
                        setupMelody =
                            if (it != null)
                                SetupMelody(melody = it, repeat = SetupMelodyRepeat.Once)
                            else null
                    )
                )
              },
          )
          NodePreviewRow()
          SetupMelodyPreviewRow()
          // todo: make it be 4 choices:
          // 1) Finite (has some amount of questions before going to the next node or to the start,
          // from 1 to some number)
          // 2) Immediate (to make Cadence-like Context's from Nodes, like a certain chord
          // progression playing Nodes immediately one after each other, like in FET currently)
          // 3) Endless (this node will continue/idle indefinitely to the end of the Quiz level,
          // removing/ignoring all next nodes if they exist)
          // 4) Rotate (go next node) exactly when the scale will rotate the root (when you've
          // chosen multiple or Random scale in the level setup screen)
          InlineCounter(
              label = if (isLast) "QUESTIONS BEFORE RESTART" else "QUESTIONS BEFORE NEXT",
              value = (node.duration as? ContextDuration.Finite)?.durationInQuestions ?: 1,
              onChange = { onNodeChange(node.copy(duration = ContextDuration.Finite(it))) },
          )
        }
      }
    }
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
    melody: RelativeMelody?,
    onChange: (RelativeMelody?) -> Unit,
) {
  var editing by rememberSaveable { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    AnimatedVisibility(visible = editing) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        melody?.let { melody ->
          GroupLabel("FIRST DEGREE OCTAVE")
          OctaveStepper(
              melody.firstDegree.octave,
              { onChange(melody.copy(firstDegree = melody.firstDegree.copy(octave = it))) },
          )
        }

        DirectedDegreeSequenceEditor(
            steps = melody,
            onChange = onChange,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun NodePreviewRow() {
  SoftControl(modifier = Modifier.fillMaxWidth()) {
    PlayBubble()
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
private fun SetupMelodyPreviewRow() {
  SoftControl(modifier = Modifier.fillMaxWidth()) {
    PlayBubble()
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
private fun PlayBubble() {
  Box(
      modifier = Modifier.size(34.dp).clip(CircleShape).background(ShuuenUi.Inverse),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = Icons.Rounded.PlayArrow,
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
