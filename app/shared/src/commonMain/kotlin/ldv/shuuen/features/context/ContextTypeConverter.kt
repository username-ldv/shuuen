package ldv.shuuen.features.context

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson
import ldv.shuuen.core.music.DegreeContextNode

class ContextTypeConverter {
  @ColumnTypeConverter
  fun degreeContextNodeToString(n: List<DegreeContextNode>): String = RoomJson.encode(n)

  @ColumnTypeConverter
  fun stringToDegreeContextNode(n: String): List<DegreeContextNode> = RoomJson.decode(n)
}
