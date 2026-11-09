package ldv.shuuen.features.training.level.storage

import androidx.room3.ColumnTypeConverter
import ldv.shuuen.core.database.RoomJson
import ldv.shuuen.features.training.level.LevelConfig

class SinglesLevelTypeConverter {
  @ColumnTypeConverter fun levelConfigToString(l: LevelConfig.Singles): String = RoomJson.encode(l)

  @ColumnTypeConverter fun stringToLevelConfig(l: String): LevelConfig.Singles = RoomJson.decode(l)
}
