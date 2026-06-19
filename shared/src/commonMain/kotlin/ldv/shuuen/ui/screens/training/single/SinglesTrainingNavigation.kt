package ldv.shuuen.ui.screens.training.single

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
import ldv.shuuen.ui.navigation.result.ContextRecipient
import ldv.shuuen.ui.navigation.result.LocalNavResultStore
import ldv.shuuen.ui.navigation.result.NavResultKeys.SinglesContextResult
import ldv.shuuen.ui.screens.level_end.LevelCompleteScreen
import ldv.shuuen.ui.screens.training.common.TrainingFlow
import ldv.shuuen.ui.screens.training.single.level_select.SinglesLevelSelectScreen
import ldv.shuuen.ui.screens.training.single.level_select.SinglesLevelSelectScreenViewModel
import ldv.shuuen.ui.screens.training.single.play.SinglesPlayScreen
import ldv.shuuen.ui.screens.training.single.play.SinglesPlayScreenViewModel
import ldv.shuuen.ui.screens.training.single.setup.SinglesSetupScreen
import ldv.shuuen.ui.screens.training.single.setup.SinglesSetupScreenViewModel
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
        onCreateNewLevel = { navigator.add(AppRoute.SinglesSetup) },
        viewModel = koinViewModel(),
    )
  }

  navigation<AppRoute.SinglesSetup> {
    val navigator = LocalAppNavigator.current
    val viewModel = koinViewModel<SinglesSetupScreenViewModel>()
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
        onOpenContext = { navigator.add(AppRoute.Context(ContextRecipient.SinglesSetup)) },
        onSaveLevel = { navigator.goBack() },
        viewModel = viewModel,
    )
  }

  navigation<AppRoute.SinglesPlay> { route ->
    val navigator = LocalAppNavigator.current
    SinglesPlayScreen(
        onNavigateBack = { navigator.goBack() },
        onLevelEnd = {
          navigator.replaceLastWith(AppRoute.SinglesLevelComplete(route.levelId))
        },
        viewModel = koinViewModel { parametersOf(route.levelId) },
    )
  }

  navigation<AppRoute.SinglesLevelComplete> { route ->
    val navigator = LocalAppNavigator.current
    LevelCompleteScreen(
        flow = TrainingFlow.Singles,
        onNavigateBack = { navigator.goBack() },
        onRetryLevel = { navigator.replaceLastWith(AppRoute.SinglesPlay(route.levelId)) },
        onNextLevel = { navigator.add(AppRoute.SinglesLevelSelect) },
    )
  }
}
