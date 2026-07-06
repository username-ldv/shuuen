package ldv.shuuen.training

import kotlin.test.Test
import kotlin.test.assertEquals
import ldv.shuuen.core.settings.DefaultLevelStatsWindow
import ldv.shuuen.core.settings.MaxLevelStatsWindow
import ldv.shuuen.core.settings.MinLevelStatsWindow
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import ldv.shuuen.features.training.common.LevelAccuracySample
import ldv.shuuen.features.training.common.levelAccuracyStats

class LevelStatsTest {
  @Test
  fun defaultsToLastFifteenGames() {
    assertEquals(DefaultLevelStatsWindow, coerceLevelStatsWindow(DefaultLevelStatsWindow))
  }

  @Test
  fun clampsConfiguredWindow() {
    assertEquals(MinLevelStatsWindow, coerceLevelStatsWindow(-5))
    assertEquals(MaxLevelStatsWindow, coerceLevelStatsWindow(MaxLevelStatsWindow + 20))
  }

  @Test
  fun usesMostRecentWindowAndWeightsByNotes() {
    val samples =
      listOf(
        LevelAccuracySample(correctNotes = 8, notesTotal = 10),
        LevelAccuracySample(correctNotes = 5, notesTotal = 10),
        LevelAccuracySample(correctNotes = 100, notesTotal = 100),
      )

    val stats = levelAccuracyStats(samples, windowSize = 2)

    assertEquals(2, stats.games)
    assertEquals(13, stats.correctNotes)
    assertEquals(20, stats.notesTotal)
    assertEquals(0.65f, stats.accuracy)
  }

  @Test
  fun emptyHistoryHasNoAccuracy() {
    val stats = levelAccuracyStats(emptyList(), windowSize = 20)

    assertEquals(0, stats.games)
    assertEquals(null, stats.accuracy)
  }
}
