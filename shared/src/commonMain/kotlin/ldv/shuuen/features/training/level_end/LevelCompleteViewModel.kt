package ldv.shuuen.features.training.level_end

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository

/** The played level's current definition, for the parameters section of the results screen. */
sealed interface CompletedLevel {
  data class Singles(val level: SinglesLevel) : CompletedLevel

  data class Melodies(val level: MelodiesLevel) : CompletedLevel
}

data class LevelCompleteState(
  val session: ResponseState<TrainingSession> = ResponseState.Loading,
  /** Null while loading or when the level no longer exists; the parameters section hides then. */
  val level: CompletedLevel? = null,
)

class LevelCompleteViewModel(
  sessionId: String,
  sessionRepository: TrainingSessionRepository,
  private val singlesLevelRepository: SinglesLocalLevelRepository,
  private val melodiesLevelRepository: MelodiesLocalLevelRepository,
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
    when (session.flow) {
      TrainingFlow.Singles ->
        singlesLevelRepository.getLevelById(session.levelId).collect { response ->
          when (response) {
            is ResponseState.Success ->
              _state.update { it.copy(level = CompletedLevel.Singles(response.result)) }

            is ResponseState.Error ->
              Napier.w(response.throwable) { "Couldn't load the completed level" }

            is ResponseState.Loading -> Unit
          }
        }

      TrainingFlow.Melodies ->
        melodiesLevelRepository.getLevelById(session.levelId).collect { response ->
          when (response) {
            is ResponseState.Success ->
              _state.update { it.copy(level = CompletedLevel.Melodies(response.result)) }

            is ResponseState.Error ->
              Napier.w(response.throwable) { "Couldn't load the completed level" }

            is ResponseState.Loading -> Unit
          }
        }
    }
  }
}
