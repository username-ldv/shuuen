package ldv.shuuen.features.training.melodies.level_select

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import ldv.shuuen.features.training.common.components.CourseSettingsAction
import ldv.shuuen.features.training.common.components.CourseSettingsSheet
import ldv.shuuen.features.training.common.components.CourseLevelListHeaderItemCount
import ldv.shuuen.features.training.common.components.DetailLabel
import ldv.shuuen.features.training.common.components.DetailRow
import ldv.shuuen.features.training.common.components.DeleteLevelDialog
import ldv.shuuen.features.training.common.components.LevelAccuracyLabel
import ldv.shuuen.features.training.common.components.LevelAccuracyStatsRow
import ldv.shuuen.features.training.common.components.LevelListScrollControls
import ldv.shuuen.features.training.common.components.LevelListScrollbar
import ldv.shuuen.features.training.common.components.LocalLevelListHeaderItemCount
import ldv.shuuen.features.training.common.components.LevelParametersFlow
import ldv.shuuen.features.training.common.components.LevelSettingsSheet
import ldv.shuuen.features.training.common.components.LevelSortAction
import ldv.shuuen.features.training.common.components.LevelSortOrder
import ldv.shuuen.features.training.common.components.MelodyStyleSummary
import ldv.shuuen.features.training.common.components.sourceLabel
import ldv.shuuen.features.training.common.components.sortedByLevelCreation
import ldv.shuuen.features.training.common.toBoxedItems
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MidiTransposition
import ldv.shuuen.features.training.course.presentation.CourseDiscoveryMessage
import ldv.shuuen.features.training.course.presentation.CourseLevelItemKeyPrefix
import ldv.shuuen.features.training.course.presentation.CourseLevelsMessage
import ldv.shuuen.features.training.course.presentation.CoursePagingEffect
import ldv.shuuen.features.training.course.presentation.CourseSectionDivider
import ldv.shuuen.features.training.course.presentation.CourseSourceSelection
import ldv.shuuen.features.training.course.presentation.CourseSourceTopBarSelector
import ldv.shuuen.features.training.course.presentation.ProgressionGroupTabs
import ldv.shuuen.features.training.course.presentation.progressionGroupSwipeNavigation

