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
)

class LevelCompleteViewModel(
  sessionId: String,
  sessionRepository: TrainingSessionRepository,
  private val levelResolver: TrainingLevelResolver,
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
}
