package ldv.shuuen.features.training.chords.level_select

import androidx.lifecycle.ViewModel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository

class ChordsLevelSelectScreenViewModel(levelRepository: ChordsLocalLevelRepository) :
  ViewModel() {
  val levels = levelRepository.getLevels()
}
