package ldv.shuuen.features.main

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val mainMenuNavigationModule = module {
  navigation<AppRoute.MainMenu> {
    val navigator = LocalAppNavigator.current
    MainMenuScreen(
        onOpenFreePlay = { navigator.add(AppRoute.FreePlay) },
        onOpenMelodies = { navigator.add(AppRoute.MelodiesLevelSelect) },
        onOpenSingles = { navigator.add(AppRoute.SinglesLevelSelect) },
        onOpenChords = { navigator.add(AppRoute.ChordsLevelSelect) },
        onOpenPitchSlide = { navigator.add(AppRoute.PitchSlide) },
        onOpenSettings = { navigator.add(AppRoute.Settings) },
    )
  }
}
