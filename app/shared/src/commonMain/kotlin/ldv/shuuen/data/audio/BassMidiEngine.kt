package ldv.shuuen.data.audio

import io.github.aakira.napier.Napier
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import ldv.shuuen.bass.Bass
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.engine.SoundFontProvider
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.NeutralPresetCutoff
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.scaledChannelVolume
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.settings.SettingsRepository

class BassMidiEngine(
  private val settingsRepository: SettingsRepository,
  private val soundFontProvider: SoundFontProvider,
) : MidiEngine {
  private var midiStreamHandle: Int = 0
  private var soundFontHandle: Int = 0
  private var initialized: Boolean = false
  private val channelFineTuneCents = mutableMapOf<MidiChannel, Int>()
  private val channelBendRange = mutableMapOf<MidiChannel, Int>()

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
      enableSincMidiInterpolation(midiStreamHandle)
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
        val preset = settings.presets.forChannel(channel)
        setPreset(channel, preset)
        setCutoff(
          channel,
          settings.presetCutoffs.effectiveForPreset(preset, originalVelocityMelody = false)
            ?: NeutralPresetCutoff,
        )
        setVolume(
          channel,
          scaledChannelVolume(
            settings.volumes.forChannel(channel),
            settings.presetVolumes.forPreset(preset),
          ),
        )
      }

      initialized = true
      MidiEngineStatus.Ready
    }.getOrElse { throwable ->
      close()
      MidiEngineStatus.Failed(throwable.message ?: "Unable to initialize MIDI engine.")
    }
  }

  override fun playNote(note: Note, channel: MidiChannel, velocity: Int, detuneCents: Int): Boolean {
    if (!initialized) return false
    Napier.v { "Detune note cents: $detuneCents" }
    applyFineTune(channel, detuneCents)
    return Bass.streamEvent(
      streamHandle = midiStreamHandle,
      channel = channel.id,
      event = Bass.MIDI_EVENT_NOTE,
      parameter = Bass.makeWord(note.midiIndex, velocity.coerceIn(0, 127)),
    )
  }

  /** Sets the channel's fine tuning (MIDI RPN 1) ahead of a note-on, skipped when unchanged. */
  private fun applyFineTune(channel: MidiChannel, cents: Int) {
    if (channelFineTuneCents.getOrElse(channel) { 0 } == cents) return
    val applied =
      Bass.streamEvent(midiStreamHandle, channel.id, Bass.MIDI_EVENT_FINETUNE, fineTuneParam(cents))
    if (applied) channelFineTuneCents[channel] = cents
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

  override fun setPitchBendRange(channel: MidiChannel, semitones: Int): Boolean {
    if (!initialized) return false
    val applied =
      Bass.streamEvent(midiStreamHandle, channel.id, Bass.MIDI_EVENT_PITCHRANGE, semitones)
    if (applied) channelBendRange[channel] = semitones
    return applied
  }

  override fun setPitchBend(channel: MidiChannel, semitones: Double): Boolean {
    if (!initialized) return false
    val range = channelBendRange.getOrElse(channel) { DefaultBendRangeSemitones }
    return Bass.streamEvent(
      midiStreamHandle,
      channel.id,
      Bass.MIDI_EVENT_PITCH,
      pitchWheelParam(semitones, range),
    )
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

  override fun setCutoff(channel: MidiChannel, value: Int): Boolean {
    if (midiStreamHandle == 0) return false
    return Bass.streamEvent(
      midiStreamHandle,
      channel.id,
      Bass.MIDI_EVENT_CUTOFF,
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
    channelFineTuneCents.clear()
    channelBendRange.clear()
  }

  private companion object {
    const val DeviceUpdatePeriodMs = 10
    const val DeviceBufferMs = 40
    const val StreamUpdatePeriodMs = 10
    const val LiveStreamBufferMs = 30

    /** Pitch wheel reach when no MIDI_EVENT_PITCHRANGE was sent, per the MIDI standard. */
    const val DefaultBendRangeSemitones = 2
  }
}

/** MIDI_EVENT_FINETUNE parameter for a cent offset: 0 = -100¢, 8192 = in tune, 16383 = +100¢. */
internal fun fineTuneParam(cents: Int): Int =
  (8192 + cents * 8192 / 100).coerceIn(0, 16383)

/** MIDI_EVENT_PITCH parameter for a bend of [semitones] on a wheel spanning ±[rangeSemitones]. */
internal fun pitchWheelParam(semitones: Double, rangeSemitones: Int): Int {
  val deflection = (semitones / rangeSemitones).coerceIn(-1.0, 1.0)
  return (8192 + deflection * 8191).roundToInt().coerceIn(0, 16383)
}
