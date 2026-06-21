package ldv.shuuen.core.audio.engine

sealed interface MidiEngineStatus {
  data object Ready : MidiEngineStatus
  data class Failed(val message: String) : MidiEngineStatus
}