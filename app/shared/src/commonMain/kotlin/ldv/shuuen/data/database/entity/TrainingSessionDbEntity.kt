package ldv.shuuen.data.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.level_end.domain.QuestionResult

/**
 * One finished training session. No foreign key to the level tables: results outlive their level
 * (levels can be edited or deleted), so [levelId] is a plain reference and [levelName] a snapshot.
 */
@Entity(tableName = "training_sessions", indices = [Index("levelId")])
data class TrainingSessionDbEntity(
  @PrimaryKey val id: String,
  val flow: TrainingFlow,
  val levelId: String,
  val levelName: String,
  val completedAtEpochMillis: Long,
  val finishedEarly: Boolean,
  val questionsAnswered: Int,
  val notesTotal: Int,
  val correctNotes: Int,
  val missedNotes: Int,
  val replays: Int,
  val durationMillis: Long,
  val avgAnswerMillis: Long?,
  val avgDeltaMillis: Long?,
  val bestStreak: Int,
  val keysPracticed: Int,
  val questionResults: List<QuestionResult>,
)
