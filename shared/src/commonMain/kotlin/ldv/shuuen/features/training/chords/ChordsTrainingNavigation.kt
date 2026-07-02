package ldv.shuuen.features.training.chords

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import ldv.shuuen.app.navigation.result.ContextRecipient
import ldv.shuuen.app.navigation.result.LocalNavResultStore
import ldv.shuuen.app.navigation.result.NavResultKeys.ChordsContextResult
import ldv.shuuen.features.training.chords.level_select.ChordsLevelSelectScreen
import ldv.shuuen.features.training.chords.level_select.ChordsLevelSelectScreenViewModel
import ldv.shuuen.features.training.chords.play.ChordsPlayScreen
import ldv.shuuen.features.training.chords.play.ChordsPlayScreenViewModel
import ldv.shuuen.features.training.chords.setup.ChordsSetupScreen
import ldv.shuuen.features.training.chords.setup.ChordsSetupScreenViewModel
import ldv.shuuen.features.training.level_end.LevelCompleteScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val chordsTrainingNavigationModule = module {
  viewModel<ChordsLevelSelectScreenViewModel>()
  viewModel<ChordsSetupScreenViewModel>()
  viewModel<ChordsPlayScreenViewModel>()

  navigation<AppRoute.ChordsLevelSelect> {
    val navigator = LocalAppNavigator.current
    ChordsLevelSelectScreen(
      onNavigateBack = { navigator.goBack() },
      onStartLevel = { levelId -> navigator.add(AppRoute.ChordsPlay(levelId)) },
      onCreateNewLevel = { navigator.add(AppRoute.ChordsSetup) },
      viewModel = koinViewModel(),
    )
  }

  navigation<AppRoute.ChordsSetup> {
    val navigator = LocalAppNavigator.current
    val viewModel = koinViewModel<ChordsSetupScreenViewModel>()
    val resultStore = LocalNavResultStore.current
    val result = resultStore.peek(ChordsContextResult)
    LaunchedEffect(result) {
      result?.let {
        viewModel.updateContext(it.context)
        resultStore.clear(ChordsContextResult)
      }
    }

    ChordsSetupScreen(
      onNavigateBack = { navigator.goBack() },
      onOpenContext = { navigator.add(AppRoute.Context(ContextRecipient.ChordsSetup)) },
      onSaveLevel = { navigator.goBack() },
      viewModel = viewModel,
    )
  }

  navigation<AppRoute.ChordsPlay> { route ->
    val navigator = LocalAppNavigator.current
    ChordsPlayScreen(
      onNavigateBack = { navigator.goBack() },
      onLevelEnd = { sessionId ->
        navigator.replaceLastWith(AppRoute.ChordsLevelComplete(route.levelId, sessionId))
      },
      viewModel = koinViewModel { parametersOf(route.levelId) },
    )
  }

  navigation<AppRoute.ChordsLevelComplete> { route ->
    val navigator = LocalAppNavigator.current
    LevelCompleteScreen(
      onNavigateBack = { navigator.goBack() },
      onRetryLevel = { navigator.replaceLastWith(AppRoute.ChordsPlay(route.levelId)) },
      // The level select this play session started from is right below on the back stack.
      onLevelSelect = { navigator.goBack() },
      viewModel = koinViewModel { parametersOf(route.sessionId) },
    )
  }
}
