package ldv.shuuen.core.audio.input

/**
 * Incremental parser for a raw MIDI byte stream. Use one instance per device (or port): running
 * status persists across [feed] calls, so interleaving streams from different sources through one
 * parser would corrupt it.
 *
 * Only note messages become [MidiKeyboardEvent]s; everything else (controllers, pitch bend,
 * SysEx, real-time ticks) is consumed and dropped. A note-on with velocity 0 is a note-off, per
 * the MIDI spec.
 */
class MidiMessageParser {
  private var status = 0
  private var firstDataByte = -1
  private var inSysEx = false

  fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset): List<MidiKeyboardEvent> {
    var events: MutableList<MidiKeyboardEvent>? = null
    for (i in offset until offset + count) {
      val byte = bytes[i].toInt() and 0xFF
      when {
        // System real-time bytes may appear between any two bytes and never disturb parser state.
        byte >= 0xF8 -> Unit

        byte == 0xF7 -> inSysEx = false

        // Other system common messages (incl. SysEx start) cancel running status.
        byte >= 0xF0 -> {
          inSysEx = byte == 0xF0
          status = 0
          firstDataByte = -1
        }

        byte >= 0x80 -> {
          status = byte
          firstDataByte = -1
          inSysEx = false
        }

        // Data bytes inside SysEx, or with no status to attach to (truncated stream): skip.
        inSysEx || status == 0 -> Unit

        // Program change and channel pressure carry one data byte; all other channel messages two.
        (status and 0xE0) == 0xC0 -> Unit

        firstDataByte < 0 -> firstDataByte = byte

        else -> {
          noteEvent(firstDataByte, byte)?.let {
            (events ?: mutableListOf<MidiKeyboardEvent>().also { list -> events = list }).add(it)
          }
          // Running status: the next data pair reuses the current status byte.
          firstDataByte = -1
        }
      }
    }
    return events ?: emptyList()
  }

  private fun noteEvent(note: Int, velocity: Int): MidiKeyboardEvent? =
    when (status and 0xF0) {
      0x90 ->
        if (velocity > 0) MidiKeyboardEvent.NoteOn(note, velocity)
        else MidiKeyboardEvent.NoteOff(note)

      0x80 -> MidiKeyboardEvent.NoteOff(note)

      else -> null
    }
}
