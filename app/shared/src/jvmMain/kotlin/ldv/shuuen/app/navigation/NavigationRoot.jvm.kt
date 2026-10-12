package ldv.shuuen.app.navigation

import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

actual val transitions: Transitions =
  Transitions(
    defaultTransitionSpec(),
    defaultTransitionSpec(),
    defaultPredictivePopTransitionSpec(),
  )
