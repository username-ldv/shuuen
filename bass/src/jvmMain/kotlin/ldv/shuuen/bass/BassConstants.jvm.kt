package ldv.shuuen.bass

internal actual object BassConstants {
  actual val BASS_OK: Int = 0
  actual val BASS_ERROR_INIT: Int = 8
  actual val BASS_CONFIG_BUFFER: Int = 0
  actual val BASS_CONFIG_UPDATEPERIOD: Int = 1
  actual val BASS_CONFIG_DEV_BUFFER: Int = 27
  actual val BASS_CONFIG_DEV_PERIOD: Int = 53
  actual val BASS_ATTRIB_BUFFER: Int = 13
  actual val BASS_ATTRIB_VOL: Int = 2
  actual val BASS_STREAM_DECODE: Int = 0x200000
  actual val BASS_MIDI_DECAYEND: Int = 0x1000
  actual val BASS_CONFIG_MIDI_VOICES: Int = 0x10401
  actual val MIDI_EVENT_NOTE: Int = 1
  actual val MIDI_EVENT_PROGRAM: Int = 2
  actual val MIDI_EVENT_BANK: Int = 10
  actual val MIDI_EVENT_BANK_LSB: Int = 70
  actual val MIDI_EVENT_VOLUME: Int = 12
  actual val MIDI_EVENT_EXPRESSION: Int = 14
  actual val MIDI_EVENT_FINETUNE: Int = 7
  actual val MIDI_EVENT_PITCH: Int = 4
  actual val MIDI_EVENT_PITCHRANGE: Int = 5
  actual val MIDI_EVENT_REVERB_LEVEL: Int = 36
  actual val MIDI_EVENT_CHORUS_LEVEL: Int = 41
  actual val MIDI_EVENT_CHORUS_MACRO: Int = 31
  actual val MIDI_EVENT_SYSTEM: Int = 61
  actual val MIDI_EVENT_SYSTEMEX: Int = 0x10002
  actual val MIDI_EVENT_NOTESOFF: Int = 18
  actual val MIDI_EVENT_NOTES: Int = 0x20000
  actual val BASS_FILE_MEM: Int = 1
  actual val BASS_POS_BYTE: Int = 0
  actual val BASS_POS_MIDI_TICK: Int = 2
  actual val BASS_POS_FLUSH: Int = 0x1000000
  actual val BASS_ACTIVE_STOPPED: Int = 0
  actual val BASS_ACTIVE_PLAYING: Int = 1
  actual val BASS_ACTIVE_PAUSED: Int = 3
}
