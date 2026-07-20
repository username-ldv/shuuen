package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.util.toRoundedString
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.single.play.AnswerColors

@Composable
fun LevelAccuracyLabel(
  stats: LevelAccuracyStats,
  modifier: Modifier = Modifier,
) {
  val accuracy = stats.accuracy
  Text(
    text = accuracy?.percentLabel() ?: "--",
//    color = accuracy?.let(::accuracyColor) ?: ShuuenUi.Dim,
    color = ShuuenUi.Muted,
    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    textAlign = TextAlign.End,
    maxLines = 1,
    modifier = modifier.widthIn(min = 42.dp),
  )
}

@Composable
fun LevelAccuracyStatsRow(
  stats: LevelAccuracyStats,
  modifier: Modifier = Modifier,
) {
  val accuracy = stats.accuracy
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LevelAccuracyBar(
      accuracy = accuracy,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = if (accuracy == null) "No stats" else "${stats.games}/${stats.windowSize}",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.End,
      maxLines = 1,
//      modifier = Modifier.widthIn(min = 52.dp),
    )
  }
}

@Composable
private fun LevelAccuracyBar(
  accuracy: Float?,
  modifier: Modifier = Modifier,
) {
  val trackColor = ShuuenUi.Ink.copy(alpha = 0.10f)
  Canvas(modifier = modifier.fillMaxWidth().height(7.dp)) {
    val radius = size.height / 2f
    drawRoundRect(
      color = trackColor,
      size = size,
      cornerRadius = CornerRadius(radius, radius),
    )

    val progress = accuracy?.coerceIn(0f, 1f) ?: 0f
    if (progress <= 0f) return@Canvas

    drawRoundRect(
      brush = accuracyBrush(progress),
      size = Size(size.width * progress, size.height),
      cornerRadius = CornerRadius(radius, radius),
    )
  }
}

private fun Float.percentLabel(): String = "${(this.coerceIn(0f, 1f) * 100f).toRoundedString(1)}%"

private fun accuracyBrush(accuracy: Float): Brush =
  if (accuracy >= 0.98f) {
    Brush.horizontalGradient(
      listOf(
        AccuracyRed,
        AccuracyYellow,
        AccuracyGreen,
        AccuracyPurple,
      )
    )
  } else {
    val color = accuracyColor(accuracy)
    Brush.horizontalGradient(listOf(color, color))
  }

private fun accuracyColor(accuracy: Float): Color {
  val value = accuracy.coerceIn(0f, 1f)
  return when {
    value >= 0.95f -> lerp(AccuracyGreen, AccuracyPurple, (value - 0.95f) / 0.03f)
    value >= 0.8f -> lerp(AccuracyYellow, AccuracyGreen, (value - 0.8f) / 0.18f)
    else -> lerp(AccuracyRed, AccuracyYellow, value / 0.8f)
  }
}

private val AccuracyRed = AnswerColors.Incorrect.color
private val AccuracyYellow = Color(0xFFc2cc32)
private val AccuracyGreen = AnswerColors.Correct.color
private val AccuracyPurple = Color(0xFFbf32cc)
