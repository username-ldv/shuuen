package ldv.shuuen.features.pitch_slide

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.LocalAppNavigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val pitchSlideNavigationModule = module {
  viewModel<PitchSlideViewModel>()

  navigation<AppRoute.PitchSlide> {
    val navigator = LocalAppNavigator.current
    PitchSlideScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navigator.goBack() },
    )
  }
}
