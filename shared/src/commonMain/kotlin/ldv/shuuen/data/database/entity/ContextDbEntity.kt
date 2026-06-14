package ldv.shuuen.data.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import ldv.shuuen.domain.audio.music.DegreeContextNode
import ldv.shuuen.domain.training.context.ContextSource

// in the future, this will be both Absolute and Relative types of Context
// but the current UI is only relative so
@Entity(tableName = "context")
data class ContextDbEntity(
  @PrimaryKey
  val id: String,
  val name: String?,
  val source: ContextSource,
  val nodes: List<DegreeContextNode>
)