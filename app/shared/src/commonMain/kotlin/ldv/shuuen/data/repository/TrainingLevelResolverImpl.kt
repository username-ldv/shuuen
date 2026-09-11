package ldv.shuuen.data.repository

import kotlinx.coroutines.flow.first
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.course.domain.TrainingLevelResolver
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository

internal class TrainingLevelResolverImpl(
  private val singlesRepository: SinglesLocalLevelRepository,
  private val melodiesRepository: MelodiesLocalLevelRepository,
  private val chordsRepository: ChordsLocalLevelRepository,
  private val courseRepository: CourseRepository,
) : TrainingLevelResolver {
  override suspend fun resolveSingles(encodedReference: String): SinglesLevel =
    when (val reference = LevelReference.decode(encodedReference)) {
      is LevelReference.Local -> singlesRepository.getLevelById(reference.id).resolved()
      is LevelReference.Remote -> {
        require(reference.mode == TrainingFlow.Singles) { "Expected a Singles level reference." }
        val playable = courseRepository.getLevel(reference).playable
        (playable as? PlayableTrainingLevel.Singles)?.level
          ?: error("The course returned a different level mode for ${reference.encoded}.")
      }
    }

  override suspend fun resolveMelodies(encodedReference: String): MelodiesLevel =
    when (val reference = LevelReference.decode(encodedReference)) {
      is LevelReference.Local -> melodiesRepository.getLevelById(reference.id).resolved()
      is LevelReference.Remote -> {
        require(reference.mode == TrainingFlow.Melodies) { "Expected a Melodies level reference." }
        val playable = courseRepository.getLevel(reference).playable
        (playable as? PlayableTrainingLevel.Melodies)?.level
          ?: error("The course returned a different level mode for ${reference.encoded}.")
      }
    }

  override suspend fun resolveChords(encodedReference: String): ChordsLevel =
    when (val reference = LevelReference.decode(encodedReference)) {
      is LevelReference.Local -> chordsRepository.getLevelById(reference.id).resolved()
      is LevelReference.Remote -> {
        require(reference.mode == TrainingFlow.Chords) { "Expected a Chords level reference." }
        val playable = courseRepository.getLevel(reference).playable
        (playable as? PlayableTrainingLevel.Chords)?.level
          ?: error("The course returned a different level mode for ${reference.encoded}.")
      }
    }
}

private suspend fun <T : Any> kotlinx.coroutines.flow.Flow<ResponseState<T>>.resolved(): T =
  when (val response = first { it !is ResponseState.Loading }) {
    is ResponseState.Success -> response.result
    is ResponseState.Error -> throw response.throwable
    is ResponseState.Loading -> error("Unreachable loading state.")
  }