@Composable
fun MelodiesLevelSelectScreen(
  onNavigateBack: () -> Unit,
  onStartLevel: (levelId: String, transposition: MidiTransposition) -> Unit,
  onCreateNewLevel: () -> Unit,
  onEditLevel: (levelId: String) -> Unit,
  viewModel: MelodiesLevelSelectScreenViewModel,
) {
  val levels by viewModel.levels.collectAsStateWithLifecycle(ResponseState.Loading)
  val courseState by viewModel.courseState.collectAsStateWithLifecycle()
  val attemptedLevelIds by viewModel.attemptedLevelIds.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val showingLocal = courseState.selection == CourseSourceSelection.MyLevels
  val selectedCourse =
    (courseState.selection as? CourseSourceSelection.Course)?.let { selection ->
      courseState.courses.firstOrNull { it.id == selection.courseId }
    }
  CoursePagingEffect(
    listState = listState,
    levels = courseState.levels,
    canLoadMore = courseState.canLoadMore,
    onLoadMore = viewModel::loadNextPage,
  )
  var sortOrder by rememberSaveable { mutableStateOf(LevelSortOrder.Descending) }
  val orderedLocalLevels =
    when (val state = levels) {
      is ResponseState.Success -> state.result.sortedByLevelCreation(sortOrder) { it.id }
      else -> emptyList()
    }
  val orderedLevelIds =
    if (showingLocal) orderedLocalLevels.map { it.id }
    else courseState.levels.map { it.reference }
  val firstLevelItemIndex =
    if (showingLocal) LocalLevelListHeaderItemCount else CourseLevelListHeaderItemCount
  val totalLevelCount =
    if (showingLocal) orderedLevelIds.size.toLong() else courseState.total
  var levelPendingDelete by remember { mutableStateOf<MelodiesLevel?>(null) }
  var levelPendingSettings by remember { mutableStateOf<LevelSettingsTarget?>(null) }
  var courseSettingsOpen by remember { mutableStateOf(false) }
  StaticScreenFrame(
    topBar = {
      ShuuenTopAppBar(
        onBack = onNavigateBack,
        actions = {
          if (showingLocal) {
            LevelSortAction(sortOrder, onOrderChange = { sortOrder = it })
          } else {
            CourseSettingsAction(onClick = { courseSettingsOpen = true })
          }
        },
        type = ShuuenTopAppBarType.Simple,
        titleContent = {
          CourseSourceTopBarSelector(
            courses = courseState.courses,
            selection = courseState.selection,
            onMyLevelsSelected = viewModel::selectMyLevels,
            onCourseSelected = viewModel::selectCourse,
          )
        },
      )
    },
    scrollable = false,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (!showingLocal && courseState.mode != null) {
        ProgressionGroupTabs(
          groups = courseState.groups,
          selectedGroupId = courseState.selectedGroupId,
          onGroupSelected = viewModel::selectGroup,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      Box(
        modifier =
          Modifier
            .weight(1f)
            .then(
              if (showingLocal) Modifier
              else
                Modifier.progressionGroupSwipeNavigation(
                  groups = courseState.groups,
                  selectedGroupId = courseState.selectedGroupId,
                  onGroupSelected = viewModel::selectGroup,
                )
            )
      ) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
      item {
        CourseDiscoveryMessage(
          isLoading = courseState.isDiscoveringCourses,
        )
      }
      if (showingLocal) {
        item {
          PrimaryCta(
            text = "CREATE NEW",
            modifier = Modifier.padding(top = 8.dp),
            icon = Icons.Rounded.Create,
            onClick = onCreateNewLevel,
          )
        }
        when (val l = levels) {
          is ResponseState.Loading ->
            item {
              Text("Loading...", color = ShuuenUi.Muted, style = MaterialTheme.typography.bodyLarge)
            }
          is ResponseState.Success ->
            if (l.result.isEmpty()) {
              item { EmptyState() }
            } else {
              items(orderedLocalLevels, key = { it.id }) { level ->
                val statsFlow = remember(viewModel, level.id) { viewModel.levelStats(level.id) }
                val stats by statsFlow.collectAsStateWithLifecycle(LevelAccuracyStats())
                LevelCard(
                  level,
                  stats = stats,
                  onLevelChosen = { onStartLevel(it.id, MidiTransposition()) },
                  onOpenSettings = {
                    levelPendingSettings =
                      LevelSettingsTarget(
                        reference = level.id,
                        name = level.name,
                        hasMidiOptions = level.config is LevelConfig.Melodies.Midi,
                      )
                  },
                  onEditLevel = { onEditLevel(it.id) },
                  onDeleteLevel = { levelPendingDelete = it },
                )
              }
            }
          is ResponseState.Error ->
            item {
              Text(
                "Error loading levels: ${l.throwable.message}",
                color = ShuuenUi.Incorrect,
                style = MaterialTheme.typography.bodyLarge,
              )
            }
        }
      } else {
        itemsIndexed(
          items = courseState.levels,
          key = { _, item -> "$CourseLevelItemKeyPrefix${item.reference}" },
        ) { index, item ->
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val previousSections = courseState.levels.getOrNull(index - 1)?.sections
            if (item.sections.isNotEmpty() && item.sections != previousSections) {
              CourseSectionDivider(item.sections)
            }
            val statsFlow = remember(viewModel, item.reference) { viewModel.levelStats(item.reference) }
            val stats by statsFlow.collectAsStateWithLifecycle(LevelAccuracyStats())
            LevelCard(
              level = item.playableLevel,
              stats = stats,
              onLevelChosen = { onStartLevel(item.reference, MidiTransposition()) },
              onOpenSettings = {
                levelPendingSettings =
                  LevelSettingsTarget(
                    reference = item.reference,
                    name = item.playableLevel.name,
                    hasMidiOptions = item.playableLevel.config is LevelConfig.Melodies.Midi,
                  )
              },
              onEditLevel = null,
              onDeleteLevel = null,
            )
          }
        }
        item {
          CourseLevelsMessage(
            isLoading = courseState.isLoadingLevels,
            isLoadingMore = courseState.isLoadingMore,
            isEmpty =
              courseState.mode != null && !courseState.isLoadingLevels &&
                courseState.levels.isEmpty() && courseState.levelsError == null,
            error = courseState.levelsError,
            onRetry = viewModel::retryCourseLevels,
          )
        }
      }
      }
      LevelListScrollbar(
        listState = listState,
        loadedLevelCount = orderedLevelIds.size,
        totalLevelCount = totalLevelCount,
        firstLevelItemIndex = firstLevelItemIndex,
        modifier =
          Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp).padding(end = 2.dp),
      )
      LevelListScrollControls(
        listState = listState,
        orderedLevelIds = orderedLevelIds,
        attemptedLevelIds = attemptedLevelIds,
        resolveFirstUnattemptedLevelId =
          if (showingLocal) null
          else ({ viewModel.firstUnattemptedCourseLevel(attemptedLevelIds) }),
        firstLevelItemIndex = firstLevelItemIndex,
        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
      )
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
  levelPendingSettings?.let { level ->
    val statsFlow = remember(viewModel, level.reference) { viewModel.levelStats(level.reference) }
    val stats by statsFlow.collectAsStateWithLifecycle(LevelAccuracyStats())
    if (level.hasMidiOptions) {
      MidiLevelOptionsSheet(
        levelName = level.name,
        levelReference = level.reference,
        stats = stats,
        onStart = { transposition ->
          levelPendingSettings = null
          onStartLevel(level.reference, transposition)
        },
        onDeleteLastPlayStatistics = {
          viewModel.deleteLastPlayStatistics(level.reference)
        },
        onDeleteAllStatistics = { viewModel.deleteAllLevelStatistics(level.reference) },
        onDismiss = { levelPendingSettings = null },
      )
    } else {
      LevelSettingsSheet(
        levelName = level.name,
        hasStatistics = stats.games > 0,
        onDeleteLastPlayStatistics = {
          viewModel.deleteLastPlayStatistics(level.reference)
        },
        onDeleteAllStatistics = { viewModel.deleteAllLevelStatistics(level.reference) },
        onDismiss = { levelPendingSettings = null },
      )
    }
  }
  if (courseSettingsOpen && selectedCourse != null) {
    CourseSettingsSheet(
      courseName = selectedCourse.name,
      onDeleteAllStatistics = { viewModel.deleteAllCourseStatistics(selectedCourse.id) },
      onDismiss = { courseSettingsOpen = false },
    )
  }
}

private data class LevelSettingsTarget(
  val reference: String,
  val name: String,
  val hasMidiOptions: Boolean,
)

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
  onOpenSettings: () -> Unit,
  onEditLevel: ((MelodiesLevel) -> Unit)?,
  onDeleteLevel: ((MelodiesLevel) -> Unit)?,
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
      IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
        Icon(
          imageVector = Icons.Rounded.Tune,
          contentDescription = "Level settings",
          tint = ShuuenUi.Text,
          modifier = Modifier.size(20.dp),
        )
      }
      if (onEditLevel != null) {
        IconButton(onClick = { onEditLevel(level) }, modifier = Modifier.size(34.dp)) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit level",
            tint = ShuuenUi.Dim,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      if (onDeleteLevel != null) {
        IconButton(onClick = { onDeleteLevel(level) }, modifier = Modifier.size(34.dp)) {
          Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Remove level",
            tint = ShuuenUi.Text,
            modifier = Modifier.size(20.dp),
          )
        }
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
          if (config.tuneInconsistencyCents > 0) {
            add("±${config.tuneInconsistencyCents}¢ tune" to Icons.Rounded.Tune)
          }
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
