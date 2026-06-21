package ldv.shuuen.core.audio.midi

/** MIDI channel volume (0..127). Drone sits quieter as a background voice. */
enum class DefaultVolume(val value: Int) {
  Notes(127),
  Drone(55),
  Cadence(127),
}
