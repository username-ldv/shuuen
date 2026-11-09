package ldv.shuuen.features.training.level

import kotlinx.coroutines.flow.Flow

/** The user's own Chords levels, stored on the device. */
interface ChordsLocalLevelRepository {
  /** Every stored level; emits again after any change to them or their contexts. */
  fun observeLevels(): Flow<List<ChordsLevel>>

  /** The level with [id], or null when there is none. */
  suspend fun getLevel(id: String): ChordsLevel?

  suspend fun upsertLevel(level: ChordsLevel)

  suspend fun deleteLevel(id: String)
}
