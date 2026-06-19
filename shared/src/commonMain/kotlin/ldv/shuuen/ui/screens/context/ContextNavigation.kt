package ldv.shuuen.ui.screens.context

import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
import ldv.shuuen.ui.navigation.result.AppNavResult
import ldv.shuuen.ui.navigation.result.LocalNavResultStore
import ldv.shuuen.ui.navigation.result.resultKey
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val contextNavigationModule = module {
  navigation<AppRoute.Context> { route ->
    val navigator = LocalAppNavigator.current
    val resultStore = LocalNavResultStore.current
    ContextScreen(
        onNavigateBack = { navigator.goBack() },
        onContextChosen = {
          resultStore.send(route.recipient.resultKey(), AppNavResult.ContextPickedResult(it))
        },
    )
  }
}
