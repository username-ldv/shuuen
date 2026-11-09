package ldv.shuuen.features.context

import ldv.shuuen.core.database.AppDatabase
import ldv.shuuen.core.navigation.AppRoute
import ldv.shuuen.core.navigation.LocalAppNavigator
import ldv.shuuen.core.navigation.result.AppNavResult
import ldv.shuuen.core.navigation.result.LocalNavResultStore
import ldv.shuuen.core.navigation.result.resultKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val contextModule = module {
  single<ContextDao> { get<AppDatabase>().contextDao() }
  single<ContextRepository>()
  viewModel<ContextViewModel>()

  navigation<AppRoute.Context> { route ->
    val navigator = LocalAppNavigator.current
    val resultStore = LocalNavResultStore.current
    ContextScreen(
      onNavigateBack = { navigator.goBack() },
      onContextChosen = {
        resultStore.send(route.recipient.resultKey(), AppNavResult.ContextPickedResult(it))
      },
      viewModel = koinViewModel { parametersOf(route.contextId.orEmpty()) },
    )
  }
}
