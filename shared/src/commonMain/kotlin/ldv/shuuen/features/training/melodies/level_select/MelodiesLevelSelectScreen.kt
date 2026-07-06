package ldv.shuuen.features.training.melodies.level_select

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
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speed
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
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.components.ContextDetails
import ldv.shuuen.features.training.common.components.DetailLabel
import ldv.shuuen.features.training.common.components.DetailRow
import ldv.shuuen.features.training.common.components.DeleteLevelDialog
import ldv.shuuen.features.training.common.components.LevelAccuracyLabel
import ldv.shuuen.features.training.common.components.LevelAccuracyStatsRow
import ldv.shuuen.features.training.common.components.LevelParametersFlow
import ldv.shuuen.features.training.common.components.MelodyStyleSummary
import ldv.shuuen.features.training.common.components.sourceLabel
import ldv.shuuen.features.training.common.toBoxedItems
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel

@Composable
fun MelodiesLevelSelectScreen(
  onNavigateBack: () -> Unit,
  onStartLevel: (levelId: String) -> Unit,
  onCreateNewLevel: () -> Unit,
  onEditLevel: (levelId: String) -> Unit,
  viewModel: MelodiesLevelSelectScreenViewModel,
) {
  val levels by viewModel.levels.collectAsStateWithLifecycle(ResponseState.Loading)
  var levelPendingDelete by remember { mutableStateOf<MelodiesLevel?>(null) }
  StaticScreenFrame(
    topBar = {
      ShuuenTopAppBar(
        title = "LEVEL SELECT",
        subtitle = "Transcribe melodies from MIDI or random sequences.",
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
          if (l.result.isEmpty()) {
            item { EmptyState() }
          } else {
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
private fun EmptyState() {
  SurfaceCard(verticalSpacing = Arrangement.spacedBy(6.dp)) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "No saved melody levels yet",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = "Create a new level with random sequences or a .midi file.",
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun LevelCard(
  level: MelodiesLevel,
  stats: LevelAccuracyStats,
  onLevelChosen: (MelodiesLevel) -> Unit,
  onEditLevel: (MelodiesLevel) -> Unit,
  onDeleteLevel: (MelodiesLevel) -> Unit,
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
    when (val config = level.config) {
      is LevelConfig.Melodies.Random ->
        when (val scale = config.scaleConfig) {
          is ScaleConfig.AbsoluteScaleConfig ->
            BoxedItemRow(scale.pitchStates.toBoxedItems(), itemSize = 32.dp)

          is ScaleConfig.RelativeScaleConfig ->
            BoxedItemRow(scale.degreeStates.toBoxedItems(), itemSize = 32.dp)
        }

      is LevelConfig.Melodies.Midi -> Unit
    }
    AnimatedVisibility(visible = expanded) {
      LevelDetails(level)
    }
  }
}

@Composable
private fun LevelParameterRow(
  level: MelodiesLevel,
  modifier: Modifier = Modifier,
) {
  val items =
    when (val config = level.config) {
      is LevelConfig.Melodies.Random ->
        buildList {
          val notesPerSequence = config.notesPerSequence
          if (notesPerSequence == null) {
            add("Endless notes" to Icons.Rounded.AllInclusive)
          } else {
            add(
              (config.questionsNumber?.let { "$it questions" } ?: "Unlimited") to
                Icons.AutoMirrored.Rounded.HelpOutline
            )
            add("$notesPerSequence-note sequences" to Icons.Rounded.MusicNote)
          }
          add("${config.tempo} BPM" to Icons.Rounded.Speed)
          add(config.melodyStyle.name to Icons.Rounded.Casino)
          add(config.range.toPair().toList().joinToString(" - ") to Icons.Rounded.Keyboard)
        }

      is LevelConfig.Melodies.Midi ->
        listOf(config.fileName to Icons.Rounded.FolderOpen)
    }

  LevelParametersFlow(items, modifier = modifier)
}

@Composable
private fun LevelDetails(level: MelodiesLevel) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Hairline()

    DetailRow("SOURCE", sourceLabel(level.source))

    when (val config = level.config) {
      is LevelConfig.Melodies.Random -> {
        val rotationLabel =
          config.rotateEveryQuestions?.let { "Every $it questions" } ?: "Off"
        DetailRow("SCALE ROTATION", rotationLabel)

        DetailRow("RHYTHM", "${config.melodyStyle.name} · ${config.melodyStyle.tier.label}")
        MelodyStyleSummary(config.melodyStyle)
      }

      is LevelConfig.Melodies.Midi -> {
        DetailRow("FILE", config.fileName)
        DetailRow(
          "NOTE VELOCITIES",
          if (config.useOriginalVelocities) "Original file values" else "Full velocity (127)",
        )
      }
    }

    val context = level.context
    if (context != null) {
      DetailLabel("CONTEXT")
      ContextDetails(context)
    } else {
      DetailRow("CONTEXT", "None")
    }
  }
}
