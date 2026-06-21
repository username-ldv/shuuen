package ldv.shuuen.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import io.github.aakira.napier.Napier

class AppNavigator(
  private val backStack: NavBackStack<AppRoute>,
) {

  fun add(destination: AppRoute) {
    Napier.v { "Navigate happened" }
    backStack.add(destination)
  }

  fun goBack() {
    Napier.v { "Navigate back triggered $backStack" }
    if (backStack.size > 1) backStack.removeLastOrNull()
    Napier.v { "After navigate back triggered $backStack" }
  }

  fun replaceLastWith(destination: AppRoute) {
    backStack[backStack.size - 1] = destination
  }
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> {
  error("AppNavigator is not available outside NavigationRoot")
}
