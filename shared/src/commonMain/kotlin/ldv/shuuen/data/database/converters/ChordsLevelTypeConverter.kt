package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.data.database.RoomJson
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.domain.LevelConfig

class ChordsLevelTypeConverter {
  @TypeConverter
  fun levelConfigToString(l: LevelConfig.Chords): String = RoomJson.encode(l)

  @TypeConverter
  fun stringToLevelConfig(l: String): LevelConfig.Chords = RoomJson.decode(l)

  @TypeConverter
  fun chordSizeRangeToString(r: ChordSizeRange): String = RoomJson.encode(r)

  @TypeConverter
  fun stringToChordSizeRange(r: String): ChordSizeRange = RoomJson.decode(r)
}
