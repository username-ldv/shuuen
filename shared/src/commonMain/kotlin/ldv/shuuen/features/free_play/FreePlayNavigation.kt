package ldv.shuuen.features.free_play

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val freePlayNavigationModule = module {
  viewModel<FreePlayViewModel>()

  navigation<AppRoute.FreePlay> {
    val navigator = LocalAppNavigator.current
    FreePlayScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navigator.goBack() },
    )
  }
}
