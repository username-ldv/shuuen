package ldv.shuuen.core.navigation.result

import kotlinx.serialization.Serializable
import ldv.shuuen.core.navigation.result.NavResultKeys.ChordsContextResult
import ldv.shuuen.core.navigation.result.NavResultKeys.MelodiesContextResult
import ldv.shuuen.core.navigation.result.NavResultKeys.MelodiesLevelOptionsContextResult
import ldv.shuuen.core.navigation.result.NavResultKeys.SinglesContextResult

@Serializable
enum class ContextRecipient {
  SinglesSetup,
  MelodiesSetup,
  /** The shared context for MIDI melody levels, picked from the level-select options sheet. */
  MelodiesLevelOptions,
  ChordsSetup,
}

fun ContextRecipient.resultKey() =
  when (this) {
    ContextRecipient.SinglesSetup -> SinglesContextResult
    ContextRecipient.MelodiesSetup -> MelodiesContextResult
    ContextRecipient.MelodiesLevelOptions -> MelodiesLevelOptionsContextResult
    ContextRecipient.ChordsSetup -> ChordsContextResult
  }
