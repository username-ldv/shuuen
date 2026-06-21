package ldv.shuuen.domain.training.melodies

import ldv.shuuen.domain.audio.music.DegreeContext
import ldv.shuuen.domain.training.level.LevelSource

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
