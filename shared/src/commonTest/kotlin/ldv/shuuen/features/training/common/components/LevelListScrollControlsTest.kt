package ldv.shuuen.features.training.common.components

import kotlin.test.Test
import kotlin.test.assertEquals

class LevelListScrollControlsTest {
  private val levels = listOf("one", "two", "three", "four")

  @Test
  fun progressTargetFollowsTheLastAttemptedLevel() {
    assertEquals(3, nextLevelAfterLastAttemptedIndex(levels, setOf("one", "three")))
  }

  @Test
  fun progressTargetStartsAtTheFirstLevelWhenNoneWereAttempted() {
    assertEquals(0, nextLevelAfterLastAttemptedIndex(levels, emptySet()))
  }

  @Test
  fun progressTargetFallsBackToEndWhenTheLastLevelWasAttempted() {
    assertEquals(3, nextLevelAfterLastAttemptedIndex(levels, setOf("four")))
  }

  @Test
  fun progressTargetReturnsNoTargetForAnEmptyList() {
    assertEquals(-1, nextLevelAfterLastAttemptedIndex(emptyList(), emptySet()))
  }

  @Test
  fun levelJumpsClampToListBounds() {
    assertEquals(0, jumpLevelIndex(currentIndex = 3, amount = -10, lastIndex = 19))
    assertEquals(19, jumpLevelIndex(currentIndex = 15, amount = 10, lastIndex = 19))
    assertEquals(12, jumpLevelIndex(currentIndex = 2, amount = 10, lastIndex = 19))
  }

  @Test
  fun centeredScrollOffsetAlignsItemAndViewportCenters() {
    assertEquals(
      -300,
      centeredItemScrollOffset(
        viewportStartOffset = 0,
        viewportEndOffset = 800,
        itemSize = 200,
      ),
    )
    assertEquals(
      -310,
      centeredItemScrollOffset(
        viewportStartOffset = 20,
        viewportEndOffset = 820,
        itemSize = 180,
      ),
    )
  }
}
