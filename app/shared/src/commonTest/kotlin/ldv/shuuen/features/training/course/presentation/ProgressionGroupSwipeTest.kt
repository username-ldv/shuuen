package ldv.shuuen.features.training.course.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionGroupSwipeTest {
  private val groups = listOf("first", "second", "third")

  @Test
  fun forwardSwipeSelectsNextGroup() {
    assertEquals(
      "third",
      adjacentProgressionGroupId(groups, selectedGroupId = "second", moveForward = true),
    )
  }

  @Test
  fun backwardSwipeSelectsPreviousGroup() {
    assertEquals(
      "first",
      adjacentProgressionGroupId(groups, selectedGroupId = "second", moveForward = false),
    )
  }

  @Test
  fun swipeStopsAtEitherEnd() {
    assertNull(adjacentProgressionGroupId(groups, "first", moveForward = false))
    assertNull(adjacentProgressionGroupId(groups, "third", moveForward = true))
  }
}
