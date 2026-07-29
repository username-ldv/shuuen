package ldv.shuuen.features.training.level_end

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.course.domain.TrainingLevelResolver
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.single.domain.SinglesLevel

/** The played level's current definition, for the parameters section of the results screen. */
sealed interface CompletedLevel {
  data class Singles(val level: SinglesLevel) : CompletedLevel

  data class Melodies(val level: MelodiesLevel) : CompletedLevel

  data class Chords(val level: ChordsLevel) : CompletedLevel
}

data class LevelCompleteState(
  val session: ResponseState<TrainingSession> = ResponseState.Loading,
  /** Null while loading or when the level no longer exists; the parameters section hides then. */
  val level: CompletedLevel? = null,
  /** Encoded remote reference for the next level in this progression group, when one exists. */
  val nextLevelReference: String? = null,
)

class LevelCompleteViewModel(
  sessionId: String,
  sessionRepository: TrainingSessionRepository,
  private val levelResolver: TrainingLevelResolver,
  private val courseRepository: CourseRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(LevelCompleteState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      sessionRepository.getSessionById(sessionId).collect { response ->
        _state.update { it.copy(session = response) }
        if (response is ResponseState.Success) loadLevel(response.result)
      }
    }
  }

  private suspend fun loadLevel(session: TrainingSession) {
    val reference =
      runCatching { LevelReference.decode(session.levelId) }
        .getOrElse { error ->
          Napier.w(error) { "Couldn't decode the completed level reference" }
          return
        }
    if (reference is LevelReference.Remote) {
      loadCourseLevel(session, reference)
      return
    }

    runCatching {
      when (session.flow) {
        TrainingFlow.Singles -> CompletedLevel.Singles(levelResolver.resolveSingles(session.levelId))
        TrainingFlow.Melodies -> CompletedLevel.Melodies(levelResolver.resolveMelodies(session.levelId))
        TrainingFlow.Chords -> CompletedLevel.Chords(levelResolver.resolveChords(session.levelId))
      }
    }.onSuccess { level ->
      _state.update { it.copy(level = level) }
    }.onFailure { error ->
      Napier.w(error) { "Couldn't load the completed level" }
    }
  }

  private suspend fun loadCourseLevel(
    session: TrainingSession,
    reference: LevelReference.Remote,
  ) {
    runCatching {
      require(reference.mode == session.flow) { "The course level mode doesn't match the session." }
      val item = courseRepository.getLevel(reference)
      val completedLevel =
        when (val playable = item.playable) {
          is PlayableTrainingLevel.Singles -> CompletedLevel.Singles(playable.level)
          is PlayableTrainingLevel.Melodies -> CompletedLevel.Melodies(playable.level)
          is PlayableTrainingLevel.Chords -> CompletedLevel.Chords(playable.level)
        }
      val nextReference =
        item.navigation?.nextLevelId?.let { nextLevelId ->
          LevelReference.Remote(reference.courseId, reference.mode, nextLevelId).encoded
        }
      completedLevel to nextReference
    }.onSuccess { (level, nextReference) ->
      _state.update { it.copy(level = level, nextLevelReference = nextReference) }
    }.onFailure { error ->
      Napier.w(error) { "Couldn't load the completed course level" }
    }
  }
}
