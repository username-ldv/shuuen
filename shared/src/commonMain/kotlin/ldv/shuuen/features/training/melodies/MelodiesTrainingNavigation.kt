package ldv.shuuen.features.training.melodies

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.features.training.level_end.LevelCompleteScreen
import ldv.shuuen.features.training.melodies.level_select.MelodiesLevelSelectScreen
import ldv.shuuen.features.training.melodies.level_select.MelodiesLevelSelectScreenViewModel
import ldv.shuuen.features.training.melodies.play.MelodiesPlayScreen
import ldv.shuuen.features.training.melodies.play.MelodiesPlayScreenViewModel
import ldv.shuuen.features.training.melodies.setup.MelodiesSetupScreen
import ldv.shuuen.features.training.melodies.setup.MelodiesSetupScreenViewModel
import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import ldv.shuuen.app.navigation.result.ContextRecipient
import ldv.shuuen.app.navigation.result.LocalNavResultStore
import ldv.shuuen.app.navigation.result.NavResultKeys.MelodiesContextResult
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val melodiesTrainingNavigationModule = module {
  viewModel<MelodiesLevelSelectScreenViewModel>()
  viewModel<MelodiesSetupScreenViewModel>()
  viewModel<MelodiesPlayScreenViewModel>()

  navigation<AppRoute.MelodiesLevelSelect> {
    val navigator = LocalAppNavigator.current
    MelodiesLevelSelectScreen(
      onNavigateBack = { navigator.goBack() },
      onStartLevel = { levelId -> navigator.add(AppRoute.MelodiesPlay(levelId)) },
      onCreateNewLevel = { navigator.add(AppRoute.MelodiesSetup()) },
      onEditLevel = { levelId -> navigator.add(AppRoute.MelodiesSetup(levelId)) },
      viewModel = koinViewModel(),
    )
  }

  navigation<AppRoute.MelodiesSetup> { route ->
    val navigator = LocalAppNavigator.current
    val viewModel = koinViewModel<MelodiesSetupScreenViewModel> {
      parametersOf(route.levelId.orEmpty())
    }
    val resultStore = LocalNavResultStore.current
    val result = resultStore.peek(MelodiesContextResult)
    LaunchedEffect(result) {
      result?.let {
        viewModel.updateContext(it.context)
        resultStore.clear(MelodiesContextResult)
      }
    }
    MelodiesSetupScreen(
      onNavigateBack = { navigator.goBack() },
      onOpenContext = { contextId ->
        navigator.add(AppRoute.Context(ContextRecipient.MelodiesSetup, contextId))
      },
      onSaveLevel = { navigator.goBack() },
      viewModel = viewModel,
    )
  }

  navigation<AppRoute.MelodiesPlay> { route ->
    val navigator = LocalAppNavigator.current
    MelodiesPlayScreen(
      onNavigateBack = { navigator.goBack() },
      onLevelEnd = { sessionId ->
        navigator.replaceLastWith(AppRoute.MelodiesLevelComplete(route.levelId, sessionId))
      },
      viewModel = koinViewModel { parametersOf(route.levelId) },
    )
  }

  navigation<AppRoute.MelodiesLevelComplete> { route ->
    val navigator = LocalAppNavigator.current
    LevelCompleteScreen(
      onNavigateBack = { navigator.goBack() },
      onRetryLevel = { navigator.replaceLastWith(AppRoute.MelodiesPlay(route.levelId)) },
      // The level select this play session started from is right below on the back stack.
      onLevelSelect = { navigator.goBack() },
      viewModel = koinViewModel { parametersOf(route.sessionId) },
    )
  }
}
