package ldv.shuuen.features.context

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import ldv.shuuen.app.navigation.result.AppNavResult
import ldv.shuuen.app.navigation.result.LocalNavResultStore
import ldv.shuuen.app.navigation.result.resultKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val contextNavigationModule = module {
  viewModel<ContextViewModel>()

  navigation<AppRoute.Context> { route ->
    val navigator = LocalAppNavigator.current
    val resultStore = LocalNavResultStore.current
    ContextScreen(
        onNavigateBack = { navigator.goBack() },
        onContextChosen = {
          resultStore.send(route.recipient.resultKey(), AppNavResult.ContextPickedResult(it))
        },
        viewModel = koinViewModel(),
    )
  }
}
