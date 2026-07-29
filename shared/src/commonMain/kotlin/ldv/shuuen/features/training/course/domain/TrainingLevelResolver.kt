package ldv.shuuen.features.training.course.domain

import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.single.domain.SinglesLevel

interface TrainingLevelResolver {
  suspend fun resolveSingles(encodedReference: String): SinglesLevel

  suspend fun resolveMelodies(encodedReference: String): MelodiesLevel

  suspend fun resolveChords(encodedReference: String): ChordsLevel
}
