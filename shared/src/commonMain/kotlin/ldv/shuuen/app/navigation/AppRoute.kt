package ldv.shuuen.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import ldv.shuuen.app.navigation.result.ContextRecipient

@Serializable
sealed interface AppRoute : NavKey {
  @Serializable data object MainMenu : AppRoute

  @Serializable data object FreePlay : AppRoute

  @Serializable data object Settings : AppRoute

  @Serializable data class Context(val recipient: ContextRecipient) : AppRoute

  @Serializable data object SinglesLevelSelect : AppRoute

  @Serializable data object MelodiesLevelSelect : AppRoute

  @Serializable data object ChordsLevelSelect : AppRoute

  @Serializable data class SinglesLevelComplete(val levelId: String, val sessionId: String) : AppRoute

  @Serializable data class MelodiesLevelComplete(val levelId: String, val sessionId: String) : AppRoute

  @Serializable data class ChordsLevelComplete(val levelId: String, val sessionId: String) : AppRoute

  @Serializable data object SinglesSetup : AppRoute

  @Serializable data class SinglesPlay(val levelId: String) : AppRoute

  @Serializable data object MelodiesSetup : AppRoute

  @Serializable data class MelodiesPlay(val levelId: String) : AppRoute

  @Serializable data object ChordsSetup : AppRoute

  @Serializable data class ChordsPlay(val levelId: String) : AppRoute
}
