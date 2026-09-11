package ldv.shuuen.features.training.single.domain

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class SinglesLevel(
  val id: String,
  val name: String,
  val levelConfig: LevelConfig.Singles,
  val context: DegreeContext?,
  val source: LevelSource,
  val questionsNumber: Int?,
  val range: NoteRange
)