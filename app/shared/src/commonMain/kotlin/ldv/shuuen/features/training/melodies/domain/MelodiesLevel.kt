package ldv.shuuen.features.training.melodies.domain

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource

/**
 * A melody training level: either randomly generated sequences from a scale
 * ([LevelConfig.Melodies.Random]) or a melody read from a MIDI file ([LevelConfig.Melodies.Midi]).
 */
@Serializable
data class MelodiesLevel(
  val id: String,
  val name: String,
  val config: LevelConfig.Melodies,
  val context: DegreeContext?,
  val source: LevelSource,
)
