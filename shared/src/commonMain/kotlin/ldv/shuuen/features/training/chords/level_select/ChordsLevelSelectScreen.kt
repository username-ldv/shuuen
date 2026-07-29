package ldv.shuuen.features.training.chords.level_select

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.ui.components.BoxedItemRow
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.components.ChordStyleSummary
import ldv.shuuen.features.training.common.components.ContextDetails
import ldv.shuuen.features.training.common.components.DetailLabel
import ldv.shuuen.features.training.common.components.DetailRow
import ldv.shuuen.features.training.common.components.DeleteLevelDialog
import ldv.shuuen.features.training.common.components.LevelAccuracyLabel
import ldv.shuuen.features.training.common.components.LevelAccuracyStatsRow
import ldv.shuuen.features.training.common.components.LevelParametersFlow
import ldv.shuuen.features.training.common.components.LevelSortAction
import ldv.shuuen.features.training.common.components.LevelSortOrder
import ldv.shuuen.features.training.common.components.sourceLabel
import ldv.shuuen.features.training.common.components.sortedByLevelCreation
import ldv.shuuen.features.training.common.toBoxedItems
import ldv.shuuen.features.training.domain.LevelConfig

@Composable
fun ChordsLevelSelectScreen(
    onNavigateBack: () -> Unit,
    onStartLevel: (levelId: String) -> Unit,
    onCreateNewLevel: () -> Unit,
    onEditLevel: (levelId: String) -> Unit,
    viewModel: ChordsLevelSelectScreenViewModel,
) {
  val levels by viewModel.levels.collectAsStateWithLifecycle(ResponseState.Loading)
  var sortOrder by rememberSaveable { mutableStateOf(LevelSortOrder.Descending) }
  var levelPendingDelete by remember { mutableStateOf<ChordsLevel?>(null) }
  StaticScreenFrame(
      topBar = {
        ShuuenTopAppBar(
            title = "LEVEL SELECT",
            subtitle = "Choose a chord training level.",
            onBack = onNavigateBack,
            actions = {
              LevelSortAction(sortOrder, onOrderChange = { sortOrder = it })
            },
            type = ShuuenTopAppBarType.Labeled,
        )
      },
      scrollable = false,
  ) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        PrimaryCta(
            text = "CREATE NEW",
            icon = Icons.Rounded.Create,
            onClick = onCreateNewLevel,
            modifier = Modifier.padding(top = 8.dp),
        )
      }
      when (val l = levels) {
        is ResponseState.Loading ->
            item {
              Text(
                  text = "Loading...",
                  color = ShuuenUi.Muted,
                  style = MaterialTheme.typography.bodyLarge,
              )
            }

        is ResponseState.Success ->
            items(
                items = l.result.sortedByLevelCreation(sortOrder) { it.id },
                key = { it.id },
            ) { level ->
              val statsFlow = remember(viewModel, level.id) { viewModel.levelStats(level.id) }
              val stats by statsFlow.collectAsStateWithLifecycle(LevelAccuracyStats())
              LevelCard(
                  level,
                  stats = stats,
                  onLevelChosen = { onStartLevel(it.id) },
                  onEditLevel = { onEditLevel(it.id) },
                  onDeleteLevel = { levelPendingDelete = it },
              )
            }

        is ResponseState.Error ->
            item {
              Text(
                  text = "Error loading levels: ${l.throwable.message}",
                  color = ShuuenUi.Incorrect,
                  style = MaterialTheme.typography.bodyLarge,
              )
            }
      }
    }
  }
  levelPendingDelete?.let { level ->
    DeleteLevelDialog(
        levelName = level.name,
        onConfirm = {
          viewModel.deleteLevel(level.id)
          levelPendingDelete = null
        },
        onDismiss = { levelPendingDelete = null },
    )
  }
}

@Composable
private fun LevelCard(
    level: ChordsLevel,
    stats: LevelAccuracyStats,
    onLevelChosen: (ChordsLevel) -> Unit,
    onEditLevel: (ChordsLevel) -> Unit,
    onDeleteLevel: (ChordsLevel) -> Unit,
) {
  var expanded by rememberSaveable(level.id) { mutableStateOf(false) }

  SurfaceCard(
      onClick = { onLevelChosen(level) },
      verticalSpacing = Arrangement.spacedBy(12.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
          text = level.name,
          color = ShuuenUi.Text,
          style =
              MaterialTheme.typography.titleMedium.copy(
                  letterSpacing = ShuuenUi.titlesSpacing,
                  fontWeight = FontWeight.SemiBold,
              ),
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      LevelAccuracyLabel(stats = stats)
      IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse details" else "Expand details",
            tint = ShuuenUi.Dim,
            modifier = Modifier.size(24.dp),
        )
      }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      LevelAccuracyStatsRow(stats = stats, modifier = Modifier.weight(1f))
      IconButton(onClick = { onEditLevel(level) }, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit level",
            tint = ShuuenUi.Dim,
            modifier = Modifier.size(20.dp),
        )
      }
      IconButton(onClick = { onDeleteLevel(level) }, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Remove level",
            tint = ShuuenUi.Text,
            modifier = Modifier.size(20.dp),
        )
      }
    }
    LevelParameterRow(level = level)
    when (val levelConfig = level.levelConfig) {
      is LevelConfig.Chords.Absolute -> {
        BoxedItemRow(levelConfig.scales.first().pitchStates.toBoxedItems(), itemSize = 32.dp)
      }

      is LevelConfig.Chords.Relative -> {
        BoxedItemRow(levelConfig.scaleConfig.degreeStates.toBoxedItems(), itemSize = 32.dp)
      }
    }
    AnimatedVisibility(visible = expanded) {
      LevelDetails(level)
    }
  }
}

@Composable
private fun LevelParameterRow(
    level: ChordsLevel,
    modifier: Modifier = Modifier,
) {
  val items =
      listOf(
          "${level.chordSize} notes" to Icons.Rounded.GraphicEq,
          (level.questionsNumber?.let { "$it questions" } ?: "Unlimited") to
              Icons.AutoMirrored.Rounded.HelpOutline,
          level.levelConfig.chordStyle.name to Icons.Rounded.Casino,
          level.range.toPair().toList().joinToString(" - ") to Icons.Rounded.Keyboard,
      )

  LevelParametersFlow(items, modifier = modifier)
}

@Composable
private fun LevelDetails(level: ChordsLevel) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Hairline()

    DetailRow("SOURCE", sourceLabel(level.source))

    val rotationLabel = level.levelConfig.rotateEveryQuestions?.let {"Every $it questions"} ?: "Off"
    DetailRow("SCALE ROTATION", rotationLabel)

    DetailRow("PLAYBACK", if (level.sustainNotes) "Sustained" else "Timed")
    DetailRow("ANSWER ORDER", level.answerOrder.label)

    val style = level.levelConfig.chordStyle
    DetailRow("CHORD SHAPES", "${style.name} · ${style.tier.label}")
    ChordStyleSummary(style)

    val context = level.context
    val hasContext = context != null
    if (hasContext) {
      DetailLabel("CONTEXT")
      ContextDetails(context)
    } else {
      DetailRow("CONTEXT", "None")
    }
  }
}
