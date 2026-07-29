package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ldv.shuuen.core.ui.components.ShuuenUi

private const val LevelJumpSize = 10

/** The discovery message and create control precede local levels. */
const val LocalLevelListHeaderItemCount = 2

/** Only the discovery message precedes course levels; group tabs live above the swipe area. */
const val CourseLevelListHeaderItemCount = 1

internal fun nextLevelAfterLastAttemptedIndex(
  orderedLevelIds: List<String>,
  attemptedLevelIds: Set<String>,
): Int =
  if (orderedLevelIds.isEmpty()) {
    -1
  } else {
    (orderedLevelIds.indexOfLast { it in attemptedLevelIds } + 1)
      .coerceAtMost(orderedLevelIds.lastIndex)
  }

internal fun jumpLevelIndex(currentIndex: Int, amount: Int, lastIndex: Int): Int =
  (currentIndex + amount).coerceIn(0, lastIndex)

internal fun centeredItemScrollOffset(
  viewportStartOffset: Int,
  viewportEndOffset: Int,
  itemSize: Int,
): Int {
  val viewportSize = viewportEndOffset - viewportStartOffset
  return itemSize / 2 - viewportSize / 2
}

@Composable
fun LevelListScrollControls(
  listState: LazyListState,
  orderedLevelIds: List<String>,
  attemptedLevelIds: Set<String>,
  modifier: Modifier = Modifier,
  firstLevelItemIndex: Int = LocalLevelListHeaderItemCount,
) {
  if (orderedLevelIds.isEmpty()) return

  val scope = rememberCoroutineScope()
  val lastLevelIndex = orderedLevelIds.lastIndex
  val currentLevelIndex by remember(listState, firstLevelItemIndex, lastLevelIndex) {
    derivedStateOf {
      (listState.firstVisibleItemIndex - firstLevelItemIndex).coerceIn(0, lastLevelIndex)
    }
  }
  val targetLevelIndex =
    remember(orderedLevelIds, attemptedLevelIds) {
      nextLevelAfterLastAttemptedIndex(orderedLevelIds, attemptedLevelIds)
    }
  val isAtStart = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

  fun scrollToLevel(levelIndex: Int) {
    scope.launch { listState.animateScrollToItem(firstLevelItemIndex + levelIndex) }
  }

  fun scrollToLevelCentered(levelIndex: Int) {
    scope.launch {
      val itemIndex = firstLevelItemIndex + levelIndex
      val layoutInfo = listState.layoutInfo
      val targetItemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }?.size
      val measuredLevelSizes =
        layoutInfo.visibleItemsInfo
          .filter { it.index in firstLevelItemIndex..(firstLevelItemIndex + lastLevelIndex) }
          .map { it.size }
          .filter { it > 0 }
      val estimatedItemSize =
        targetItemSize
          ?: measuredLevelSizes.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
          ?: (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 4
      val centeredOffset =
        centeredItemScrollOffset(
          viewportStartOffset = layoutInfo.viewportStartOffset,
          viewportEndOffset = layoutInfo.viewportEndOffset,
          itemSize = estimatedItemSize,
        )
      listState.animateScrollToItem(itemIndex, centeredOffset)
    }
  }

  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    LevelScrollFab(
      enabled = !isAtStart,
      contentDescription = "Scroll to start",
      onClick = { scope.launch { listState.animateScrollToItem(0) } },
    ) {
      Icon(
        imageVector = Icons.Rounded.VerticalAlignTop,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
    }
    LevelScrollFab(
      enabled = currentLevelIndex > 0,
      contentDescription = "Scroll up 10 levels",
      onClick = {
        scrollToLevel(jumpLevelIndex(currentLevelIndex, -LevelJumpSize, lastLevelIndex))
      },
    ) {
      Text("↑10", fontWeight = FontWeight.Bold)
    }
    LevelScrollFab(
      enabled = currentLevelIndex < lastLevelIndex,
      contentDescription = "Scroll down 10 levels",
      onClick = {
        scrollToLevel(jumpLevelIndex(currentLevelIndex, LevelJumpSize, lastLevelIndex))
      },
    ) {
      Text("↓10", fontWeight = FontWeight.Bold)
    }
    LevelScrollFab(
      enabled = true,
      contentDescription = "Scroll to the level after the last attempted level, or end",
      onClick = { scrollToLevelCentered(targetLevelIndex) },
    ) {
      Icon(
        imageVector = Icons.Rounded.Flag,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun LevelScrollFab(
  enabled: Boolean,
  contentDescription: String,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  SmallFloatingActionButton(
    onClick = { if (enabled) onClick() },
    modifier =
      Modifier
        .alpha(if (enabled) 1f else 0.38f)
        .semantics {
          this.contentDescription = contentDescription
          if (!enabled) disabled()
        },
    containerColor = ShuuenUi.Inverse,
    contentColor = ShuuenUi.OnInverse,
    shape = ShuuenUi.ControlShape,
    content = content,
  )
}
