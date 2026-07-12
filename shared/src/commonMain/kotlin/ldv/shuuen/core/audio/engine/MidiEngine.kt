package ldv.shuuen.core.audio.engine

import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.Note

interface MidiEngine {
  suspend fun initialize(): MidiEngineStatus

  /**
   * [detuneCents] plays the note out of tune by that many cents (±100 max). The offset applies to
   * the whole channel until another note changes it, so it is passed per note-on rather than set
   * separately — a caller that plays without it re-centers the channel.
   */
  fun playNote(
    note: Note,
    channel: MidiChannel = MidiChannel.Notes,
    velocity: Int = 127,
    detuneCents: Int = 0,
  ): Boolean

  fun stopNote(
    note: Note,
    channel: MidiChannel = MidiChannel.Notes,
  ): Boolean

  fun playChord(
    chord: Chord,
    channel: MidiChannel = MidiChannel.Notes,
    velocity: Int = 127,
  ): Boolean

  fun stopChord(
    chord: Chord,
    channel: MidiChannel = MidiChannel.Notes,
  ): Boolean

  fun stopAll(channel: MidiChannel? = null): Boolean

  /** Sets how far the channel's pitch wheel reaches at full deflection (MIDI RPN 0). */
  fun setPitchBendRange(channel: MidiChannel, semitones: Int): Boolean

  /**
   * Moves the channel's pitch wheel to [semitones] away from center, bending any sounding notes
   * in real time. Clamped to the range configured by [setPitchBendRange] (±2 by MIDI default).
   */
  fun setPitchBend(channel: MidiChannel, semitones: Double): Boolean

  fun setPreset(channel: MidiChannel, preset: Preset): Boolean

  fun setVolume(channel: MidiChannel, value: Int): Boolean

  fun availablePresets(): List<Preset>

  fun close()
}
