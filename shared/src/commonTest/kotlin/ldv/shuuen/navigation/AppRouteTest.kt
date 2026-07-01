package ldv.shuuen.navigation

import ldv.shuuen.app.navigation.AppRoute
import ldv.shuuen.app.navigation.result.ContextRecipient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppRouteTest {
  @Test
  fun includesMelodiesTrainingRoutes() {
    val routes: List<AppRoute> = listOf(
      AppRoute.MelodiesSetup,
      AppRoute.MelodiesPlay("test-id"),
      AppRoute.Context(ContextRecipient.MelodiesSetup),
      AppRoute.SinglesLevelSelect,
      AppRoute.MelodiesLevelSelect,
      AppRoute.SinglesLevelComplete("test-id"),
      AppRoute.MelodiesLevelComplete,
    )

    assertEquals(AppRoute.MelodiesSetup, routes.first())
    assertEquals(AppRoute.MelodiesLevelComplete, routes.last())
    assertTrue(routes.toSet().size == routes.size)
  }
}
