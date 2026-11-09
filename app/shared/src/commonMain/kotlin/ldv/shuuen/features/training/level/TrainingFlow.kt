package ldv.shuuen.features.training.level

enum class TrainingFlow {
  Singles,
  Melodies,
  Chords,
}

/** The flow's name in the backend API: routes, level kinds and sync payloads. */
val TrainingFlow.apiName: String
  get() = name.lowercase()
