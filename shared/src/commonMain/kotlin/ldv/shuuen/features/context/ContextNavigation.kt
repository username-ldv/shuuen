package ldv.shuuen.features.context

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import ldv.shuuen.app.navigation.result.AppNavResult
import ldv.shuuen.app.navigation.result.LocalNavResultStore
import ldv.shuuen.app.navigation.result.resultKey
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
