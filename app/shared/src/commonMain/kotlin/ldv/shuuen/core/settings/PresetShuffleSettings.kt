package ldv.shuuen.core.settings

import kotlinx.serialization.Serializable
import ldv.shuuen.core.audio.midi.MidiChannel

/**
 * When a channel re-rolls its preset, for channels with more than one preset chosen. Only levels
 * shuffle; previews and free play always sound the channel's base preset.
 */
enum class PresetShuffleMode {
  /** One roll when the level starts, held until it ends. */
  PerLevel,

  /** A different preset on every question. */
  PerQuestion,

  /**
   * A different preset on every melody note. Only the notes of a melody level are played one by
   * one, so everywhere else (singles, chords, and the drone/cadence chords) this behaves like
   * [PerQuestion].
   */
  PerNote,
}

@Serializable
data class PresetShuffleSettings(
  val notes: PresetShuffleMode = PresetShuffleMode.PerLevel,
  val drone: PresetShuffleMode = PresetShuffleMode.PerLevel,
  val cadence: PresetShuffleMode = PresetShuffleMode.PerLevel,
  /**
   * Whether [PresetShuffleMode.PerNote] also applies to imported MIDI melodies. Those play through
   * BASS rather than note by note, so the change is driven by the position poll and can land up to
   * one poll interval late; off keeps per-note changes to generated melodies and makes an imported
   * one behave like [PresetShuffleMode.PerQuestion].
   */
  val perNoteOnImportedMelodies: Boolean = true,
) {
  fun forChannel(channel: MidiChannel): PresetShuffleMode =
    when (channel) {
      MidiChannel.Notes -> notes
      MidiChannel.Drone -> drone
      MidiChannel.Cadence -> cadence
    }

  fun withMode(channel: MidiChannel, mode: PresetShuffleMode): PresetShuffleSettings =
    when (channel) {
      MidiChannel.Notes -> copy(notes = mode)
      MidiChannel.Drone -> copy(drone = mode)
      MidiChannel.Cadence -> copy(cadence = mode)
    }
}
