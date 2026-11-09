package ldv.shuuen.features.training.course

import ldv.shuuen.features.training.level.ChordsLevel
import ldv.shuuen.features.training.level.MelodiesLevel
import ldv.shuuen.features.training.level.SinglesLevel

interface TrainingLevelResolver {
  suspend fun resolveSingles(encodedReference: String): SinglesLevel

  suspend fun resolveMelodies(encodedReference: String): MelodiesLevel

  suspend fun resolveChords(encodedReference: String): ChordsLevel
}
