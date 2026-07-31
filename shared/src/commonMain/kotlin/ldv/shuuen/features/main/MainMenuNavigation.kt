package ldv.shuuen.features.main

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import ldv.shuuen.core.online.BackendStatusMonitor
import ldv.shuuen.features.training.common.TrainingFlow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val mainMenuNavigationModule = module {
  viewModel<MainMenuViewModel>()

  navigation<AppRoute.MainMenu> {
    val navigator = LocalAppNavigator.current
    val backendStatusMonitor = koinInject<BackendStatusMonitor>()
    MainMenuScreen(
        viewModel = koinViewModel(),
        onStartLevel = { flow, levelReference ->
          navigator.add(
            when (flow) {
              TrainingFlow.Singles -> AppRoute.SinglesPlay(levelReference)
              TrainingFlow.Melodies -> AppRoute.MelodiesPlay(levelReference)
              TrainingFlow.Chords -> AppRoute.ChordsPlay(levelReference)
            }
          )
        },
        onOpenFreePlay = { navigator.add(AppRoute.FreePlay) },
        onOpenMelodies = { navigator.add(AppRoute.MelodiesLevelSelect) },
        onOpenSingles = { navigator.add(AppRoute.SinglesLevelSelect) },
        onOpenChords = { navigator.add(AppRoute.ChordsLevelSelect) },
        onOpenPitchSlide = { navigator.add(AppRoute.PitchSlide) },
        onOpenSettings = { navigator.add(AppRoute.Settings) },
        onRefreshBackend = backendStatusMonitor::refresh,
    )
  }
}
