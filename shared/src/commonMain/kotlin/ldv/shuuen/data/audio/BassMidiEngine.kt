package ldv.shuuen.data.audio

import kotlinx.coroutines.flow.first
import ldv.shuuen.bass.Bass
import ldv.shuuen.domain.audio.engine.MidiEngine
import ldv.shuuen.domain.audio.engine.MidiEngineStatus
import ldv.shuuen.domain.audio.engine.SoundFontProvider
import ldv.shuuen.domain.audio.midi.MidiChannel
import ldv.shuuen.domain.audio.midi.Preset
import ldv.shuuen.domain.audio.music.Chord
import ldv.shuuen.domain.audio.music.Note
import ldv.shuuen.domain.repository.SettingsRepository

class BassMidiEngine(
  private val settingsRepository: SettingsRepository,
  private val soundFontProvider: SoundFontProvider,
) : MidiEngine {
  private var midiStreamHandle: Int = 0
  private var soundFontHandle: Int = 0
  private var initialized: Boolean = false

  override suspend fun initialize(): MidiEngineStatus {
    if (initialized) return MidiEngineStatus.Ready

    return runCatching {
      Bass.load()
      Bass.setConfig(Bass.BASS_CONFIG_DEV_PERIOD, DeviceUpdatePeriodMs)
      Bass.setConfig(Bass.BASS_CONFIG_DEV_BUFFER, DeviceBufferMs)
      require(Bass.init()) { "Unable to initialize BASS: ${Bass.errorCode()}." }

      Bass.setConfig(Bass.BASS_CONFIG_UPDATEPERIOD, StreamUpdatePeriodMs)
      Bass.setConfig(Bass.BASS_CONFIG_BUFFER, LiveStreamBufferMs)
      Bass.setConfig(Bass.BASS_CONFIG_MIDI_VOICES, 128)

      midiStreamHandle = Bass.createLiveMidiStream(channels = 128)
      require(midiStreamHandle != 0) { "Unable to create MIDI stream: ${Bass.errorCode()}." }
      Bass.setChannelAttribute(midiStreamHandle, Bass.BASS_ATTRIB_BUFFER, 0f)
      Bass.start(midiStreamHandle)

      val settings = settingsRepository.settings.first()
      soundFontHandle = settings.soundFontPath?.let { soundFontProvider.loadSoundFont(it) }
        ?: soundFontProvider.loadDefaultSoundFont()
      require(soundFontHandle != 0) { "Unable to load soundfont: ${Bass.errorCode()}." }
      require(Bass.setStreamSoundFont(midiStreamHandle, soundFontHandle)) {
        "Unable to attach soundfont to stream: ${Bass.errorCode()}."
      }
      require(BassMidiFxDefaults.applyToStream(midiStreamHandle)) {
        "Unable to apply MIDI effect defaults: ${Bass.errorCode()}."
      }
      // Also set it as the default soundfont (handle 0) so separately-created MIDI file streams
      // (the melodies file player) inherit it without re-loading the soundfont.
      Bass.setStreamSoundFont(0, soundFontHandle)

      MidiChannel.entries.forEach { channel ->
        setPreset(channel, settings.presets.forChannel(channel))
        setVolume(channel, settings.volumes.forChannel(channel))
      }

      initialized = true
      MidiEngineStatus.Ready
    }.getOrElse { throwable ->
      close()
      MidiEngineStatus.Failed(throwable.message ?: "Unable to initialize MIDI engine.")
    }
  }

  override fun playNote(note: Note, channel: MidiChannel, velocity: Int): Boolean {
    if (!initialized) return false
    return Bass.streamEvent(
      streamHandle = midiStreamHandle,
      channel = channel.id,
      event = Bass.MIDI_EVENT_NOTE,
      parameter = Bass.makeWord(note.midiIndex, velocity.coerceIn(0, 127)),
    )
  }

  override fun stopNote(note: Note, channel: MidiChannel): Boolean {
    if (!initialized) return false
    return Bass.streamEvent(
      streamHandle = midiStreamHandle,
      channel = channel.id,
      event = Bass.MIDI_EVENT_NOTE,
      parameter = Bass.makeWord(note.midiIndex, 0),
    )
  }

  override fun playChord(chord: Chord, channel: MidiChannel, velocity: Int): Boolean =
    chord.notes.map { playNote(it, channel, velocity) }.all { it }

  override fun stopChord(chord: Chord, channel: MidiChannel): Boolean =
    chord.notes.map { stopNote(it, channel) }.all { it }

  override fun stopAll(channel: MidiChannel?): Boolean {
    if (!initialized) return false
    val channels = channel?.let(::listOf) ?: MidiChannel.entries
    return channels.map {
      Bass.streamEvent(midiStreamHandle, it.id, Bass.MIDI_EVENT_NOTESOFF, 0)
    }.all { it }
  }

  override fun setPreset(channel: MidiChannel, preset: Preset): Boolean {
    if (midiStreamHandle == 0) return false
    val bankChanged =
      Bass.streamEvent(midiStreamHandle, channel.id, Bass.MIDI_EVENT_BANK, preset.bank)
    val programChanged =
      Bass.streamEvent(midiStreamHandle, channel.id, Bass.MIDI_EVENT_PROGRAM, preset.id)
    return bankChanged && programChanged
  }

  override fun setVolume(channel: MidiChannel, value: Int): Boolean {
    if (midiStreamHandle == 0) return false
    return Bass.streamEvent(
      midiStreamHandle,
      channel.id,
      Bass.MIDI_EVENT_VOLUME,
      value.coerceIn(0, 127),
    )
  }

  override fun availablePresets(): List<Preset> {
    if (soundFontHandle == 0) return emptyList()
    return Bass.getSoundFontPresets(soundFontHandle).map { packed ->
      val preset = Preset.fromPacked(packed)
      preset.copy(name = Bass.getSoundFontPresetName(soundFontHandle, preset.id, preset.bank))
    }
  }

  override fun close() {
    if (midiStreamHandle != 0) {
      Bass.freeStream(midiStreamHandle)
      midiStreamHandle = 0
    }
    if (soundFontHandle != 0) {
      Bass.freeSoundFont(soundFontHandle)
      soundFontHandle = 0
    }
    Bass.freePlugins()
    Bass.free()
    initialized = false
  }

  private companion object {
    const val DeviceUpdatePeriodMs = 10
    const val DeviceBufferMs = 40
    const val StreamUpdatePeriodMs = 10
    const val LiveStreamBufferMs = 30
  }
}
