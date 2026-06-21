package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.data.database.RoomJson
import ldv.shuuen.features.training.domain.LevelConfig

class SinglesLevelTypeConverter {
  @TypeConverter
  fun levelConfigToString(l: LevelConfig.Singles): String = RoomJson.encode(l)

  @TypeConverter
  fun stringToLevelConfig(l: String): LevelConfig.Singles = RoomJson.decode(l)
}