package ldv.shuuen.features.training.level

import kotlinx.coroutines.flow.Flow

/** The user's own Singles levels, stored on the device. */
interface SinglesLocalLevelRepository {
  /** Every stored level; emits again after any change to them or their contexts. */
  fun observeLevels(): Flow<List<SinglesLevel>>

  /** The level with [id], or null when there is none. */
  suspend fun getLevel(id: String): SinglesLevel?

  suspend fun upsertLevel(level: SinglesLevel)

  suspend fun deleteLevel(id: String)
}
