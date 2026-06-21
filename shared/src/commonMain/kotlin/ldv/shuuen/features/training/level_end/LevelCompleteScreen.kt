package ldv.shuuen.features.training.level_end

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard

private data class LevelCompleteData(
  val levelTitle: String,
  val flowLabel: String,
  val key: String,
  val parameterDescription: String,
  val parameters: List<Pair<ImageVector, String>>,
)

@Composable
fun LevelCompleteScreen(
  flow: TrainingFlow,
  onNavigateBack: () -> Unit,
  onRetryLevel: () -> Unit,
  onNextLevel: () -> Unit,
) {
  val data = levelCompleteData(flow)
  StaticScreenFrame(
    maxWidth = 920.dp,
    verticalSpacing = 18.dp,
    topBar = {
      ShuuenTopAppBar(
        title = "SESSION COMPLETE",
        onBack = onNavigateBack,
        trailingIcon = Icons.Rounded.Share,
        type = ShuuenTopAppBarType.Simple,
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            CompletionTitle(data)
            ScoreHero(data)
            CompletionActions(
              onRetryLevel = onRetryLevel,
              onNextLevel = onNextLevel,
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            PerformanceOverview()
            Hairline()
            LevelParameters(data)
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          CompletionTitle(data)
          ScoreHero(data)
          CompletionActions(
            onRetryLevel = onRetryLevel,
            onNextLevel = onNextLevel,
          )
          PerformanceOverview()
          Hairline()
          LevelParameters(data)
        }
      }
    }
  }
}

private fun levelCompleteData(flow: TrainingFlow): LevelCompleteData {
  return when (flow) {
    TrainingFlow.Singles -> LevelCompleteData(
      levelTitle = "Level 2 - Triad Tones",
      flowLabel = "Singles",
      key = "D Major",
      parameterDescription = "Root, third, and fifth of the triad.",
      parameters = listOf(
        Icons.Rounded.MusicNote to "3 notes",
        Icons.AutoMirrored.Rounded.HelpOutline to "20 questions",
        Icons.Rounded.Speed to "96 BPM",
        Icons.Rounded.Keyboard to "C3-C5",
        Icons.Rounded.Replay to "Random",
        Icons.Rounded.TrackChanges to "D Major",
      ),
    )

    TrainingFlow.Melodies -> LevelCompleteData(
      levelTitle = "Level 2 - Triad Motifs",
      flowLabel = "Melodies",
      key = "D Major",
      parameterDescription = "Four-note melodies built from triad tones.",
      parameters = listOf(
        Icons.Rounded.MusicNote to "4 notes",
        Icons.AutoMirrored.Rounded.HelpOutline to "20 questions",
        Icons.Rounded.Speed to "96 BPM",
        Icons.Rounded.Keyboard to "C3-C5",
        Icons.Rounded.Replay to "Random",
        Icons.Rounded.TrackChanges to "D Major",
      ),
    )
  }
}

@Composable
private fun CompletionTitle(data: LevelCompleteData) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = data.levelTitle,
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
      textAlign = TextAlign.Center,
    )
    Text(
      text = "${data.flowLabel} • ${data.key}",
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun ScoreHero(data: LevelCompleteData) {
  SurfaceCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      ScoreRing()
      ScoreSummary(data, Modifier.weight(1f))
    }
  }
}

@Composable
private fun ScoreRing() {
  Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(116.dp)) {
      val stroke = 7.dp.toPx()
      drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
      drawArc(
        color = ShuuenUi.Text,
        startAngle = -88f,
        sweepAngle = 245f,
        useCenter = false,
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "68%",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.displayLarge.copy(
          fontSize = 32.sp, fontWeight = FontWeight.Bold
        ),
      )
      Text(
        text = "SCORE",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.labelLarge.copy(
          letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold
        ),
      )
    }
  }
}

