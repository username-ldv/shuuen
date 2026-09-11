package ldv.shuuen.features.free_play

import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale

enum class FreePlayDisplayMode {
  Degrees,
  Notes,
}

data class FreePlayState(
  val tonic: Pitch = Pitch.C,
  val enabledKeyboardKeys: List<Boolean> = List(12) { true },
  val activeKeyboardKeys: Set<Int> = emptySet(),
  val activeFifthsItems: Set<Int> = emptySet(),
  val displayMode: FreePlayDisplayMode = FreePlayDisplayMode.Degrees,
  val audioReady: Boolean = false,
  val initializingAudio: Boolean = true,
  val errorMessage: String? = null,
) {
  companion object {
    fun initial(tonic: Pitch = Pitch.C): FreePlayState {
      val enabledPitches = Scale.naturalMinor(tonic).pitches.map { it.ordinal }.toSet()
      return FreePlayState(
        tonic = tonic,
        enabledKeyboardKeys = List(12) { it in enabledPitches },
      )
    }
  }
}

sealed interface FreePlayAction {
  data class PressPitch(val pitchIndex: Int) : FreePlayAction
  data class ReleasePitch(val pitchIndex: Int) : FreePlayAction
  data class ToggleDrone(val fifthsIndex: Int) : FreePlayAction
  data object StopAll : FreePlayAction
  data object DismissError : FreePlayAction
}
