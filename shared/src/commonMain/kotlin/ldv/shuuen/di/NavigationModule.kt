package ldv.shuuen.di

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.ui.navigation.result.AppNavResult
import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
import ldv.shuuen.ui.navigation.result.LocalNavResultStore
import ldv.shuuen.ui.navigation.result.ContextRecipient
import ldv.shuuen.ui.navigation.result.NavResultKeys.MelodiesContextResult
import ldv.shuuen.ui.navigation.result.NavResultKeys.SinglesContextResult
import ldv.shuuen.ui.navigation.result.resultKey
import ldv.shuuen.ui.screens.app_settings.SettingsScreen
import ldv.shuuen.ui.screens.app_settings.SettingsViewModel
import ldv.shuuen.ui.screens.context.ContextScreen
import ldv.shuuen.ui.screens.free_play.FreePlayScreen
import ldv.shuuen.ui.screens.free_play.FreePlayViewModel
import ldv.shuuen.ui.screens.level_end.LevelCompleteScreen
import ldv.shuuen.ui.screens.main.MainMenuScreen
import ldv.shuuen.ui.screens.training.common.TrainingFlow
import ldv.shuuen.ui.screens.training.melodies.play.MelodiesPlayScreen
import ldv.shuuen.ui.screens.training.melodies.setup.MelodiesSetupScreen
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
val navigationModule = module {
  navigation<AppRoute.MainMenu> {
    val navigator = LocalAppNavigator.current
    MainMenuScreen(
        onOpenFreePlay = { navigator.add(AppRoute.FreePlay) },
        onOpenMelodies = { navigator.add(AppRoute.MelodiesLevelSelect) },
        onOpenSingles = { navigator.add(AppRoute.SinglesLevelSelect) },
        onOpenSettings = { navigator.add(AppRoute.Settings) },
    )
  }

  viewModel<FreePlayViewModel>()
  navigation<AppRoute.FreePlay> {
    val navigator = LocalAppNavigator.current
    FreePlayScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navigator.goBack() },
    )
  }

  viewModel<SettingsViewModel>()
  navigation<AppRoute.Settings> {
    val navigator = LocalAppNavigator.current
    SettingsScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navigator.goBack() },
    )
  }

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

  navigation<AppRoute.MelodiesLevelSelect> {
    val navigator = LocalAppNavigator.current
    // for now
    SinglesLevelSelectScreen(
        onNavigateBack = { navigator.goBack() },
        onStartLevel = { navigator.add(AppRoute.MelodiesPlay) },
        onCreateNewLevel = { navigator.add(AppRoute.MelodiesSetup) },
        viewModel = koinViewModel(),
    )
    //    LevelSelectScreen(
    //      flow = TrainingFlow.Melodies,
    //      onNavigateBack = { navigator.navigateBack() },
    //      onStartLevel = { navigator.navigateTo(AppRoute.MelodiesSetup) },
    //    )
  }

  navigation<AppRoute.MelodiesLevelComplete> {
    val navigator = LocalAppNavigator.current
    LevelCompleteScreen(
        flow = TrainingFlow.Melodies,
        onNavigateBack = { navigator.goBack() },
        onRetryLevel = { navigator.add(AppRoute.MelodiesSetup) },
        onNextLevel = { navigator.add(AppRoute.MelodiesLevelSelect) },
    )
  }

  viewModel<SinglesLevelSelectScreenViewModel>()
  navigation<AppRoute.SinglesLevelSelect> {
    val navigator = LocalAppNavigator.current
    SinglesLevelSelectScreen(
        onNavigateBack = { navigator.goBack() },
        onStartLevel = { levelId -> navigator.add(AppRoute.SinglesPlay(levelId)) },
        onCreateNewLevel = { navigator.add(AppRoute.SinglesSetup) },
        viewModel = koinViewModel(),
    )
  }

  viewModel<SinglesSetupScreenViewModel>()
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

  viewModel<SinglesPlayScreenViewModel>()
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

  navigation<AppRoute.MelodiesSetup> {
    val navigator = LocalAppNavigator.current
    val resultStore = LocalNavResultStore.current
    val result = resultStore.peek(MelodiesContextResult)
    LaunchedEffect(result) {
      result?.let {
        resultStore.clear(MelodiesContextResult)
        // viewModel.updateContext(it.context)
        // update the context in the future viewmodel
      }
    }
    MelodiesSetupScreen(
        onNavigateBack = { navigator.goBack() },
        onOpenContext = { navigator.add(AppRoute.Context(ContextRecipient.MelodiesSetup)) },
        onStartTraining = { navigator.add(AppRoute.MelodiesPlay) },
    )
  }

  navigation<AppRoute.MelodiesPlay> {
    val navigator = LocalAppNavigator.current
    MelodiesPlayScreen(
        onNavigateBack = { navigator.goBack() },
        onLevelEnd = {
          navigator.add(AppRoute.MelodiesLevelComplete)
        },
    )
  }
}
