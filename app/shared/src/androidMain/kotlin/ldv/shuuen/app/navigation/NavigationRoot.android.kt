package ldv.shuuen.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import ldv.shuuen.app.navigation.Transitions

actual val transitions: Transitions = Transitions(
  {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith slideOutOfContainer(
      AnimatedContentTransitionScope.SlideDirection.Left
    )
  },
  {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) togetherWith slideOutOfContainer(
      AnimatedContentTransitionScope.SlideDirection.Right
    )
  },
  {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) togetherWith slideOutOfContainer(
      AnimatedContentTransitionScope.SlideDirection.Right
    )
  })
