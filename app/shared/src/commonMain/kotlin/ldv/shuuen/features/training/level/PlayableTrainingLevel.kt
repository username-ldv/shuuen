package ldv.shuuen.features.training.level

/** A level of any training flow, as one value. */
sealed interface PlayableTrainingLevel {
  val id: String

  data class Singles(val level: SinglesLevel) : PlayableTrainingLevel {
    override val id: String
      get() = level.id
  }

  data class Melodies(val level: MelodiesLevel) : PlayableTrainingLevel {
    override val id: String
      get() = level.id
  }

  data class Chords(val level: ChordsLevel) : PlayableTrainingLevel {
    override val id: String
      get() = level.id
  }
}
