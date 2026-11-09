package ldv.shuuen.core.database

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.music.NoteRange

class GeneralTypeConverter {
  @ColumnTypeConverter fun noteRangeToString(n: NoteRange): String = RoomJson.encode(n)

  @ColumnTypeConverter fun stringTonoteRange(n: String): NoteRange = RoomJson.decode(n)
}
