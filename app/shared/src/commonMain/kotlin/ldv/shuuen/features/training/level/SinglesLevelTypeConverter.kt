package ldv.shuuen.features.training.level

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson

class SinglesLevelTypeConverter {
  @ColumnTypeConverter fun levelConfigToString(l: LevelConfig.Singles): String = RoomJson.encode(l)

  @ColumnTypeConverter fun stringToLevelConfig(l: String): LevelConfig.Singles = RoomJson.decode(l)
}
