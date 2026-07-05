package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.SetupMelodyRepeat
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.music.DegreeSequenceChips
import ldv.shuuen.features.training.domain.LevelSource

/**
 * A level card's parameter chips, wrapping onto extra rows when they don't fit — a plain row would
 * silently clip the trailing chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelParametersFlow(
    items: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier,
) {
  FlowRow(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items.forEach { (text, icon) ->
      LevelParameter(text, icon)
    }
  }
}

/** A compact icon + text pair shown in a level card's parameter row. */
@Composable
fun LevelParameter(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = text,
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

/** A label/value line inside a level card's expanded details. */
@Composable
fun DetailRow(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DetailLabel(label, modifier = Modifier.weight(1f))
    Text(
        text = value,
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun DetailLabel(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text,
      color = ShuuenUi.Dim,
      style =
          MaterialTheme.typography.labelSmall.copy(
              letterSpacing = ShuuenUi.labelSpacing,
              fontWeight = FontWeight.SemiBold,
          ),
      modifier = modifier,
  )
}

/** The level's degree context, node by node, as shown in expanded level details. */
@Composable
fun ContextDetails(context: DegreeContext) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    context.name?.let {
      Text(
          text = it,
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodyMedium,
      )
    }
    context.nodes.forEachIndexed { index, node ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val durationText =
          when (val d = node.duration) {
            is ContextDuration.SameAsScaleRotation -> "Same as scale"
            is ContextDuration.Endless -> "Endless"
            is ContextDuration.Finite -> "${d.durationInQuestions} questions"
            is ContextDuration.Immediate -> "Immediate"
          }
        Text(
            text = "Node ${index + 1} · ${sustainLabel(node.sustain)} · $durationText",
            color = ShuuenUi.Dim,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        )
        val labels = node.degreeLabels(index)
        DegreeSequenceChips(labels = labels)
      }
      node.setupMelody?.let { setupMelody ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
              text = "Node ${index + 1} · Setup melody · ${repeatLabel(setupMelody.repeat)}",
              color = ShuuenUi.Dim,
              style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
          )
          DegreeSequenceChips(
              labels =
                  listOf(setupMelody.melody.firstDegree.toString()) +
                      setupMelody.melody.extraDegrees.map { it.toString() }
          )
        }
      }
    }
  }
}

fun sourceLabel(source: LevelSource): String =
    when (source) {
      LevelSource.BuiltIn -> "Built-in"
      LevelSource.User -> "Custom"
      LevelSource.Imported -> "Imported"
    }

private fun sustainLabel(sustain: Sustain): String =
    when (sustain) {
      is Sustain.Endless -> "Sustained"
      is Sustain.Finite -> "Timed"
    }

private fun repeatLabel(repeat: SetupMelodyRepeat): String =
    when (repeat) {
      SetupMelodyRepeat.Once -> "Once"
      SetupMelodyRepeat.EveryTime -> "Every time"
    }

private fun DegreeContextNode.degreeLabels(index: Int): List<String> {
  val first =
      if (index == 0) {
        firstDegree.toString()
      } else {
        "${firstDegree.degree.label} ${relativeDirection.arrow}"
      }
  return listOf(first) + extraDegrees.map { it.label }
}
