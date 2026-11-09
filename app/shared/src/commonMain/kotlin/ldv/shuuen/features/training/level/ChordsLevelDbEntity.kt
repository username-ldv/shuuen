package ldv.shuuen.features.training.level

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

@ColumnTypeConverters(ChordsLevelTypeConverter::class)
@Entity(
  tableName = "levels_chords",
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
data class ChordsLevelDbEntity(
  @PrimaryKey val id: String,
  val name: String,
  val config: LevelConfig.Chords,
  val contextId: String?,
  val source: LevelSource,
  val questionsNumber: Int?,
  val range: NoteRange,
  val chordSize: ChordSizeRange,
  val sustainNotes: Boolean,
  val answerOrder: ChordAnswerOrder,
)

/** A stored level read together with its context, in one transaction. */
data class ChordsLevelWithContext(
  @Embedded val level: ChordsLevelDbEntity,
  @Relation(parentColumns = ["contextId"], entityColumns = ["id"]) val context: ContextDbEntity?,
)

fun ChordsLevelWithContext.toLevel(): ChordsLevel = level.toLevel(context?.toDomainEntity())

fun ChordsLevelDbEntity.toLevel(context: DegreeContext?): ChordsLevel =
  ChordsLevel(
    id = id,
    name = name,
    levelConfig = config,
    context = context,
    source = source,
    questionsNumber = questionsNumber,
    range = range,
    chordSize = chordSize,
    sustainNotes = sustainNotes,
    answerOrder = answerOrder,
  )

fun ChordsLevel.toDbEntity(): ChordsLevelDbEntity =
  ChordsLevelDbEntity(
    id = id,
    name = name,
    config = levelConfig,
    contextId = context?.id,
    source = source,
    questionsNumber = questionsNumber,
    range = range,
    chordSize = chordSize,
    sustainNotes = sustainNotes,
    answerOrder = answerOrder,
  )
