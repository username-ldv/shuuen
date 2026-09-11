package ldv.shuuen.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.serializer
import ldv.shuuen.app.navigation.result.LocalNavResultStore
import ldv.shuuen.app.navigation.result.rememberNavResultStore
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

data class Transitions(
    val transitionSpec: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform,
    val popTransitionSpec: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform,
    val predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<AppRoute>>.(Int) -> ContentTransform,
)

expect val transitions: Transitions

@OptIn(KoinExperimentalAPI::class)
@Composable
fun NavigationRoot() {
  val backStack = rememberAppNavBackStack(AppRoute.MainMenu)
  val navigator = remember(backStack) { AppNavigator(backStack) }
  val entryProvider = koinEntryProvider<AppRoute>()
  val navResultStore = rememberNavResultStore()


  CompositionLocalProvider(
      LocalAppNavigator provides navigator,
      LocalNavResultStore provides navResultStore,
  ) {
    NavDisplay(
        entryProvider = entryProvider,
        backStack = backStack,
        onBack = { navigator.goBack() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        transitionSpec = transitions.transitionSpec,
        popTransitionSpec = transitions.popTransitionSpec,
        predictivePopTransitionSpec = transitions.predictivePopTransitionSpec,
    )
  }
}

@Composable
private fun rememberAppNavBackStack(
    vararg elements: AppRoute,
): NavBackStack<AppRoute> =
    rememberSerializable(serializer = serializer()) {
      NavBackStack(*elements)
    }
