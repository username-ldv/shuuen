package ldv.shuuen.features.training.level_end.domain

import kotlinx.serialization.Serializable
import ldv.shuuen.features.training.common.TrainingFlow

/**
 * One answered question of a finished session, kept for per-range accuracy breakdowns. A Singles
 * question and a MIDI-melody note are both a single note ([noteCount] = 1); a random-melody
 * question spans its whole sequence.
 */
@Serializable
data class QuestionResult(
  val questionNumber: Int,
  val noteCount: Int,
  val missedCount: Int,
)

/**
 * The saved outcome of one training session, written when a level ends (naturally or through the
 * finish-early action) and read back by the level-complete screen.
 *
 * Scoring is note-based so both flows compare the same way: [correctNotes] counts notes answered
 * right on the first try out of [notesTotal] answered notes. For Singles a question is one note.
 */
data class TrainingSession(
  val id: String,
  val flow: TrainingFlow,
  val levelId: String,
  val levelName: String,
  val completedAtEpochMillis: Long,
  val finishedEarly: Boolean,
  val questionsAnswered: Int,
  val notesTotal: Int,
  val correctNotes: Int,
  val missedNotes: Int,
  /** Repeat-note presses (Singles) or rewinds (Melodies). */
  val replays: Int,
  val durationMillis: Long,
  /** Mean time from hearing a question to answering it; null where it isn't meaningful (Melodies). */
  val avgAnswerMillis: Long?,
  /** Longest run of consecutive first-try-correct notes. */
  val bestStreak: Int,
  /** Distinct tonics practiced; 0 when the level has no tracked key (MIDI melodies). */
  val keysPracticed: Int,
  val questionResults: List<QuestionResult>,
) {
  val accuracy: Float
    get() = if (notesTotal > 0) correctNotes.toFloat() / notesTotal else 0f
}
