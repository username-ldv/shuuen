package ldv.shuuen.features.training.single.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class SinglesLevel(
  val id: String,
  val name: String,
  val levelConfig: LevelConfig.Singles,
  val context: DegreeContext?,
  val source: LevelSource,
  val questionsNumber: Int?,
  val range: NoteRange,
)
