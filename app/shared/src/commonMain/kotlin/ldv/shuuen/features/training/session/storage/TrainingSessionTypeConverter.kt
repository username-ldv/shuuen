package ldv.shuuen.features.training.session.storage

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson
import ldv.shuuen.features.training.session.QuestionResult

class TrainingSessionTypeConverter {
  @ColumnTypeConverter
  fun questionResultsToString(results: List<QuestionResult>): String = RoomJson.encode(results)

  @ColumnTypeConverter
  fun stringToQuestionResults(results: String): List<QuestionResult> = RoomJson.decode(results)
}
