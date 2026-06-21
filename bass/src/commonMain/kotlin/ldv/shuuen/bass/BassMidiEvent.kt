package ldv.shuuen.bass

/**
 * A single MIDI event retrieved from a stream via [Bass.streamGetEvents]. Mirrors BASS_MIDI_EVENT.
 *
 * For a note event ([BassConstants.MIDI_EVENT_NOTE]/[BassConstants.MIDI_EVENT_NOTES]), [param]'s low
 * byte is the note number and the high byte is the velocity.
 */
data class BassMidiEvent(
  val event: Int,
  val param: Int,
  val channel: Int,
  val tick: Long,
  val pos: Long,
)
