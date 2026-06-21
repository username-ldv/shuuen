package ldv.shuuen.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun LinearTrainingProgress(
  progress: Float,
  modifier: Modifier = Modifier.Companion,
  color: Color = ldv.shuuen.core.ui.components.ShuuenUi.Text,
) {
  Canvas(
    modifier = modifier.fillMaxWidth().height(4.dp),
  ) {
    val stroke = size.height
    val y = size.height / 2f
    drawLine(
      color = Color.White.copy(alpha = 0.10f),
      start = Offset(0f, y),
      end = Offset(size.width, y),
      strokeWidth = stroke,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = color,
      start = Offset(0f, y),
      end = Offset(size.width * progress.coerceIn(0f, 1f), y),
      strokeWidth = stroke,
      cap = StrokeCap.Round,
    )
  }
}
