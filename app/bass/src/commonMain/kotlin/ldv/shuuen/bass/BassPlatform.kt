package ldv.shuuen.bass

internal expect object BassPlatform {
  fun load()
  fun version(): Int
  fun midiVersion(): Int
  fun init(device: Int, frequency: Int, flags: Int): Boolean
  fun setConfig(option: Int, value: Int): Boolean
  fun free(): Boolean
  fun freePlugins(handle: Int): Boolean
  fun errorCode(): Int
  fun createLiveMidiStream(channels: Int, flags: Int, frequency: Int): Int
  fun createMidiStream(filePath: String, flags: Int, frequency: Int): Int
  fun createMidiStreamFromMemory(data: ByteArray, flags: Int, frequency: Int): Int

  /**
   * Creates an audio stream (MP3/OGG/WAV/AIFF) from [data]. Unlike MIDI streams, BASS decodes
   * file streams lazily, so the implementation pins [data]'s native copy until [freeStream].
   */
  fun createFileStreamFromMemory(data: ByteArray, flags: Int): Int

  /** BASS_ChannelSetLink: [chan] starts/stops/pauses/resumes together with [handle]. */
  fun linkChannels(handle: Int, chan: Int): Boolean

  fun unlinkChannels(handle: Int, chan: Int): Boolean
  fun streamGetEvents(streamHandle: Int, track: Int, filter: Int): List<BassMidiEvent>
  fun setMidiStreamMelodyFilter(
    streamHandle: Int,
    enabled: Boolean,
    preset: Int,
    bank: Int,
    normalizeNoteVelocity: Boolean,
    cutoffOverride: Int?,
  ): Boolean
  fun loadSoundFont(filePath: String, flags: Int): Int
  fun setStreamSoundFont(streamHandle: Int, soundFontHandle: Int, preset: Int, bank: Int): Boolean
  fun play(channelHandle: Int, restart: Boolean): Boolean
  fun start(channelHandle: Int): Boolean
  fun pause(channelHandle: Int): Boolean
  fun stop(channelHandle: Int): Boolean
  fun channelIsActive(channelHandle: Int): Int
  fun channelGetPosition(channelHandle: Int, mode: Int): Long
  fun channelSetPosition(channelHandle: Int, position: Long, mode: Int): Boolean
  fun channelUpdate(channelHandle: Int, length: Int): Boolean
  fun channelGetLength(channelHandle: Int, mode: Int): Long
  fun channelBytes2Seconds(channelHandle: Int, position: Long): Double
  fun channelSeconds2Bytes(channelHandle: Int, seconds: Double): Long
  fun setChannelAttribute(channelHandle: Int, attribute: Int, value: Float): Boolean
  fun streamEvent(streamHandle: Int, channel: Int, event: Int, parameter: Int): Boolean
  fun getSoundFontPresets(soundFontHandle: Int): List<Int>
  fun getSoundFontPresetName(soundFontHandle: Int, preset: Int, bank: Int): String?
  fun freeStream(streamHandle: Int): Boolean
  fun freeSoundFont(soundFontHandle: Int): Boolean
  val midiInputSupported: Boolean
  fun midiInGetDeviceInfo(device: Int): BassMidiInputDeviceInfo?
  fun midiInInit(device: Int, onData: (ByteArray) -> Unit): Boolean
  fun midiInStart(device: Int): Boolean
  fun midiInStop(device: Int): Boolean
  fun midiInFree(device: Int): Boolean
}
