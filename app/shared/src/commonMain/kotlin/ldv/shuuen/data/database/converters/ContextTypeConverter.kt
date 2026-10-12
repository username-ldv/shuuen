package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.data.database.RoomJson

class ContextTypeConverter {
  @TypeConverter
  fun degreeContextNodeToString(n: List<DegreeContextNode>): String = RoomJson.encode(n)

  @TypeConverter
  fun stringToDegreeContextNode(n: String): List<DegreeContextNode> = RoomJson.decode(n)
}
