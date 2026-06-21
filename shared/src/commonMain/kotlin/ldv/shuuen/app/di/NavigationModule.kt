package ldv.shuuen.app.di

import ldv.shuuen.features.settings.settingsNavigationModule
import ldv.shuuen.features.context.contextNavigationModule
import ldv.shuuen.features.free_play.freePlayNavigationModule
import ldv.shuuen.features.main.mainMenuNavigationModule
import ldv.shuuen.features.training.melodies.melodiesTrainingNavigationModule
import ldv.shuuen.features.training.single.singlesTrainingNavigationModule
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
