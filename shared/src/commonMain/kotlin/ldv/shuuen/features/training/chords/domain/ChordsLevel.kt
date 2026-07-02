package ldv.shuuen.features.training.chords.domain

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import kotlin.uuid.ExperimentalUuidApi

/** In which order the chord's notes must be answered. */
@Serializable
enum class ChordAnswerOrder(val label: String) {
  /** Any unanswered chord note is accepted. */
  Any("Any"),

  /** Notes must be answered lowest first. */
  FromBottom("From bottom"),

  /** Notes must be answered highest first. */
  FromTop("From top"),
}

/**
 * How many extra same-pitch-class copies a chord of [size] notes may contain when answered in
 * [ChordAnswerOrder.Any] mode: a pair stays all-distinct, 3–4 notes allow one duplicate (a class
 * at most twice), 5–7 allow two extras (at most three of a kind), 8–10 allow three (at most four).
 * Ordered answering is exempt — each octave copy is named in turn, so duplicates stay unrestricted.
 */
fun chordRepeatBudget(size: Int): Int =
  when {
    size <= 2 -> 0
    size <= 4 -> 1
    size <= 7 -> 2
    else -> 3
  }

/**
 * How many notes each question's chord has, chosen uniformly per question among the sizes the
 * scale and range can actually produce. A chord may repeat a pitch class across octaves: freely
 * with ordered answering, within [chordRepeatBudget] with [ChordAnswerOrder.Any] (where one press
 * resolves every copy of the class at once).
 */
@Serializable
data class ChordSizeRange(val min: Int, val max: Int) {
  init {
    require(min in MinSize..max) { "Chord size range must satisfy $MinSize <= min <= max, was $min..$max." }
    require(max <= MaxSize) { "Chord size max must be at most $MaxSize, was $max." }
  }

  override fun toString(): String = if (min == max) "$min" else "$min–$max"

  companion object {
    const val MinSize = 2
    const val MaxSize = 10
  }
}

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class ChordsLevel(
  val id: String,
  val name: String,
  val levelConfig: LevelConfig.Chords,
  val context: DegreeContext?,
  val source: LevelSource,
  val questionsNumber: Int?,
  val range: NoteRange,
  val chordSize: ChordSizeRange,
  /** true holds each chord until the question advances (or a replay); false releases it after ~2s. */
  val sustainNotes: Boolean,
  val answerOrder: ChordAnswerOrder,
)
