package ldv.shuuen.features.training.common.components

import kotlin.test.Test
import kotlin.test.assertEquals

class LevelSortActionTest {
  @Test
  fun sortsLevelsOldestFirstInAscendingOrder() {
    val ids =
      listOf(
        "0192f1a0-0000-7000-8000-000000000000",
        "018f1000-0000-7000-8000-000000000000",
        "01900000-0000-7000-8000-000000000000",
      )

    assertEquals(
      listOf(
        "018f1000-0000-7000-8000-000000000000",
        "01900000-0000-7000-8000-000000000000",
        "0192f1a0-0000-7000-8000-000000000000",
      ),
      ids.sortedByLevelCreation(LevelSortOrder.Ascending) { it },
    )
  }

  @Test
  fun sortsLevelsNewestFirstInDescendingOrder() {
    val ids =
      listOf(
        "0192f1a0-0000-7000-8000-000000000000",
        "018f1000-0000-7000-8000-000000000000",
        "01900000-0000-7000-8000-000000000000",
      )

    assertEquals(
      listOf(
        "0192f1a0-0000-7000-8000-000000000000",
        "01900000-0000-7000-8000-000000000000",
        "018f1000-0000-7000-8000-000000000000",
      ),
      ids.sortedByLevelCreation(LevelSortOrder.Descending) { it },
    )
  }

  @Test
  fun togglesBetweenAscendingAndDescending() {
    assertEquals(LevelSortOrder.Descending, LevelSortOrder.Ascending.toggled())
    assertEquals(LevelSortOrder.Ascending, LevelSortOrder.Descending.toggled())
  }
}
