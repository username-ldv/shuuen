package ldv.shuuen.features.training.level_end

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.LevelAccuracyStats
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
  /** The same rolling level accuracy shown on level select. */
  val levelAccuracyStats: LevelAccuracyStats? = null,
)

class LevelCompleteViewModel(
  sessionId: String,
  private val sessionRepository: TrainingSessionRepository,
  private val settingsRepository: SettingsRepository,
  private val levelResolver: TrainingLevelResolver,
  private val courseRepository: CourseRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(LevelCompleteState())
  val state = _state.asStateFlow()
  private var levelAccuracyJob: Job? = null

  init {
    viewModelScope.launch {
      sessionRepository.getSessionById(sessionId).collect { response ->
        _state.update { it.copy(session = response) }
        if (response is ResponseState.Success) {
          observeLevelAccuracy(response.result)
          loadLevel(response.result)
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeLevelAccuracy(session: TrainingSession) {
    levelAccuracyJob?.cancel()
    levelAccuracyJob =
      viewModelScope.launch {
        settingsRepository.settings
          .map { coerceLevelStatsWindow(it.levelStatsWindow) }
          .distinctUntilChanged()
          .flatMapLatest { window ->
            sessionRepository.observeLevelAccuracyStats(session.flow, session.levelId, window)
          }
          .collect { stats -> _state.update { it.copy(levelAccuracyStats = stats) } }
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
