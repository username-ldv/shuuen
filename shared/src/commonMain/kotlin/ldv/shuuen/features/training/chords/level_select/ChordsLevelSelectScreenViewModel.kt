package ldv.shuuen.features.training.chords.level_select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ldv.shuuen.core.settings.DefaultLevelStatsWindow
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository

class ChordsLevelSelectScreenViewModel(
  levelRepository: ChordsLocalLevelRepository,
  settingsRepository: SettingsRepository,
  private val trainingSessionRepository: TrainingSessionRepository,
) : ViewModel() {
  val levels = levelRepository.getLevels()

  private val statsWindow =
    settingsRepository.settings
      .map { coerceLevelStatsWindow(it.levelStatsWindow) }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultLevelStatsWindow)

  @OptIn(ExperimentalCoroutinesApi::class)
  fun levelStats(levelId: String): Flow<LevelAccuracyStats> =
    statsWindow.flatMapLatest { window ->
      trainingSessionRepository.observeLevelAccuracyStats(TrainingFlow.Chords, levelId, window)
    }
}
