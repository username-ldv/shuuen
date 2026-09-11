package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.data.database.RoomJson
import ldv.shuuen.core.music.NoteRange

class GeneralTypeConverter {
  @TypeConverter
  fun noteRangeToString(n: NoteRange): String = RoomJson.encode(n)

  @TypeConverter
  fun stringTonoteRange(n: String): NoteRange = RoomJson.decode(n)
}