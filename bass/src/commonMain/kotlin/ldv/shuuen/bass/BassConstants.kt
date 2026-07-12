package ldv.shuuen.bass

internal expect object BassConstants {
  val BASS_OK: Int
  val BASS_ERROR_INIT: Int
  val BASS_CONFIG_BUFFER: Int
  val BASS_CONFIG_UPDATEPERIOD: Int
  val BASS_CONFIG_DEV_BUFFER: Int
  val BASS_CONFIG_DEV_PERIOD: Int
  val BASS_ATTRIB_BUFFER: Int
  val BASS_ATTRIB_VOL: Int
  val BASS_STREAM_DECODE: Int
  val BASS_MIDI_DECAYEND: Int
  val BASS_CONFIG_MIDI_VOICES: Int
  val MIDI_EVENT_NOTE: Int
  val MIDI_EVENT_PROGRAM: Int
  val MIDI_EVENT_BANK: Int
  val MIDI_EVENT_BANK_LSB: Int
  val MIDI_EVENT_VOLUME: Int
  val MIDI_EVENT_EXPRESSION: Int
  val MIDI_EVENT_FINETUNE: Int
  val MIDI_EVENT_PITCH: Int
  val MIDI_EVENT_PITCHRANGE: Int
  val MIDI_EVENT_REVERB_LEVEL: Int
  val MIDI_EVENT_CHORUS_LEVEL: Int
  val MIDI_EVENT_CHORUS_MACRO: Int
  val MIDI_EVENT_SYSTEM: Int
  val MIDI_EVENT_SYSTEMEX: Int
  val MIDI_EVENT_NOTESOFF: Int

  /** Special filter for BASS_MIDI_StreamGetEvents: only note-on events (note with velocity > 0). */
  val MIDI_EVENT_NOTES: Int

  /** filetype for BASS_MIDI_StreamCreateFile: file points to a memory block. */
  val BASS_FILE_MEM: Int

  // BASS_ChannelGetLength/GetPosition/SetPosition modes.
  val BASS_POS_BYTE: Int
  val BASS_POS_MIDI_TICK: Int
  val BASS_POS_FLUSH: Int

  // BASS_ChannelIsActive return values.
  val BASS_ACTIVE_STOPPED: Int
  val BASS_ACTIVE_PLAYING: Int
  val BASS_ACTIVE_PAUSED: Int
}
