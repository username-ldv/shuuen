package ldv.shuuen.features.training.melodies.level_select

import androidx.lifecycle.ViewModel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository

class MelodiesLevelSelectScreenViewModel(levelRepository: MelodiesLocalLevelRepository) :
  ViewModel() {
  val levels = levelRepository.getLevels()
}
