package ldv.shuuen.features.training.common

import ldv.shuuen.core.settings.DefaultLevelStatsWindow
import ldv.shuuen.core.settings.coerceLevelStatsWindow

data class LevelAccuracySample(
  val correctNotes: Int,
  val notesTotal: Int,
)

data class LevelAccuracyStats(
  val games: Int = 0,
  val windowSize: Int = DefaultLevelStatsWindow,
  val correctNotes: Int = 0,
  val notesTotal: Int = 0,
) {
  val accuracy: Float?
    get() = if (notesTotal > 0) correctNotes.toFloat() / notesTotal else null
}

fun levelAccuracyStats(
  samples: List<LevelAccuracySample>,
  windowSize: Int,
): LevelAccuracyStats {
  val coercedWindow = coerceLevelStatsWindow(windowSize)
  val considered = samples.take(coercedWindow)
  return LevelAccuracyStats(
    games = considered.size,
    windowSize = coercedWindow,
    correctNotes = considered.sumOf { it.correctNotes },
    notesTotal = considered.sumOf { it.notesTotal },
  )
}
