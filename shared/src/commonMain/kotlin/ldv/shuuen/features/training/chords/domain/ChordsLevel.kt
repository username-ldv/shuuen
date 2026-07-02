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
 * How many notes each question's chord has, chosen uniformly per question. Notes always have
 * distinct pitch classes (the inputs answer by pitch class, so a doubled octave would be
 * unanswerable), which caps the effective size at the scale's active pitch count.
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
