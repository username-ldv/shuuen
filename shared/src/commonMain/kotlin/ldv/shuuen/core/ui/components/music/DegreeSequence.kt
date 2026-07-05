package ldv.shuuen.core.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.DirectedDegree
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.stepLabels
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * Building blocks for editing degree sequences (context nodes, setup melodies).
 * Pure UI: callers own the sequence state.
 */


@Composable
fun DegreeChip(
  label: String,
  modifier: Modifier = Modifier,
  inverted: Boolean = false,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val shape = ShuuenUi.ControlShape
  val currentOnClick by rememberUpdatedState(onClick)
  Box(
    modifier = modifier.height(34.dp).widthIn(min = 38.dp).clip(shape)
      .background(if (inverted) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
      .then(
        if (selected) {
          Modifier.border(
            width = 1.dp,
            color = if (inverted) ShuuenUi.OnInverse.copy(alpha = 0.28f) else ShuuenUi.Text,
            shape = shape,
          )
        } else {
          Modifier
        }
      )
      .then(
        if (onClick != null) {
          Modifier.clickable { currentOnClick?.invoke() }
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = if (inverted) ShuuenUi.OnInverse else ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
    )
  }
}

/** All twelve degrees in chromatic order; callers decide whether a pick appends or edits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DegreePalette(
  onPick: (Degree) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnPick by rememberUpdatedState(onPick)
  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Degree.chromaticOrder.forEach { degree ->
      DegreeChip(
        label = degree.label,
        onClick = { currentOnPick(degree) },
      )
    }
  }
}

/**
 * The built sequence rendered as inverted chips, with an optional backspace control.
 * When [selectedIndex] is set, only that chip is inverted so editors can show replacement focus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DegreeSequenceChips(
  labels: List<String>,
  modifier: Modifier = Modifier,
  emptyPlaceholder: String = "—",
  selectedIndex: Int? = null,
  insertAfterSelected: Boolean = false,
  onChipClick: ((index: Int) -> Unit)? = null,
  onInsertAfterSelected: (() -> Unit)? = null,
  onBackspace: (() -> Unit)? = null,
) {
  val selectedChipIndex = selectedIndex?.takeIf { it in labels.indices }
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FlowRow(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (labels.isEmpty()) {
        DegreeChip(label = emptyPlaceholder)
      } else {
        labels.forEachIndexed { index, label ->
          val selected = selectedChipIndex == index
          DegreeChip(
            label = label,
            inverted = selectedChipIndex == null || selected,
            selected = selected,
            onClick = onChipClick?.let { { it(index) } },
          )
        }
      }
    }
    if (onInsertAfterSelected != null) {
      SequenceIconButton(
        imageVector = Icons.Rounded.Add,
        contentDescription = "Insert after selected degree",
        selected = insertAfterSelected,
        onClick = onInsertAfterSelected,
      )
    }
    if (onBackspace != null) {
      SequenceIconButton(
        imageVector = Icons.AutoMirrored.Rounded.Backspace,
        contentDescription = "Remove degree",
        onClick = onBackspace,
      )
    }
  }
}

@Composable
private fun SequenceIconButton(
  imageVector: ImageVector,
  contentDescription: String,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  val currentOnClick by rememberUpdatedState(onClick)
  Box(
    modifier = Modifier.size(34.dp).clip(ShuuenUi.ControlShape)
      .background(if (selected) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
      .clickable { currentOnClick() },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = contentDescription,
      tint = if (selected) ShuuenUi.OnInverse else ShuuenUi.Muted,
      modifier = Modifier.size(18.dp),
    )
  }
}

/**
 * Inline editor for a plain degree sequence (e.g. a node's setup melody):
 * current chips on top, palette below to append.
 */
@Composable
fun DegreeSequenceEditor(
  degrees: List<Degree>,
  onAppend: (Degree) -> Unit,
  onBackspace: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DegreeSequenceChips(
      labels = degrees.map { it.label },
      onBackspace = onBackspace,
    )
    DegreePalette(onPick = onAppend)
  }
}

/**
 * Inline editor for a directed degree sequence (setup melodies that can move up and down).
 * The ↑/↓ toggle picks the direction applied to newly added degrees or updates the selected step.
 * The first step is the anchor and has no direction.
 */
@Composable
fun DirectedDegreeSequenceEditor(
  steps: RelativeMelody?,
  onChange: (RelativeMelody?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var inputDirection by remember { mutableStateOf(DegreeDirection.Up) }
  var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
  var insertAfterSelected by rememberSaveable { mutableStateOf(false) }
  val labels = steps?.stepLabels() ?: listOf()
  val selectedStepIndex = selectedIndex?.takeIf { it in labels.indices }
  val selectedDirection =
    selectedStepIndex?.takeIf { it > 0 }?.let { index ->
      steps?.extraDegrees?.getOrNull(index - 1)?.direction
    }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DegreeSequenceChips(
      labels = labels,
      selectedIndex = selectedStepIndex,
      insertAfterSelected = insertAfterSelected,
      onChipClick = {
        selectedIndex = if (selectedStepIndex == it) null else it
        insertAfterSelected = false
      },
      onInsertAfterSelected =
        steps?.let {
          {
            selectedIndex = selectedStepIndex ?: labels.lastIndex
            insertAfterSelected = !insertAfterSelected
          }
        },
      onBackspace = {
        steps?.let { melody ->
          val selected = selectedStepIndex
          val updated =
            when {
              melody.extraDegrees.isEmpty() -> null
              selected != null && selected > 0 ->
                melody.copy(
                  extraDegrees =
                    melody.extraDegrees.toMutableList().also { it.removeAt(selected - 1) }
                )
              else -> melody.copy(extraDegrees = melody.extraDegrees.dropLast(1))
          }
          onChange(updated)
          selectedIndex = selectedIndex?.coerceAtMost((updated?.extraDegrees?.size ?: 0))
          insertAfterSelected = false
        }
      },
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DegreeDirection.entries.forEach { direction ->
        DegreeChip(
          label = direction.arrow,
          inverted = direction == (selectedDirection ?: inputDirection),
          onClick = {
            val selected = selectedStepIndex
            if (selected != null && selected > 0 && steps != null) {
              onChange(
                steps.copy(
                  extraDegrees =
                    steps.extraDegrees.toMutableList().also {
                      it[selected - 1] = it[selected - 1].copy(direction = direction)
                    }
                )
              )
            } else {
              inputDirection = direction
            }
          },
        )
      }
      Text(
        text = "Direction for added or selected degrees.",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f),
      )
    }
    key(selectedStepIndex, insertAfterSelected, labels.size, inputDirection) {
      DegreePalette(
        onPick = { degree ->
          if (steps == null) {
            onChange(RelativeMelody(firstDegree = DegreeWithOctave(degree, 3)))
            insertAfterSelected = false
          } else {
            val selected = selectedStepIndex
            val updated =
              when {
                insertAfterSelected && selected != null -> {
                  val insertIndex = selected
                  steps.copy(
                    extraDegrees =
                      steps.extraDegrees.toMutableList().also {
                        it.add(insertIndex, DirectedDegree(degree, inputDirection))
                      }
                  )
                }
                selected == 0 -> steps.copy(firstDegree = steps.firstDegree.copy(degree = degree))
                selected != null && selected > 0 ->
                  steps.copy(
                    extraDegrees =
                      steps.extraDegrees.toMutableList().also {
                        it[selected - 1] = it[selected - 1].copy(degree = degree)
                      }
                  )
                else ->
                  steps.copy(
                    extraDegrees = steps.extraDegrees + DirectedDegree(degree, inputDirection)
                  )
              }
            onChange(updated)
            if (insertAfterSelected && selected != null) {
              selectedIndex = selected + 1
              insertAfterSelected = false
            }
          }
        },
      )
    }
  }
}

/** Compact stepper for the first degree's octave. */
@Composable
fun OctaveStepper(
  value: Int,
  onChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  range: IntRange = 0..8,
) {
  Row(
    modifier = modifier.height(34.dp).clip(ShuuenUi.PillShape)
      .background(Color.White.copy(alpha = 0.05f)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StepperPiece("−") { if (value > range.first) onChange(value - 1) }
    Text(
      text = "Oct $value",
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.titleSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 48.dp),
      maxLines = 1,
    )
    StepperPiece("+") { if (value < range.last) onChange(value + 1) }
  }
}

@Composable
private fun StepperPiece(text: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier.size(34.dp).clip(ShuuenUi.ControlShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = text, color = ShuuenUi.Muted, style = MaterialTheme.typography.titleMedium)
  }
}
