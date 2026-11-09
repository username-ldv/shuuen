package ldv.shuuen.features.training.level.storage

import androidx.room3.ColumnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.features.context.ContextDbEntity
import ldv.shuuen.features.context.toDomainEntity
import ldv.shuuen.features.training.level.LevelConfig
import ldv.shuuen.features.training.level.LevelSource
import ldv.shuuen.features.training.level.SinglesLevel

@ColumnTypeConverters(SinglesLevelTypeConverter::class)
@Entity(
  tableName = "levels_singles",
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
data class SinglesLevelDbEntity(
  @PrimaryKey val id: String,
  val name: String,
  val config: LevelConfig.Singles,
  val contextId: String?,
  val source: LevelSource,
  val questionsNumber: Int?,
  val range: NoteRange,
)

/** A stored level read together with its context, in one transaction. */
data class SinglesLevelWithContext(
  @Embedded val level: SinglesLevelDbEntity,
  @Relation(parentColumns = ["contextId"], entityColumns = ["id"]) val context: ContextDbEntity?,
)

fun SinglesLevelWithContext.toLevel(): SinglesLevel = level.toLevel(context?.toDomainEntity())

fun SinglesLevelDbEntity.toLevel(context: DegreeContext?): SinglesLevel =
  SinglesLevel(
    id = id,
    name = name,
    levelConfig = config,
    context = context,
    source = source,
    questionsNumber = questionsNumber,
    range = range,
  )

fun SinglesLevel.toDbEntity(): SinglesLevelDbEntity =
  SinglesLevelDbEntity(
    id = id,
    name = name,
    config = levelConfig,
    contextId = context?.id,
    source = source,
    questionsNumber = questionsNumber,
    range = range,
  )
