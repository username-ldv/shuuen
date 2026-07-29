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
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.ui.components.BoxedItemRow
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.util.toRoundedString
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.common.components.ChordStyleSummary
import ldv.shuuen.features.training.common.components.ContextDetails
import ldv.shuuen.features.training.common.components.DetailLabel
import ldv.shuuen.features.training.common.components.DetailRow
import ldv.shuuen.features.training.common.components.MelodyStyleSummary
import ldv.shuuen.features.training.common.components.sourceLabel
import ldv.shuuen.features.training.common.toBoxedItems
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.level_end.domain.AccuracyBucket
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.accuracyBuckets
import kotlin.math.round

@Composable
fun LevelCompleteScreen(
  onNavigateBack: () -> Unit,
  onRetryLevel: () -> Unit,
  onNextLevel: (levelReference: String) -> Unit,
  onLevelSelect: () -> Unit,
  viewModel: LevelCompleteViewModel,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  StaticScreenFrame(
    maxWidth = 920.dp,
    verticalSpacing = 18.dp,
    topBar = {
      ShuuenTopAppBar(
        title = "SESSION COMPLETE",
        onBack = onNavigateBack,
        type = ShuuenTopAppBarType.Simple,
      )
    },
  ) {
    when (val session = state.session) {
      is ResponseState.Loading ->
        Text(
          text = "Loading results...",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodyLarge,
        )

      is ResponseState.Error ->
        Text(
          text = "Couldn't load the session results: ${session.throwable.message}",
          color = ShuuenUi.Incorrect,
          style = MaterialTheme.typography.bodyLarge,
        )

      is ResponseState.Success ->
        LevelCompleteContent(
          session = session.result,
          level = state.level,
          nextLevelReference = state.nextLevelReference,
          onRetryLevel = onRetryLevel,
          onNextLevel = onNextLevel,
          onLevelSelect = onLevelSelect,
        )
    }
  }
}

@Composable
private fun LevelCompleteContent(
  session: TrainingSession,
  level: CompletedLevel?,
  nextLevelReference: String?,
  onRetryLevel: () -> Unit,
  onNextLevel: (levelReference: String) -> Unit,
  onLevelSelect: () -> Unit,
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
          CompletionTitle(session, level)
          ScoreHero(session)
          CompletionActions(
            onRetryLevel = onRetryLevel,
            nextLevelReference = nextLevelReference,
            onNextLevel = onNextLevel,
            onLevelSelect = onLevelSelect,
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          PerformanceOverview(session)
          if (level != null) {
            Hairline()
            LevelParameters(level)
          }
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        CompletionTitle(session, level)
        ScoreHero(session)
        CompletionActions(
          onRetryLevel = onRetryLevel,
          nextLevelReference = nextLevelReference,
          onNextLevel = onNextLevel,
          onLevelSelect = onLevelSelect,
        )
        PerformanceOverview(session)
        if (level != null) {
          Hairline()
          LevelParameters(level)
        }
      }
    }
  }
}

@Composable
private fun CompletionTitle(session: TrainingSession, level: CompletedLevel?) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = session.levelName,
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
      textAlign = TextAlign.Center,
    )
    Text(
      text = sessionSubtitle(session, level),
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
      textAlign = TextAlign.Center,
    )
  }
}

private fun sessionSubtitle(session: TrainingSession, level: CompletedLevel?): String {
  val flowLabel =
    when (session.flow) {
      TrainingFlow.Singles -> "Singles"
      TrainingFlow.Melodies -> "Melodies"
      TrainingFlow.Chords -> "Chords"
    }
  val keyLabel =
    when (level) {
      is CompletedLevel.Singles ->
        when (val config = level.level.levelConfig) {
          is LevelConfig.Singles.Absolute -> scaleLabel(config.scales.first())
          is LevelConfig.Singles.Relative -> "Random keys (${config.scaleConfig.scaleType})"
        }

      is CompletedLevel.Melodies ->
        when (val config = level.level.config) {
          is LevelConfig.Melodies.Random ->
            when (val scale = config.scaleConfig) {
              is ScaleConfig.AbsoluteScaleConfig -> scaleLabel(scale)
              is ScaleConfig.RelativeScaleConfig -> "Random keys (${scale.scaleType})"
            }

          is LevelConfig.Melodies.Midi -> "MIDI melody"
        }

      is CompletedLevel.Chords ->
        when (val config = level.level.levelConfig) {
          is LevelConfig.Chords.Absolute -> scaleLabel(config.scales.first())
          is LevelConfig.Chords.Relative -> "Random keys (${config.scaleConfig.scaleType})"
        }

      null -> null
    }
  return listOfNotNull(
    flowLabel,
    keyLabel,
    "Ended early".takeIf { session.finishedEarly },
  ).joinToString(" • ")
}

