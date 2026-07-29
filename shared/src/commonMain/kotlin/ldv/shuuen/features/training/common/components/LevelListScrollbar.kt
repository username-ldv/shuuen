package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.ui.components.ShuuenUi

internal data class LevelListScrollbarMetrics(
  val positionFraction: Float,
  val sizeFraction: Float,
  val trackStartFraction: Float = 0f,
)

internal fun levelListTrackStartFraction(
  firstVisibleLevelIndex: Int,
  firstLevelItemOffset: Int,
  viewportStartOffset: Int,
  viewportEndOffset: Int,
): Float {
  if (firstVisibleLevelIndex != 0) return 0f
  val viewportSize = viewportEndOffset - viewportStartOffset
  if (viewportSize <= 0) return 0f
  return ((firstLevelItemOffset - viewportStartOffset).toFloat() / viewportSize)
    .coerceIn(0f, 1f)
}

/**
 * Converts the first visible logical level into a scrollbar position for the entire list. Online
 * lists pass the backend's total here, so unloaded pages still reserve their share of the track.
 */
internal fun levelListScrollbarMetrics(
  firstVisibleLevelIndex: Int,
  firstItemHiddenFraction: Float,
  totalLevelCount: Long,
  reachedStart: Boolean = false,
  reachedEnd: Boolean = false,
): LevelListScrollbarMetrics? {
  if (firstVisibleLevelIndex < 0 || totalLevelCount <= 1) return null
  val effectiveTotal = maxOf(totalLevelCount, firstVisibleLevelIndex.toLong() + 1)

  // A stable logical estimate: unlike the live visible-card count, this cannot change when a
  // paging message is replaced by newly loaded cards. The minimum pixel height below still keeps
  // very long lists usable as an indicator.
  val estimatedVisibleLevels =
    minOf(EstimatedVisibleLevelCount, effectiveTotal - 1).coerceAtLeast(1L)
  val sizeFraction = (estimatedVisibleLevels.toDouble() / effectiveTotal).toFloat()
  val positionFraction =
    when {
      reachedStart -> 0f
      reachedEnd -> 1f
      else ->
        ((firstVisibleLevelIndex + firstItemHiddenFraction.coerceIn(0f, 1f)) /
            (effectiveTotal - 1).toDouble())
          .toFloat()
          .coerceIn(0f, 1f)
    }
  return LevelListScrollbarMetrics(positionFraction, sizeFraction)
}

/** A passive scrollbar that never intercepts vertical scrolling or horizontal group swipes. */
@Composable
fun LevelListScrollbar(
  listState: LazyListState,
  loadedLevelCount: Int,
  totalLevelCount: Long,
  firstLevelItemIndex: Int,
  modifier: Modifier = Modifier,
) {
  if (loadedLevelCount <= 0 || totalLevelCount <= 0) return

  val metrics by
    remember(listState, loadedLevelCount, totalLevelCount, firstLevelItemIndex) {
      derivedStateOf {
        val layout = listState.layoutInfo
        val effectiveTotal = maxOf(totalLevelCount, loadedLevelCount.toLong())
        val hasUnloadedLevels = loadedLevelCount.toLong() < effectiveTotal
        if (
          effectiveTotal <= 1 ||
            (!hasUnloadedLevels && !listState.canScrollBackward && !listState.canScrollForward)
        ) {
          return@derivedStateOf null
        }
        val lastLevelItemIndex = firstLevelItemIndex + loadedLevelCount - 1
        val visibleItems = layout.visibleItemsInfo
        val first =
          visibleItems.firstOrNull { it.index in firstLevelItemIndex..lastLevelItemIndex }
        if (first == null) {
          return@derivedStateOf when {
            !hasUnloadedLevels && !listState.canScrollForward ->
              levelListScrollbarMetrics(
                firstVisibleLevelIndex =
                  minOf(effectiveTotal - 1, Int.MAX_VALUE.toLong()).toInt(),
                firstItemHiddenFraction = 0f,
                totalLevelCount = effectiveTotal,
                reachedEnd = true,
              )
            else -> null
          }
        }
        val firstHiddenFraction =
          if (first.size > 0) {
            ((layout.viewportStartOffset - first.offset).toFloat() / first.size).coerceIn(0f, 1f)
          } else {
            0f
          }
        val firstVisibleLevelIndex = first.index - firstLevelItemIndex
        levelListScrollbarMetrics(
            firstVisibleLevelIndex = firstVisibleLevelIndex,
            firstItemHiddenFraction = firstHiddenFraction,
            totalLevelCount = effectiveTotal,
            reachedStart = !listState.canScrollBackward,
            reachedEnd = !hasUnloadedLevels && !listState.canScrollForward,
          )
          ?.copy(
            trackStartFraction =
              levelListTrackStartFraction(
                firstVisibleLevelIndex = firstVisibleLevelIndex,
                firstLevelItemOffset = first.offset,
                viewportStartOffset = layout.viewportStartOffset,
                viewportEndOffset = layout.viewportEndOffset,
              )
          )
      }
    }
  val current = metrics ?: return
  val trackColor = ShuuenUi.Ink.copy(alpha = 0.10f)
  val thumbColor = ShuuenUi.Dim.copy(alpha = 0.75f)

  Canvas(modifier = modifier.width(4.dp).fillMaxHeight()) {
    val radius = size.width / 2f
    val trackTop = size.height * current.trackStartFraction
    val trackHeight = size.height - trackTop
    if (trackHeight <= 0f) return@Canvas
    drawRoundRect(
      color = trackColor,
      topLeft = Offset(0f, trackTop),
      cornerRadius = CornerRadius(radius, radius),
      size = Size(size.width, trackHeight),
    )
    val thumbHeight =
      (trackHeight * current.sizeFraction)
        .coerceAtLeast(MinimumScrollbarThumbHeight.toPx())
        .coerceAtMost(trackHeight)
    val thumbTop = trackTop + (trackHeight - thumbHeight) * current.positionFraction
    drawRoundRect(
      color = thumbColor,
      topLeft = Offset(0f, thumbTop),
      size = Size(size.width, thumbHeight),
      cornerRadius = CornerRadius(radius, radius),
    )
  }
}

private val MinimumScrollbarThumbHeight = 32.dp
private const val EstimatedVisibleLevelCount = 3L
