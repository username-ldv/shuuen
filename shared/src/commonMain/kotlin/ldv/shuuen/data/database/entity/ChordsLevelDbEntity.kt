package ldv.shuuen.data.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource

@Entity(
  tableName = "levels_chords", foreignKeys = [ForeignKey(
    entity = ContextDbEntity::class,
    parentColumns = ["id"],
    childColumns = ["contextId"],
    onDelete = ForeignKey.SET_NULL
  )], indices = [Index("contextId")]
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
