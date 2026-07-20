package ldv.shuuen.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.map
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.app.di.commonModule
import ldv.shuuen.app.di.platformModule
import ldv.shuuen.app.navigation.NavigationRoot
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.ThemeAppearance
import ldv.shuuen.core.settings.ThemeSettings
import ldv.shuuen.core.ui.theme.ShuuenTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
  KoinApplication(configuration = koinConfiguration {
    modules(listOf(commonModule, platformModule))
  }) {
    val settingsRepository = koinInject<SettingsRepository>()
    val theme by remember(settingsRepository) { settingsRepository.settings.map { it.theme } }
      .collectAsStateWithLifecycle(ThemeSettings())
    val darkTheme = when (theme.appearance) {
      ThemeAppearance.System -> isSystemInDarkTheme()
      ThemeAppearance.Dark -> true
      ThemeAppearance.Light -> false
    }
    ShuuenTheme(
      modifier = Modifier.fillMaxSize(),
      style = theme.style,
      darkTheme = darkTheme,
    ) {
      NavigationRoot()
    }
  }
}
