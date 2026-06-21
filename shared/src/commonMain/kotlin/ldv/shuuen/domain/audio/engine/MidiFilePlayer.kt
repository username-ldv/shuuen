package ldv.shuuen.domain.audio.engine

import ldv.shuuen.domain.audio.music.Note

/** A note within a loaded melody: the pitch plus its start position in MIDI ticks. */
data class MelodyNote(val note: Note, val tick: Long)

/** The outcome of loading a MIDI file: its note-on sequence and total length. */
data class LoadedMelody(
  val notes: List<MelodyNote>,
  val lengthTicks: Long,
  val lengthSeconds: Double,
)

data class MidiFilePlaybackOptions(
  val useOriginalVelocities: Boolean = false,
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

  /** Frees the underlying stream. Safe to call repeatedly. */
  fun release()
}
