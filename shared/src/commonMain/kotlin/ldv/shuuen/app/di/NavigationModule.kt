package ldv.shuuen.app.di

import ldv.shuuen.features.settings.settingsNavigationModule
import ldv.shuuen.features.context.contextNavigationModule
import ldv.shuuen.features.free_play.freePlayNavigationModule
import ldv.shuuen.features.main.mainMenuNavigationModule
import ldv.shuuen.features.pitch_slide.pitchSlideNavigationModule
import ldv.shuuen.features.training.chords.chordsTrainingNavigationModule
import ldv.shuuen.features.training.level_end.LevelCompleteViewModel
import ldv.shuuen.features.training.melodies.melodiesTrainingNavigationModule
import ldv.shuuen.features.training.single.singlesTrainingNavigationModule
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val navigationModule = module {
  // Shared by the Singles, Melodies, and Chords level-complete destinations.
  viewModel<LevelCompleteViewModel>()

  includes(
      mainMenuNavigationModule,
      freePlayNavigationModule,
      pitchSlideNavigationModule,
      settingsNavigationModule,
      contextNavigationModule,
      singlesTrainingNavigationModule,
      melodiesTrainingNavigationModule,
      chordsTrainingNavigationModule,
  )
}
