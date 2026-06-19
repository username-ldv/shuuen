package ldv.shuuen.ui.screens.training.melodies

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.ui.navigation.AppRoute
import ldv.shuuen.ui.navigation.LocalAppNavigator
import ldv.shuuen.ui.navigation.result.ContextRecipient
import ldv.shuuen.ui.navigation.result.LocalNavResultStore
import ldv.shuuen.ui.navigation.result.NavResultKeys.MelodiesContextResult
import ldv.shuuen.ui.screens.level_end.LevelCompleteScreen
import ldv.shuuen.ui.screens.training.common.TrainingFlow
import ldv.shuuen.ui.screens.training.melodies.play.MelodiesPlayScreen
import ldv.shuuen.ui.screens.training.melodies.setup.MelodiesSetupScreen
import ldv.shuuen.ui.screens.training.single.level_select.SinglesLevelSelectScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val melodiesTrainingNavigationModule = module {
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
