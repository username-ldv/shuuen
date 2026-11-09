package ldv.shuuen.features.training.course

import kotlinx.coroutines.flow.first
import ldv.shuuen.core.settings.CourseOverrides
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.features.training.level.ChordsLevel
import ldv.shuuen.features.training.level.ChordsLocalLevelRepository
import ldv.shuuen.features.training.level.MelodiesLevel
import ldv.shuuen.features.training.level.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.level.PlayableTrainingLevel
import ldv.shuuen.features.training.level.SinglesLevel
import ldv.shuuen.features.training.level.SinglesLocalLevelRepository
import ldv.shuuen.features.training.level.TrainingFlow

/** Course levels come back with their course's local overrides already applied. */
internal class TrainingLevelResolverImpl(
  private val singlesRepository: SinglesLocalLevelRepository,
  private val melodiesRepository: MelodiesLocalLevelRepository,
  private val chordsRepository: ChordsLocalLevelRepository,
  private val courseRepository: CourseRepository,
  private val settingsRepository: SettingsRepository,
) : TrainingLevelResolver {
  override suspend fun resolveSingles(encodedReference: String): SinglesLevel =
    when (val reference = LevelReference.decode(encodedReference)) {
      is LevelReference.Local -> singlesRepository.getLevel(reference.id) ?: notFound(reference)
      is LevelReference.Remote -> {
        require(reference.mode == TrainingFlow.Singles) { "Expected a Singles level reference." }
        val playable = courseRepository.getLevel(reference).playable
        val level =
          (playable as? PlayableTrainingLevel.Singles)?.level
            ?: error("The course returned a different level mode for ${reference.encoded}.")
        level.withCourseOverrides(courseOverrides(reference))
      }
    }

  override suspend fun resolveMelodies(encodedReference: String): MelodiesLevel =
    when (val reference = LevelReference.decode(encodedReference)) {
      is LevelReference.Local -> melodiesRepository.getLevel(reference.id) ?: notFound(reference)
      is LevelReference.Remote -> {
        require(reference.mode == TrainingFlow.Melodies) { "Expected a Melodies level reference." }
        val playable = courseRepository.getLevel(reference).playable
        val level =
          (playable as? PlayableTrainingLevel.Melodies)?.level
            ?: error("The course returned a different level mode for ${reference.encoded}.")
        level.withCourseOverrides(courseOverrides(reference))
      }
    }

  override suspend fun resolveChords(encodedReference: String): ChordsLevel =
    when (val reference = LevelReference.decode(encodedReference)) {
      is LevelReference.Local -> chordsRepository.getLevel(reference.id) ?: notFound(reference)
      is LevelReference.Remote -> {
        require(reference.mode == TrainingFlow.Chords) { "Expected a Chords level reference." }
        val playable = courseRepository.getLevel(reference).playable
        (playable as? PlayableTrainingLevel.Chords)?.level
          ?: error("The course returned a different level mode for ${reference.encoded}.")
      }
    }

  private suspend fun courseOverrides(reference: LevelReference.Remote): CourseOverrides? =
    settingsRepository.settings.first().courseOverrides[reference.courseId]
}

private fun notFound(reference: LevelReference.Local): Nothing =
  error("level with id ${reference.id} not found")
