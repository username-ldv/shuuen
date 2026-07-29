package ldv.shuuen.data.audio

import kotlin.math.abs
import kotlinx.coroutines.flow.first
import ldv.shuuen.bass.Bass
import ldv.shuuen.bass.BassMidiEvent
import ldv.shuuen.core.audio.engine.LoadedMelody
import ldv.shuuen.core.audio.engine.MelodyNote
import ldv.shuuen.core.audio.engine.MidiFilePlaybackOptions
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import ldv.shuuen.core.audio.midi.FullPresetVolume
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetVolumes
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

  // The melody's gain before its instrument's own trim is folded in, plus the trims themselves,
  // both snapshotted at load so a preset swap mid-playback can recompute the gain on its own.
  private var untrimmedMelodyGain: Float = 1f
  private var presetVolumes: PresetVolumes = PresetVolumes()

  // Backing track: a parallel BASS audio stream linked to the MIDI stream, so both start
  // simultaneously; only its position is managed by hand (see alignBackingTrack).
  private var backingHandle: Int = 0
  private var backingOffsetSeconds: Double = 0.0
  private var backingLengthBytes: Long = 0L
  private var backingLinked: Boolean = false

  override suspend fun load(
    bytes: ByteArray,
    options: MidiFilePlaybackOptions,
  ): LoadedMelody {
    release()
    val settings = settingsRepository.settings.first()
    val notesPreset = settings.presets.forChannel(MidiChannel.Notes)
    Bass.setConfig(Bass.BASS_CONFIG_BUFFER, MidiFilePlaybackBufferMs)
    val handle = Bass.createMidiStreamFromMemory(bytes)
    val backing = options.backingTrack
    // PRESCAN builds an exact MP3 seek table; without it seeks are approximate and the two
    // streams would land audibly apart after every rewind.
    val backingStream =
      if (backing != null && handle != 0) {
        Bass.createFileStreamFromMemory(backing.bytes, Bass.BASS_STREAM_PRESCAN)
      } else {
        0
      }
    Bass.setConfig(Bass.BASS_CONFIG_BUFFER, LiveStreamBufferMs)
    require(handle != 0) { "Unable to create MIDI stream from file: ${Bass.errorCode()}." }
    if (backing != null && backingStream == 0) {
      Bass.freeStream(handle)
      error("Unable to create the backing track stream: ${Bass.errorCode()}.")
    }
    streamHandle = handle
    if (backing != null) {
      backingHandle = backingStream
      backingOffsetSeconds = backing.offsetMs / 1000.0
      backingLengthBytes = Bass.channelGetLength(backingStream, Bass.BASS_POS_BYTE)
      Bass.setChannelAttribute(
        backingStream,
        Bass.BASS_ATTRIB_VOL,
        volumeGain(settings.backingTrackVolume),
      )
      setBackingLink(true)
    }
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
    require(options.transpositionSemitones in MinimumBassTransposition..MaximumBassTransposition) {
      "MIDI transposition must be between $MinimumBassTransposition and $MaximumBassTransposition."
    }
    if (options.transpositionSemitones != 0) {
      require(
        Bass.streamEvent(
          streamHandle = handle,
          channel = 0,
          event = Bass.MIDI_EVENT_TRANSPOSE,
          parameter = BassNeutralTransposition + options.transpositionSemitones,
        )
      ) {
        "Unable to transpose MIDI stream: ${Bass.errorCode()}."
      }
    }
    // With the mute setting on, a backing track replaces the melody's sound entirely: the MIDI
    // stream still runs (it drives the quiz position) but at zero volume.
    untrimmedMelodyGain =
      if (backing != null && settings.backingTrackMutesMelody) {
        0f
      } else {
        melodyStreamGain(
          notesVolume = settings.volumes.forChannel(MidiChannel.Notes),
          originalVelocityBoost =
            if (options.useOriginalVelocities) settings.melodyOriginalVolumeBoost else 0,
        )
      }
    presetVolumes = settings.presetVolumes
    require(Bass.setChannelAttribute(handle, Bass.BASS_ATTRIB_VOL, trimmedGain(notesPreset))) {
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
        .map { event ->
          val rawMidiNumber = event.param and MidiByteMask
          // BASSMIDI's global transpose event deliberately leaves each port's drum channel alone.
          val semitones =
            if (event.channel % MidiFileChannelCount == DrumChannelIndex) 0
            else options.transpositionSemitones
          MelodyNote(note = noteFromMidi(rawMidiNumber + semitones), tick = event.tick)
        }

    val lengthTicks = Bass.channelGetLength(handle, Bass.BASS_POS_MIDI_TICK)
    lengthBytes = Bass.channelGetLength(handle, Bass.BASS_POS_BYTE)
    val lengthSeconds = Bass.channelBytes2Seconds(handle, lengthBytes)

    return LoadedMelody(notes = notes, lengthTicks = lengthTicks, lengthSeconds = lengthSeconds)
  }

  override fun play() {
    if (streamHandle != 0) {
      applyActivePreset()
      BassMidiFxDefaults.applyToStream(streamHandle)
      alignBackingTrack()
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

  override fun syncBackingTrack() {
    if (streamHandle == 0 || backingHandle == 0) return
    val midiPlaying = isPlaying()
    val backingPlaying = Bass.channelIsActive(backingHandle) == Bass.BASS_ACTIVE_PLAYING

    if (!midiPlaying) {
      // The MIDI reached its end (or stopped some other way the link doesn't propagate); a still
      // running backing track has outlived the melody.
      if (backingPlaying) Bass.pause(backingHandle)
      return
    }

    val target = backingTargetBytes()
    if (!backingPlaying) {
      // The backing was held back (negative offset, or the melody ran past the audio's end);
      // start it the moment the melody reaches its range.
      if (target in 0 until backingLengthBytes) {
        Bass.channelSetPosition(backingHandle, target, Bass.BASS_POS_BYTE or Bass.BASS_POS_FLUSH)
        setBackingLink(true)
        Bass.play(backingHandle, restart = false)
      }
      return
    }

    if (target !in 0 until backingLengthBytes) {
      // The melody plays outside the audio's range; the backing has nothing to sound.
      setBackingLink(false)
      Bass.pause(backingHandle)
      return
    }

    // Both are playing off the same device clock, so they cannot drift on their own — but an
    // output stall or a missed state transition could leave them apart. Nudge only past a clear
    // threshold; each correction is a small audible skip in the backing audio.
    val driftSeconds =
      Bass.channelBytes2Seconds(
        backingHandle,
        Bass.channelGetPosition(backingHandle, Bass.BASS_POS_BYTE),
      ) - backingTargetSeconds()
    if (abs(driftSeconds) > MaxBackingDriftSeconds) {
      Bass.channelSetPosition(
        backingHandle,
        backingTargetBytes(),
        Bass.BASS_POS_BYTE or Bass.BASS_POS_FLUSH,
      )
    }
  }

  override fun setPreset(preset: Preset) {
    activePreset = preset
    applyActivePreset()
    // The new instrument brings its own loudness trim with it.
    if (streamHandle != 0) {
      Bass.setChannelAttribute(streamHandle, Bass.BASS_ATTRIB_VOL, trimmedGain(preset))
    }
  }

  /** The stream gain for [preset], i.e. the melody's own gain scaled by that instrument's trim. */
  private fun trimmedGain(preset: Preset): Float =
    untrimmedMelodyGain * presetVolumes.forPreset(preset) / FullPresetVolume

  override fun setBackingTrackVolume(volume: Int) {
    if (backingHandle != 0) {
      Bass.setChannelAttribute(backingHandle, Bass.BASS_ATTRIB_VOL, volumeGain(volume))
    }
  }

  override fun release() {
    if (backingHandle != 0) {
      setBackingLink(false)
      Bass.stop(backingHandle)
      Bass.freeStream(backingHandle)
      backingHandle = 0
    }
    backingOffsetSeconds = 0.0
    backingLengthBytes = 0L
    if (streamHandle != 0) {
      Bass.setMidiStreamMelodyFilter(streamHandle, enabled = false)
      Bass.stop(streamHandle)
      Bass.freeStream(streamHandle)
      streamHandle = 0
    }
    lengthBytes = 0L
    activePreset = null
    untrimmedMelodyGain = 1f
    presetVolumes = PresetVolumes()
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
    alignBackingTrack()

    if (wasPlaying) Bass.play(streamHandle, restart = false)
  }

  /**
   * Repositions the (paused) backing track to mirror the MIDI's current position, so the next
   * linked start plays both in sync. When the target position falls outside the audio — a
   * negative-offset lead-in or a melody longer than the audio — the backing is unlinked so it
   * stays silent; [syncBackingTrack] starts it if its range is reached later.
   */
  private fun alignBackingTrack() {
    if (backingHandle == 0) return
    val target = backingTargetBytes()
    if (target in 0 until backingLengthBytes) {
      Bass.channelSetPosition(backingHandle, target, Bass.BASS_POS_BYTE or Bass.BASS_POS_FLUSH)
      Bass.channelUpdate(backingHandle, SeekPrebufferMs)
      setBackingLink(true)
    } else {
      setBackingLink(false)
      Bass.pause(backingHandle)
    }
  }

  /** The backing-audio position, in seconds, that matches the MIDI's current position. */
  private fun backingTargetSeconds(): Double = positionSeconds() + backingOffsetSeconds

  private fun backingTargetBytes(): Long =
    if (backingTargetSeconds() >= 0) {
      Bass.channelSeconds2Bytes(backingHandle, backingTargetSeconds())
    } else {
      -1L
    }

  /** Links/unlinks the backing to the MIDI stream so transport operations move them together. */
  private fun setBackingLink(enabled: Boolean) {
    if (backingLinked == enabled) return
    if (streamHandle == 0 || backingHandle == 0) {
      backingLinked = false
      return
    }
    if (enabled) {
      Bass.linkChannels(streamHandle, backingHandle)
    } else {
      Bass.unlinkChannels(streamHandle, backingHandle)
    }
    backingLinked = enabled
  }

  private fun applyPresetToAllChannels(streamHandle: Int, preset: Preset) {
    repeat(MidiFileChannelCount) { channel ->
      Bass.streamEvent(streamHandle, channel, Bass.MIDI_EVENT_BANK_LSB, DefaultBankLsb)
      Bass.streamEvent(streamHandle, channel, Bass.MIDI_EVENT_BANK, preset.bank)
      Bass.streamEvent(streamHandle, channel, Bass.MIDI_EVENT_PROGRAM, preset.id)
    }
  }

  private fun melodyStreamGain(notesVolume: Int, originalVelocityBoost: Int): Float =
    volumeGain(notesVolume) * originalVelocityVolumeGain(originalVelocityBoost)

  private fun volumeGain(volume: Int): Float =
    volume.coerceIn(0, MidiValueMax).toFloat() / MidiValueMax

  private fun originalVelocityVolumeGain(boost: Int): Float =
    1f + (MaxOriginalVelocityGain - 1f) * boost.coerceIn(0, MidiValueMax) / MidiValueMax

  private companion object {
    const val MidiFileChannelCount = 16
    const val DrumChannelIndex = 9
    const val DefaultBankLsb = 0
    const val MidiFilePlaybackBufferMs = 250
    const val LiveStreamBufferMs = 30
    const val SeekPrebufferMs = 20
    const val MidiByteMask = 0xFF
    const val MidiValueMax = 127
    const val MaxOriginalVelocityGain = 4f
    const val MinimumBassTransposition = -100
    const val MaximumBassTransposition = 100
    const val BassNeutralTransposition = 100

    /**
     * Positional disagreement between the MIDI and its backing track beyond which the periodic
     * sync repositions the backing. Wide enough that it never trips in normal linked playback.
     */
    const val MaxBackingDriftSeconds = 0.1
  }
}
