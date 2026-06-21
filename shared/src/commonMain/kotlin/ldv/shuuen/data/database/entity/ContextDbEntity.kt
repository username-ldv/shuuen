package ldv.shuuen.data.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.ContextSource

// in the future, this will be both Absolute and Relative types of Context
// but the current UI is only relative so
@Entity(tableName = "context")
data class ContextDbEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val source: ContextSource,
    val nodes: List<DegreeContextNode>,
)

fun DegreeContext.toDbEntity(): ContextDbEntity {
  return ContextDbEntity(
      id = this.id,
      name = this.name,
      source = this.source,
      nodes = this.nodes,
  )
}

fun ContextDbEntity.toDomainEntity(): DegreeContext {
  return DegreeContext(
      id = this.id,
      source = this.source,
      nodes = this.nodes,
      name = this.name,
  )
}
