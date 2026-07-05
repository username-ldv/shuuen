package ldv.shuuen.core.audio.input

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A note event from a connected hardware MIDI keyboard.
 *
 * [midiIndex] is the raw MIDI note number (0..127); it can lie outside the app's 88-key
 * [ldv.shuuen.core.music.Note] range, so consumers reduce it themselves (pitch class via
 * `midiIndex mod 12`, or exact-note comparison against a Note's midiIndex).
 */
sealed interface MidiKeyboardEvent {
  val midiIndex: Int

  data class NoteOn(override val midiIndex: Int, val velocity: Int) : MidiKeyboardEvent

  data class NoteOff(override val midiIndex: Int) : MidiKeyboardEvent
}

/**
 * Hardware MIDI keyboard input: watches for devices being plugged in or removed and streams
 * their note events. Implementations start watching on creation and merge all connected devices
 * into the one [events] flow.
 */
interface MidiKeyboardInput {
  /** Names of the currently connected (and successfully opened) MIDI input devices. */
  val connectedDevices: StateFlow<List<String>>

  /** Note on/off events from every connected device. */
  val events: SharedFlow<MidiKeyboardEvent>
}
