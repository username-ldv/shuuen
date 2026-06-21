package ldv.shuuen.features.settings

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val settingsNavigationModule = module {
  viewModel<SettingsViewModel>()

  navigation<AppRoute.Settings> {
    val navigator = LocalAppNavigator.current
    SettingsScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navigator.goBack() },
    )
  }
}
