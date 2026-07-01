package ldv.shuuen.data.database.converters

import androidx.room3.TypeConverter
import ldv.shuuen.data.database.RoomJson
import ldv.shuuen.features.training.domain.LevelConfig

class MelodiesLevelTypeConverter {
  @TypeConverter
  fun melodiesConfigToString(l: LevelConfig.Melodies): String = RoomJson.encode(l)

  @TypeConverter
  fun stringToMelodiesConfig(l: String): LevelConfig.Melodies = RoomJson.decode(l)
}