@Composable
private fun ScoreSummary(data: LevelCompleteData, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "14 / 20 CORRECT",
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.titleLarge.copy(
        letterSpacing = 2.sp, fontWeight = FontWeight.Bold
      ),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      "${data.flowLabel} level result",
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun PerformanceOverview() {
  FlatSection(label = "PERFORMANCE OVERVIEW") {
    StatsGrid()
    Text(
      text = "ACCURACY BY QUESTION RANGE",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelMedium.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
    AccuracyRangeBar()
  }
}

@Composable
private fun StatsGrid() {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(modifier = Modifier.fillMaxWidth()) {
      StatCell(Icons.Rounded.TrackChanges, "68%", "ACCURACY", Modifier.weight(1f))
      StatCell(Icons.Rounded.Schedule, "2.8s", "AVG TIME", Modifier.weight(1f))
      StatCell(Icons.Rounded.LocalFireDepartment, "6", "BEST", Modifier.weight(1f))
    }
    Hairline()
    Row(modifier = Modifier.fillMaxWidth()) {
      StatCell(Icons.Rounded.Replay, "2", "REPLAYS", Modifier.weight(1f))
      StatCell(Icons.Rounded.Close, "6", "WRONG", Modifier.weight(1f))
      StatCell(Icons.Rounded.BarChart, "", "HOTSPOTS", Modifier.weight(1f), trailing = true)
    }
  }
}

@Composable
private fun RowScope.StatCell(
  icon: ImageVector,
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  trailing: Boolean = false,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(icon, contentDescription = null, tint = ShuuenUi.Muted, modifier = Modifier.size(18.dp))
      if (value.isNotBlank()) {
        Text(
          value,
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
          maxLines = 1,
        )
      }
      if (trailing) {
        Icon(
          Icons.Rounded.ChevronRight,
          contentDescription = null,
          tint = ShuuenUi.Dim,
          modifier = Modifier.size(22.dp)
        )
      }
    }
    Text(
      label,
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun AccuracyRangeBar() {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      listOf("80%", "70%", "60%", "50%").forEach { label ->
        Text(
          text = label,
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.titleSmall,
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth().height(14.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      listOf(0.92f, 0.65f, 0.40f, 0.20f).forEach { alpha ->
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth()
            .height(14.dp)
            .background(Color.White.copy(alpha = alpha), MaterialTheme.shapes.extraSmall),
        )
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      listOf("1-5", "6-10", "11-15", "16-20").forEach {
        Text(it, color = ShuuenUi.Dim, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun LevelParameters(data: LevelCompleteData) {
  FlatSection(
    label = "LEVEL PARAMETERS",
    supporting = data.parameterDescription,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val compact = maxWidth < 420.dp
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        data.parameters.chunked(if (compact) 2 else 3).forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            row.forEach { (icon, text) ->
              ParameterChip(icon, text, Modifier.weight(1f))
            }
            repeat((if (compact) 2 else 3) - row.size) {
              Box(Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ParameterChip(
  icon: ImageVector,
  text: String,
  modifier: Modifier = Modifier,
) {
  SoftControl(modifier = modifier.height(44.dp)) {
    Icon(
      icon, contentDescription = null, tint = ShuuenUi.Muted, modifier = Modifier.size(20.dp)
    )
    Text(
      text = text,
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CompletionActions(
  onRetryLevel: () -> Unit,
  onNextLevel: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    CompactCompletionButton(
      text = "RETRY LEVEL",
      icon = Icons.Rounded.PlayArrow,
      onClick = onRetryLevel,
      filled = true,
    )
    CompactCompletionButton(
      text = "NEXT LEVEL 3",
      icon = Icons.Rounded.ChevronRight,
      onClick = onNextLevel,
      filled = false,
    )
  }
}

@Composable
private fun CompactCompletionButton(
  text: String,
  icon: ImageVector,
  onClick: () -> Unit,
  filled: Boolean,
) {
  val shape = ShuuenUi.PillShape
  val contentColor = if (filled) ShuuenUi.OnInverse else ShuuenUi.Text
  Row(
    modifier = Modifier.fillMaxWidth(0.74f).widthIn(max = 360.dp).height(50.dp).clip(shape)
      .background(if (filled) ShuuenUi.Inverse else Color.Transparent)
      .border(1.dp, if (filled) Color.Transparent else ShuuenUi.HairlineStrong, shape)
      .clickable(onClick = onClick).padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = text,
      color = contentColor,
      style = MaterialTheme.typography.titleMedium.copy(
        letterSpacing = 3.sp,
        fontWeight = FontWeight.Bold,
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
