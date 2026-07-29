package ldv.shuuen.features.training.single

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.features.training.level_end.LevelCompleteScreen
import ldv.shuuen.features.training.single.level_select.SinglesLevelSelectScreen
import ldv.shuuen.features.training.single.level_select.SinglesLevelSelectScreenViewModel
import ldv.shuuen.features.training.single.play.SinglesPlayScreen
import ldv.shuuen.features.training.single.play.SinglesPlayScreenViewModel
import ldv.shuuen.features.training.single.setup.SinglesSetupScreen
import ldv.shuuen.features.training.single.setup.SinglesSetupScreenViewModel
import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import ldv.shuuen.app.navigation.result.ContextRecipient
import ldv.shuuen.app.navigation.result.LocalNavResultStore
import ldv.shuuen.app.navigation.result.NavResultKeys.SinglesContextResult
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val singlesTrainingNavigationModule = module {
  viewModel<SinglesLevelSelectScreenViewModel>()
  viewModel<SinglesSetupScreenViewModel>()
  viewModel<SinglesPlayScreenViewModel>()

  navigation<AppRoute.SinglesLevelSelect> {
    val navigator = LocalAppNavigator.current
    SinglesLevelSelectScreen(
      onNavigateBack = { navigator.goBack() },
      onStartLevel = { levelId -> navigator.add(AppRoute.SinglesPlay(levelId)) },
      onCreateNewLevel = { navigator.add(AppRoute.SinglesSetup()) },
      onEditLevel = { levelId -> navigator.add(AppRoute.SinglesSetup(levelId)) },
      viewModel = koinViewModel(),
    )
  }

  navigation<AppRoute.SinglesSetup> { route ->
    val navigator = LocalAppNavigator.current
    val viewModel = koinViewModel<SinglesSetupScreenViewModel> {
      parametersOf(route.levelId.orEmpty())
    }
    val resultStore = LocalNavResultStore.current
    val result = resultStore.peek(SinglesContextResult)
    LaunchedEffect(result) {
      result?.let {
        viewModel.updateContext(it.context)
        resultStore.clear(SinglesContextResult)
      }
    }

    SinglesSetupScreen(
      onNavigateBack = { navigator.goBack() },
      onOpenContext = { contextId ->
        navigator.add(AppRoute.Context(ContextRecipient.SinglesSetup, contextId))
      },
      onSaveLevel = { navigator.goBack() },
      viewModel = viewModel,
    )
  }

  navigation<AppRoute.SinglesPlay> { route ->
    val navigator = LocalAppNavigator.current
    SinglesPlayScreen(
      onNavigateBack = { navigator.goBack() },
      onLevelEnd = { sessionId ->
        navigator.replaceLastWith(AppRoute.SinglesLevelComplete(route.levelId, sessionId))
      },
      viewModel = koinViewModel { parametersOf(route.levelId) },
    )
  }

  navigation<AppRoute.SinglesLevelComplete> { route ->
    val navigator = LocalAppNavigator.current
    LevelCompleteScreen(
      onNavigateBack = { navigator.goBack() },
      onRetryLevel = { navigator.replaceLastWith(AppRoute.SinglesPlay(route.levelId)) },
      onNextLevel = { nextLevelId ->
        navigator.replaceLastWith(AppRoute.SinglesPlay(nextLevelId))
      },
      // The level select this play session started from is right below on the back stack.
      onLevelSelect = { navigator.goBack() },
      viewModel = koinViewModel { parametersOf(route.sessionId) },
    )
  }
}
