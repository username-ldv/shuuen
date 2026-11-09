package ldv.shuuen.features.training.level.storage

import androidx.room3.ColumnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.features.context.ContextDbEntity
import ldv.shuuen.features.context.toDomainEntity
import ldv.shuuen.features.training.level.LevelConfig
import ldv.shuuen.features.training.level.LevelSource
import ldv.shuuen.features.training.level.MelodiesLevel

@ColumnTypeConverters(MelodiesLevelTypeConverter::class)
@Entity(
  tableName = "levels_melodies",
  foreignKeys =
    [
      ForeignKey(
        entity = ContextDbEntity::class,
        parentColumns = ["id"],
        childColumns = ["contextId"],
        onDelete = ForeignKey.SET_NULL,
      )
    ],
  indices = [Index("contextId")],
)
data class MelodiesLevelDbEntity(
  @PrimaryKey val id: String,
  val name: String,
  val config: LevelConfig.Melodies,
  val contextId: String?,
  val source: LevelSource,
)

/** A stored level read together with its context, in one transaction. */
data class MelodiesLevelWithContext(
  @Embedded val level: MelodiesLevelDbEntity,
  @Relation(parentColumns = ["contextId"], entityColumns = ["id"]) val context: ContextDbEntity?,
)

fun MelodiesLevelWithContext.toLevel(): MelodiesLevel = level.toLevel(context?.toDomainEntity())

fun MelodiesLevelDbEntity.toLevel(context: DegreeContext?): MelodiesLevel =
  MelodiesLevel(
    id = id,
    name = name,
    config = config,
    context = context,
    source = source,
  )

fun MelodiesLevel.toDbEntity(): MelodiesLevelDbEntity =
  MelodiesLevelDbEntity(
    id = id,
    name = name,
    config = config,
    contextId = context?.id,
    source = source,
  )
