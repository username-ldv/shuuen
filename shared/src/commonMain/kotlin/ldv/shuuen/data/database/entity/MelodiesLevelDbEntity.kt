package ldv.shuuen.data.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource

@Entity(
  tableName = "levels_melodies", foreignKeys = [ForeignKey(
    entity = ContextDbEntity::class,
    parentColumns = ["id"],
    childColumns = ["contextId"],
    onDelete = ForeignKey.SET_NULL
  )], indices = [Index("contextId")]
)
data class MelodiesLevelDbEntity(
  @PrimaryKey val id: String,
  val name: String,
  val config: LevelConfig.Melodies,
  val contextId: String?,
  val source: LevelSource,
)
