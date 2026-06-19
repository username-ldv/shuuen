package ldv.shuuen.ui.screens.app_settings

import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
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
