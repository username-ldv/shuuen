package ldv.shuuen.features.training.level.storage

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson
import ldv.shuuen.features.training.level.LevelConfig

class MelodiesLevelTypeConverter {
  @ColumnTypeConverter
  fun melodiesConfigToString(l: LevelConfig.Melodies): String = RoomJson.encode(l)

  @ColumnTypeConverter
  fun stringToMelodiesConfig(l: String): LevelConfig.Melodies = RoomJson.decode(l)
}
