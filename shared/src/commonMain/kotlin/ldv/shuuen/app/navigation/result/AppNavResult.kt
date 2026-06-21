package ldv.shuuen.app.navigation.result

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.DegreeContext

@Serializable
sealed interface AppNavResult {
  @Serializable
  data class ContextPickedResult(val context: DegreeContext) : AppNavResult
}

object NavResultKeys {
  val SinglesContextResult = NavResultKey(
    "singles-context",
    AppNavResult.ContextPickedResult.serializer(),
  )
  val MelodiesContextResult = NavResultKey(
    id = "melodies-context",
    serializer = AppNavResult.ContextPickedResult.serializer(),
  )
}