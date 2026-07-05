package ldv.shuuen.bass

object Bass {
  val BASS_OK: Int
    get() = BassConstants.BASS_OK
  val BASS_ERROR_INIT: Int
    get() = BassConstants.BASS_ERROR_INIT
  val BASS_CONFIG_BUFFER: Int
    get() = BassConstants.BASS_CONFIG_BUFFER
  val BASS_CONFIG_UPDATEPERIOD: Int
    get() = BassConstants.BASS_CONFIG_UPDATEPERIOD
  val BASS_CONFIG_DEV_BUFFER: Int
    get() = BassConstants.BASS_CONFIG_DEV_BUFFER
  val BASS_CONFIG_DEV_PERIOD: Int
    get() = BassConstants.BASS_CONFIG_DEV_PERIOD
  val BASS_ATTRIB_BUFFER: Int
    get() = BassConstants.BASS_ATTRIB_BUFFER
  val BASS_ATTRIB_VOL: Int
    get() = BassConstants.BASS_ATTRIB_VOL
  val BASS_STREAM_DECODE: Int
    get() = BassConstants.BASS_STREAM_DECODE
  val BASS_MIDI_DECAYEND: Int
    get() = BassConstants.BASS_MIDI_DECAYEND
  val BASS_CONFIG_MIDI_VOICES: Int
    get() = BassConstants.BASS_CONFIG_MIDI_VOICES
  val MIDI_EVENT_NOTE: Int
    get() = BassConstants.MIDI_EVENT_NOTE
  val MIDI_EVENT_PROGRAM: Int
    get() = BassConstants.MIDI_EVENT_PROGRAM
  val MIDI_EVENT_BANK: Int
    get() = BassConstants.MIDI_EVENT_BANK
  val MIDI_EVENT_BANK_LSB: Int
    get() = BassConstants.MIDI_EVENT_BANK_LSB
  val MIDI_EVENT_VOLUME: Int
    get() = BassConstants.MIDI_EVENT_VOLUME
  val MIDI_EVENT_EXPRESSION: Int
    get() = BassConstants.MIDI_EVENT_EXPRESSION
  val MIDI_EVENT_REVERB_LEVEL: Int
    get() = BassConstants.MIDI_EVENT_REVERB_LEVEL
  val MIDI_EVENT_CHORUS_LEVEL: Int
    get() = BassConstants.MIDI_EVENT_CHORUS_LEVEL
  val MIDI_EVENT_CHORUS_MACRO: Int
    get() = BassConstants.MIDI_EVENT_CHORUS_MACRO
  val MIDI_EVENT_SYSTEM: Int
    get() = BassConstants.MIDI_EVENT_SYSTEM
  val MIDI_EVENT_SYSTEMEX: Int
    get() = BassConstants.MIDI_EVENT_SYSTEMEX
  val MIDI_EVENT_NOTESOFF: Int
    get() = BassConstants.MIDI_EVENT_NOTESOFF
  val MIDI_EVENT_NOTES: Int
    get() = BassConstants.MIDI_EVENT_NOTES
  val BASS_FILE_MEM: Int
    get() = BassConstants.BASS_FILE_MEM
  val BASS_POS_BYTE: Int
    get() = BassConstants.BASS_POS_BYTE
  val BASS_POS_MIDI_TICK: Int
    get() = BassConstants.BASS_POS_MIDI_TICK
  val BASS_POS_FLUSH: Int
    get() = BassConstants.BASS_POS_FLUSH
  val BASS_ACTIVE_STOPPED: Int
    get() = BassConstants.BASS_ACTIVE_STOPPED
  val BASS_ACTIVE_PLAYING: Int
    get() = BassConstants.BASS_ACTIVE_PLAYING
  val BASS_ACTIVE_PAUSED: Int
    get() = BassConstants.BASS_ACTIVE_PAUSED

  fun load() = BassPlatform.load()

  fun version(): Int = BassPlatform.version()

  fun midiVersion(): Int = BassPlatform.midiVersion()

  fun versionText(): String = version().toBassVersionText()

  fun midiVersionText(): String = midiVersion().toBassVersionText()

  fun init(device: Int = -1, frequency: Int = 44_100, flags: Int = 0): Boolean =
    BassPlatform.init(device, frequency, flags)

  fun setConfig(option: Int, value: Int): Boolean = BassPlatform.setConfig(option, value)

  fun free(): Boolean = BassPlatform.free()

  fun freePlugins(handle: Int = 0): Boolean = BassPlatform.freePlugins(handle)

  fun errorCode(): Int = BassPlatform.errorCode()

  fun createLiveMidiStream(
    channels: Int = 128,
    flags: Int = 0,
    frequency: Int = 44_100,
  ): Int = BassPlatform.createLiveMidiStream(channels, flags, frequency)

  fun createMidiStream(
    filePath: String,
    flags: Int = 0,
    frequency: Int = 44_100,
  ): Int = BassPlatform.createMidiStream(filePath, flags, frequency)

