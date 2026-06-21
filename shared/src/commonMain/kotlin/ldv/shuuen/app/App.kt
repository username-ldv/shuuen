package ldv.shuuen.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ldv.shuuen.app.di.commonModule
import ldv.shuuen.app.di.platformModule
import ldv.shuuen.app.navigation.NavigationRoot
import ldv.shuuen.core.ui.theme.ShuuenTheme
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
  KoinApplication(configuration = koinConfiguration {
    modules(listOf(commonModule, platformModule))
  }) {
    ldv.shuuen.core.ui.theme.ShuuenTheme(modifier = Modifier.fillMaxSize()) {
      NavigationRoot()
    }
  }
}
