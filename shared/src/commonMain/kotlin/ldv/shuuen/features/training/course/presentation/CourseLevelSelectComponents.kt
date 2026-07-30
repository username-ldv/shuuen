package ldv.shuuen.features.training.course.presentation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.features.training.course.domain.CourseSection
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.ProgressionGroup

const val CourseLevelItemKeyPrefix = "course-level:"

private val GroupSwipeThreshold = 64.dp

internal fun adjacentProgressionGroupId(
  groupIds: List<String>,
  selectedGroupId: String?,
  moveForward: Boolean,
): String? {
  val selectedIndex = groupIds.indexOf(selectedGroupId)
  if (selectedIndex < 0) return null
  val targetIndex = selectedIndex + if (moveForward) 1 else -1
  return groupIds.getOrNull(targetIndex)
}

/** Horizontal content swipes select adjacent groups; vertical drags remain with the lazy list. */
@Composable
fun Modifier.progressionGroupSwipeNavigation(
  groups: List<ProgressionGroup>,
  selectedGroupId: String?,
  onGroupSelected: (String) -> Unit,
): Modifier {
  if (groups.size < 2) return this
  val thresholdPx = with(LocalDensity.current) { GroupSwipeThreshold.toPx() }
  val currentOnGroupSelected by rememberUpdatedState(onGroupSelected)
  val groupIds = remember(groups) { groups.map { it.id } }
  return pointerInput(groupIds, selectedGroupId, thresholdPx) {
    var accumulatedDrag = 0f
    detectHorizontalDragGestures(
      onDragStart = { accumulatedDrag = 0f },
      onDragCancel = { accumulatedDrag = 0f },
      onDragEnd = {
        val target =
          when {
            accumulatedDrag <= -thresholdPx ->
              adjacentProgressionGroupId(groupIds, selectedGroupId, moveForward = true)
            accumulatedDrag >= thresholdPx ->
              adjacentProgressionGroupId(groupIds, selectedGroupId, moveForward = false)
            else -> null
          }
        if (target != null) currentOnGroupSelected(target)
        accumulatedDrag = 0f
      },
      onHorizontalDrag = { change, dragAmount ->
        accumulatedDrag += dragAmount
        change.consume()
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSourceTopBarSelector(
  courses: List<CourseSummary>,
  selection: CourseSourceSelection,
  onMyLevelsSelected: () -> Unit,
  onCourseSelected: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val selectedLabel =
    when (selection) {
      CourseSourceSelection.MyLevels -> "My Levels"
      is CourseSourceSelection.Course ->
        courses.firstOrNull { it.id == selection.courseId }?.name ?: "Public course"
    }
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier.fillMaxWidth().widthIn(max = 340.dp),
  ) {
    Box(
      modifier =
        Modifier
          .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      Text(
        text = selectedLabel,
        color = ShuuenUi.Text,
        style =
          MaterialTheme.typography.titleLarge.copy(
            letterSpacing = ShuuenUi.titlesSpacing,
            fontWeight = FontWeight.SemiBold,
          ),
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Icon(
        imageVector = Icons.Rounded.ExpandMore,
        contentDescription = "Choose level source or course",
        tint = ShuuenUi.Muted,
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.exposedDropdownSize(matchAnchorWidth = true),
    ) {
      SourceMenuItem(
        label = "My Levels",
        selected = selection == CourseSourceSelection.MyLevels,
        onClick = {
          onMyLevelsSelected()
          expanded = false
        },
      )
      courses.forEach { course ->
        SourceMenuItem(
          label = course.name,
          supporting = course.author.takeIf { it.isNotBlank() },
          selected = (selection as? CourseSourceSelection.Course)?.courseId == course.id,
          onClick = {
            onCourseSelected(course.id)
            expanded = false
          },
        )
      }
    }
  }
}

@Composable
private fun SourceMenuItem(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  supporting: String? = null,
) {
  DropdownMenuItem(
    text = {
      Column {
        Text(label)
        if (supporting != null) {
          Text(supporting, color = ShuuenUi.Muted, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    onClick = onClick,
    trailingIcon = {
      if (selected) Icon(Icons.Default.Check, contentDescription = null)
    },
  )
}

@Composable
fun ProgressionGroupTabs(
  groups: List<ProgressionGroup>,
  selectedGroupId: String?,
  onGroupSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (groups.isEmpty()) return
  val selectedTabIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)
  PrimaryScrollableTabRow(
    selectedTabIndex = selectedTabIndex,
    modifier = modifier.fillMaxWidth(),
    containerColor = Color.Transparent,
    contentColor = ShuuenUi.Text,
    edgePadding = 0.dp,
    minTabWidth = 96.dp,
    indicator = {
      TabRowDefaults.PrimaryIndicator(
        modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
        width = Dp.Unspecified,
        height = 2.dp,
        color = ShuuenUi.Text,
      )
    },
    divider = { HorizontalDivider(color = ShuuenUi.Hairline) },
  ) {
    groups.forEach { group ->
      Tab(
        selected = group.id == selectedGroupId,
        onClick = { onGroupSelected(group.id) },
        selectedContentColor = ShuuenUi.Text,
        unselectedContentColor = ShuuenUi.Muted,
        text = {
          Text(
            text = group.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
      )
    }
  }
}

@Composable
fun CourseSectionDivider(sections: List<CourseSection>, modifier: Modifier = Modifier) {
  if (sections.isEmpty()) return
  Column(
    modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = sections.joinToString("  ›  ") { it.name }.uppercase(),
      color = ShuuenUi.Muted,
      style =
        MaterialTheme.typography.labelMedium.copy(
          letterSpacing = ShuuenUi.labelSpacing,
          fontWeight = FontWeight.SemiBold,
        ),
    )
    Hairline()
  }
}

@Composable
fun CourseDiscoveryMessage(
  isLoading: Boolean,
  modifier: Modifier = Modifier,
) {
  if (isLoading) {
    Text(
      "Loading public courses…",
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.bodyMedium,
      modifier = modifier,
    )
  }
}

@Composable
fun CourseLevelsMessage(
  isLoading: Boolean,
  isLoadingMore: Boolean,
  isEmpty: Boolean,
  error: String?,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when {
    error != null ->
      InlineCourseMessage(
        message = "Couldn't load course levels: $error",
        action = "RETRY",
        onAction = onRetry,
        isError = true,
        modifier = modifier,
      )
    isLoading ->
      Text("Loading course levels…", color = ShuuenUi.Muted, modifier = modifier)
    isEmpty ->
      Text("This progression group has no levels.", color = ShuuenUi.Muted, modifier = modifier)
    isLoadingMore ->
      Text("Loading more…", color = ShuuenUi.Muted, modifier = modifier)
  }
}

@Composable
private fun InlineCourseMessage(
  message: String,
  action: String,
  onAction: () -> Unit,
  isError: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      message,
      color = if (isError) ShuuenUi.Incorrect else ShuuenUi.Muted,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onAction) { Text(action) }
  }
}

/** Requests another page when a visible course-level item enters the final five loaded levels. */
@Composable
fun CoursePagingEffect(
  listState: LazyListState,
  levels: List<LevelSelectItem<*>>,
  canLoadMore: Boolean,
  onLoadMore: () -> Unit,
) {
  LaunchedEffect(listState, levels, canLoadMore) {
    snapshotFlow {
      listState.layoutInfo.visibleItemsInfo
        .asReversed()
        .firstNotNullOfOrNull { info ->
          (info.key as? String)?.removePrefix(CourseLevelItemKeyPrefix)
            ?.takeIf { info.key.toString().startsWith(CourseLevelItemKeyPrefix) }
        }
    }.distinctUntilChanged().collect { reference ->
      val index = levels.indexOfLast { it.reference == reference }
      if (canLoadMore && index >= (levels.size - 5).coerceAtLeast(0)) onLoadMore()
    }
  }
}
