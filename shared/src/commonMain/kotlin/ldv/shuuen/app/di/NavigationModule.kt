package ldv.shuuen.app.di

import ldv.shuuen.features.settings.settingsNavigationModule
import ldv.shuuen.features.context.contextNavigationModule
import ldv.shuuen.features.free_play.freePlayNavigationModule
import ldv.shuuen.features.main.mainMenuNavigationModule
import ldv.shuuen.features.training.level_end.LevelCompleteViewModel
import ldv.shuuen.features.training.melodies.melodiesTrainingNavigationModule
import ldv.shuuen.features.training.single.singlesTrainingNavigationModule
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val navigationModule = module {
  // Shared by the Singles and Melodies level-complete destinations.
  viewModel<LevelCompleteViewModel>()

  includes(
      mainMenuNavigationModule,
      freePlayNavigationModule,
      settingsNavigationModule,
      contextNavigationModule,
      singlesTrainingNavigationModule,
      melodiesTrainingNavigationModule,
  )
}
