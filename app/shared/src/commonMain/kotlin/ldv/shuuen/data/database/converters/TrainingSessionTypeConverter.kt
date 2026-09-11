package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.data.database.RoomJson
import ldv.shuuen.features.training.level_end.domain.QuestionResult

class TrainingSessionTypeConverter {
  @TypeConverter
  fun questionResultsToString(results: List<QuestionResult>): String = RoomJson.encode(results)

  @TypeConverter
  fun stringToQuestionResults(results: String): List<QuestionResult> = RoomJson.decode(results)
}
