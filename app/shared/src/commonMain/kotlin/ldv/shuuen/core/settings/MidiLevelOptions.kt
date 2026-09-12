package ldv.shuuen.core.settings

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.MidiTransposition

/**
 * How imported MIDI levels are played. Chosen from the level-select options sheet and kept as a
 * setting, so "next level" and "retry" reuse it without asking again; a Random transposition is
 * still re-rolled every time a level starts.
 */
@Serializable
data class MidiLevelOptions(
  val transposition: MidiTransposition = MidiTransposition(),
  /**
   * Harmonic context framing the melody, played from the file's labelled tonic. Ignored for
   * melodies without a key, because there is no tonic to build the chords on.
   */
  val context: DegreeContext? = null,
)
