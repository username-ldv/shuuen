package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.data.database.RoomJson

class GeneralTypeConverter {
  @TypeConverter fun noteRangeToString(n: NoteRange): String = RoomJson.encode(n)

  @TypeConverter fun stringTonoteRange(n: String): NoteRange = RoomJson.decode(n)
}
