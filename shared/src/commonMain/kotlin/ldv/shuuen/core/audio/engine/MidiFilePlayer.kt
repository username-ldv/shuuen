package ldv.shuuen.core.audio.engine

import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.music.Note

/**
 * A note within a loaded melody: the pitch plus its start position in MIDI ticks.
 * [durationQuarters] is the note's rhythm value in quarter-note beats; only generated (random)
 * melodies play by it — a MIDI file's own player keeps the file's real timing. [detuneCents]
 * plays the note out of tune by that many cents; also generated melodies only.
 */
data class MelodyNote(
  val note: Note,
  val tick: Long,
  val durationQuarters: Double = 1.0,
  val detuneCents: Int = 0,
)

/** The outcome of loading a MIDI file: its note-on sequence and total length. */
data class LoadedMelody(
  val notes: List<MelodyNote>,
  val lengthTicks: Long,
  val lengthSeconds: Double,
)

/**
 * A backing audio file (e.g. an MP3 of the real song) played in sync with the MIDI melody.
 * [offsetMs] is the position in the audio that lines up with the MIDI's time zero: positive when
 * the audio has an intro before the MIDI's first tick, negative when the audio starts late.
 */
class BackingTrackData(val bytes: ByteArray, val offsetMs: Long)

data class MidiFilePlaybackOptions(
  val useOriginalVelocities: Boolean = false,
  val backingTrack: BackingTrackData? = null,
)

/**
 * Plays a loaded MIDI file at its natural tempo with transport controls (BASS handles the timing).
 * Playback position is polled by the caller — e.g. a ViewModel ticking on a timer — via
 * [positionTicks]/[positionSeconds]/[isPlaying]; there is one active melody at a time.
 */
interface MidiFilePlayer {
  /** Loads [bytes] as a MIDI stream and returns its note sequence and length. Does not start playback. */
  suspend fun load(
    bytes: ByteArray,
    options: MidiFilePlaybackOptions = MidiFilePlaybackOptions(),
  ): LoadedMelody

  fun play()

  fun pause()

  /** Seeks to an absolute MIDI tick position. */
  fun seekToTick(tick: Long)

  /** Seeks forward (positive) or backward (negative) by a number of seconds, clamped to the file. */
  fun seekBySeconds(deltaSeconds: Double)

  fun positionTicks(): Long

  fun positionSeconds(): Double

  fun isPlaying(): Boolean

  /**
   * Keeps the backing track (when one is loaded) aligned with the melody; the caller invokes it
   * periodically, e.g. from the same timer that polls the position. Play/pause/seek keep the two
   * in sync on their own — this covers what they can't: starting a late backing track (negative
   * offset), silencing it past the melody's end, and correcting drift after audio hiccups.
   */
  fun syncBackingTrack() {}

  /** Applies a new backing-track volume (0..127) mid-playback. A no-op without a backing track. */
  fun setBackingTrackVolume(volume: Int) {}

  /**
   * Switches the instrument the loaded melody sounds with, mid-playback. Notes already ringing keep
   * the old instrument; everything started afterwards uses [preset].
   */
  fun setPreset(preset: Preset) {}

  /** Frees the underlying stream. Safe to call repeatedly. */
  fun release()
}
