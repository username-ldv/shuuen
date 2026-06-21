package ldv.shuuen.app.navigation.result

import kotlinx.serialization.Serializable
import ldv.shuuen.app.navigation.result.NavResultKeys.MelodiesContextResult
import ldv.shuuen.app.navigation.result.NavResultKeys.SinglesContextResult

@Serializable
enum class ContextRecipient {
  SinglesSetup,
  MelodiesSetup,
}

fun ContextRecipient.resultKey() =
    when (this) {
      ContextRecipient.SinglesSetup -> SinglesContextResult
      ContextRecipient.MelodiesSetup -> MelodiesContextResult
    }
