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
      AppRoute.MelodiesSetup(),
      AppRoute.MelodiesSetup("edit-id"),
      AppRoute.MelodiesPlay("test-id"),
      AppRoute.Context(ContextRecipient.MelodiesSetup),
      AppRoute.Context(ContextRecipient.MelodiesSetup, "context-id"),
      AppRoute.SinglesLevelSelect,
      AppRoute.MelodiesLevelSelect,
      AppRoute.SinglesLevelComplete("test-id", "session-id"),
      AppRoute.MelodiesLevelComplete("test-id", "session-id"),
    )

    assertEquals(AppRoute.MelodiesSetup(), routes.first())
    assertEquals(AppRoute.MelodiesLevelComplete("test-id", "session-id"), routes.last())
    assertTrue(routes.toSet().size == routes.size)
  }
}
