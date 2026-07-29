package ldv.shuuen.features.training.common.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelListScrollbarTest {
  @Test
  fun backendTotalControlsThumbSizeBeforeAllPagesAreLoaded() {
    val metrics =
      requireNotNull(
        levelListScrollbarMetrics(
          firstVisibleLevelIndex = 0,
          firstItemHiddenFraction = 0f,
          totalLevelCount = 100,
        )
      )

    assertEquals(0.03f, metrics.sizeFraction)
    assertEquals(0f, metrics.positionFraction)
  }

  @Test
  fun finalViewportReachesTheEndOfTheTrack() {
    val metrics =
      requireNotNull(
        levelListScrollbarMetrics(
          firstVisibleLevelIndex = 95,
          firstItemHiddenFraction = 0f,
          totalLevelCount = 100,
          reachedEnd = true,
        )
      )

    assertEquals(1f, metrics.positionFraction)
  }

  @Test
  fun aSingleLogicalLevelDoesNotNeedAScrollbar() {
    assertNull(
      levelListScrollbarMetrics(
        firstVisibleLevelIndex = 0,
        firstItemHiddenFraction = 0f,
        totalLevelCount = 1,
      )
    )
  }

  @Test
  fun positionDependsOnTheLogicalAnchorRatherThanLoadedPageSize() {
    val metrics =
      requireNotNull(
        levelListScrollbarMetrics(
          firstVisibleLevelIndex = 15,
          firstItemHiddenFraction = 0.25f,
          totalLevelCount = 100,
        )
      )

    assertEquals(15.25f / 99f, metrics.positionFraction)
  }

  @Test
  fun trackStartsAtTheFirstLevelWhileAHeaderIsVisible() {
    assertEquals(
      0.25f,
      levelListTrackStartFraction(
        firstVisibleLevelIndex = 0,
        firstLevelItemOffset = 150,
        viewportStartOffset = 0,
        viewportEndOffset = 600,
      ),
    )
    assertEquals(
      0f,
      levelListTrackStartFraction(
        firstVisibleLevelIndex = 1,
        firstLevelItemOffset = 80,
        viewportStartOffset = 0,
        viewportEndOffset = 600,
      ),
    )
  }
}
