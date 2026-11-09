package ldv.shuuen.features.training.session

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson

class TrainingSessionTypeConverter {
  @ColumnTypeConverter
  fun questionResultsToString(results: List<QuestionResult>): String = RoomJson.encode(results)

  @ColumnTypeConverter
  fun stringToQuestionResults(results: String): List<QuestionResult> = RoomJson.decode(results)
}