private fun scaleLabel(scale: ScaleConfig.AbsoluteScaleConfig): String =
  "${scale.root} ${scale.scaleType}"

// region Score

@Composable
private fun ScoreHero(session: TrainingSession) {
  SurfaceCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      ScoreRing(session.accuracy)
      ScoreSummary(session, Modifier.weight(1f))
    }
  }
}

@Composable
private fun ScoreRing(accuracy: Float) {
  val trackColor = ShuuenUi.Ink.copy(alpha = 0.10f)
  val ringColor = ShuuenUi.Text
  Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(116.dp)) {
      // Flat caps and a thin stroke keep the gap readable near 100%: rounded caps would swallow a
      // small gap and make a near-perfect score look broken instead of nearly full.
      val stroke = 5.dp.toPx()
      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(stroke, cap = StrokeCap.Butt),
      )
      drawArc(
        color = ringColor,
        startAngle = -90f,
        sweepAngle = 360f * accuracy.coerceIn(0f, 1f),
        useCenter = false,
        style = Stroke(stroke, cap = StrokeCap.Butt),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "${(accuracy * 100).toRoundedString(1)}%",
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
private fun ScoreSummary(session: TrainingSession, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "${session.correctNotes} / ${session.notesTotal} CORRECT",
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.titleLarge.copy(
        letterSpacing = 2.sp, fontWeight = FontWeight.Bold
      ),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = scoreDescription(session),
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

private fun scoreDescription(session: TrainingSession): String =
  when (session.flow) {
    TrainingFlow.Singles ->
      "${session.questionsAnswered} " +
        (if (session.questionsAnswered == 1) "question" else "questions") + " answered"

    TrainingFlow.Melodies ->
      // Per-note sessions (a MIDI melody or the endless stream) have one entry per note; a
      // sequence count would just repeat the note count there.
      if (session.questionsAnswered == session.notesTotal) {
        "${session.notesTotal} notes answered"
      } else {
        "${session.questionsAnswered} sequences • ${session.notesTotal} notes"
      }

    TrainingFlow.Chords ->
      "${session.questionsAnswered} " +
        (if (session.questionsAnswered == 1) "chord" else "chords") + " answered"
  }

// endregion

// region Performance

private data class StatCellData(
  val icon: ImageVector,
  val value: String,
  val label: String,
)

@Composable
private fun PerformanceOverview(session: TrainingSession) {
  FlatSection(label = "PERFORMANCE OVERVIEW") {
    StatsGrid(session)
    val buckets = accuracyBuckets(session.questionResults)
    if (buckets.isNotEmpty()) {
      Text(
        text = "ACCURACY ACROSS THE SESSION",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = ShuuenUi.labelSpacing),
      )
      AccuracyRangeBar(buckets)
    }
  }
}

private fun statCells(session: TrainingSession): List<StatCellData> = buildList {
  add(
    StatCellData(
      Icons.Rounded.TrackChanges,
      "${(session.accuracy * 100).roundToInt()}%",
      "ACCURACY",
    )
  )
  session.avgAnswerMillis?.let {
    add(StatCellData(Icons.Rounded.Timer, formatSeconds(it), "AVG TIME"))
  }
  // How far behind the first full hearing the answers landed; 0 = real-time transcription.
  session.avgDeltaMillis?.let {
    add(StatCellData(Icons.Rounded.Timer, formatDelta(it), "DELTA"))
  }
  add(StatCellData(Icons.Rounded.Schedule, formatDuration(session.durationMillis), "TOTAL TIME"))
  add(StatCellData(Icons.Rounded.LocalFireDepartment, "${session.bestStreak}", "BEST STREAK"))
  add(
    StatCellData(
      Icons.Rounded.Replay,
      "${session.replays}",
      if (session.flow == TrainingFlow.Melodies) "REWINDS" else "REPLAYS",
    )
  )
  add(StatCellData(Icons.Rounded.Close, "${session.missedNotes}", "MISSED"))
  // A single fixed key isn't a stat; the cell only appears once the scale actually rotated.
  if (session.keysPracticed > 1) {
    add(StatCellData(Icons.Rounded.MusicNote, "${session.keysPracticed}", "KEYS"))
  }
}

@Composable
private fun StatsGrid(session: TrainingSession) {
  val rows = statCells(session).chunked(3)
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    rows.forEachIndexed { index, row ->
      if (index > 0) Hairline()
      Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { cell ->
          StatCell(cell.icon, cell.value, cell.label, Modifier.weight(1f))
        }
        repeat(3 - row.size) {
          Box(Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun RowScope.StatCell(
  icon: ImageVector,
  value: String,
  label: String,
  modifier: Modifier = Modifier,
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
      Text(
        value,
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
        maxLines = 1,
      )
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
private fun AccuracyRangeBar(buckets: List<AccuracyBucket>) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      buckets.forEach { bucket ->
        Text(
          text = "${(bucket.accuracy * 100).roundToInt()}%",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.titleSmall,
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth().height(14.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      buckets.forEach { bucket ->
        // A small alpha floor keeps a fully-missed range visible as an empty slot.
        val alpha = 0.12f + 0.8f * bucket.accuracy
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth()
            .height(14.dp)
            .background(ShuuenUi.Ink.copy(alpha = alpha), MaterialTheme.shapes.extraSmall),
        )
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      buckets.forEach { bucket ->
        Text(bucket.rangeLabel, color = ShuuenUi.Dim, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

// endregion

// region Level parameters

@Composable
private fun LevelParameters(level: CompletedLevel) {
  FlatSection(label = "LEVEL PARAMETERS") {
    ParameterChips(parameterChips(level))
    when (level) {
      is CompletedLevel.Singles ->
        when (val config = level.level.levelConfig) {
          is LevelConfig.Singles.Absolute ->
            BoxedItemRow(config.scales.first().pitchStates.toBoxedItems(), itemSize = 32.dp)

          is LevelConfig.Singles.Relative ->
            BoxedItemRow(config.scaleConfig.degreeStates.toBoxedItems(), itemSize = 32.dp)
        }

      is CompletedLevel.Melodies ->
        when (val config = level.level.config) {
          is LevelConfig.Melodies.Random ->
            when (val scale = config.scaleConfig) {
              is ScaleConfig.AbsoluteScaleConfig ->
                BoxedItemRow(scale.pitchStates.toBoxedItems(), itemSize = 32.dp)

              is ScaleConfig.RelativeScaleConfig ->
                BoxedItemRow(scale.degreeStates.toBoxedItems(), itemSize = 32.dp)
            }

          is LevelConfig.Melodies.Midi -> Unit
        }

      is CompletedLevel.Chords ->
        when (val config = level.level.levelConfig) {
          is LevelConfig.Chords.Absolute ->
            BoxedItemRow(config.scales.first().pitchStates.toBoxedItems(), itemSize = 32.dp)

          is LevelConfig.Chords.Relative ->
            BoxedItemRow(config.scaleConfig.degreeStates.toBoxedItems(), itemSize = 32.dp)
        }
    }
    LevelStyle(level)
    LevelContext(
      when (level) {
        is CompletedLevel.Singles -> level.level.context
        is CompletedLevel.Melodies -> level.level.context
        is CompletedLevel.Chords -> level.level.context
      }
    )
  }
}

/** The generation style the level played with, mirroring the level cards' expanded details. */
@Composable
private fun LevelStyle(level: CompletedLevel) {
  when (level) {
    is CompletedLevel.Melodies -> {
      val config = level.level.config as? LevelConfig.Melodies.Random ?: return
      DetailRow("RHYTHM", "${config.melodyStyle.name} · ${config.melodyStyle.tier.label}")
      MelodyStyleSummary(config.melodyStyle)
    }

    is CompletedLevel.Chords -> {
      val style = level.level.levelConfig.chordStyle
      DetailRow("CHORD SHAPES", "${style.name} · ${style.tier.label}")
      ChordStyleSummary(style)
    }

    is CompletedLevel.Singles -> Unit
  }
}

/** The level's degree context, node by node — the same detail block the level cards expand to. */
@Composable
private fun LevelContext(context: DegreeContext?) {
  if (context != null) {
    DetailLabel("CONTEXT")
    ContextDetails(context)
  } else {
    DetailRow("CONTEXT", "None")
  }
}

/** Mirrors the level-select card: the same parameters, as chips. */
private fun parameterChips(level: CompletedLevel): List<Pair<ImageVector, String>> =
  when (level) {
    is CompletedLevel.Singles -> {
      val l = level.level
      buildList {
        add(
          Icons.AutoMirrored.Rounded.HelpOutline to
            (l.questionsNumber?.let { "$it questions" } ?: "Unlimited")
        )
        add(Icons.Rounded.Keyboard to l.range.toPair().toList().joinToString(" - "))
        add(
          Icons.Rounded.Replay to
            (l.levelConfig.rotateEveryQuestions?.let { "Rotate every $it" } ?: "No rotation")
        )
        if (l.levelConfig.tuneInconsistencyCents > 0) {
          add(Icons.Rounded.Tune to "±${l.levelConfig.tuneInconsistencyCents}¢ tune")
        }
        add(Icons.Rounded.Bookmark to sourceLabel(l.source))
      }
    }

    is CompletedLevel.Melodies -> {
      val l = level.level
      when (val config = l.config) {
        is LevelConfig.Melodies.Random ->
          buildList {
            val notesPerSequence = config.notesPerSequence
            if (notesPerSequence == null) {
              add(Icons.Rounded.AllInclusive to "Endless notes")
            } else {
              add(
                Icons.AutoMirrored.Rounded.HelpOutline to
                  (config.questionsNumber?.let { "$it questions" } ?: "Unlimited")
              )
              add(Icons.Rounded.MusicNote to "$notesPerSequence-note sequences")
            }
            add(Icons.Rounded.Speed to "${config.tempo} BPM")
            if (config.tuneInconsistencyCents > 0) {
              add(Icons.Rounded.Tune to "±${config.tuneInconsistencyCents}¢ tune")
            }
            add(Icons.Rounded.Casino to config.melodyStyle.name)
            add(Icons.Rounded.Keyboard to config.range.toPair().toList().joinToString(" - "))
            add(
              Icons.Rounded.Replay to
                (config.rotateEveryQuestions?.let { "Rotate every $it" } ?: "No rotation")
            )
            add(Icons.Rounded.Bookmark to sourceLabel(l.source))
          }

        is LevelConfig.Melodies.Midi ->
          buildList {
            add(Icons.Rounded.FolderOpen to config.fileName)
            add(
              Icons.Rounded.Tune to
                if (config.useOriginalVelocities) "Original velocities" else "Full velocity"
            )
            add(Icons.Rounded.Bookmark to sourceLabel(l.source))
          }
      }
    }

    is CompletedLevel.Chords -> {
      val l = level.level
      buildList {
        add(Icons.Rounded.GraphicEq to "${l.chordSize} notes")
        add(Icons.Rounded.Casino to l.levelConfig.chordStyle.name)
        add(
          Icons.AutoMirrored.Rounded.HelpOutline to
            (l.questionsNumber?.let { "$it questions" } ?: "Unlimited")
        )
        add(Icons.Rounded.Keyboard to l.range.toPair().toList().joinToString(" - "))
        add(
          Icons.Rounded.Replay to
            (l.levelConfig.rotateEveryQuestions?.let { "Rotate every $it" } ?: "No rotation")
        )
        add(Icons.Rounded.Tune to if (l.sustainNotes) "Sustained" else "Timed")
        add(Icons.Rounded.SwapVert to l.answerOrder.label)
        add(Icons.Rounded.Bookmark to sourceLabel(l.source))
      }
    }
  }

@Composable
private fun ParameterChips(parameters: List<Pair<ImageVector, String>>) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val compact = maxWidth < 420.dp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      parameters.chunked(if (compact) 2 else 3).forEach { row ->
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

// endregion

// region Actions

@Composable
private fun CompletionActions(
  onRetryLevel: () -> Unit,
  nextLevelReference: String?,
  onNextLevel: (levelReference: String) -> Unit,
  onLevelSelect: () -> Unit,
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
    if (nextLevelReference != null) {
      CompactCompletionButton(
        text = "NEXT LEVEL",
        icon = Icons.Rounded.SkipNext,
        onClick = { onNextLevel(nextLevelReference) },
        filled = false,
      )
    }
    CompactCompletionButton(
      text = "LEVEL SELECT",
      icon = Icons.Rounded.ChevronRight,
      onClick = onLevelSelect,
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

// endregion

/** "1:32" — total time spent in the session. */
private fun formatDuration(millis: Long): String {
  val totalSeconds = millis / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** "2.8s" — mean time to answer a question. */
private fun formatSeconds(millis: Long): String {
  val tenths = (millis / 100).toInt()
  return "${tenths / 10}.${tenths % 10}s"
}

/** Sub-minute deltas as "2.8s"; longer ones (a whole MIDI file) as "1:32". */
private fun formatDelta(millis: Long): String =
  if (millis < 60_000) formatSeconds(millis) else formatDuration(millis)
