package ldv.shuuen.features.training.course.domain

import ldv.shuuen.core.settings.CourseOverrides
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.single.domain.SinglesLevel

/** This course level as played with its course's local [overrides]; null leaves it unchanged. */
fun SinglesLevel.withCourseOverrides(overrides: CourseOverrides?): SinglesLevel {
  val cents = overrides?.tuneInconsistencyCents ?: return this
  val config =
    when (val config = levelConfig) {
      is LevelConfig.Singles.Absolute -> config.copy(tuneInconsistencyCents = cents)
      is LevelConfig.Singles.Relative -> config.copy(tuneInconsistencyCents = cents)
    }
  return copy(levelConfig = config)
}

/**
 * This course level as played with its course's local [overrides]; null leaves it unchanged. MIDI
 * levels have no tune inconsistency and are never changed.
 */
fun MelodiesLevel.withCourseOverrides(overrides: CourseOverrides?): MelodiesLevel {
  val cents = overrides?.tuneInconsistencyCents ?: return this
  val random = config as? LevelConfig.Melodies.Random ?: return this
  return copy(config = random.copy(tuneInconsistencyCents = cents))
}
