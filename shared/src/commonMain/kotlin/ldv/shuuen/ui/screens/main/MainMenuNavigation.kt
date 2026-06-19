package ldv.shuuen.ui.screens.main

import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
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
        onOpenSettings = { navigator.add(AppRoute.Settings) },
    )
  }
}
