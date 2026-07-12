package ldv.shuuen.features.training.single.level_select

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
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Tune
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
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.features.training.common.components.ContextDetails
import ldv.shuuen.features.training.common.components.DetailLabel
import ldv.shuuen.features.training.common.components.DetailRow
import ldv.shuuen.features.training.common.components.DeleteLevelDialog
import ldv.shuuen.features.training.common.components.LevelAccuracyLabel
import ldv.shuuen.features.training.common.components.LevelAccuracyStatsRow
import ldv.shuuen.features.training.common.components.LevelParametersFlow
import ldv.shuuen.features.training.common.components.sourceLabel
import ldv.shuuen.features.training.common.toBoxedItems
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard
import ldv.shuuen.core.ui.components.BoxedItemRow

@Composable
fun SinglesLevelSelectScreen(
    onNavigateBack: () -> Unit,
    onStartLevel: (levelId: String) -> Unit,
    onCreateNewLevel: () -> Unit,
    onEditLevel: (levelId: String) -> Unit,
    viewModel: SinglesLevelSelectScreenViewModel,
) {
  val levels by viewModel.levels.collectAsStateWithLifecycle(ResponseState.Loading)
  var levelPendingDelete by remember { mutableStateOf<SinglesLevel?>(null) }
  StaticScreenFrame(
      topBar = {
        ShuuenTopAppBar(
            title = "LEVEL SELECT",
            subtitle = "Choose a training level.",
            onBack = onNavigateBack,
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
            items(items = l.result, key = { it.id }) { level ->
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
    level: SinglesLevel,
    stats: LevelAccuracyStats,
    onLevelChosen: (SinglesLevel) -> Unit,
    onEditLevel: (SinglesLevel) -> Unit,
    onDeleteLevel: (SinglesLevel) -> Unit,
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
      is LevelConfig.Singles.Absolute -> {
        BoxedItemRow(levelConfig.scales.first().pitchStates.toBoxedItems(), itemSize = 32.dp)
      }

      is LevelConfig.Singles.Relative -> {
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
    level: SinglesLevel,
    modifier: Modifier = Modifier,
) {
  val items = buildList {
    add(
        (level.questionsNumber?.let { "$it questions" } ?: "Unlimited") to
            Icons.AutoMirrored.Rounded.HelpOutline
    )
    add(level.range.toPair().toList().joinToString(" - ") to Icons.Rounded.Keyboard)
    val tune = level.levelConfig.tuneInconsistencyCents
    if (tune > 0) add("±$tune¢ tune" to Icons.Rounded.Tune)
  }

  LevelParametersFlow(items, modifier = modifier)
}

@Composable
private fun LevelDetails(level: SinglesLevel) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Hairline()

    DetailRow("SOURCE", sourceLabel(level.source))

    val rotationLabel = level.levelConfig.rotateEveryQuestions?.let {"Every $it questions"} ?: "Off"
    DetailRow("SCALE ROTATION", rotationLabel)

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

