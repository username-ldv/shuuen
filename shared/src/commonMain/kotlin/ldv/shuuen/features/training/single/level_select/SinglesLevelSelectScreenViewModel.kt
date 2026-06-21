package ldv.shuuen.features.training.single.level_select

import androidx.lifecycle.ViewModel
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository

class SinglesLevelSelectScreenViewModel(levelRepository: SinglesLocalLevelRepository) :
  ViewModel() {
  val levels = levelRepository.getLevels()

}