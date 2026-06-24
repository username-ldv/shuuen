package ldv.shuuen.features.training.melodies

import androidx.compose.runtime.LaunchedEffect
import ldv.shuuen.features.training.melodies.domain.MelodiesSession
import ldv.shuuen.features.training.level_end.LevelCompleteScreen
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.melodies.level_select.MelodiesLevelSelectScreen
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
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val melodiesTrainingNavigationModule = module {
  single<MelodiesSession>()
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
