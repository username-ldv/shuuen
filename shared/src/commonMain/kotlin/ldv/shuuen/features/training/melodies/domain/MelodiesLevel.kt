package ldv.shuuen.features.training.melodies.domain

import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.features.training.domain.LevelSource

/**
 * A melody training session built from a loaded MIDI file. The raw [midiBytes] are handed to the
 * player, which loads them into BASS to play at natural tempo and to read out the note sequence.
 *
 * Random/endless generated melodies are a later task; for now a level always wraps a MIDI file.
 */
class MelodiesLevel(
  val id: String,
  val name: String,
  val midiBytes: ByteArray,
  val useOriginalVelocities: Boolean,
  val context: DegreeContext?,
  val source: LevelSource,
)
