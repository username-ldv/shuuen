package ldv.shuuen.di

import ldv.shuuen.ui.screens.app_settings.settingsNavigationModule
import ldv.shuuen.ui.screens.context.contextNavigationModule
import ldv.shuuen.ui.screens.free_play.freePlayNavigationModule
import ldv.shuuen.ui.screens.main.mainMenuNavigationModule
import ldv.shuuen.ui.screens.training.melodies.melodiesTrainingNavigationModule
import ldv.shuuen.ui.screens.training.single.singlesTrainingNavigationModule
import org.koin.dsl.module

val navigationModule = module {
  includes(
      mainMenuNavigationModule,
      freePlayNavigationModule,
      settingsNavigationModule,
      contextNavigationModule,
      singlesTrainingNavigationModule,
      melodiesTrainingNavigationModule,
  )
}
