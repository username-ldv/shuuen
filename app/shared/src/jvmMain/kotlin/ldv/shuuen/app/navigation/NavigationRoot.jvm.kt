package ldv.shuuen.app.navigation

import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import ldv.shuuen.app.navigation.Transitions

actual val transitions: Transitions = Transitions(
  defaultTransitionSpec(), defaultTransitionSpec(), defaultPredictivePopTransitionSpec()
)
