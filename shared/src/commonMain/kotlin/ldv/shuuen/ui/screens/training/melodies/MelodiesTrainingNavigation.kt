package ldv.shuuen.ui.screens.training.melodies

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.domain.training.melodies.MelodiesSession
import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
import ldv.shuuen.ui.navigation.result.ContextRecipient
import ldv.shuuen.ui.navigation.result.LocalNavResultStore
import ldv.shuuen.ui.navigation.result.NavResultKeys.MelodiesContextResult
import ldv.shuuen.ui.screens.level_end.LevelCompleteScreen
import ldv.shuuen.ui.screens.training.common.TrainingFlow
import ldv.shuuen.ui.screens.training.melodies.level_select.MelodiesLevelSelectScreen
import ldv.shuuen.ui.screens.training.melodies.play.MelodiesPlayScreen
import ldv.shuuen.ui.screens.training.melodies.play.MelodiesPlayScreenViewModel
import ldv.shuuen.ui.screens.training.melodies.setup.MelodiesSetupScreen
import ldv.shuuen.ui.screens.training.melodies.setup.MelodiesSetupScreenViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val melodiesTrainingNavigationModule = module {
  single { MelodiesSession() }
  viewModel<MelodiesSetupScreenViewModel>()
  viewModel<MelodiesPlayScreenViewModel>()

  navigation<AppRoute.MelodiesLevelSelect> {
    val navigator = LocalAppNavigator.current
    MelodiesLevelSelectScreen(
        onNavigateBack = { navigator.goBack() },
        onCreateNewLevel = { navigator.add(AppRoute.MelodiesSetup) },
    )
  }

  navigation<AppRoute.MelodiesSetup> {
    val navigator = LocalAppNavigator.current
    val viewModel = koinViewModel<MelodiesSetupScreenViewModel>()
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
        onOpenContext = { navigator.add(AppRoute.Context(ContextRecipient.MelodiesSetup)) },
        onStartTraining = { navigator.add(AppRoute.MelodiesPlay) },
        viewModel = viewModel,
    )
  }

  navigation<AppRoute.MelodiesPlay> {
    val navigator = LocalAppNavigator.current
    MelodiesPlayScreen(
        onNavigateBack = { navigator.goBack() },
        onLevelEnd = { navigator.replaceLastWith(AppRoute.MelodiesLevelComplete) },
        viewModel = koinViewModel(),
    )
  }

  navigation<AppRoute.MelodiesLevelComplete> {
    val navigator = LocalAppNavigator.current
    LevelCompleteScreen(
        flow = TrainingFlow.Melodies,
        onNavigateBack = { navigator.goBack() },
        onRetryLevel = { navigator.replaceLastWith(AppRoute.MelodiesSetup) },
        onNextLevel = { navigator.add(AppRoute.MelodiesLevelSelect) },
    )
  }
}
