package ldv.shuuen.features.free_play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch

class FreePlayViewModel(
  private val midiEngine: MidiEngine,
  initialTonic: Pitch = Pitch.random(),
) : ViewModel() {
  private val mutableState = MutableStateFlow(FreePlayState.initial(initialTonic))
  val state: StateFlow<FreePlayState> = mutableState

  private val droneOctave = 2

  init {
    viewModelScope.launch {
      when (val status = midiEngine.initialize()) {
        MidiEngineStatus.Ready -> {
          mutableState.update {
            it.copy(audioReady = true, initializingAudio = false, errorMessage = null)
          }
        }

        is MidiEngineStatus.Failed -> {
          mutableState.update {
            it.copy(audioReady = false, initializingAudio = false, errorMessage = status.message)
          }
        }
      }
    }
  }

  fun onAction(action: FreePlayAction) {
    when (action) {
      FreePlayAction.DismissError -> mutableState.update { it.copy(errorMessage = null) }
      is FreePlayAction.PressPitch -> pressPitch(action.pitchIndex)
      is FreePlayAction.ReleasePitch -> releasePitch(action.pitchIndex)
      FreePlayAction.StopAll -> stopAll()
      is FreePlayAction.ToggleDrone -> toggleDrone(action.fifthsIndex)
    }
  }

  private fun pressPitch(pitchIndex: Int) {
    val current = mutableState.value
    if (!current.audioReady || pitchIndex !in current.enabledKeyboardKeys.indices) return
    if (!current.enabledKeyboardKeys[pitchIndex]) return

    midiEngine.playNote(Note(Pitch.fromOrdinal(pitchIndex)), MidiChannel.Notes)
    mutableState.update { it.copy(activeKeyboardKeys = it.activeKeyboardKeys + pitchIndex) }
  }

  private fun releasePitch(pitchIndex: Int) {
    val current = mutableState.value
    if (pitchIndex !in current.enabledKeyboardKeys.indices) return

    midiEngine.stopNote(Note(Pitch.fromOrdinal(pitchIndex)), MidiChannel.Notes)
    mutableState.update { it.copy(activeKeyboardKeys = it.activeKeyboardKeys - pitchIndex) }
  }

  private fun toggleDrone(fifthsIndex: Int) {
    val current = mutableState.value
    if (!current.audioReady || fifthsIndex !in 0..11) return

    val pitch = current.tonic + fifthsIndex
    val note = Note(pitch, droneOctave)
    if (fifthsIndex in current.activeFifthsItems) {
      midiEngine.stopNote(note, MidiChannel.Drone)
      mutableState.update { it.copy(activeFifthsItems = it.activeFifthsItems - fifthsIndex) }
    } else {
      midiEngine.playNote(note, MidiChannel.Drone)
      mutableState.update { it.copy(activeFifthsItems = it.activeFifthsItems + fifthsIndex) }
    }
  }

  private fun stopAll() {
    midiEngine.stopAll()
    mutableState.update { it.copy(activeKeyboardKeys = emptySet(), activeFifthsItems = emptySet()) }
  }

  override fun onCleared() {
    stopAll()
  }
}
