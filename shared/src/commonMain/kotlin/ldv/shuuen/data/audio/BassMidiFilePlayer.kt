package ldv.shuuen.data.audio

import kotlin.math.abs
import kotlinx.coroutines.flow.first
import ldv.shuuen.bass.Bass
import ldv.shuuen.bass.BassMidiEvent
import ldv.shuuen.core.audio.engine.LoadedMelody
import ldv.shuuen.core.audio.engine.MelodyNote
import ldv.shuuen.core.audio.engine.MidiFilePlaybackOptions
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.settings.SettingsRepository

/**
 * BASS-backed [MidiFilePlayer]. Loads the MIDI from memory, lets BASS render it at its natural
 * tempo, and reads the note-on events out of the stream for display. The MIDI engine must be
 * initialized first (it sets the default soundfont that this stream inherits).
 */
class BassMidiFilePlayer(
  private val settingsRepository: SettingsRepository,
) : MidiFilePlayer {
  private var streamHandle: Int = 0
  private var lengthBytes: Long = 0L
  private var activePreset: Preset? = null

  override suspend fun load(
    bytes: ByteArray,
    options: MidiFilePlaybackOptions,
  ): LoadedMelody {
    release()
    val settings = settingsRepository.settings.first()
    val notesPreset = settings.presets.forChannel(MidiChannel.Notes)
    Bass.setConfig(Bass.BASS_CONFIG_BUFFER, MidiFilePlaybackBufferMs)
    val handle = Bass.createMidiStreamFromMemory(bytes)
    Bass.setConfig(Bass.BASS_CONFIG_BUFFER, LiveStreamBufferMs)
    require(handle != 0) { "Unable to create MIDI stream from file: ${Bass.errorCode()}." }
    streamHandle = handle
    require(
      Bass.setMidiStreamMelodyFilter(
        streamHandle = handle,
        enabled = true,
        preset = notesPreset.id,
        bank = notesPreset.bank,
        normalizeNoteVelocity = !options.useOriginalVelocities,
      ),
    ) {
      "Unable to set MIDI stream filter: ${Bass.errorCode()}."
    }
    require(
      Bass.setChannelAttribute(
        handle,
        Bass.BASS_ATTRIB_VOL,
        melodyStreamGain(
          notesVolume = settings.volumes.forChannel(MidiChannel.Notes),
          originalVelocityBoost =
            if (options.useOriginalVelocities) settings.melodyOriginalVolumeBoost else 0,
        ),
      ),
    ) {
      "Unable to set MIDI stream volume boost: ${Bass.errorCode()}."
    }
    require(BassMidiFxDefaults.applyToStream(handle)) {
      "Unable to apply MIDI effect defaults: ${Bass.errorCode()}."
    }
    activePreset = notesPreset
    applyActivePreset()

    // MIDI_EVENT_NOTES asks BASSMIDI for note-on events, but some native versions also return
    // velocity-0 note releases; filter defensively before building the quiz sequence.
    val notes =
      Bass.streamGetEvents(handle, track = -1, filter = Bass.MIDI_EVENT_NOTES)
        .filter(::isNoteOn)
        .sortedWith(compareBy({ it.tick }, { it.pos }, { it.channel }))
        .map { MelodyNote(note = noteFromMidi(it.param and 0xFF), tick = it.tick) }

    val lengthTicks = Bass.channelGetLength(handle, Bass.BASS_POS_MIDI_TICK)
    lengthBytes = Bass.channelGetLength(handle, Bass.BASS_POS_BYTE)
    val lengthSeconds = Bass.channelBytes2Seconds(handle, lengthBytes)

    return LoadedMelody(notes = notes, lengthTicks = lengthTicks, lengthSeconds = lengthSeconds)
  }

  override fun play() {
    if (streamHandle != 0) {
      applyActivePreset()
      BassMidiFxDefaults.applyToStream(streamHandle)
      Bass.play(streamHandle, restart = false)
    }
  }

  override fun pause() {
    if (streamHandle != 0) Bass.pause(streamHandle)
  }

  override fun seekToTick(tick: Long) {
    if (streamHandle == 0) return
    seekWithPreset(
      position = tick.coerceAtLeast(0),
      mode = Bass.BASS_POS_MIDI_TICK or Bass.BASS_POS_FLUSH,
    )
  }

  override fun seekBySeconds(deltaSeconds: Double) {
    if (streamHandle == 0) return
    val current = Bass.channelGetPosition(streamHandle, Bass.BASS_POS_BYTE)
    val delta = Bass.channelSeconds2Bytes(streamHandle, abs(deltaSeconds))
    val target = if (deltaSeconds >= 0) current + delta else current - delta
    seekWithPreset(
      position = target.coerceIn(0L, lengthBytes),
      mode = Bass.BASS_POS_BYTE or Bass.BASS_POS_FLUSH,
    )
  }

  override fun positionTicks(): Long =
    if (streamHandle == 0) 0L else Bass.channelGetPosition(streamHandle, Bass.BASS_POS_MIDI_TICK)

  override fun positionSeconds(): Double {
    if (streamHandle == 0) return 0.0
    val posBytes = Bass.channelGetPosition(streamHandle, Bass.BASS_POS_BYTE)
    return Bass.channelBytes2Seconds(streamHandle, posBytes)
  }

  override fun isPlaying(): Boolean =
    streamHandle != 0 && Bass.channelIsActive(streamHandle) == Bass.BASS_ACTIVE_PLAYING

  override fun release() {
    if (streamHandle != 0) {
      Bass.setMidiStreamMelodyFilter(streamHandle, enabled = false)
      Bass.stop(streamHandle)
      Bass.freeStream(streamHandle)
      streamHandle = 0
    }
    lengthBytes = 0L
    activePreset = null
  }

  /** Folds a raw MIDI note number into the supported 88-key piano range by whole octaves. */
  private fun noteFromMidi(midiNumber: Int): Note {
    var n = midiNumber
    while (n < Note.MidiMin) n += 12
    while (n > Note.MidiMax) n -= 12
    return Note(n)
  }

  private fun isNoteOn(event: BassMidiEvent): Boolean =
    ((event.param shr 8) and MidiByteMask) > 0

  private fun applyActivePreset() {
    val preset = activePreset ?: return
    if (streamHandle != 0) applyPresetToAllChannels(streamHandle, preset)
  }

  private fun seekWithPreset(position: Long, mode: Int) {
    val wasPlaying = isPlaying()
    if (wasPlaying) Bass.pause(streamHandle)

    Bass.channelSetPosition(streamHandle, position, mode)
    applyActivePreset()
    BassMidiFxDefaults.applyToStream(streamHandle)
    Bass.channelUpdate(streamHandle, SeekPrebufferMs)

    if (wasPlaying) Bass.play(streamHandle, restart = false)
  }

  private fun applyPresetToAllChannels(streamHandle: Int, preset: Preset) {
    repeat(MidiFileChannelCount) { channel ->
      Bass.streamEvent(streamHandle, channel, Bass.MIDI_EVENT_BANK_LSB, DefaultBankLsb)
      Bass.streamEvent(streamHandle, channel, Bass.MIDI_EVENT_BANK, preset.bank)
      Bass.streamEvent(streamHandle, channel, Bass.MIDI_EVENT_PROGRAM, preset.id)
    }
  }

  private fun melodyStreamGain(notesVolume: Int, originalVelocityBoost: Int): Float =
    notesChannelVolumeGain(notesVolume) * originalVelocityVolumeGain(originalVelocityBoost)

  private fun notesChannelVolumeGain(volume: Int): Float =
    volume.coerceIn(0, MidiValueMax).toFloat() / MidiValueMax

  private fun originalVelocityVolumeGain(boost: Int): Float =
    1f + (MaxOriginalVelocityGain - 1f) * boost.coerceIn(0, MidiValueMax) / MidiValueMax

  private companion object {
    const val MidiFileChannelCount = 16
    const val DefaultBankLsb = 0
    const val MidiFilePlaybackBufferMs = 250
    const val LiveStreamBufferMs = 30
    const val SeekPrebufferMs = 20
    const val MidiByteMask = 0xFF
    const val MidiValueMax = 127
    const val MaxOriginalVelocityGain = 4f
  }
}