  fun createMidiStreamFromMemory(
    data: ByteArray,
    flags: Int = 0,
    frequency: Int = 44_100,
  ): Int = BassPlatform.createMidiStreamFromMemory(data, flags, frequency)

  /** Retrieves events from a MIDI stream. track = -1 for all tracks; filter e.g. [MIDI_EVENT_NOTES]. */
  fun streamGetEvents(
    streamHandle: Int,
    track: Int = -1,
    filter: Int = 0,
  ): List<BassMidiEvent> = BassPlatform.streamGetEvents(streamHandle, track, filter)

  fun setMidiStreamMelodyFilter(
    streamHandle: Int,
    enabled: Boolean,
    preset: Int = 0,
    bank: Int = 0,
    normalizeNoteVelocity: Boolean = true,
  ): Boolean =
    BassPlatform.setMidiStreamMelodyFilter(
      streamHandle,
      enabled,
      preset,
      bank,
      normalizeNoteVelocity,
    )

  fun loadSoundFont(filePath: String, flags: Int = 0): Int =
    BassPlatform.loadSoundFont(filePath, flags)

  fun setStreamSoundFont(
    streamHandle: Int,
    soundFontHandle: Int,
    preset: Int = -1,
    bank: Int = 0,
  ): Boolean = BassPlatform.setStreamSoundFont(streamHandle, soundFontHandle, preset, bank)

  fun play(channelHandle: Int, restart: Boolean = false): Boolean =
    BassPlatform.play(channelHandle, restart)

  fun start(channelHandle: Int): Boolean =
    BassPlatform.start(channelHandle)

  fun pause(channelHandle: Int): Boolean = BassPlatform.pause(channelHandle)

  fun stop(channelHandle: Int): Boolean = BassPlatform.stop(channelHandle)

  fun channelIsActive(channelHandle: Int): Int = BassPlatform.channelIsActive(channelHandle)

  fun channelGetPosition(channelHandle: Int, mode: Int = BASS_POS_BYTE): Long =
    BassPlatform.channelGetPosition(channelHandle, mode)

  fun channelSetPosition(channelHandle: Int, position: Long, mode: Int = BASS_POS_BYTE): Boolean =
    BassPlatform.channelSetPosition(channelHandle, position, mode)

  fun channelUpdate(channelHandle: Int, length: Int = 0): Boolean =
    BassPlatform.channelUpdate(channelHandle, length)

  fun channelGetLength(channelHandle: Int, mode: Int = BASS_POS_BYTE): Long =
    BassPlatform.channelGetLength(channelHandle, mode)

  fun channelBytes2Seconds(channelHandle: Int, position: Long): Double =
    BassPlatform.channelBytes2Seconds(channelHandle, position)

  fun channelSeconds2Bytes(channelHandle: Int, seconds: Double): Long =
    BassPlatform.channelSeconds2Bytes(channelHandle, seconds)

  fun setChannelAttribute(channelHandle: Int, attribute: Int, value: Float): Boolean =
    BassPlatform.setChannelAttribute(channelHandle, attribute, value)

  fun streamEvent(streamHandle: Int, channel: Int, event: Int, parameter: Int): Boolean =
    BassPlatform.streamEvent(streamHandle, channel, event, parameter)

  fun getSoundFontPresets(soundFontHandle: Int): List<Int> =
    BassPlatform.getSoundFontPresets(soundFontHandle)

  fun getSoundFontPresetName(soundFontHandle: Int, preset: Int, bank: Int): String? =
    BassPlatform.getSoundFontPresetName(soundFontHandle, preset, bank)

  fun freeStream(streamHandle: Int): Boolean = BassPlatform.freeStream(streamHandle)

  fun freeSoundFont(soundFontHandle: Int): Boolean =
    BassPlatform.freeSoundFont(soundFontHandle)

  /** Whether this platform's BASSMIDI build offers MIDI input (it does not on Android). */
  val midiInputSupported: Boolean
    get() = BassPlatform.midiInputSupported

  /** Info on MIDI input device number [device] (0 = first), or null past the end of the list. */
  fun midiInGetDeviceInfo(device: Int): BassMidiInputDeviceInfo? =
    BassPlatform.midiInGetDeviceInfo(device)

  /**
   * Initializes MIDI input device [device]. [onData] receives the raw MIDI bytes as they arrive,
   * on an internal BASS thread — it must hand the data off quickly and never block.
   */
  fun midiInInit(device: Int, onData: (ByteArray) -> Unit): Boolean =
    BassPlatform.midiInInit(device, onData)

  fun midiInStart(device: Int): Boolean = BassPlatform.midiInStart(device)

  fun midiInStop(device: Int): Boolean = BassPlatform.midiInStop(device)

  fun midiInFree(device: Int): Boolean = BassPlatform.midiInFree(device)

  fun makeWord(low: Int, high: Int): Int = (low and 0xff) or ((high and 0xff) shl 8)
}

private fun Int.toBassVersionText(): String {
  val major = (this shr 24) and 0xff
  val minor = (this shr 16) and 0xff
  val revision = (this shr 8) and 0xff
  val build = this and 0xff
  return "$major.$minor.$revision.$build"
}
