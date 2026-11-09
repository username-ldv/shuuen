package ldv.shuuen.features.training.level_end

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

/**
 * The level-complete screen is shared by the Singles, Melodies and Chords flows; each flow
 * registers its own navigation entry for it.
 */
val levelEndModule = module {
  viewModel<LevelCompleteViewModel>()
}
