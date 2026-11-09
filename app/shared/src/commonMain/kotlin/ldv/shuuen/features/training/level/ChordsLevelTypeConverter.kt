package ldv.shuuen.features.training.level

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson

class ChordsLevelTypeConverter {
  @ColumnTypeConverter fun levelConfigToString(l: LevelConfig.Chords): String = RoomJson.encode(l)

  @ColumnTypeConverter fun stringToLevelConfig(l: String): LevelConfig.Chords = RoomJson.decode(l)

  @ColumnTypeConverter fun chordSizeRangeToString(r: ChordSizeRange): String = RoomJson.encode(r)

  @ColumnTypeConverter fun stringToChordSizeRange(r: String): ChordSizeRange = RoomJson.decode(r)
}
